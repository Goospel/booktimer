package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DailyQuotaCalculatorTest {

    private static final LocalDate BASE = LocalDate.of(2026, 1, 10);
    private static final long GOAL = 3600L;

    @Test
    @DisplayName("빈 기록 → 0")
    void empty_returns0() {
        assertThat(DailyQuotaCalculator.metDayCount(Map.of(), d -> GOAL, BASE)).isEqualTo(0);
    }

    @Test
    @DisplayName("달성·미달성 혼재 → 달성 날만 카운트")
    void mixed_countsOnlyMet() {
        Map<LocalDate, Long> seconds = Map.of(
                BASE, GOAL,              // 달성
                BASE.plusDays(1), 1800L  // 미달성 (목표 3600, 읽은 1800)
        );
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> GOAL, BASE)).isEqualTo(1);
    }

    @Test
    @DisplayName("정확히 목표 = 달성 경계값")
    void exactlyAtGoal_countsMet() {
        Map<LocalDate, Long> seconds = Map.of(BASE, GOAL);
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> GOAL, BASE)).isEqualTo(1);
    }

    @Test
    @DisplayName("목표 1초 미달 → 달성 아님")
    void oneSecondShort_notMet() {
        Map<LocalDate, Long> seconds = Map.of(BASE, GOAL - 1);
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> GOAL, BASE)).isEqualTo(0);
    }

    @Test
    @DisplayName("목표 변경 후 그날 목표로 판정 — 소급 함정 방지(N-059)")
    void goalChanged_usesPerDayGoal() {
        LocalDate day1 = BASE;
        LocalDate day2 = BASE.plusDays(1);
        Map<LocalDate, Long> seconds = Map.of(day1, 1800L, day2, 1800L);
        // day1: 목표 1800(달성), day2: 목표 3600(미달성)
        int count = DailyQuotaCalculator.metDayCount(seconds,
                d -> d.isBefore(day2) ? 1800L : 3600L, BASE);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("baseline 이전 날짜 제외")
    void beforeBaseline_excluded() {
        LocalDate before = BASE.minusDays(1);
        Map<LocalDate, Long> seconds = Map.of(before, GOAL, BASE, GOAL);
        // before는 baseline(BASE) 이전 → 1개만 카운트
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> GOAL, BASE)).isEqualTo(1);
    }

    @Test
    @DisplayName("목표 0 이하인 날 제외 (가입 전 미설정)")
    void zeroGoal_notCounted() {
        Map<LocalDate, Long> seconds = Map.of(BASE, GOAL);
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> 0L, BASE)).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 날 모두 달성 → 전부 카운트")
    void allMet_countsAll() {
        Map<LocalDate, Long> seconds = Map.of(
                BASE, GOAL, BASE.plusDays(1), GOAL, BASE.plusDays(2), GOAL + 100
        );
        assertThat(DailyQuotaCalculator.metDayCount(seconds, d -> GOAL, BASE)).isEqualTo(3);
    }
}
