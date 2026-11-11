package com.booktimer.blog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-231 — 블로그 글을 문단 → 줄 → 문장으로 나누는 규칙(계획 2026-11-11-b1-blog-core §6.1 사례 표 25건).
 *
 * <p>기대값 표기: 「|」 = 문장 경계 · 「↵」 = 그 문장 뒤 줄바꿈(같은 문단의 다음 줄) · 「][」 = 문단 경계.
 * C01 · C04 · C05 · C06은 <b>알려진 천장</b>이다 — 규칙이 하나로 예측되게 둔 오결합 · 잘게 나뉨을 그대로 박는다
 * (고치려면 계획 §4.3부터 바꾼다).
 */
class SentenceSplitterTest {

    static Stream<Arguments> cases() {
        String p250 = "가나다 ".repeat(250).strip();
        return Stream.of(
                Arguments.of("T01 따옴표 뒤 조사", "그는 \"오늘은 쉬자.\"라고 말했다.", "그는 \"오늘은 쉬자.\"라고 말했다."),
                Arguments.of("T02 줄 머리 목록 번호", "1. 첫 번째 이유는 시간이다. 두 번째는 돈이다.", "1. 첫 번째 이유는 시간이다.|두 번째는 돈이다."),
                Arguments.of("T03 띄어 쓴 날짜", "회의는 2026. 10. 29. 오후에 열렸다.", "회의는 2026. 10. 29. 오후에 열렸다."),
                Arguments.of("T04 붙여 쓴 날짜", "2026.10.29에 시작했다. 끝은 모른다.", "2026.10.29에 시작했다.|끝은 모른다."),
                Arguments.of("T05 영문 약어", "Dr. Kim은 U.S. 출신이다. 그렇다.", "Dr. Kim은 U.S. 출신이다.|그렇다."),
                Arguments.of("T06 괄호 안 e.g.", "과일(e.g. 사과)을 샀다.", "과일(e.g. 사과)을 샀다."),
                Arguments.of("T07 마침표 없는 구어 줄바꿈", "오늘 진짜 피곤했다\n그래도 책은 읽었다", "오늘 진짜 피곤했다↵|그래도 책은 읽었다"),
                Arguments.of("T08 음...", "음... 그래서 나는 떠났다.", "음...|그래서 나는 떠났다."),
                Arguments.of("T09 소수", "원주율은 3.14이다. 외우기 쉽다.", "원주율은 3.14이다.|외우기 쉽다."),
                Arguments.of("T10 버전 번호", "v1.2.0에서 고쳤다. 이제 된다.", "v1.2.0에서 고쳤다.|이제 된다."),
                Arguments.of("T11 ?! 무리", "정말?! 믿을 수 없다.", "정말?!|믿을 수 없다."),
                Arguments.of("T12 닫는 따옴표 · 괄호", "그가 말했다. \"가자.\" (그때는 몰랐다.) 우리는 갔다.",
                        "그가 말했다.|\"가자.\"|(그때는 몰랐다.)|우리는 갔다."),
                Arguments.of("T13 줄바꿈 · 빈 줄", "첫 줄이다.\n둘째 줄이다.\n\n둘째 문단이다.", "첫 줄이다.↵|둘째 줄이다.][둘째 문단이다."),
                Arguments.of("T14 공백뿐인 줄 · 앞뒤 공백", "  \n  첫 문장.   둘째 문장.  \n \t \n\n  ", "첫 문장.|둘째 문장."),
                Arguments.of("T15 이모지 · URL", "좋았다😊 https://example.com/a.b 참고. 끝.", "좋았다😊 https://example.com/a.b 참고.|끝."),
                // 2,400자 · 마침표 없음 → 1,000 안의 마지막 공백에서 자른다(조각 셋 · 각 999자 이하 · 이으면 원문)
                Arguments.of("T16 1,000자 자르기", "가나다 ".repeat(600), p250 + "|" + p250 + "|" + "가나다 ".repeat(100).strip()),
                // 공백이 없으면 1,000에서 — 999번째가 상위 서로게이트면 999에서(쌍을 가르지 않는다)
                Arguments.of("T17 공백 없는 1,000자 · 서로게이트", "가".repeat(999) + "😊" + "나".repeat(10),
                        "가".repeat(999) + "|😊" + "나".repeat(10)),
                Arguments.of("T18 빈 글", "  \n\n \n", ""),
                Arguments.of("T19 NBSP 뒤 문장", "그는 웃었다." + (char) 0xA0 + "그리고 갔다.", "그는 웃었다.|그리고 갔다."),
                Arguments.of("C01 천장 — 마침표 뒤 붙여 씀", "했다.그리고 갔다.", "했다.그리고 갔다."),
                Arguments.of("C02 말줄임표 문자 가운데", "그건… 아니었다.", "그건…|아니었다."),
                Arguments.of("C03 CRLF", "첫째.\r\n둘째.", "첫째.↵|둘째."),
                Arguments.of("C04 천장 — 숫자 마침표 뒤 숫자", "점수는 100. 2등은 90점이다.", "점수는 100. 2등은 90점이다."),
                Arguments.of("C05 천장 — 줄 가운데 번호", "이유는 1. 시간 2. 돈이다.", "이유는 1.|시간 2.|돈이다."),
                Arguments.of("C06 천장 — 약어 아닌 No.", "No. 5를 골랐다.", "No.|5를 골랐다.")
        );
    }

    @ParameterizedTest(name = "REQ-231 · {0}")
    @MethodSource("cases")
    void splitsLikeTheTable(String name, String body, String expected) {
        assertThat(show(SentenceSplitter.split(body))).isEqualTo(expected);
    }

    /** 문단은 「][」, 같은 문단의 줄은 「↵|」, 같은 줄의 문장은 「|」로 잇는다. */
    static String show(List<SentenceSplitter.Paragraph> paragraphs) {
        return paragraphs.stream()
                .map(p -> p.lines().stream()
                        .map(l -> String.join("|", l.sentences()))
                        .collect(Collectors.joining("↵|")))
                .collect(Collectors.joining("]["));
    }
}
