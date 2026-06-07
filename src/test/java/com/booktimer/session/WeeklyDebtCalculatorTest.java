package com.booktimer.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7일 윈도우 per-day 부채 순수 계산 검증 (DB/시간 무관).
 *
 * <p>부채 = 날짜별 독립. 하루 부채 = max(0, 하루목표 − 그날 읽은 초). 활성 범위는 최근 7일(오늘 포함)이고,
 * 그보다 오래된 날은 표시·집계 대상이 아니다(= cap을 대체하는 자동 용서). "오늘"은 호출자가 유저 타임존으로
 * 정해 넘긴다(N-010) — 여기선 순수 계산만 본다.
 */
class WeeklyDebtCalculatorTest {

    private static final long GOAL = 3600L; // 하루 목표 1시간
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 7);

    private static Map<LocalDate, Long> reads() {
        return new HashMap<>();
    }

    // --- 오늘 부채 ---

    @Test
    @DisplayName("오늘 안 읽었으면 오늘 부채 = 하루 목표")
    void todayDebt_noReading_equalsGoal() {
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads(), GOAL, TODAY);
        assertThat(debt.todayDebtSeconds()).isEqualTo(GOAL);
    }

    @Test
    @DisplayName("오늘 일부 읽었으면 오늘 부채 = 목표 − 읽은 양")
    void todayDebt_partialReading_isRemainder() {
        Map<LocalDate, Long> reads = reads();
        reads.put(TODAY, 1200L); // 20분 읽음
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.todayDebtSeconds()).isEqualTo(GOAL - 1200L);
    }

    @Test
    @DisplayName("오늘 목표를 초과해 읽었으면 오늘 부채 = 0 (음수로 안 내려감)")
    void todayDebt_overGoal_isZero() {
        Map<LocalDate, Long> reads = reads();
        reads.put(TODAY, GOAL + 5000L);
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.todayDebtSeconds()).isZero();
    }

    // --- 빠뜨린 날(미충족) 목록 ---

    @Test
    @DisplayName("빠뜨린 날 목록에는 오늘이 포함되지 않는다 (오늘은 헤드라인)")
    void missedDays_excludeToday() {
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads(), GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(TODAY);
    }

    @Test
    @DisplayName("아무 날도 안 읽었으면 윈도우 내 과거 6일이 모두 빠뜨린 날로 잡힌다")
    void missedDays_allEmpty_listsSixPastDaysInWindow() {
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads(), GOAL, TODAY);
        assertThat(debt.missedDays()).hasSize(6); // today-1 .. today-6
        assertThat(debt.missedDays()).allSatisfy(d -> assertThat(d.debtSeconds()).isEqualTo(GOAL));
    }

    @Test
    @DisplayName("목표를 채운 과거 날은 빠뜨린 날 목록에서 빠진다")
    void missedDays_fullyReadDayExcluded() {
        Map<LocalDate, Long> reads = reads();
        reads.put(TODAY.minusDays(2), GOAL); // 이틀 전은 목표 달성
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(TODAY.minusDays(2));
        assertThat(debt.missedDays()).hasSize(5); // 6일 중 채운 1일 제외
    }

    @Test
    @DisplayName("윈도우 경계: 정확히 6일 전(today-6)은 포함, 7일 전(today-7)은 자동 용서로 제외")
    void missedDays_windowBoundary() {
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads(), GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date)
                .contains(TODAY.minusDays(6))
                .doesNotContain(TODAY.minusDays(7));
    }

    @Test
    @DisplayName("빠뜨린 날은 최근이 먼저(내림차순) 정렬된다")
    void missedDays_orderedNewestFirst() {
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads(), GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date)
                .containsExactly(
                        TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3),
                        TODAY.minusDays(4), TODAY.minusDays(5), TODAY.minusDays(6));
    }

    @Test
    @DisplayName("윈도우 내 모든 날을 채웠으면 오늘 부채 0 + 빠뜨린 날 없음")
    void allMet_zeroTodayDebt_noMissed() {
        Map<LocalDate, Long> reads = reads();
        for (int offset = 0; offset <= 6; offset++) {
            reads.put(TODAY.minusDays(offset), GOAL);
        }
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.todayDebtSeconds()).isZero();
        assertThat(debt.missedDays()).isEmpty();
    }

    @Test
    @DisplayName("총 부채 = 오늘 부채 + 빠뜨린 날 부채 합 (관리자 요약용)")
    void totalDebt_sumsTodayAndMissed() {
        Map<LocalDate, Long> reads = reads();
        reads.put(TODAY, 600L);              // 오늘 부채 3000
        reads.put(TODAY.minusDays(1), 3000L); // 어제 부채 600
        // 나머지 5일(today-2..today-6)은 각 3600 부채
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        long expected = 3000L + 600L + 5 * GOAL;
        assertThat(debt.totalDebtSeconds()).isEqualTo(expected);
    }
}
