package com.booktimer.blog;

import com.booktimer.blog.ContentFilter.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 걸러내기(계획 2026-11-16-blog-b2 §6.1 · §6.2 — 순수). 보이지 않는 글자 · 이모지는 코드 포인트로 조립한다 — 소스에 맨 글자로
 * 두지 않는다(§8 머리 「서식 문자 함정」). 매개변수 이름표는 ASCII(시험 보고서 XML을 그 이름으로 찾는다 — U-204).
 */
class ContentFilterTest {

    private static final String BANNED = "이 표현은 올릴 수 없어요";
    private static final String INVISIBLE = "이름에 줄바꿈이나 보이지 않는 글자는 쓸 수 없어요";
    private static final String REPEAT = "같은 글자를 너무 많이 이어 썼어요";
    private static final String DIRECTION = "글자 방향을 바꾸는 보이지 않는 글자는 쓸 수 없어요";

    static String s(int... cps) {
        StringBuilder b = new StringBuilder();
        for (int cp : cps) b.appendCodePoint(cp);
        return b.toString();
    }

    private static final String SIBAL = s(0xC2DC, 0xBC1C); // 금지어 픽스처(완성형)

    // ── §6.2 이름의 보이지 않는 글자 ─────────────────────────────────────────

    static Stream<Arguments> zwjNames() {
        return Stream.of(
                Arguments.of("woman-technologist", s(0x1F469, 0x200D, 0x1F4BB) + "독서가"),
                Arguments.of("family", s(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)),
                Arguments.of("rainbow-flag", s(0x1F3F3, 0xFE0F, 0x200D, 0x1F308)),
                Arguments.of("skin-tone-technologist", s(0x1F469, 0x1F3FD, 0x200D, 0x1F4BB)));
    }

    @ParameterizedTest(name = "REQ-265 · 이모지 사이 ZWJ 이름 통과 · {0}")
    @MethodSource("zwjNames")
    void zwjBetweenEmojiPasses(String label, String name) {
        assertThat(ContentFilter.violation(name, Kind.NAME)).isEmpty();
    }

    static Stream<Arguments> visibleNames() {
        return Stream.of(
                Arguments.of("plain", "독자"),
                Arguments.of("keycap", s(0x31, 0xFE0F, 0x20E3) + "독자"),
                Arguments.of("kr-flag", s(0x1F1F0, 0x1F1F7) + "독자"),
                Arguments.of("thumb-skin", s(0x1F44D, 0x1F3FD)),
                Arguments.of("combining-e", s(0x65, 0x301, 0x65)),
                Arguments.of("trailing-line-separator", "독자" + s(0x2028)),
                Arguments.of("ga-x30", "가".repeat(30))); // 이름엔 반복 규칙이 없다(§4.2)
    }

    @ParameterizedTest(name = "REQ-265 · 보이는 이름 통과 · {0}")
    @MethodSource("visibleNames")
    void visibleNamesPass(String label, String name) {
        assertThat(ContentFilter.violation(name, Kind.NAME)).isEmpty();
    }

    static Stream<Arguments> invisibleNames() {
        return Stream.of(
                Arguments.of("zwj-hangul", "가" + s(0x200D) + "나"),
                Arguments.of("zwsp-pair", s(0x200B, 0x200B)),
                Arguments.of("zwsp-inner", "가" + s(0x200B) + "나"),
                Arguments.of("rlo", s(0x202E) + "독자"),
                Arguments.of("nbsp-pair", s(0xA0, 0xA0)),
                Arguments.of("hangul-filler-pair", s(0x3164, 0x3164)),
                Arguments.of("braille-blank-pair", s(0x2800, 0x2800)),
                Arguments.of("line-separator-inner", "가" + s(0x2028) + "나"),
                Arguments.of("paragraph-separator", "가" + s(0x2029) + "나"),
                Arguments.of("next-line", "가" + s(0x85) + "나"),
                Arguments.of("bom", s(0xFEFF) + "독자"),
                Arguments.of("soft-hyphen", "가" + s(0xAD) + "나"),
                Arguments.of("combining-only", s(0x301, 0x301)),
                Arguments.of("england-tags", s(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)),
                Arguments.of("lrm", "독자" + s(0x200E)),
                Arguments.of("word-joiner", "가" + s(0x2060) + "나"),
                Arguments.of("ideographic-space-pair", s(0x3000, 0x3000)),
                Arguments.of("tab", "가\t나"),
                Arguments.of("newline", "가\n나"));
    }

    @ParameterizedTest(name = "REQ-265 · 보이지 않는 글자 이름 막힘 · {0}")
    @MethodSource("invisibleNames")
    void invisibleNamesBlocked(String label, String name) {
        assertThat(ContentFilter.violation(name, Kind.NAME)).contains(INVISIBLE);
    }

