package com.booktimer.personality;

/**
 * 책BTI 분석 결과 — 사실(독서 프로필) + 해석(LLM 서술)을 묶은 read model(책BTI Phase 3).
 *
 * <p>{@code narration}이 null이면 <b>폴백</b> 상태다 — LLM 비활성/실패/콜드스타트 등으로 서술을 못 만든 경우.
 * 이때 화면(Phase 5)은 사실(프로필)만 보여주고 "잠시 후 다시 분석"을 안내한다(reading-personality-design §4).
 *
 * @param profile   코드가 집계한 결정적 사실(항상 존재)
 * @param narration LLM 서술 + 태그. 폴백이면 null
 */
public record ReadingPersonality(ReadingProfile profile, PersonalityNarration narration) {

    /** 서술이 있는가 — false면 폴백(사실만 표시). */
    public boolean hasNarration() {
        return narration != null;
    }

    /** 사실만 있는 폴백 결과를 만든다(LLM 없이). */
    public static ReadingPersonality factsOnly(ReadingProfile profile) {
        return new ReadingPersonality(profile, null);
    }
}
