package com.booktimer.personality;

import java.util.List;

/**
 * LLM이 생성한 독서 성향 산출물(책BTI Phase 3) — 사실(독서 프로필)을 해석한 자연어 결과.
 *
 * @param narrative MBTI 설명문체의 한 문단 — 사람이 읽는 성향 서술(v1 본인 노출)
 * @param tags      비교/매칭용 짧은 태그(예: 잡식·완독러·정독형). v1은 저장만, 노출은 후속 매칭 단계
 */
public record PersonalityNarration(String narrative, List<String> tags) {

    public PersonalityNarration {
        // 태그는 null 안전 + 불변 복사 — 비교/저장에서 일관되게 다룬다.
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }
}
