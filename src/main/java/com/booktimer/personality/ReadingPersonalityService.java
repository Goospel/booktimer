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
 * 책BTI 분석 오케스트레이션 서비스 — 사실 집계(Phase 2) + LLM 서술(Phase 3) + 저장·캐시·갱신(Phase 4).
 *
 * <p><b>성향은 공개(PUBLIC)+완독 책만으로</b> 뽑는다({@link ReadingProfileService#publicProfileOf}) — 책BTI는
 * 사용자끼리 즐기는 재미 요소라 항상 책방(공개 프로필)에 노출되므로, 비공개 책 취향이 새지 않도록 입력 자체를
 * 공개 책으로 한정한다(설계 §7, 공개/비공개 분기 폐지 2026-06-08). 본인 {@code /personality}와 책방
 * {@code /u/{loginId}}가 같은 단일 캐시(reading_personality)를 본다.
 *
 * <p>{@link #analyze}는 매번 LLM을 부르는 "항상 새로" 경로, {@link #analyzeCached}가 실사용 진입점(캐시 활용)이다.
 * 생성/재생성(LLM 호출)은 소유자 행동(페이지 방문·"다시 분석")에서만 일어나고, 방문자의 책방 조회는 캐시를
 * 읽기만 한다(비용 안전장치 — {@code ProfileService}).
 */
@Service
@Transactional(readOnly = true)
public class ReadingPersonalityService {

    /**
     * 콜드스타트 임계 — <b>(공개) 완독 책</b>이 이 미만이면 분석을 보류한다(reading-personality-design §6).
     * <p>1권으로 설정(2026-06-08): 정확도는 낮아도 "어떤 결과라도 보여주는 재미" + "책을 쌓을수록 결과가
     * 바뀌는 재미"를 우선(사용자 결정). 완독 0권만 보류한다.
     */
    public static final int COLD_START_MIN_BOOKS = 1;

    /** 태그를 한 컬럼에 이어 붙일 때 쓰는 구분자(개행) — 태그 안에 잘 안 나오는 문자. */
    private static final String TAG_DELIMITER = "\n";

    private final ReadingProfileService profileService;
    private final ReadingPersonalityNarrator narrator;
    private final ReadingPersonalityCacheRepository cacheRepository;
    private final Clock clock;

    public ReadingPersonalityService(ReadingProfileService profileService,
                                     ReadingPersonalityNarrator narrator,
                                     ReadingPersonalityCacheRepository cacheRepository,
                                     Clock clock) {
        this.profileService = profileService;
        this.narrator = narrator;
        this.cacheRepository = cacheRepository;
        this.clock = clock;
    }

    /** 사용자의 책BTI 결과(사실 + 가능하면 서술)를 <b>항상 새로</b> 만든다(캐시 미사용, 공개 책 기반). 서술 실패 시 사실만 폴백. */
    public ReadingPersonality analyze(User user) {
        ReadingProfile profile = profileService.publicProfileOf(user);
        return narrator.narrate(profile)
                .map(narration -> new ReadingPersonality(profile, narration))
                .orElseGet(() -> ReadingPersonality.factsOnly(profile));
    }

    /**
     * 사용자의 책BTI 결과를 캐시를 활용해 만든다 — 실사용 진입점(공개 책 기반, 단일 캐시 reading_personality).
     *
     * <p>규칙: (1) 콜드스타트 보류, (2) 캐시 히트 시 LLM 생략, (3) force·캐시없음·시그니처 변동 시 재생성,
     * (4) LLM 실패 시 직전 캐시(stale)를 내보내거나 사실만 폴백하되 기존 캐시는 덮어쓰지 않는다
     * (serve-stale-on-error, N-060).
     *
     * @param force "다시 분석" 등 강제 재생성이면 true(시그니처가 같아도 LLM 재호출)
     */
    @Transactional(propagation = Propagation.SUPPORTS) // LLM 호출을 트랜잭션 밖으로(커넥션 점유 회피)
    public ReadingPersonality analyzeCached(User user, boolean force) {
        ReadingProfile profile = profileService.publicProfileOf(user);

        // (1) 콜드스타트 — 공개 완독 책 부족: LLM·캐시 없이 사실만 보류
        if (profile.finishedBooks() < COLD_START_MIN_BOOKS) {
            return ReadingPersonality.factsOnly(profile);
        }

        String signature = ProfileSignature.of(profile);
        Optional<CachedNarration> cached = findCached(user);

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
        upsertCache(user, narration, signature, Instant.now(clock));
        return new ReadingPersonality(profile, narration);
    }

    /** 캐시 한 행을 서비스가 다루는 형태로(서술 + 시그니처)로 옮긴 read view. */
    private record CachedNarration(PersonalityNarration narration, String signature) {
    }

    private Optional<CachedNarration> findCached(User user) {
        return cacheRepository.findByUser(user)
                .map(c -> new CachedNarration(
                        new PersonalityNarration(c.getNarrative(), splitTags(c.getTags())),
                        c.getInputSignature()));
    }

    private void upsertCache(User user, PersonalityNarration narration, String signature, Instant generatedAt) {
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
