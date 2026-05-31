package com.booktimer.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lazy 누적 순수 계산 로직 테스트 (DB 무관).
 *
 * 규칙(N-001): remaining += daysElapsed × increment, 그 뒤 min(remaining, cap).
 * 경계: 0일 / 1일 / N일 경과, cap 초과 클램프, 이미 cap 도달, 음수 방지.
 */
class AccrualCalculatorTest {

    private static final long HOUR = 3600L;

    @Test
    @DisplayName("0일 경과면 잔여는 그대로다")
    void zeroDays_keepsRemaining() {
        long result = AccrualCalculator.accrue(1800L, HOUR, 5 * HOUR, 0);
        assertThat(result).isEqualTo(1800L);
    }

    @Test
    @DisplayName("1일 경과면 증가값만큼 더해진다 (30분 + 1시간 = 1시간 30분)")
    void oneDay_addsIncrement() {
        long result = AccrualCalculator.accrue(1800L, HOUR, 5 * HOUR, 1);
        assertThat(result).isEqualTo(1800L + HOUR);
    }

    @Test
    @DisplayName("N일 경과면 증가값 × N 만큼 더해진다 (소급 누적)")
    void multipleDays_addsIncrementTimesN() {
        long result = AccrualCalculator.accrue(0L, HOUR, 10 * HOUR, 3);
        assertThat(result).isEqualTo(3 * HOUR);
    }

    @Test
    @DisplayName("누적 결과가 cap을 넘으면 cap으로 클램프된다")
    void exceedsCap_clampedToCap() {
        // 4시간 잔여 + 3시간(3일) = 7시간 → cap 5시간으로 제한
        long cap = 5 * HOUR;
        long result = AccrualCalculator.accrue(4 * HOUR, HOUR, cap, 3);
        assertThat(result).isEqualTo(cap);
    }

    @Test
    @DisplayName("이미 cap에 도달했으면 더 쌓이지 않는다")
    void alreadyAtCap_staysCap() {
        long cap = 5 * HOUR;
        long result = AccrualCalculator.accrue(cap, HOUR, cap, 2);
        assertThat(result).isEqualTo(cap);
    }

    @Test
    @DisplayName("음수 경과일(시계 역행 등)은 잔여를 그대로 둔다")
    void negativeDays_keepsRemaining() {
        long result = AccrualCalculator.accrue(1800L, HOUR, 5 * HOUR, -2);
        assertThat(result).isEqualTo(1800L);
    }

    @Test
    @DisplayName("음수 increment/cap 등 잘못된 설정은 예외")
    void invalidConfig_throws() {
        assertThatThrownBy(() -> AccrualCalculator.accrue(0L, -1L, HOUR, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccrualCalculator.accrue(0L, HOUR, -1L, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