    // ── 금지어 ──────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "REQ-251 · 금지어 픽스처 막힘 · 평문 통과 · {0}")
    @EnumSource(Kind.class)
    void bannedWordBlockedPlainPasses(Kind kind) {
        assertThat(ContentFilter.violation("정말 " + SIBAL + " 같은 책", kind)).contains(BANNED);
        assertThat(ContentFilter.violation("정말 좋은 책", kind)).isEmpty(); // 양성 대조
        assertThat(ContentFilter.violation("this is FUCKING bad", kind)).contains(BANNED);
    }

    @ParameterizedTest(name = "REQ-251 · 우회 표기(점 · 숫자 · 기호 · 초성) 막힘 · {0}")
    @MethodSource("bypasses")
    void bypassSpellingsBlocked(String label, String text) {
        assertThat(ContentFilter.violation(text, Kind.COMMENT)).contains(BANNED);
    }

    static Stream<Arguments> bypasses() {
        return Stream.of(
                Arguments.of("dot", "시.발"),
                Arguments.of("digit", "시1발"),
                Arguments.of("symbol", "시*발"),
                Arguments.of("chosung", s(0x3145, 0x3142)),
                Arguments.of("chosung-dash", s(0x3145) + "-" + s(0x3142)),
                Arguments.of("zwsp-inside", "시" + s(0x200B) + "발"));
    }

    @Test
    @DisplayName("REQ-251 · 「오늘 시 발표가 있다」 · 「시발점」 통과(거짓 양성 대조)")
    void falsePositiveControls() {
        for (Kind kind : Kind.values()) {
            assertThat(ContentFilter.violation("오늘 시 발표가 있다", kind)).isEmpty();
            assertThat(ContentFilter.violation("시발점에서 다시 출발했다", kind)).isEmpty();
            assertThat(ContentFilter.violation("시발역", kind)).isEmpty();
        }
        assertThat(ContentFilter.violation("시발점은 좋은데 " + SIBAL, Kind.COMMENT)).contains(BANNED); // 예외 단어가 옆 금지어를 덮지 않는다
    }

    @Test
    @DisplayName("REQ-251 · 금지어를 품은 흔한 낱말(감염병 · 전염병 · 일본 성씨 -shita) 통과 · 금지어 자체는 막힘")
    void embeddedWordFalsePositives() {
        for (Kind kind : Kind.values()) {
            assertThat(ContentFilter.violation("감염병 예방법을 다룬 책이다", kind)).isEmpty();
            assertThat(ContentFilter.violation("전염병의 역사", kind)).isEmpty();
            assertThat(ContentFilter.violation("Yamashita Tatsuro", kind)).isEmpty();
            assertThat(ContentFilter.violation("matsushita konosuke · kinoshita", kind)).isEmpty();
        }
        assertThat(ContentFilter.violation("염병하네", Kind.COMMENT)).contains(BANNED); // 양성 대조
        assertThat(ContentFilter.violation("감염병 말고 염병", Kind.COMMENT)).contains(BANNED); // 예외 단어가 옆 금지어를 덮지 않는다
        assertThat(ContentFilter.violation("shitty book", Kind.COMMENT)).contains(BANNED);
        assertThat(ContentFilter.violation("bullshit", Kind.COMMENT)).contains(BANNED);
    }

    @Test
    @DisplayName("REQ-251 · 예외 단어가 옆 금지어 글자를 먹지 않는다(shitass · shit-ass · bullshita · 전염병+신) · 성씨는 그대로 통과")
    void exceptionDoesNotSwallowNeighbour() {
        for (String text : List.of("shitass", "you shit-ass", "shit.a", "bullshita",
                "감염병" + "신", "넌 전염병" + "신이야")) {
            assertThat(ContentFilter.violation(text, Kind.COMMENT)).as(text).contains(BANNED);
            assertThat(ContentFilter.violation(text, Kind.POST)).as(text).contains(BANNED);
        }
        assertThat(ContentFilter.violation("Morishita · Oshita · Kinoshita", Kind.COMMENT)).isEmpty(); // 대조 — 모음 + shita
    }

    static Stream<Arguments> nfkcBypasses() {
        return Stream.of(
                Arguments.of("nfd-hangul", s(0x1109, 0x1175, 0x1107, 0x1161, 0x11AF)),
                Arguments.of("fullwidth", s(0xFF46, 0xFF55, 0xFF43, 0xFF4B)),
                Arguments.of("fullwidth-upper", s(0xFF26, 0xFF35, 0xFF23, 0xFF2B)),
                Arguments.of("math-bold", s(0x1D41F, 0x1D42E, 0x1D41C, 0x1D424)),
                Arguments.of("circled", s(0x24D5, 0x24E4, 0x24D2, 0x24DA)),
                Arguments.of("halfwidth-jamo", s(0xFFB5, 0xFFB2)),
                Arguments.of("conjoining-jamo", s(0x1109, 0x1107)));
    }

