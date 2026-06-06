package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 책BTI 분석 오케스트레이션 서비스 — 사실 집계(Phase 2) + LLM 서술(Phase 3) + 저장·캐시·갱신(Phase 4).
 *
 * <p>{@link #analyze}는 매번 LLM을 부르는 "항상 새로" 경로다. 실사용 진입점은 {@link #analyzeCached}로,
 * 캐시가 유효하면(입력 시그니처 일치) LLM을 건너뛰고, 책장이 의미있게 변했거나 "다시 분석" 요청 시에만 재생성한다.
 * 책이 너무 적으면(콜드스타트) 분석을 보류한다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingPersonalityService {

    /** 콜드스타트 임계 — 이 미만이면 신호가 부족해 분석을 보류한다(reading-personality-design §6, 잠정 5권). */
    public static final int COLD_START_MIN_BOOKS = 5;

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

    /** 사용자의 책BTI 결과(사실 + 가능하면 서술)를 <b>항상 새로</b> 만든다(캐시 미사용). 서술 실패 시 사실만 담은 폴백. */
    public ReadingPersonality analyze(User user) {
        ReadingProfile profile = profileService.profileOf(user);
        return narrator.narrate(profile)
                .map(narration -> new ReadingPersonality(profile, narration))
                .orElseGet(() -> ReadingPersonality.factsOnly(profile));
    }

    /**
     * 사용자의 책BTI 결과를 <b>캐시를 활용해</b> 만든다 — 실사용 진입점.
     *
     * @param force "다시 분석" 등 강제 재생성이면 true(시그니처가 같아도 LLM 재호출)
     */
    @Transactional // 캐시를 쓰므로 읽기전용 아님(클래스 기본을 override)
    public ReadingPersonality analyzeCached(User user, boolean force) {
        ReadingProfile profile = profileService.profileOf(user);

        // (1) 콜드스타트 — 신호 부족: LLM·캐시 없이 사실만 보류
        if (profile.totalBooks() < COLD_START_MIN_BOOKS) {
            return ReadingPersonality.factsOnly(profile);
        }

        String signature = ProfileSignature.of(profile);
        Optional<ReadingPersonalityCache> cached = cacheRepository.findByUser(user);

        // (2) 캐시 히트 — 강제 아니고 시그니처 일치: LLM 건너뜀
        if (!force && cached.isPresent() && signature.equals(cached.get().getInputSignature())) {
            return new ReadingPersonality(profile, toNarration(cached.get()));
        }

        // (3) 재생성 — force거나, 캐시 없거나, 시그니처 변동
        Optional<PersonalityNarration> fresh = narrator.narrate(profile);
        if (fresh.isEmpty()) {
            return ReadingPersonality.factsOnly(profile); // LLM 실패 → 폴백(기존 캐시는 건드리지 않음)
        }
        PersonalityNarration narration = fresh.get();
        upsert(user, cached.orElse(null), narration, signature);
        return new ReadingPersonality(profile, narration);
    }

    /** 캐시 저장/갱신 — 기존 행이 있으면 같은 행을 갱신(user_id unique 보존), 없으면 새로 만든다. */
    private void upsert(User user, ReadingPersonalityCache existing, PersonalityNarration narration, String signature) {
        String joinedTags = String.join(TAG_DELIMITER, narration.tags());
        Instant now = Instant.now(clock);
        if (existing == null) {
            cacheRepository.save(
                    ReadingPersonalityCache.create(user, narration.narrative(), joinedTags, signature, now));
        } else {
            existing.refresh(narration.narrative(), joinedTags, signature, now);
            cacheRepository.save(existing);
        }
    }

    private PersonalityNarration toNarration(ReadingPersonalityCache cache) {
        return new PersonalityNarration(cache.getNarrative(), splitTags(cache.getTags()));
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
