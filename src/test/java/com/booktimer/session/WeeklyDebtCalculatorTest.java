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
    @DisplayName("1분 미만 부족한 과거 날은 빠뜨린 날이 아니다 — 분 단위로 올린 목표가 만든 '0분 부족' 거짓 미충족 차단")
    void missedDays_subMinuteShortfall_forgiven() {
        // 그날 옛 분 단위 목표(예: 60분=3600초)는 채웠는데, 이후 목표가 1분 올라(61분=3660초) 30초 부족으로 보이는 잔재.
        // 목표는 항상 분 단위라 1분 미만 부족 = 목표를 사후에 분 단위로 올려 생긴 반올림 잔재 → 미충족으로 치지 않는다.
        Map<LocalDate, Long> reads = reads();
        LocalDate met = TODAY.minusDays(2);
        reads.put(met, GOAL - 30L); // 30초 부족 → 표시상 "0분 부족"
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(met);
    }

    @Test
    @DisplayName("경계: 정확히 1분(60초) 부족한 날은 여전히 빠뜨린 날로 잡힌다 (진짜 미충족)")
    void missedDays_exactlyOneMinuteShort_stillListed() {
        Map<LocalDate, Long> reads = reads();
        LocalDate day = TODAY.minusDays(3);
        reads.put(day, GOAL - 60L); // 정확히 1분 부족 → 표시상 "1분 부족"
        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, GOAL, TODAY);
        assertThat(debt.missedDays()).extracting(DayDebt::date).contains(day);
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

    // --- per-day 목표(날짜별로 목표가 다를 때) ---

    /** 윈도우 7일 각 날짜에 목표를 채운 맵을 만든다. {@code raisedFromOffset}일 전부터(=더 과거) 옛 목표, 그 이후 최근은 새 목표. */
    private static Map<LocalDate, Long> goalsRaisedRecently(long oldGoal, long newGoal, int raisedFromOffset) {
        Map<LocalDate, Long> goals = new HashMap<>();
        for (int offset = 0; offset <= 6; offset++) {
            goals.put(TODAY.minusDays(offset), offset >= raisedFromOffset ? oldGoal : newGoal);
        }
        return goals;
    }

    @Test
    @DisplayName("per-day 목표: 과거 날은 그날 목표로 판정 — 목표를 올려도 옛 목표를 채운 날은 빠뜨린 날이 아니다")
    void perDayGoal_pastDayMetUnderItsOwnGoal_excluded() {
        LocalDate metDay = TODAY.minusDays(4); // 그날 목표는 옛 60분, 그날 60분 읽어 채움
        Map<LocalDate, Long> reads = reads();
        reads.put(metDay, 3600L);
        // 3일 전부터(=더 과거) 옛 목표 3600, 최근 0~2일 전은 새 목표 3660 — 즉 3일 전 목표 인상
        Map<LocalDate, Long> goalByDate = goalsRaisedRecently(3600L, 3660L, 3);

        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, goalByDate, TODAY);

        // 현재 목표(3660)로 일괄 판정했다면 60초 부족으로 떴겠지만, 그날 목표(3600)로 보면 부채 0 → 제외
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(metDay);
    }

    @Test
    @DisplayName("per-day 목표: 그날 목표에 진짜 미달한 날은 여전히 빠뜨린 날로 잡힌다 (과용서 안 함)")
    void perDayGoal_dayUnderItsOwnGoal_stillListed() {
        LocalDate shortDay = TODAY.minusDays(2); // 이 날 목표는 새 목표 3660, 3000만 읽음 → 660초 부족
        Map<LocalDate, Long> reads = reads();
        reads.put(shortDay, 3000L);
        Map<LocalDate, Long> goalByDate = goalsRaisedRecently(3600L, 3660L, 3);

        WeeklyDebt debt = WeeklyDebtCalculator.compute(reads, goalByDate, TODAY);

        assertThat(debt.missedDays()).contains(new DayDebt(shortDay, 660L));
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