    @ParameterizedTest(name = "REQ-251 · NFKC로 접히는 우회 막힘 · {0}")
    @MethodSource("nfkcBypasses")
    void nfkcBypassesBlocked(String label, String text) {
        assertThat(ContentFilter.violation(text, Kind.COMMENT)).contains(BANNED);
        assertThat(ContentFilter.violation(text, Kind.POST)).contains(BANNED);
    }

    // ── 반복(댓글만) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-251 · 같은 글자 29개 통과 · 30개 막힘(댓글) · 「─」 30개는 통과")
    void repeatThreshold() {
        assertThat(ContentFilter.violation("ㅋ".repeat(29), Kind.COMMENT)).isEmpty();
        assertThat(ContentFilter.violation("ㅋ".repeat(30), Kind.COMMENT)).contains(REPEAT);
        assertThat(ContentFilter.violation("좋아요 " + "가".repeat(30) + " 끝", Kind.COMMENT)).contains(REPEAT);
        assertThat(ContentFilter.violation("─".repeat(30), Kind.COMMENT)).isEmpty();
        assertThat(ContentFilter.violation(".".repeat(40), Kind.COMMENT)).isEmpty();
        assertThat(ContentFilter.violation("가".repeat(29) + "나" + "가".repeat(29), Kind.COMMENT)).isEmpty(); // 다른 글자가 끊는다
        assertThat(ContentFilter.violation("가".repeat(499), Kind.POST)).isEmpty(); // 글 · 이름엔 반복 규칙이 없다
    }

    @Test
    @DisplayName("REQ-251 · 같은 글자 30개 사이에 ZWSP · 공백 · 점을 끼워도 막힘 · 호환 자모 15 + 첫소리 자모 15도 막힘")
    void repeatIgnoresSeparators() {
        assertThat(ContentFilter.violation(String.join(s(0x200B), java.util.Collections.nCopies(30, "ㅋ")), Kind.COMMENT))
                .contains(REPEAT);
        assertThat(ContentFilter.violation(String.join(" ", java.util.Collections.nCopies(30, "ㅋ")), Kind.COMMENT))
                .contains(REPEAT);
        assertThat(ContentFilter.violation(String.join(".", java.util.Collections.nCopies(30, "ㅋ")), Kind.COMMENT))
                .contains(REPEAT);
        assertThat(ContentFilter.violation(String.join(".", java.util.Collections.nCopies(29, "ㅋ")), Kind.COMMENT)).isEmpty();
        assertThat(ContentFilter.violation(s(0x314B).repeat(15) + s(0x110F).repeat(15), Kind.COMMENT)).contains(REPEAT);
    }

    // ── 링크 ────────────────────────────────────────────────────────────────

    private static String links(int n) {
        StringBuilder b = new StringBuilder("읽어 보세요");
        for (int i = 0; i < n; i++) b.append(" https://e").append(i).append(".com");
        return b.toString();
    }

    @Test
    @DisplayName("REQ-251 · 링크 — 댓글 3 통과 4 막힘 · 글 20 통과 21 막힘 · 이름 1 막힘")
    void linkLimits() {
        assertThat(ContentFilter.violation(links(3), Kind.COMMENT)).isEmpty();
        assertThat(ContentFilter.violation(links(4), Kind.COMMENT)).contains("링크는 댓글 하나에 3개까지 넣을 수 있어요");
        assertThat(ContentFilter.violation(links(20), Kind.POST)).isEmpty();
        assertThat(ContentFilter.violation(links(21), Kind.POST)).contains("링크는 글 하나에 20개까지 넣을 수 있어요");
        assertThat(ContentFilter.violation("독자", Kind.NAME)).isEmpty();
        assertThat(ContentFilter.violation("www.독자.kr", Kind.NAME)).contains("이름에는 링크를 넣을 수 없어요");
        // https://www. 는 링크 하나다 — 3개면 댓글 통과
        assertThat(ContentFilter.violation("https://www.a.com http://www.b.com WWW.c.com", Kind.COMMENT)).isEmpty();
    }

