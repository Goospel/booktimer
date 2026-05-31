package com.booktimer.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadingTimer 도메인 메서드 테스트 (DB 무관 — 객체만 생성해 검증).
 *
 * accrueUntil(today): lastAccrualDate ~ today 경과 일수만큼 소급 누적하고
 * lastAccrualDate 를 today 로 전진시킨다. (N-001 Lazy 계산)
 */
class ReadingTimerTest {

    private static final long HOUR = 3600L;
    private static final LocalDate DAY0 = LocalDate.of(2026, 5, 31);

    private ReadingTimer timerWith(long remaining, LocalDate last) {
        // increment 1h, cap 5h
        return ReadingTimer.of(HOUR, 5 * HOUR, remaining, last);
    }

    @Test
    @DisplayName("3일 경과: 잔여가 3시간 늘고 기준일이 today로 전진한다")
    void accrueUntil_threeDaysLater() {
        ReadingTimer timer = timerWith(0L, DAY0);

        timer.accrueUntil(DAY0.plusDays(3));

        assertThat(timer.getRemainingSeconds()).isEqualTo(3 * HOUR);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0.plusDays(3));
    }

    @Test
    @DisplayName("같은 날 재호출: 잔여·기준일 모두 그대로 (멱등)")
    void accrueUntil_sameDay_idempotent() {
        ReadingTimer timer = timerWith(1800L, DAY0);

        timer.accrueUntil(DAY0);

        assertThat(timer.getRemainingSeconds()).isEqualTo(1800L);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
    }

    @Test
    @DisplayName("누적이 cap을 넘으면 cap으로 클램프된다")
    void accrueUntil_clampedToCap() {
        ReadingTimer timer = timerWith(4 * HOUR, DAY0); // +3h(3일) = 7h → cap 5h

        timer.accrueUntil(DAY0.plusDays(3));

        assertThat(timer.getRemainingSeconds()).isEqualTo(5 * HOUR);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0.plusDays(3));
    }

    @Test
    @DisplayName("과거 날짜로 호출(시계 역행): 잔여·기준일 그대로")
    void accrueUntil_pastDate_noChange() {
        ReadingTimer timer = timerWith(1800L, DAY0);

        timer.accrueUntil(DAY0.minusDays(2));

        assertThat(timer.getRemainingSeconds()).isEqualTo(1800L);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
    }
}
