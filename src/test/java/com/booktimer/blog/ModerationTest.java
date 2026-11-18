package com.booktimer.blog;

import com.booktimer.blog.Moderation.Action;
import com.booktimer.blog.Moderation.Kind;
import com.booktimer.blog.Moderation.Next;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 가림 상태 · 만료 · 전이 표(계획 2026-11-16-blog-b2 §4.5 · §6.5 — 순수). */
class ModerationTest {

    private static final Instant HID = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-12-20T00:00:00Z");

    @Test
    @DisplayName("REQ-333 · 임시조치 moderation.until = 가린 시각 + 30일 · 저작권 중단 · 정책 가림은 until 없음 · 만료 목록에도 없다")
    void untilOnlyForTemp() {
        assertThat(Moderation.of(HID, Kind.TEMP, "n").until()).isEqualTo(HID.plus(Duration.ofDays(30)));
        assertThat(Moderation.of(HID, Kind.COPYRIGHT, null).until()).isNull();
        assertThat(Moderation.of(HID, Kind.POLICY, null).until()).isNull();
        Instant late = HID.plus(Duration.ofDays(400));
        assertThat(Moderation.expired(HID, Kind.COPYRIGHT, late)).isFalse();
        assertThat(Moderation.expired(HID, Kind.POLICY, late)).isFalse();
        assertThat(Moderation.expired(HID, Kind.REMOVED, late)).isFalse();
        assertThat(Moderation.of(null, null, null)).as("안 가렸으면 moderation 없음").isNull();
    }

    @Test
    @DisplayName("REQ-333 · 30일 지난 임시조치만 만료 목록(29일 23:59는 아니다)")
    void expiryBoundary() {
        Instant thirty = HID.plus(Duration.ofDays(30));
        assertThat(Moderation.expired(HID, Kind.TEMP, thirty.minusSeconds(1))).isFalse();
        assertThat(Moderation.expired(HID, Kind.TEMP, thirty)).isTrue();
        assertThat(Moderation.expired(HID, Kind.TEMP, thirty.plusSeconds(1))).isTrue();
        assertThat(Moderation.expired(null, Kind.TEMP, thirty)).isFalse();
    }

    @Test
    @DisplayName("REQ-333 · hidden_at만 있고 hide_kind null인 행은 POLICY로 읽는다")
    void nullKindReadsAsPolicy() {
        assertThat(Moderation.kindOf(HID, null)).isEqualTo(Kind.POLICY);
        assertThat(Moderation.kindOf(null, null)).isNull();
        assertThat(Moderation.kindOf(null, Kind.TEMP)).as("hidden_at이 null이면 보임").isNull();
        assertThat(Moderation.of(HID, null, null).kind()).isEqualTo("POLICY");
    }

    /**
     * §6.5 전이 표 — 지금 상태(VISIBLE = 안 가림 · NULLKIND = hidden_at만) × 행동. 기대: SAME(그대로) · BAD(400) ·
     * VISIBLE · 아니면 kind 이름 + hidden_at이 새로(NOW)인지 그대로(HID)인지.
     */
    @ParameterizedTest(name = "REQ-333 · 지운 단위 다시 보이기 · 가림 400 · 다시 지우기는 내용을 다시 비운다 · 전이 표 · {0} {1}")
    @CsvSource({
            "VISIBLE,TEMP,TEMP NOW", "VISIBLE,COPYRIGHT,COPYRIGHT NOW", "VISIBLE,POLICY,POLICY NOW", "VISIBLE,RESTORE,SAME", "VISIBLE,REMOVE,REMOVED NOW",
            "TEMP,TEMP,SAME", "TEMP,COPYRIGHT,COPYRIGHT HID", "TEMP,POLICY,POLICY HID", "TEMP,RESTORE,VISIBLE", "TEMP,REMOVE,REMOVED HID",
            "COPYRIGHT,TEMP,TEMP NOW", "COPYRIGHT,COPYRIGHT,SAME", "COPYRIGHT,POLICY,POLICY HID", "COPYRIGHT,RESTORE,VISIBLE", "COPYRIGHT,REMOVE,REMOVED HID",
            "POLICY,TEMP,TEMP NOW", "POLICY,COPYRIGHT,COPYRIGHT HID", "POLICY,POLICY,SAME", "POLICY,RESTORE,VISIBLE", "POLICY,REMOVE,REMOVED HID",
            "NULLKIND,TEMP,TEMP NOW", "NULLKIND,POLICY,SAME", "NULLKIND,RESTORE,VISIBLE",
            "REMOVED,TEMP,BAD", "REMOVED,COPYRIGHT,BAD", "REMOVED,POLICY,BAD", "REMOVED,RESTORE,BAD", "REMOVED,REMOVE,REWIPE"
    })
    void transitions(String from, Action action, String expected) {
        Instant hiddenAt = from.equals("VISIBLE") ? null : HID;
        Kind kind = switch (from) {
            case "VISIBLE", "NULLKIND" -> null;
            default -> Kind.valueOf(from);
        };
        if (expected.equals("BAD")) {
            assertThatThrownBy(() -> Moderation.next(hiddenAt, kind, "옛 글", action, "새 글", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            return;
        }
        Next n = Moderation.next(hiddenAt, kind, "옛 글", action, "새 글", NOW);
        switch (expected) {
            case "SAME" -> assertThat(n.changed()).isFalse();
            case "REWIPE" -> {
                assertThat(n.changed()).as("상태는 그대로 — 기록 · 메일 0").isFalse();
                assertThat(n.kind()).isEqualTo(Kind.REMOVED);
            }
            case "VISIBLE" -> {
                assertThat(n.changed()).isTrue();
                assertThat(n.hiddenAt()).isNull();
                assertThat(n.kind()).isNull();
                assertThat(n.note()).as("다시 보이기는 note를 지운다").isNull();
            }
            default -> {
                String[] e = expected.split(" ");
                assertThat(n.changed()).isTrue();
                assertThat(n.kind()).isEqualTo(Kind.valueOf(e[0]));
                assertThat(n.hiddenAt()).isEqualTo(e[1].equals("NOW") ? NOW : HID);
                assertThat(n.note()).isEqualTo("새 글");
            }
        }
    }

    @Test
    @DisplayName("REQ-333 · note를 비우고 가리면 옛 note를 그대로 둔다 · 적으면 덮어쓴다")
    void blankNoteKeepsOld() {
        assertThat(Moderation.next(HID, Kind.TEMP, "옛 글", Action.POLICY, "  ", NOW).note()).isEqualTo("옛 글");
        assertThat(Moderation.next(HID, Kind.TEMP, "옛 글", Action.POLICY, "새", NOW).note()).isEqualTo("새");
    }
}
