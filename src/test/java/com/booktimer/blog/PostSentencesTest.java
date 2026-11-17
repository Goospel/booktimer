package com.booktimer.blog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 댓글 묶음 — 합침 사슬의 끝(계획 2026-11-16-blog-b2 §6.3). 순수(엔티티만 만든다 · DB 0). */
class PostSentencesTest {

    private static final Instant AT = Instant.parse("2026-11-17T00:00:00Z");

    private static BlogSentence live(String sid) {
        return BlogSentence.create(null, sid, sid, null);
    }

    private static BlogSentence merged(String sid, String into) {
        BlogSentence s = live(sid);
        s.mergeInto(into, AT);
        return s;
    }

    private static BlogSentence tomb(String sid) {
        BlogSentence s = live(sid);
        s.tombstone(AT);
        return s;
    }

    private static PostSentences of(BlogSentence... rows) {
        return new PostSentences(new Layout(List.of()), List.of(rows));
    }

    @Test
    @DisplayName("REQ-250 · 묶음 — 산 문장은 자기 · 한 번 합침 · 두 번 합침 사슬은 끝의 산 문장으로 모인다")
    void chainsEndAtLiveSentence() {
        PostSentences ps = of(live("CCCCCCCC"), merged("AAAAAAAA", "BBBBBBBB"), merged("BBBBBBBB", "CCCCCCCC"),
                live("DDDDDDDD"), merged("EEEEEEEE", "DDDDDDDD"));

        assertThat(ps.end("CCCCCCCC")).isEqualTo("CCCCCCCC");
        assertThat(ps.end("BBBBBBBB")).isEqualTo("CCCCCCCC");
        assertThat(ps.end("AAAAAAAA")).isEqualTo("CCCCCCCC");
        assertThat(ps.group("AAAAAAAA")).containsExactlyInAnyOrder("AAAAAAAA", "BBBBBBBB", "CCCCCCCC");
        assertThat(ps.group("CCCCCCCC")).containsExactlyInAnyOrder("AAAAAAAA", "BBBBBBBB", "CCCCCCCC");
        assertThat(ps.group("DDDDDDDD")).containsExactlyInAnyOrder("DDDDDDDD", "EEEEEEEE");
    }

    @Test
    @DisplayName("REQ-250 · 묶음 — 묘비는 자기 묶음 · 묘비로 끝나는 합침은 그 묘비로 · 이 글의 행이 아니면 빈 묶음")
    void tombstonesAndStrangers() {
        PostSentences ps = of(tomb("TTTTTTTT"), merged("MMMMMMMM", "TTTTTTTT"), merged("OOOOOOOO", "ZZZZZZZZ"),
                live("LLLLLLLL"));

        assertThat(ps.end("TTTTTTTT")).isEqualTo("TTTTTTTT");
        assertThat(ps.end("MMMMMMMM")).isEqualTo("TTTTTTTT");
        assertThat(ps.group("TTTTTTTT")).containsExactlyInAnyOrder("TTTTTTTT", "MMMMMMMM");
        assertThat(ps.end("OOOOOOOO")).as("이 글에 없는 행으로 합쳐졌으면 거기서 멈춘다").isEqualTo("OOOOOOOO");
        assertThat(ps.group("OOOOOOOO")).containsExactly("OOOOOOOO");
        assertThat(ps.group("XXXXXXXX")).isEmpty();
        assertThat(ps.group("LLLLLLLL")).containsExactly("LLLLLLLL");
    }

    @Test
    @DisplayName("REQ-250 · 묶음 — 고리 픽스처에서도 멈춘다")
    void cycleStops() {
        PostSentences ps = of(merged("AAAAAAAA", "BBBBBBBB"), merged("BBBBBBBB", "AAAAAAAA"));

        assertThat(ps.end("AAAAAAAA")).isIn("AAAAAAAA", "BBBBBBBB");
        assertThat(ps.group("AAAAAAAA")).isNotEmpty();
    }
}
