package com.booktimer.writing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문 기록({@code writing_draft.questions_json})의 모양 — PR-B는 형만 만들고(늘 빈 기록) PR-C가 채운다(REQ-46).
 *
 * <p>겨누는 실패: 아직 AI를 한 번도 부르지 않은 글(질문 기록 {@code null})이 화면에서 {@code null}로 새는 것,
 * 그리고 라운드를 덧붙이는 쪽이 이전 기록을 제자리에서 바꿔 「저장 전 실패」가 반쯤 바뀐 기록을 남기는 것.
 */
class QuestionLogTest {

    private static final QuestionLog.Round ROUND = new QuestionLog.Round(Step.ASK,
            Instant.parse("2026-11-23T01:02:03Z"), null, 0,
            List.of(new Question("이 문장에서 무엇이 떠올랐나요?", "새는 알에서"), new Question("왜 그랬을까요?", null)));

    @Test
    @DisplayName("parse: null·빈 값은 라운드가 빈 기록이다")
    void parseNullOrBlankIsEmpty() {
        assertThat(QuestionLog.parse(null).rounds()).isEmpty();
        assertThat(QuestionLog.parse("").rounds()).isEmpty();
        assertThat(QuestionLog.parse("  ").rounds()).isEmpty();
    }

    @Test
    @DisplayName("toJson → parse 왕복이 같은 기록을 돌려준다")
    void roundTrip() {
        QuestionLog log = QuestionLog.parse(null).append(ROUND);

        String json = log.toJson();

        assertThat(json).contains("\"rounds\"", "\"ASK\"", "새는 알에서");
        assertThat(QuestionLog.parse(json)).isEqualTo(log);
    }

    @Test
    @DisplayName("append는 새 기록을 돌려주고 원래 기록은 그대로다(불변)")
    void appendIsImmutable() {
        QuestionLog empty = QuestionLog.parse(null);

        QuestionLog one = empty.append(ROUND);
        QuestionLog two = one.append(new QuestionLog.Round(Step.FOLLOW, Instant.parse("2026-11-23T02:00:00Z"),
                "왜 그랬을까요?", 42, List.of(new Question("그때 누가 있었나요?", null))));

        assertThat(empty.rounds()).isEmpty();
        assertThat(one.rounds()).containsExactly(ROUND);
        assertThat(two.rounds()).hasSize(2).first().isEqualTo(ROUND);
        assertThat(two.rounds().get(1).step()).isEqualTo(Step.FOLLOW);
    }
}
