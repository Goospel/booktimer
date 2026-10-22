package com.booktimer.writing;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 글이 받은 AI 질문의 기록 — {@code writing_draft.questions_json}의 모양(v1 REQ-46).
 *
 * <p>PR-B는 형만 만든다(늘 빈 기록). 라운드를 채우는 것은 PR-C의 AI 단계다.
 *
 * @param rounds 먼저 받은 것부터 — 비어 있어도 {@code null}이 아니다(화면이 {@code null}을 다루지 않게)
 */
public record QuestionLog(List<Round> rounds) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * AI 한 번의 결과.
     *
     * @param focus     FOLLOW가 답하던 질문(없으면 {@code null})
     * @param bodyChars 그때 글의 글자 수
     */
    public record Round(Step step, Instant at, String focus, int bodyChars, List<Question> questions) {
    }

    public QuestionLog {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    /** {@code null}·빈 값 → 빈 기록. */
    public static QuestionLog parse(String json) {
        if (json == null || json.isBlank()) {
            return new QuestionLog(List.of());
        }
        return JSON.readValue(json, QuestionLog.class);
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    /** 라운드를 하나 덧붙인 <b>새</b> 기록 — 이 기록은 그대로다. */
    public QuestionLog append(Round r) {
        List<Round> next = new ArrayList<>(rounds);
        next.add(r);
        return new QuestionLog(next);
    }
}
