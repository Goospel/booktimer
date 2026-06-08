package com.booktimer.web;

import com.booktimer.personality.PersonalityHistoryEntry;
import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingProfile;

import java.util.List;

/**
 * 책BTI 화면 표시 모델(책BTI Phase 5) — 분석 결과를 화면이 그릴 3가지 상태로 분류하고, 히스토리(최대 3개)를 싣는다.
 *
 * <p>같은 "서술 없음"이라도 이유가 다르면 화면 문구가 다르다:
 * <ul>
 *   <li>{@code READY} — 대표 서술 있음(정상 노출). {@link #entries()}에 최대 3개의 과거 분석이 최신순으로 담긴다.</li>
 *   <li>{@code COLD_START} — 책이 임계 미만이라 분석 보류("조금 더 읽으면 성향이 보여요")</li>
 *   <li>{@code FALLBACK} — 책은 충분하나 LLM 실패/지연("잠시 후 다시 분석")</li>
 * </ul>
 * COLD_START·FALLBACK이면 아직 저장된 분석이 없어 {@link #entries()}는 비어 있다.
 *
 * @param state             화면 상태
 * @param narrative         대표 성향 서술(READY일 때만, 아니면 null)
 * @param tags              대표 태그(v1은 화면 비노출 — 저장만)
 * @param profile           집계된 사실(항상 존재 — 콜드스타트·폴백에서도 요약 표시)
 * @param coldStartMinBooks 콜드스타트 임계(안내 문구의 "최소 N권"에 쓰임)
 * @param entries           분석 히스토리(최신순, 최대 3개) — 각 카드에 대표 여부·stale 표시
 */
public record PersonalityView(State state, String narrative, List<String> tags,
                              ReadingProfile profile, int coldStartMinBooks,
                              List<PersonalityHistoryEntry> entries) {

    public PersonalityView {
        entries = (entries == null) ? List.of() : List.copyOf(entries);
    }

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

    /** 분석 결과 + 히스토리 + 콜드스타트 임계로 화면 표시 모델을 만든다. */
    public static PersonalityView from(ReadingPersonality result, List<PersonalityHistoryEntry> entries,
                                       int coldStartMinBooks) {
        ReadingProfile profile = result.profile();
        if (result.hasNarration()) {
            return new PersonalityView(State.READY, result.narration().narrative(),
                    result.narration().tags(), profile, coldStartMinBooks, entries);
        }
        // 서술 없음 — 완독 책 부족이면 콜드스타트, 충분하면 LLM 실패(폴백). 성향은 완독 책에서만 뽑으므로 완독 권수로 판정.
        State state = profile.finishedBooks() < coldStartMinBooks ? State.COLD_START : State.FALLBACK;
        return new PersonalityView(state, null, List.of(), profile, coldStartMinBooks, List.of());
    }
}
