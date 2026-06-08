package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 책BTI 분석 오케스트레이션 서비스 — 사실 집계(Phase 2) + LLM 서술(Phase 3) + 저장·캐시·갱신(Phase 4)
 * + 책방 공개용(Phase 6).
 *
 * <p>{@link #analyze}는 매번 LLM을 부르는 "항상 새로" 경로다. 실사용 진입점은 캐시를 활용하는 두 갈래다:
 * <ul>
 *   <li>{@link #analyzeCached} — <b>본인용</b>: 전체(완독) 책 기반, 본인만 봄(reading_personality 테이블).</li>
 *   <li>{@link #analyzePublicCached} — <b>책방 공개용</b>: 공개(PUBLIC)+완독 책만, 남에게 보임
 *       (reading_personality_public 테이블, §7 누출 차단).</li>
 * </ul>
 * 두 갈래는 입력 책 범위와 저장소만 다르고 캐시·재생성·콜드스타트·폴백 규칙은 동일하다 — 그 공통 규칙을
 * {@link #analyzeWithCache}에 한 번만 두고 {@link CacheStore}로 저장소만 갈아끼운다(불변식 드리프트 방지).
 */
@Service
@Transactional(readOnly = true)
public class ReadingPersonalityService {

    /** 콜드스타트 임계 — <b>완독 책</b>이 이 미만이면 신호가 부족해 분석을 보류한다(reading-personality-design §6, 잠정 5권). */
    public static final int COLD_START_MIN_BOOKS = 5;

    /** 태그를 한 컬럼에 이어 붙일 때 쓰는 구분자(개행) — 태그 안에 잘 안 나오는 문자. */
    private static final String TAG_DELIMITER = "\n";

    private final ReadingProfileService profileService;
    private final ReadingPersonalityNarrator narrator;
    private final ReadingPersonalityCacheRepository cacheRepository;
    private final PublicReadingPersonalityCacheRepository publicCacheRepository;
    private final Clock clock;

    public ReadingPersonalityService(ReadingProfileService profileService,
                                     ReadingPersonalityNarrator narrator,
                                     ReadingPersonalityCacheRepository cacheRepository,
                                     PublicReadingPersonalityCacheRepository publicCacheRepository,
                                     Clock clock) {
        this.profileService = profileService;
        this.narrator = narrator;
        this.cacheRepository = cacheRepository;
        this.publicCacheRepository = publicCacheRepository;
        this.clock = clock;
    }

    /** 사용자의 책BTI 결과(사실 + 가능하면 서술)를 <b>항상 새로</b> 만든다(캐시 미사용). 서술 실패 시 사실만 담은 폴백. */
    public ReadingPersonality analyze(User user) {
        ReadingProfile profile = profileService.profileOf(user);
        return narrator.narrate(profile)
                .map(narration -> new ReadingPersonality(profile, narration))
                .orElseGet(() -> ReadingPersonality.factsOnly(profile));
    }

    /**
     * 사용자의 <b>본인용</b> 책BTI 결과를 캐시를 활용해 만든다 — 본인 화면 진입점(전체 책 기반).
     *
     * @param force "다시 분석" 등 강제 재생성이면 true(시그니처가 같아도 LLM 재호출)
     */
    @Transactional(propagation = Propagation.SUPPORTS) // LLM 호출을 트랜잭션 밖으로(커넥션 점유 회피)
    public ReadingPersonality analyzeCached(User user, boolean force) {
        return analyzeWithCache(profileService.profileOf(user), ownerStore(user), force);
    }

    /**
     * 사용자의 <b>책방 공개용</b> 책BTI 결과를 캐시를 활용해 만든다 — 공개(PUBLIC)+완독 책만(§7 누출 차단).
     *
     * <p>본인용과 입력(공개 책)·저장소(공개 캐시 테이블)만 다르고 캐시 규칙은 동일하다. 생성/재생성(LLM 호출)은
     * 소유자 행동(토글 ON·"다시 분석")에서만 호출하고, 방문자 조회는 캐시를 읽기만 한다(비용 안전장치).
     *
     * @param force 강제 재생성이면 true
     */
    @Transactional(propagation = Propagation.SUPPORTS) // LLM 호출을 트랜잭션 밖으로(커넥션 점유 회피)
    public ReadingPersonality analyzePublicCached(User user, boolean force) {
        return analyzeWithCache(profileService.publicProfileOf(user), publicStore(user), force);
    }

    /**
     * 캐시 기반 분석의 공통 규칙(본인용/공개용 공유): (1) 콜드스타트 보류, (2) 캐시 히트 시 LLM 생략,
     * (3) force·캐시없음·시그니처 변동 시 재생성, (4) LLM 실패 시 직전 캐시(stale)를 내보내거나 사실만 폴백하되
     * 기존 캐시는 덮어쓰지 않는다(serve-stale-on-error, N-060).
     */
    private ReadingPersonality analyzeWithCache(ReadingProfile profile, CacheStore store, boolean force) {
        // (1) 콜드스타트 — 완독 책 부족: LLM·캐시 없이 사실만 보류(성향은 완독 책에서만 뽑음)
        if (profile.finishedBooks() < COLD_START_MIN_BOOKS) {
            return ReadingPersonality.factsOnly(profile);
        }

        String signature = ProfileSignature.of(profile);
        Optional<CachedNarration> cached = store.find();

        // (2) 캐시 히트 — 강제 아니고 시그니처 일치: LLM 건너뜀
        if (!force && cached.isPresent() && signature.equals(cached.get().signature())) {
            return new ReadingPersonality(profile, cached.get().narration());
        }

        // (3) 재생성 — force거나, 캐시 없거나, 시그니처 변동
        Optional<PersonalityNarration> fresh = narrator.narrate(profile);
        if (fresh.isEmpty()) {
            // LLM 실패/빈응답 → 직전 캐시(stale)가 있으면 그걸 내보내 빈 화면을 막는다(serve-stale-on-error, N-060).
            // 없으면 사실만 폴백. 어느 경우든 기존 캐시 행은 덮어쓰지 않는다(실패가 좋은 캐시를 망가뜨리지 않게).
            return cached
                    .map(c -> new ReadingPersonality(profile, c.narration()))
                    .orElseGet(() -> ReadingPersonality.factsOnly(profile));
        }
        PersonalityNarration narration = fresh.get();
        store.upsert(narration, signature, Instant.now(clock));
        return new ReadingPersonality(profile, narration);
    }

    /** 캐시 한 행을 서비스가 다루는 형태로(서술 + 시그니처)로 옮긴 read view. */
    private record CachedNarration(PersonalityNarration narration, String signature) {
    }

    /**
     * 저장소 추상화 — 본인용/공개용이 같은 캐시 규칙을 공유하되 실제 테이블만 갈아끼우게 한다.
     * 엔티티 타입이 달라(공유 슈퍼타입 없음) 인터페이스로 find/upsert만 노출한다.
     */
    private interface CacheStore {
        Optional<CachedNarration> find();

        void upsert(PersonalityNarration narration, String signature, Instant generatedAt);
    }

    /** 본인용 캐시 저장소(reading_personality). */
    private CacheStore ownerStore(User user) {
        return new CacheStore() {
            @Override
            public Optional<CachedNarration> find() {
                return cacheRepository.findByUser(user)
                        .map(c -> new CachedNarration(
                                new PersonalityNarration(c.getNarrative(), splitTags(c.getTags())),
                                c.getInputSignature()));
            }

            @Override
            public void upsert(PersonalityNarration narration, String signature, Instant generatedAt) {
                String joinedTags = String.join(TAG_DELIMITER, narration.tags());
                ReadingPersonalityCache existing = cacheRepository.findByUser(user).orElse(null);
                if (existing == null) {
                    cacheRepository.save(ReadingPersonalityCache.create(
                            user, narration.narrative(), joinedTags, signature, generatedAt));
                } else {
                    existing.refresh(narration.narrative(), joinedTags, signature, generatedAt);
                    cacheRepository.save(existing);
                }
            }
        };
    }

    /** 책방 공개용 캐시 저장소(reading_personality_public). */
    private CacheStore publicStore(User user) {
        return new CacheStore() {
            @Override
            public Optional<CachedNarration> find() {
                return publicCacheRepository.findByUser(user)
                        .map(c -> new CachedNarration(
                                new PersonalityNarration(c.getNarrative(), splitTags(c.getTags())),
                                c.getInputSignature()));
            }

            @Override
            public void upsert(PersonalityNarration narration, String signature, Instant generatedAt) {
                String joinedTags = String.join(TAG_DELIMITER, narration.tags());
                PublicReadingPersonalityCache existing = publicCacheRepository.findByUser(user).orElse(null);
                if (existing == null) {
                    publicCacheRepository.save(PublicReadingPersonalityCache.create(
                            user, narration.narrative(), joinedTags, signature, generatedAt));
                } else {
                    existing.refresh(narration.narrative(), joinedTags, signature, generatedAt);
                    publicCacheRepository.save(existing);
                }
            }
        };
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String t : tags.split(TAG_DELIMITER)) {
            String s = t.strip();
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
