package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.Questions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문 가드(v1 REQ-24) — 순수. 「질문만」을 구조로 강제하는 마지막 자리다.
 *
 * <p>통과하면 확정: 개수·길이·물음표·anchor 출처·FOLLOW 따옴표 조각 규칙이 각각 단독으로 걸린다(양성 대조가 같이 있어 「다 버리는」
 * 구현은 떨어진다). 실패하면 배제: 가드가 질문을 자르거나(잘린 질문은 거짓) 출처 대조를 건너뛴 것.
 */
class WritingGuardsTest {

    private static final String MATERIAL = WritingGuards.materialText("""
            [책] 데미안 / 헤르만 헤세
            [재료 — 사용자가 이 책을 읽으며 남긴 것. 1개(전체 1개 중 최근 것부터)]
            ### 여백 · 2026-09-03
            옮긴 문장: 새는 알에서 나오려고 투쟁한다
            메모: 나도 껍질을 깨고 싶었던 때가 있었다

            """);

    private static final String BODY = "회사를 그만두던 날 나는 처음으로 내 껍질을 보았다.";

    private static Question q(String text) {
        return new Question(text, null);
    }

    private static Questions of(Question... qs) {
        return new Questions(List.of(qs));
    }

    @Test
    @DisplayName("REQ-24 · ASK는 6개, FOLLOW는 3개까지 남긴다")
    void caps() {
        Questions seven = new Questions(IntStream.rangeClosed(1, 7).mapToObj(i -> q("질문 " + i + "은 무엇인가요?")).toList());

        assertThat(WritingGuards.ask(seven, MATERIAL).orElseThrow().questions()).hasSize(6)
                .extracting(Question::text).doesNotContain("질문 7은 무엇인가요?");
        assertThat(WritingGuards.follow(seven, MATERIAL, BODY).orElseThrow().questions()).hasSize(3)
                .extracting(Question::text).doesNotContain("질문 4은 무엇인가요?");
    }

    @Test
    @DisplayName("REQ-24 · 물음표로 끝나지 않는 질문은 버린다(전각 ？는 통과)")
    void questionMark() {
        Questions raw = of(q("이렇게 써 보세요."), q("그때 무엇을 느꼈나요？"), q("왜 그랬나요?"));

        assertThat(WritingGuards.ask(raw, MATERIAL).orElseThrow().questions()).extracting(Question::text)
                .containsExactly("그때 무엇을 느꼈나요？", "왜 그랬나요?");
    }

    @Test
    @DisplayName("REQ-24 · 물음표 뒤 닫는 따옴표만 있으면 통과한다")
    void closingQuoteAfterMark() {
        Questions raw = of(q("「무엇이 껍질이었나요?」 "), q("'그 말은 누구의 것이었나요?'"), q("왜 「껍질」인가요」"));

        assertThat(WritingGuards.ask(raw, MATERIAL).orElseThrow().questions()).extracting(Question::text)
                .containsExactly("「무엇이 껍질이었나요?」", "'그 말은 누구의 것이었나요?'");
    }

    @Test
    @DisplayName("REQ-24 · 121자 질문은 버린다")
    void length() {
        String ok = "가".repeat(119) + "?";
        String tooLong = "나".repeat(119) + "??"; // 121자 — 120자로 잘라도 ?로 끝나 「자르기」 구현과 갈린다

        assertThat(WritingGuards.ask(of(q(tooLong), q(ok)), MATERIAL).orElseThrow().questions())
                .extracting(Question::text).containsExactly(ok);
    }

    @Test
    @DisplayName("REQ-24 · ASK anchor가 재료에 없으면 비운다")
    void askAnchor() {
        Questions raw = of(new Question("어떤 투쟁이었나요?", "알에서 나오려고"),
                new Question("누가 떠올랐나요?", "재료에 없는 조각"),
                new Question("껍질은 무엇인가요?", "옮긴 문장"),          // 머리줄 낱말은 출처가 아니다
                new Question("왜 그랬나요?", "가".repeat(41)));

        assertThat(WritingGuards.ask(raw, MATERIAL).orElseThrow().questions()).extracting(Question::anchor)
                .containsExactly("알에서 나오려고", null, null, null);
    }

    @Test
    @DisplayName("REQ-24 · anchor는 40자까지 남기고 41자는 비운다(두 조각 모두 출처에 있다)")
    void anchorLength() {
        String longLine = "가나다라마바사아자차".repeat(5);
        String material = WritingGuards.materialText("[책] 데미안 / 헤르만 헤세\n### 여백 · 2026-09-03\n옮긴 문장: " + longLine + "\n\n");
        String forty = longLine.substring(0, 40);
        String fortyOne = longLine.substring(0, 41);
        assertThat(material).contains(forty, fortyOne);

        Questions raw = of(new Question("무엇이 남았나요?", forty), new Question("왜 그랬나요?", fortyOne));

        assertThat(WritingGuards.ask(raw, material).orElseThrow().questions()).extracting(Question::anchor)
                .containsExactly(forty, null);
    }

