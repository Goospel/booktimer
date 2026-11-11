package com.booktimer.blog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 배치 꼴 — 문단 → 줄 → sid · 박스 = q(계획 2026-11-11-b1-blog-core §6.3). {@code blog_post.layout_json}의 단일 출처. */
class LayoutTest {

    private static final String PLAN_SAMPLE = "[{\"p\":[[\"sidA\",\"sidB\"],[\"sidC\"]],\"q\":null},{\"p\":null,\"q\":12}]";

    private static Layout sample() {
        return new Layout(List.of(
                new Layout.Item(List.of(List.of("sidA", "sidB"), List.of("sidC")), null),
                new Layout.Item(null, 12L)));
    }

    @Test
    void 계획의_JSON_꼴_그대로_쓴다() {
        assertThat(sample().toJson()).isEqualTo(PLAN_SAMPLE);
    }

    @Test
    void 왕복하면_같은_배치_줄_경계가_br로_남는다() {
        Layout back = Layout.parse(PLAN_SAMPLE);
        assertThat(back).isEqualTo(sample());
        // 줄이 바뀌는 자리가 곧 br — sidB 뒤에서 줄이 바뀐다
        assertThat(back.items().getFirst().p()).containsExactly(List.of("sidA", "sidB"), List.of("sidC"));
        assertThat(back.items().get(1).q()).isEqualTo(12L);
        assertThat(back.items().get(1).p()).isNull();
    }

    @Test
    void sidsInOrder는_박스를_건너뛴_문장_순서() {
        Layout layout = new Layout(List.of(
                new Layout.Item(List.of(List.of("a1"), List.of("a2", "a3")), null),
                new Layout.Item(null, 3L),
                new Layout.Item(List.of(List.of("b1")), null)));
        assertThat(layout.sidsInOrder()).containsExactly("a1", "a2", "a3", "b1");
    }

    @Test
    void size는_문장과_박스를_함께_센다() {
        // 1,500개 상한(§6.3)이 세는 배치 항목 = 문장 3 + 박스 1
        assertThat(sample().size()).isEqualTo(4);
    }

    @Test
    void 첫_올리기의_빈_배치() {
        assertThat(Layout.parse(null).items()).isEmpty();
        assertThat(Layout.parse(" ").sidsInOrder()).isEmpty();
        assertThat(Layout.parse(null).size()).isZero();
    }
}