    @Test
    @DisplayName("REQ-251 · 전각 https:// · www. 도 링크로 센다")
    void fullwidthLinksCounted() {
        String fwHttps = s(0xFF48, 0xFF54, 0xFF54, 0xFF50, 0xFF53, 0xFF1A, 0xFF0F, 0xFF0F);
        String fwWww = s(0xFF57, 0xFF57, 0xFF57, 0xFF0E);
        assertThat(ContentFilter.violation(fwHttps + "a.com", Kind.NAME)).contains("이름에는 링크를 넣을 수 없어요");
        assertThat(ContentFilter.violation(fwWww + "a.com", Kind.NAME)).contains("이름에는 링크를 넣을 수 없어요");
        String four = fwHttps + "a " + fwHttps + "b " + fwWww + "c " + fwWww + "d";
        assertThat(ContentFilter.violation(four, Kind.COMMENT)).contains("링크는 댓글 하나에 3개까지 넣을 수 있어요");
    }

    // ── 방향 글자 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-251 · RLO · LRO가 있는 댓글 · 글 400 · 끼워 넣기 · 격리 · LRM은 통과")
    void directionOverrides() {
        String rlo = s(0x202E) + s(0xBC1C, 0xC2DC); // 화면엔 금지어로 보이는데 검사 사본엔 「발시」
        for (Kind kind : List.of(Kind.COMMENT, Kind.POST)) {
            assertThat(ContentFilter.violation(rlo, kind)).contains(DIRECTION);
            assertThat(ContentFilter.violation("좋은 " + s(0x202D) + "책", kind)).contains(DIRECTION);
            for (int cp : new int[]{0x202A, 0x202B, 0x202C, 0x2066, 0x2067, 0x2068, 0x2069, 0x200E, 0x200F}) {
                assertThat(ContentFilter.violation("좋은 " + s(cp) + "책", kind)).as("U+%04X", cp).isEmpty();
            }
        }
        assertThat(ContentFilter.violation(rlo, Kind.NAME)).contains(INVISIBLE);
    }

    // ── 보조 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-251 · 보이는 글자 판정 · 걷기는 NBSP까지")
    void hasVisibleAndStrip() {
        assertThat(ContentFilter.hasVisible("가")).isTrue();
        assertThat(ContentFilter.hasVisible(s(0x200B, 0x200B))).isFalse();
        assertThat(ContentFilter.hasVisible(s(0xA0) + " " + s(0x3164))).isFalse();
        assertThat(ContentFilter.hasVisible("")).isFalse();
        assertThat(ContentFilter.strip(s(0xA0) + " 독자 " + s(0xA0))).isEqualTo("독자");
    }

    // ── 시험 글 묶음(거짓 양성) ─────────────────────────────────────────────

    private static String resource(String path) throws IOException {
        try (InputStream in = ContentFilterTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 시험 글 묶음의 문장 줄(「- 」 · 「+ 」 접두를 뗀다). */
    static List<String> corpusLines(String raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String l = line.replace("\r", "");
            if (l.startsWith("- ") || l.startsWith("+ ")) out.add(l.substring(2));
        }
        return out;
    }

    /** 템플릿의 태그를 걷은 글자 줄(빈 줄 제외). */
    static List<String> htmlLines(String raw) {
        List<String> out = new ArrayList<>();
        String text = raw.replaceAll("(?s)<!--.*?-->", " ").replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        for (String line : text.split("\n")) {
            String l = line.strip();
            if (!l.isEmpty()) out.add(l);
        }
        return out;
    }

    private static List<String> blocked(List<String> lines) {
        return lines.stream().filter(l -> ContentFilter.violation(l, Kind.POST).isPresent()).toList();
    }

    @Test
    @DisplayName("REQ-251 · 시험 글 묶음(u136-corpus · 약관 · 처리방침 본문 글자)이 전부 통과(거짓 양성 0) · 읽은 줄 하한 · 끼운 금지어 줄 하나는 막힘(양성 대조)")
    void corpusHasNoFalsePositives() throws IOException {
        List<String> corpus = corpusLines(resource("blog/u136-corpus.txt"));
        List<String> terms = htmlLines(resource("templates/terms.html"));
        List<String> privacy = htmlLines(resource("templates/privacy.html"));
        assertThat(corpus).hasSizeGreaterThanOrEqualTo(234);
        assertThat(terms).hasSizeGreaterThanOrEqualTo(20);
        assertThat(privacy).hasSizeGreaterThanOrEqualTo(20);

        assertThat(blocked(corpus)).isEmpty();
        assertThat(blocked(terms)).isEmpty();
        assertThat(blocked(privacy)).isEmpty();

        List<String> planted = corpusLines(resource("blog/u136-corpus.txt") + "\n- 이 책은 " + SIBAL + " 별로다.\n");
        assertThat(blocked(planted)).hasSize(1);
        assertThat(ContentFilter.violation("멀쩡한 글", Kind.POST)).isEqualTo(Optional.empty());
    }
}
