package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 책BTI 분석 오케스트레이션 서비스(Phase 3) — 사실 집계(Phase 2) + LLM 서술 + 폴백 결합.
 *
 * <p>흐름: {@link ReadingProfileService}로 사실(프로필)을 집계 → {@link ReadingPersonalityNarrator}로 서술 생성
 * → 서술이 나오면 {@link ReadingPersonality}(프로필+서술), 못 나오면(비활성/실패) <b>사실만</b> 담은 폴백 결과.
 * 캐시/저장은 다음 단계(Phase 4)가 이 서비스 위에 얹는다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingPersonalityService {

    private final ReadingProfileService profileService;
    private final ReadingPersonalityNarrator narrator;

    public ReadingPersonalityService(ReadingProfileService profileService, ReadingPersonalityNarrator narrator) {
        this.profileService = profileService;
        this.narrator = narrator;
    }

    /** 사용자의 책BTI 결과(사실 + 가능하면 서술)를 만든다. 서술을 못 만들면 사실만 담은 폴백. */
    public ReadingPersonality analyze(User user) {
        ReadingProfile profile = profileService.profileOf(user);
        return narrator.narrate(profile)
                .map(narration -> new ReadingPersonality(profile, narration))
                .orElseGet(() -> ReadingPersonality.factsOnly(profile));
    }
}
