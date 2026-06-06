package com.booktimer.web;

import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingProfile;

import java.util.List;

/**
 * 책BTI 화면 표시 모델(책BTI Phase 5) — 분석 결과를 화면이 그릴 3가지 상태로 분류한다.
 *
 * <p>같은 "서술 없음"이라도 이유가 다르면 화면 문구가 다르다:
 * <ul>
 *   <li>{@code READY} — 서술 있음(정상 노출)</li>
 *   <li>{@code COLD_START} — 책이 임계 미만이라 분석 보류("조금 더 읽으면 성향이 보여요")</li>
 *   <li>{@code FALLBACK} — 책은 충분하나 LLM 실패/지연("잠시 후 다시 분석")</li>
 * </ul>
 *
 * @param state            화면 상태
 * @param narrative        성향 서술(READY일 때만, 아니면 null)
 * @param tags             태그(v1은 화면 비노출 — 저장만)
 * @param profile          집계된 사실(항상 존재 — 콜드스타트·폴백에서도 요약 표시)
 * @param coldStartMinBooks 콜드스타트 임계(안내 문구의 "최소 N권"에 쓰임)
 */
public record PersonalityView(State state, String narrative, List<String> tags,
                              ReadingProfile profile, int coldStartMinBooks) {

    public enum State { READY, COLD_START, FALLBACK }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isColdStart() {
        return state == State.COLD_START;
    }

    public boolean isFallback() {
        return state == State.FALLBACK;
    }

    /** 분석 결과 + 콜드스타트 임계로 화면 표시 모델을 만든다. */
    public static PersonalityView from(ReadingPersonality result, int coldStartMinBooks) {
        ReadingProfile profile = result.profile();
        if (result.hasNarration()) {
            return new PersonalityView(State.READY, result.narration().narrative(),
                    result.narration().tags(), profile, coldStartMinBooks);
        }
        // 서술 없음 — 책 부족이면 콜드스타트, 충분하면 LLM 실패(폴백)
        State state = profile.totalBooks() < coldStartMinBooks ? State.COLD_START : State.FALLBACK;
        return new PersonalityView(state, null, List.of(), profile, coldStartMinBooks);
    }
}