    @Test
    @DisplayName("REQ-24 · FOLLOW anchor는 사용자 글에 있으면 남기고 어디에도 없으면 비운다")
    void followAnchor() {
        Questions raw = of(new Question("그날 무엇이 보였나요?", "회사를 그만두던 날"),
                new Question("누가 있었나요?", "어디에도 없는 말"));

        assertThat(WritingGuards.follow(raw, MATERIAL, BODY).orElseThrow().questions()).extracting(Question::anchor)
                .containsExactly("회사를 그만두던 날", null);
        // 양성 대조 — 같은 anchor라도 ASK(재료만)에서는 비워진다
        assertThat(WritingGuards.ask(raw, MATERIAL).orElseThrow().questions().get(0).anchor()).isNull();
    }

    @Test
    @DisplayName("REQ-24 · FOLLOW 따옴표 속 10자 조각이 재료·사용자 글에 없으면 그 질문을 버린다")
    void followQuoteNotInSource() {
        Questions raw = of(q("「나는 껍질을 깨고 자유로워졌다」라고 정리해 보면 어떨까요?"), q("그때 어디에 있었나요?"));

        assertThat(WritingGuards.follow(raw, MATERIAL, BODY).orElseThrow().questions()).extracting(Question::text)
                .containsExactly("그때 어디에 있었나요?");
    }

    @Test
    @DisplayName("REQ-24 · FOLLOW 따옴표 속 조각이 사용자 글에 그대로 있으면 남는다")
    void followQuoteInBody() {
        Questions raw = of(q("「처음으로 내 껍질을 보았다」는 어떤 순간이었나요?"),
                q("“새는 알에서 나오려고 투쟁한다”와 어떻게 이어지나요?"));

        assertThat(WritingGuards.follow(raw, MATERIAL, BODY).orElseThrow().questions()).hasSize(2);
    }

    @Test
    @DisplayName("REQ-24 · 따옴표 속 9자 조각은 검사하지 않는다")
    void nineCharsNotChecked() {
        Questions raw = of(q("「아홉글자로된조각임」은 무슨 뜻인가요?"), q("「열글자로만된조각이다」은 무슨 뜻인가요?"));

        assertThat(WritingGuards.follow(raw, MATERIAL, BODY).orElseThrow().questions()).extracting(Question::text)
                .containsExactly("「아홉글자로된조각임」은 무슨 뜻인가요?");
    }

    @Test
    @DisplayName("REQ-24 · ASK에는 따옴표 검사가 없다")
    void askHasNoQuoteCheck() {
        Questions raw = of(q("「나는 껍질을 깨고 자유로워졌다」는 어떤가요?"));

        assertThat(WritingGuards.ask(raw, MATERIAL)).isPresent();
        assertThat(WritingGuards.follow(raw, MATERIAL, BODY)).as("양성 대조 — 같은 질문을 FOLLOW는 버린다").isEmpty();
    }

    @Test
    @DisplayName("REQ-24 · 하나도 안 남으면 실패")
    void emptyIsFailure() {
        assertThat(WritingGuards.ask(of(q("이렇게 써 보세요."), q("  "), new Question(null, null)), MATERIAL)).isEmpty();
        assertThat(WritingGuards.ask(new Questions(null), MATERIAL)).isEmpty();
        assertThat(WritingGuards.ask(null, MATERIAL)).isEmpty();
    }

    @Test
    @DisplayName("REQ-24 · 따옴표 조각은 짝이 맞는 것만 센다(‘’ '' \"\" “” 「」 『』)")
    void quotedSpans() {
        assertThat(WritingGuards.quotedSpans("‘가’ 'B' \"C\" “D” 「E」 『F』 「짝 없음"))
                .containsExactlyInAnyOrder("가", "B", "C", "D", "E", "F");
    }

    @Test
    @DisplayName("REQ-24 · materialText는 머리줄과 「옮긴 문장: 」「메모: 」 접두를 걷은 원문이다")
    void materialText() {
        assertThat(MATERIAL).contains("새는 알에서 나오려고 투쟁한다", "나도 껍질을 깨고 싶었던 때가 있었다")
                .doesNotContain("[책]", "[재료", "### 여백", "옮긴 문장", "메모:");
        assertThat(WritingGuards.materialText("[책] 없음 — 재료 없이 글만 봅니다\n")).isBlank();
    }
}
