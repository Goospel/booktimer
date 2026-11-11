package com.booktimer.blog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-248 · REQ-247 — 인용 박스 구간 판정 · 이음(계획 2026-11-11-b1-blog-core §6.5 + §15.3 ⑦ 사슬 닫힘).
 *
 * <p>글 상태를 직접 넣는다(판 비교를 거치지 않는다). 구간 = s1 s2 s3 · 앞뒤 s0 · s4 · 행 id 10~14 · 인용 때 최댓값
 * {@code SEEN} = 20 — 「인용 뒤 조각」은 행 id &gt; 20(§4.6). #11은 조각 id = SEEN(경계), R16은 두 번 나뉘어 id가 커진다.
 * 「가능」 = {@code !allDeleted ∧ norm(text) ≠ norm(스냅샷)}(§6.5 판정 — 부르는 쪽이 재는 식을 여기서 그대로 쓴다).
 */
class QuoteRangeTest {

    private static final long SEEN = 20;
    private static final List<String> M = List.of("s1", "s2", "s3");
    private static final String SNAP = "첫째 문장이다. 둘째 문장이다. 셋째 문장이다.";
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    // ── 픽스처 ──

    private static BlogSentence row(long id, String sid, String text, String splitFrom) {
        BlogSentence s = BlogSentence.create(null, sid, text, splitFrom);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static Map<String, BlogSentence> base() {
        Map<String, BlogSentence> m = new LinkedHashMap<>();
        String[][] r = {{"s0", "앞 문장이다."}, {"s1", "첫째 문장이다."}, {"s2", "둘째 문장이다."}, {"s3", "셋째 문장이다."},
                {"s4", "넷째 문장이다."}};
        for (int i = 0; i < r.length; i++) m.put(r[i][0], row(10 + i, r[i][0], r[i][1], null));
        return m;
    }

    private static void add(Map<String, BlogSentence> m, BlogSentence s) {
        m.put(s.getSid(), s);
    }

    /** 한 문단 한 줄. */
    private static Layout one(String... sids) {
        return new Layout(List.of(new Layout.Item(List.of(List.of(sids)), null)));
    }

    @SafeVarargs
    private static Layout.Item para(List<String>... lines) {
        return new Layout.Item(List.of(lines), null);
    }

    private static Layout.Item box(long label) {
        return new Layout.Item(null, label);
    }

    private static PostSentences post(Map<String, BlogSentence> rows, Layout layout) {
        return new PostSentences(layout, new ArrayList<>(rows.values()));
    }

    private static boolean updatable(QuoteRange.Current c) {
        return !c.allDeleted() && !SentenceAligner.norm(c.text()).equals(SentenceAligner.norm(SNAP));
    }

    private static QuoteRange.Current range(Map<String, BlogSentence> rows, Layout layout) {
        return QuoteRange.current(M, post(rows, layout), SEEN);
    }

    // ── REQ-248 사례 표 1~11 ──

    @Test
    @DisplayName("REQ-248 · #1 s2 한 단어 고침 → 가능 · s1 s2′ s3")
    void REQ_248_1_한_단어_고침() {
        Map<String, BlogSentence> p = base();
        p.get("s2").edit("둘째 문장을 고쳤다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
        assertThat(c.text()).isEqualTo("첫째 문장이다. 둘째 문장을 고쳤다. 셋째 문장이다.");
    }

    @Test
    @DisplayName("REQ-248 · #2 s2 지움 → 가능 · s1 s3")
    void REQ_248_2_지움() {
        Map<String, BlogSentence> p = base();
        p.get("s2").tombstone(NOW);
        QuoteRange.Current c = range(p, one("s0", "s1", "s3", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #3a s2 · s3 합침(s3 → s2) → 가능 · s1 s2(합친 글)")
    void REQ_248_3a_합침_뒤로() {
        Map<String, BlogSentence> p = base();
        p.get("s3").mergeInto("s2", NOW);
        p.get("s2").edit("둘째 문장이고 셋째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2");
        assertThat(c.text()).isEqualTo("첫째 문장이다. 둘째 문장이고 셋째 문장이다.");
    }

    @Test
    @DisplayName("REQ-248 · #3b s2 · s3 합침(s2 → s3) → 가능 · s1 s3(합친 글)")
    void REQ_248_3b_합침_앞으로() {
        Map<String, BlogSentence> p = base();
        p.get("s2").mergeInto("s3", NOW);
        p.get("s3").edit("둘째 문장이고 셋째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s3", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #4a s3이 구간 밖 s4와 합침(s3 → s4) → 가능 · s1 s2 s4")
    void REQ_248_4a_구간_밖으로_합침() {
        Map<String, BlogSentence> p = base();
        p.get("s3").mergeInto("s4", NOW);
        p.get("s4").edit("셋째 문장이고 넷째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s4");
    }

    @Test
    @DisplayName("REQ-248 · #4b s3이 구간 밖 s4와 합침(s4 → s3) → 가능 · s1 s2 s3")
    void REQ_248_4b_구간_밖에서_합침() {
        Map<String, BlogSentence> p = base();
        p.get("s4").mergeInto("s3", NOW);
        p.get("s3").edit("셋째 문장이고 넷째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #5 인용 뒤 s3이 나뉨(글자가 바뀜) → 가능 · s1 s2 s3 x")
    void REQ_248_5_인용_뒤_나뉨() {
        Map<String, BlogSentence> p = base();
        p.get("s3").edit("셋째다.");
        add(p, row(21, "x", "문장이었다.", "s3"));
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "x", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3", "x");
    }

    @Test
    @DisplayName("REQ-248 · #6 s1과 s2 사이에 끼움 → 가능 · s1 n s2 s3")
    void REQ_248_6_사이에_끼움() {
        Map<String, BlogSentence> p = base();
        add(p, row(21, "n", "끼운 문장이다.", null));
        QuoteRange.Current c = range(p, one("s0", "s1", "n", "s2", "s3", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "n", "s2", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #7 s1 · s2 · s3 모두 지움 → 불가 · allDeleted")
    void REQ_248_7_모두_지움() {
        Map<String, BlogSentence> p = base();
        M.forEach(s -> p.get(s).tombstone(NOW));
        QuoteRange.Current c = range(p, one("s0", "s4"));
        assertThat(c.allDeleted()).isTrue();
        assertThat(updatable(c)).isFalse();
        assertThat(c.sids()).isEmpty();
    }

    @Test
    @DisplayName("REQ-248 · #8 글 지움 → 불가 · allDeleted")
    void REQ_248_8_글_지움() {
        QuoteRange.Current c = QuoteRange.current(M, null, SEEN);
        assertThat(c.allDeleted()).isTrue();
        assertThat(updatable(c)).isFalse();
    }

    @Test
    @DisplayName("REQ-248 · #9 구간 밖만 고침 → 불가")
    void REQ_248_9_구간_밖만_고침() {
        Map<String, BlogSentence> p = base();
        p.get("s4").edit("넷째를 고쳤다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "s4"));
        assertThat(updatable(c)).isFalse();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #10 고쳤다가 되돌림 → 불가")
    void REQ_248_10_되돌림() {
        Map<String, BlogSentence> p = base();
        p.get("s2").edit("둘째 문장을 고쳤다.");
        p.get("s2").edit("둘째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "s4"));
        assertThat(updatable(c)).isFalse();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
    }

    @Test
    @DisplayName("REQ-248 · #11 인용 전에 s3이 이미 나뉨(조각 행 id ≤ seen) → 불가")
    void REQ_248_11_인용_전_나뉨() {
        Map<String, BlogSentence> p = base();
        add(p, row(SEEN, "y", "인용 전 조각이다.", "s3")); // id = seen 경계 — 인용 때 이미 있던 행
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "s4", "y"));
        assertThat(updatable(c)).isFalse();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
    }

    // ── 더한 사례 ──

    @Test
    @DisplayName("REQ-248 · 경계만 끼운 나뉨은 불가(R05b — 이은 글 = 스냅샷)")
    void REQ_248_R05b_경계만_끼운_나뉨() {
        Map<String, BlogSentence> p = base();
        p.get("s3").edit("셋째");
        add(p, row(21, "xb", "문장이다.", "s3"));
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "xb", "s4"));
        assertThat(c.sids()).containsExactly("s1", "s2", "s3", "xb");
        assertThat(updatable(c)).isFalse();
    }

    @Test
    @DisplayName("REQ-248 · R12 두 문단에 걸친 구간 → 문단 사이 줄바꿈 하나")
    void REQ_248_R12_두_문단() {
        QuoteRange.Current c = range(base(), new Layout(List.of(para(List.of("s0", "s1")), para(List.of("s2", "s3", "s4")))));
        assertThat(c.text()).isEqualTo("첫째 문장이다.\n둘째 문장이다. 셋째 문장이다.");
    }

    @Test
    @DisplayName("REQ-248 · R12b 같은 문단 줄바꿈(br)도 줄바꿈 하나")
    void REQ_248_R12b_줄바꿈() {
        QuoteRange.Current c = range(base(), new Layout(List.of(para(List.of("s0", "s1"), List.of("s2", "s3", "s4")))));
        assertThat(c.text()).isEqualTo("첫째 문장이다.\n둘째 문장이다. 셋째 문장이다.");
    }

    @Test
    @DisplayName("REQ-248 · 지금 구간이 박스를 걸침 → crossesBox(R13)")
    void REQ_248_R13_박스_걸침() {
        Layout l = new Layout(List.of(para(List.of("s0", "s1")), box(1), para(List.of("s2", "s3", "s4"))));
        QuoteRange.Current c = range(base(), l);
        assertThat(c.crossesBox()).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
        // 박스 밖 구간은 거짓 — 양성 대조
        assertThat(QuoteRange.current(List.of("s2", "s3"), post(base(), l), SEEN).crossesBox()).isFalse();
    }

    @Test
    @DisplayName("REQ-248 · R14 한 문장 주소는 그 문장만(나뉜 조각 0)")
    void REQ_248_R14_한_문장_주소() {
        Map<String, BlogSentence> p = base();
        p.get("s3").edit("셋째");
        add(p, row(21, "z", "문장이다.", "s3"));
        QuoteRange.Current c = QuoteRange.current(List.of("s3"), post(p, one("s0", "s1", "s2", "s3", "z", "s4")), null);
        assertThat(c.sids()).containsExactly("s3");
    }

    @Test
    @DisplayName("REQ-248 · R15 끝 문장 b가 지워진 a-b → a만")
    void REQ_248_R15_끝_문장_지워짐() {
        Map<String, BlogSentence> p = base();
        p.get("s3").tombstone(NOW);
        QuoteRange.Current c = QuoteRange.current(List.of("s1", "s3"), post(p, one("s0", "s1", "s2", "s4")), null);
        assertThat(c.sids()).containsExactly("s1");
        assertThat(c.allDeleted()).isFalse();
    }

    @Test
    @DisplayName("REQ-248 · R16 인용 뒤 두 번 나뉜 조각까지(행 id가 커진다)")
    void REQ_248_R16_두_번_나뉨() {
        Map<String, BlogSentence> p = base();
        add(p, row(21, "z1", "조각 하나.", "s3"));
        add(p, row(22, "z2", "조각 둘.", "z1"));
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "z1", "z2", "s4"));
        assertThat(c.sids()).containsExactly("s1", "s2", "s3", "z1", "z2");
    }

    @Test
    @DisplayName("REQ-248 · R17 합쳐진 문장이 다시 지워짐 → 옛 주소 allDeleted")
    void REQ_248_R17_합친_뒤_지움() {
        Map<String, BlogSentence> p = base();
        p.get("s2").mergeInto("s3", NOW);
        p.get("s3").tombstone(NOW);
        QuoteRange.Current c = QuoteRange.current(List.of("s2"), post(p, one("s0", "s1", "s4")), null);
        assertThat(c.allDeleted()).isTrue();
    }

    @Test
    @DisplayName("REQ-248 · R18 인용 뒤 조각이 구간 밖 s4에 다시 합쳐짐 → s4까지")
    void REQ_248_R18_조각이_다시_합쳐짐() {
        Map<String, BlogSentence> p = base();
        p.get("s3").edit("셋째다.");
        BlogSentence x = row(21, "x", "문장이었다.", "s3");
        x.mergeInto("s4", NOW);
        add(p, x);
        p.get("s4").edit("문장이었다 그리고 넷째 문장이다.");
        QuoteRange.Current c = range(p, one("s0", "s1", "s2", "s3", "s4"));
        assertThat(updatable(c)).isTrue();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3", "s4");
    }

    @Test
    @DisplayName("REQ-248 · V1 a-b 구성원이 합침 사슬로 닿음 → viaMerge")
    void REQ_248_V1_합침으로_닿음() {
        Map<String, BlogSentence> p = base();
        p.get("s3").mergeInto("s2", NOW);
        p.get("s2").edit("둘째 문장이고 셋째 문장이다.");
        QuoteRange.Current c = QuoteRange.current(List.of("s2", "s3"), post(p, one("s0", "s1", "s2", "s4")), null);
        assertThat(c.viaMerge()).isTrue();
        assertThat(c.sids()).containsExactly("s2");
    }

    @Test
    @DisplayName("REQ-248 · V2 산 구성원만 → viaMerge 거짓 · 사이 문장 포함")
    void REQ_248_V2_산_구성원만() {
        QuoteRange.Current c = QuoteRange.current(List.of("s1", "s3"), post(base(), one("s0", "s1", "s2", "s3", "s4")), null);
        assertThat(c.viaMerge()).isFalse();
        assertThat(c.sids()).containsExactly("s1", "s2", "s3");
    }

    @Test
    // 구현 뒤에 더한 줄(Red 없음) — hit를 산 행으로 거르는 줄의 돌연변이가 표 전부에서 살아남아 박았다
    @DisplayName("REQ-248 · V3 합침 끝이 묘비면 viaMerge 아님 — 산 구성원만 남음")
    void REQ_248_V3_합침_끝이_묘비() {
        Map<String, BlogSentence> p = base();
        p.get("s2").mergeInto("s3", NOW);
        p.get("s3").tombstone(NOW);
        QuoteRange.Current c = QuoteRange.current(List.of("s1", "s2"), post(p, one("s0", "s1", "s4")), null);
        assertThat(c.sids()).containsExactly("s1");
        assertThat(c.viaMerge()).isFalse();
    }

    // ── between(만들기) ──

    @Test
    @DisplayName("REQ-247 · between — 뒤집힌 순서도 처음 ~ 끝")
    void between_뒤집힌_순서() {
        assertThat(QuoteRange.between(post(base(), one("s0", "s1", "s2", "s3")), "s3", "s1"))
                .isEqualTo(Optional.of(List.of("s1", "s2", "s3")));
    }

    @Test
    @DisplayName("REQ-247 · between — 사이에 박스 → empty")
    void between_사이에_박스() {
        Layout l = new Layout(List.of(para(List.of("s0", "s1")), box(1), para(List.of("s2", "s3", "s4"))));
        assertThat(QuoteRange.between(post(base(), l), "s1", "s2")).isEmpty();
        assertThat(QuoteRange.between(post(base(), l), "s2", "s4")).isEqualTo(Optional.of(List.of("s2", "s3", "s4")));
    }

    @Test
    @DisplayName("REQ-247 · between — 한 문장")
    void between_한_문장() {
        assertThat(QuoteRange.between(post(base(), one("s0", "s1")), "s1", "s1")).isEqualTo(Optional.of(List.of("s1")));
    }

    // ── 이음 J1~J5(글자 수 = 문장 글자 합 + 문장 수 − 1 · UTF-16) ──

    private static QuoteRange.Current joined(List<String> members, Layout layout) {
        Map<String, BlogSentence> j = new LinkedHashMap<>();
        String[][] r = {{"a", "a."}, {"b", "b."}, {"c", "c."}, {"d", "d."}, {"e", "좋다😊."}, {"f", "끝."}};
        for (int i = 0; i < r.length; i++) j.put(r[i][0], row(i + 1, r[i][0], r[i][1], null));
        return QuoteRange.current(members, post(j, layout), null);
    }

    @Test
    @DisplayName("REQ-247 · 이은 글 길이 = 글자 합 + (문장 수 − 1) · J1 한 줄")
    void REQ_247_J1_한_줄() {
        String t = joined(List.of("a", "b"), one("a", "b")).text();
        assertThat(t).isEqualTo("a. b.").hasSize(2 + 2 + 1);
    }

    @Test
    @DisplayName("REQ-247 · 이은 글 길이 = 글자 합 + (문장 수 − 1) · J2 줄바꿈")
    void REQ_247_J2_줄바꿈() {
        String t = joined(List.of("a", "b"), new Layout(List.of(para(List.of("a"), List.of("b"))))).text();
        assertThat(t).isEqualTo("a.\nb.").hasSize(2 + 2 + 1);
    }

    @Test
    @DisplayName("REQ-247 · 이은 글 길이 = 글자 합 + (문장 수 − 1) · J3 두 문단")
    void REQ_247_J3_두_문단() {
        String t = joined(List.of("a", "b"), new Layout(List.of(para(List.of("a")), para(List.of("b"))))).text();
        assertThat(t).isEqualTo("a.\nb.").hasSize(2 + 2 + 1);
    }

    @Test
    @DisplayName("REQ-247 · 이은 글 길이 = 글자 합 + (문장 수 − 1) · J4 한 줄 · 줄바꿈 · 문단")
    void REQ_247_J4_섞임() {
        String t = joined(List.of("a", "d"),
                new Layout(List.of(para(List.of("a", "b"), List.of("c")), para(List.of("d"))))).text();
        assertThat(t).isEqualTo("a. b.\nc.\nd.").hasSize(8 + 3);
    }

    @Test
    @DisplayName("REQ-247 · 이은 글 길이 = 글자 합 + (문장 수 − 1) · J5 이모지(UTF-16 둘)")
    void REQ_247_J5_이모지() {
        String t = joined(List.of("e", "f"), one("e", "f")).text();
        assertThat(t).isEqualTo("좋다😊. 끝.");
        assertThat(t.length()).isEqualTo(5 + 2 + 1);
    }

    @Test
    @DisplayName("REQ-247 · join — 줄 목록(쪽지 생각 · MARGIN 박스도 같은 함수)")
    void join_줄_목록() {
        assertThat(QuoteRange.join(List.of(List.of("a.", "b."), List.of("c."), List.of("d.")))).isEqualTo("a. b.\nc.\nd.");
    }
}
