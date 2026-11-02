package com.booktimer.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 앱 마감 문의 날짜 정책 {@link MeasureFinishApiController#check} — 스프링 없는 고정 시각 단위 테스트.
 *
 * <p>통합 테스트는 실제 시계 기준 상대값이라 밀리초 경계를 못 잰다. 그래서 경계는 여기서 고정 시각으로 본다.
 * 앱은 422에서만 칸을 지우고 409는 남기므로, 두 상태가 1ms 차이로 갈리는 자리가 이 테스트의 요점이다
 * ({@code isAfter} ↔ {@code !isBefore} 뒤집힘을 잡는다).
 */
class MeasureFinishPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final Duration MS = Duration.ofMillis(1);

    private static void assertStatus(Instant s, Instant e, HttpStatus expected) {
        assertThatThrownBy(() -> MeasureFinishApiController.check(s, e, NOW))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }

    private static void assertPasses(Instant s, Instant e) {
        MeasureFinishApiController.Interval i = MeasureFinishApiController.check(s, e, NOW);
        assertThat(i.start()).isEqualTo(s);
        assertThat(i.end()).isEqualTo(e);
    }

    @Test
    @DisplayName("REQ-355 · 날짜 정책 경계(check — 고정 시각)")
    void boundaries() {
        Instant future5 = NOW.plus(Duration.ofMinutes(5));
        // 시작 = 지금 + 5분은 정책 안 — 끝이 미래라 409(남긴다) · 1ms 더 늦으면 422(지운다)
        assertStatus(future5, future5, HttpStatus.CONFLICT);
        assertStatus(future5.plus(MS), future5.plus(MS), HttpStatus.UNPROCESSABLE_CONTENT);

        Instant old7 = NOW.minus(Duration.ofDays(7));
        assertPasses(old7, NOW);
        assertStatus(old7.minus(MS), NOW, HttpStatus.UNPROCESSABLE_CONTENT);

        Instant s = NOW.minusSeconds(600);
        assertPasses(s, NOW);
        assertStatus(s, NOW.plus(MS), HttpStatus.CONFLICT);

        assertPasses(s, s);
        assertStatus(s, s.minus(MS), HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
