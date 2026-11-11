package com.booktimer.blog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-232~234 · 249 — 판 비교 사례 표(계획 2026-11-11-b1-blog-core §6.2 · A01~A23 · 25건).
 *
 * <p>단독으로 잡는 실패: A04 · A07 · A12는 설계 원안(포함도만)이, A14 · A15a · A15c는 사슬 없는 첫 규칙이 틀리는 줄이다.
 * A18~A23은 문턱 변이를 잡는다(합침 포함도 0 → A09 · A18 · 나뉨 포함도 0 → A23 · 계수 1/4 → A20 · A22 ·
 * {@code ≥}→{@code >} → A19 · A21 · 정수 나눗셈 → A22 — §6.2 「변이 잠금」). A09는 알려진 천장이다(짧은 문장의
 * 어미가 바뀌는 합침은 묘비) — 바라던 결과가 아니라 지금 규칙의 결과를 박는다.
 *
 * <p>표기: 새 문장마다 {@code s1}(같은 주소) · {@code s1~}(같은 주소 · 판+1) · {@code +}(새 주소) · {@code +<s1}(새 주소 ·
 * split_from = s1), 그 뒤 {@code m:} 합침(옛>대상) · {@code t:} 묘비.
 */
class SentenceAlignerTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                c("A01 한 단어 고침", List.of("나는 어제 책을 읽었다.", "재미있었다."), List.of("나는 어제 소설을 읽었다.", "재미있었다."),
                        "s1~ s2 | m: | t:"),
                c("A02 나뉨", List.of("나는 책을 읽었고 그 책은 재미있었다."), List.of("나는 책을 읽었다.", "그 책은 재미있었다."),
                        "+<s1 s1~ | m: | t:"),
                c("A03 합침", List.of("나는 책을 읽었다.", "그 책은 재미있었다."), List.of("나는 책을 읽었고 그 책은 재미있었다."),
                        "s1~ | m: s2>s1 | t:"),
                c("A04 짧은 문장 지움", List.of("첫째다.", "둘째다.", "셋째다."), List.of("첫째다.", "셋째다."),
                        "s1 s3 | m: | t: s2"),
                c("A05 다시 씀", List.of("오늘은 비가 왔다."), List.of("내일은 소풍을 간다."),
                        "+ | m: | t: s1"),
                c("A06 문단 순서 바꿈", List.of("첫 문단 하나.", "첫 문단 둘.", "둘째 문단."), List.of("둘째 문단.", "첫 문단 하나.", "첫 문단 둘."),
                        "s3 s1 s2 | m: | t:"),
                c("A07 같은 문장을 하나 더 씀", List.of("같은 문장이다."), List.of("같은 문장이다.", "같은 문장이다."),
                        "s1 + | m: | t:"),
                c("A08 같은 문장 둘 그대로", List.of("같은 문장이다.", "같은 문장이다."), List.of("같은 문장이다.", "같은 문장이다."),
                        "s1 s2 | m: | t:"),
                c("A09 천장 — 짧은 문장의 어미가 바뀌는 합침", List.of("짧다.", "이 문장은 꽤 길게 이어지는 두 번째 문장이다."),
                        List.of("짧고 이 문장은 꽤 길게 이어지는 두 번째 문장이다."),
                        "s2~ | m: | t: s1"),
                c("A10 띄어쓰기만 고침", List.of("나는책을읽었다."), List.of("나는 책을 읽었다."),
                        "s1~ | m: | t:"),
                c("A11 그대로 다시 올림", List.of("하나.", "둘.", "셋."), List.of("하나.", "둘.", "셋."),
                        "s1 s2 s3 | m: | t:"),
                c("A12 짧은 문장 지움 + 이웃 고침", List.of("첫째 장면이 좋았다.", "둘째 장면도 좋았다.", "셋째는 별로였다."),
                        List.of("첫째 장면이 참 좋았다.", "셋째는 별로였다."),
                        "s1~ s3 | m: | t: s2"),
                c("A13 합침 뒤에 문장이 더 있음", List.of("나는 책을 읽었다.", "그 책은 재미있었다.", "끝."),
                        List.of("나는 책을 읽었고 그 책은 재미있었다.", "끝."),
                        "s1~ s3 | m: s2>s1 | t:"),
                c("A14 셋을 하나로 합침", List.of("나는 책을 읽었다.", "그 책은 두꺼웠다.", "그래도 재미있었다."),
                        List.of("나는 책을 읽었고 그 책은 두꺼웠지만 그래도 재미있었다."),
                        "s3~ | m: s1>s3 s2>s3 | t:"),
                c("A15a 하나를 셋으로(짝 = 첫 조각)", List.of("나는 그 두꺼운 책을 어제 다 읽었고 그 책은 두꺼웠지만 그래도 재미있었다."),
                        List.of("나는 그 두꺼운 책을 어제 다 읽었다.", "그 책은 두꺼웠다.", "그래도 재미있었다."),
                        "s1~ +<s1 +<s1 | m: | t:"),
                c("A15b 하나를 셋으로(짝 = 가운데)", List.of("나는 책을 읽었고 그 두꺼운 책은 정말 끝도 없이 두꺼웠지만 그래도 재미있었다."),
                        List.of("나는 책을 읽었다.", "그 두꺼운 책은 정말 끝도 없이 두꺼웠다.", "그래도 재미있었다."),
                        "+<s1 s1~ +<s1 | m: | t:"),
                c("A15c 하나를 셋으로(짝 = 끝 조각)", List.of("나는 책을 읽었고 그 책은 두꺼웠지만 그래도 끝까지 아주 재미있게 다 읽었다."),
                        List.of("나는 책을 읽었다.", "그 책은 두꺼웠다.", "그래도 끝까지 아주 재미있게 다 읽었다."),
                        "+<s1 +<s1 s1~ | m: | t:"),
                c("A16 짧은 문장 둘 연달아 지움", List.of("첫째다.", "둘째다.", "셋째다.", "넷째다."), List.of("첫째다.", "넷째다."),
                        "s1 s4 | m: | t: s2 s3"),
                c("A17 합침 옆 문장 지움", List.of("나는 책을 읽었다.", "그 책은 재미있었다.", "정말 좋았다."),
                        List.of("나는 책을 읽었고 그 책은 재미있었다."),
                        "s1~ | m: s2>s1 | t: s3"),
                c("A18 지움 + 이웃이 무관하게 자람", List.of("첫째 장면이 좋았다.", "둘째다.", "셋째는 별로였다."),
                        List.of("첫째 장면이 정말로 좋았다.", "셋째는 별로였다."),
                        "s1~ s3 | m: | t: s2"),
                c("A19 합침 자람 경계 — 정확히 절반", List.of("영화는 좋았다.", "아주 좋았다."), List.of("영화는 참 아주 좋았다."),
                        "s1~ | m: s2>s1 | t:"),
                c("A20 합침 자람 경계 — 절반에서 1 모자람", List.of("영화는 좋았다.", "아주 좋았다."), List.of("영화는 아주 좋았다."),
                        "s1~ | m: | t: s2"),
                c("A21 나뉨 줄어듦 경계 — 정확히 절반", List.of("오늘 영화는 참 아주 좋았다."), List.of("오늘 영화는 좋았다.", "아주 좋았다."),
                        "s1~ +<s1 | m: | t:"),
                c("A22 나뉨 줄어듦 경계 — 절반에서 1 모자람", List.of("오늘 영화는 참 아주 좋았다."), List.of("오늘 영화는 좋았다.", "참 아주 좋았다."),
                        "s1~ + | m: | t:"),
                c("A23 나뉨 포함도 미달", List.of("첫째 장면이 정말로 좋았다."), List.of("첫째 장면이 좋았다.", "둘째다."),
                        "s1~ + | m: | t:"),
                // 표 밖 한 건 — 공백 무리만 다른 같은 문장(NBSP 포함)은 LCS 짝이라 판을 올리지 않는다(§4.10 · §6.2 norm = §6.1 0번 술어)
                c("N01 공백 무리만 다름(NBSP) → 판 그대로", List.of("나는  책을" + (char) 0xA0 + "읽었다."), List.of("나는 책을 읽었다."),
                        "s1 | m: | t:")
        );
    }

    @ParameterizedTest(name = "REQ-234 · {0}")
    @MethodSource("cases")
    void alignsLikeTheTable(String name, List<String> old, List<String> neu, String expected) {
        assertThat(show(SentenceAligner.align(olds(old), neu))).isEqualTo(expected);
    }

    private static Arguments c(String name, List<String> old, List<String> neu, String expected) {
        return Arguments.of(name, old, neu, expected);
    }

    /** 옛 문장에 s1부터 주소를 붙인다. */
    static List<SentenceAligner.Old> olds(List<String> texts) {
        List<SentenceAligner.Old> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) out.add(new SentenceAligner.Old("s" + (i + 1), texts.get(i)));
        return out;
    }

    static String show(SentenceAligner.Alignment a) {
        String ids = IntStream.range(0, a.sidOfNew().size()).mapToObj(j -> {
            String sid = a.sidOfNew().get(j);
            if (sid == null) {
                String from = a.splitFrom().get(j);
                return from == null ? "+" : "+<" + from;
            }
            return a.edited().contains(sid) ? sid + "~" : sid;
        }).collect(Collectors.joining(" "));
        String merged = a.merged().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> " " + e.getKey() + ">" + e.getValue()).collect(Collectors.joining());
        String tombs = a.tombs().stream().sorted().map(t -> " " + t).collect(Collectors.joining());
        return ids + " | m:" + merged + " | t:" + tombs;
    }
}
