package com.booktimer.session;

import com.booktimer.timer.GoalSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 잔디 그리드 빌더 경계값 테스트 — 순수 날짜 계산이라 스프링 없이 빠르게 검증한다.
 *
 * <p>GitHub 잔디와 동일 배치: 53주 열 × 7요일 행(일요일 top → 토요일 bottom), 오늘 주가 맨 오른쪽,
 * 미래 칸은 placeholder. 색 레벨은 <b>하루 목표(goalSeconds) 대비 달성 비율</b>로 0~4.
 */
class ContributionGraphBuilderTest {

    // 화요일
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);

    /** 테스트용 하루 목표: 1시간. */
    private static final long GOAL = 3600L;

    /** 일요일=0 ... 토요일=6 (그리드 행 인덱스). */
    private static int sundayOffset(LocalDate d) {
        return d.getDayOfWeek().getValue() % 7;
    }

    @Test
    @DisplayName("그리드는 53주 × 각 주 7칸이다")
    void grid_is_53_weeks_of_7() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        assertThat(graph.weeks()).hasSize(53);
        assertThat(graph.weeks()).allSatisfy(week -> assertThat(week).hasSize(7));
    }

    @Test
    @DisplayName("각 주의 첫 칸(행 0)은 일요일이다")
    void firstRow_isSunday() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        for (List<ContributionDay> week : graph.weeks()) {
            ContributionDay first = week.get(0);
            if (!first.isPlaceholder()) {
                assertThat(first.date().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            }
        }
    }

    @Test
    @DisplayName("오늘은 맨 왼쪽(가장 최근) 주의 요일 위치에 있고 placeholder가 아니다")
    void today_isInFirstWeek() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        ContributionDay todayCell = graph.weeks().get(0).get(sundayOffset(TODAY));
        assertThat(todayCell.isPlaceholder()).isFalse();
        assertThat(todayCell.date()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("열 순서는 최근이 왼쪽 — weeks[0]가 가장 최근 주, 오른쪽으로 갈수록 과거")
    void weeks_mostRecentOnLeft() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        LocalDate firstCol = graph.weeks().get(0).get(0).date();   // 맨 왼쪽 열의 일요일
        LocalDate secondCol = graph.weeks().get(1).get(0).date();
        LocalDate lastCol = graph.weeks().get(52).get(0).date();   // 맨 오른쪽 열의 일요일

        assertThat(firstCol).as("맨 왼쪽이 가장 최근").isAfter(secondCol);
        assertThat(lastCol).as("맨 오른쪽이 가장 과거").isBefore(secondCol);
    }

    @Test
    @DisplayName("오늘 이후(미래) 칸은 placeholder다 — 가장 최근(맨 왼쪽) 주에 위치")
    void future_isPlaceholder() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        List<ContributionDay> recentWeek = graph.weeks().get(0);
        int todayOffset = sundayOffset(TODAY); // 화요일=2 → 그 뒤(수~토)는 미래
        for (int row = todayOffset + 1; row < 7; row++) {
            assertThat(recentWeek.get(row).isPlaceholder())
                    .as("행 %d은 미래라 placeholder여야 한다", row)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("색 레벨: 목표 대비 비율 — 0%=0, ~25%=1, ~50%=2, ~100%미만=3, 100%이상=4 (경계는 위 레벨 포함)")
    void levels_byGoalRatio() {
        LocalDate d = LocalDate.of(2026, 3, 15); // 범위 내 임의 날짜, 목표 1시간(GOAL)
        assertThat(levelOf(0L, d)).as("안 읽음").isEqualTo(0);
        assertThat(levelOf(1L, d)).as("0 바로 초과").isEqualTo(1);
        assertThat(levelOf(GOAL / 4, d)).as("정확히 25%는 1").isEqualTo(1);
        assertThat(levelOf(GOAL / 4 + 1, d)).as("25% 초과").isEqualTo(2);
        assertThat(levelOf(GOAL / 2, d)).as("정확히 50%는 2").isEqualTo(2);
        assertThat(levelOf(GOAL / 2 + 1, d)).as("50% 초과").isEqualTo(3);
        assertThat(levelOf(GOAL - 1, d)).as("100% 직전").isEqualTo(3);
        assertThat(levelOf(GOAL, d)).as("정확히 100%는 4").isEqualTo(4);
        assertThat(levelOf(GOAL + 1, d)).as("목표 초과").isEqualTo(4);
    }

    @Test
    @DisplayName("목표가 0(퇴화)이면 읽은 날은 모두 4, 안 읽은 날은 0 (div-by-zero 없음)")
    void levels_zeroGoal_degenerate() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        assertThat(levelOfWithGoal(0L, d, 0L)).as("목표 0 + 안 읽음").isEqualTo(0);
        assertThat(levelOfWithGoal(1L, d, 0L)).as("목표 0 + 읽으면 만점").isEqualTo(4);
    }

    @Test
    @DisplayName("목표가 작으면 같은 독서량도 더 높은 레벨이 된다 (목표를 따라간다)")
    void levels_followGoal() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        long read = 1800L; // 30분
        // 목표 1시간이면 50% → lv2, 목표 30분이면 100% → lv4
        assertThat(levelOfWithGoal(read, d, 3600L)).isEqualTo(2);
        assertThat(levelOfWithGoal(read, d, 1800L)).isEqualTo(4);
    }

    @Test
    @DisplayName("per-day 목표: 각 칸은 그날 목표로 색을 정한다 — 목표를 올려도 옛 목표를 채운 과거 날 농도가 안 내려간다")
    void levels_perDayGoal_pastDayKeepsItsOwnLevel() {
        LocalDate metDay = LocalDate.of(2026, 3, 15); // 그날 목표 30분(1800)에 30분 읽어 100%
        // 4/1 이전(=과거)엔 목표 30분, 그 뒤엔 60분으로 인상됐다고 가정(그날 목표 리졸버).
        ToLongFunction<LocalDate> goalForDate = d -> d.isBefore(LocalDate.of(2026, 4, 1)) ? 1800L : 3600L;

        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(metDay, 1800L), TODAY, goalForDate);

        // 현재 목표(3600)로 일괄 판정했다면 50%→lv2였겠지만, 그날 목표(1800)로 보면 100%→lv4.
        assertThat(findCell(graph, metDay).level()).isEqualTo(4);
    }

    @Test
    @DisplayName("manualDates에 든 날 칸은 manual=true (사용자가 직접 채운 잔디)")
    void cell_manualDate_isMarkedManual() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        ContributionGraph graph = ContributionGraphBuilder.build(
                Map.of(d, 1800L), TODAY, date -> GOAL, Set.of(d));

        assertThat(findCell(graph, d).manual()).isTrue();
    }

    @Test
    @DisplayName("manualDates에 없는 날 칸은 manual=false (실시간 측정)")
    void cell_nonManualDate_isNotManual() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        ContributionGraph graph = ContributionGraphBuilder.build(
                Map.of(d, 1800L), TODAY, date -> GOAL, Set.of());

        assertThat(findCell(graph, d).manual()).isFalse();
    }

    @Test
    @DisplayName("평면 목표 오버로드로 만든 칸은 manual=false (기본)")
    void cell_flatGoalOverload_defaultsNotManual() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(d, 1800L), TODAY, GOAL);

        assertThat(findCell(graph, d).manual()).isFalse();
    }

    private int levelOf(long seconds, LocalDate date) {
        return levelOfWithGoal(seconds, date, GOAL);
    }

    private int levelOfWithGoal(long seconds, LocalDate date, long goalSeconds) {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(date, seconds), TODAY, goalSeconds);
        return findCell(graph, date).level();
    }

    @Test
    @DisplayName("해당 날짜 칸에 총초가 반영되고, 합계/활동일이 집계된다")
    void aggregates_totalsAndActiveDays() {
        Map<LocalDate, Long> data = Map.of(
                LocalDate.of(2026, 3, 15), 1200L,
                LocalDate.of(2026, 4, 10), 600L);

        ContributionGraph graph = ContributionGraphBuilder.build(data, TODAY, GOAL);

        assertThat(findCell(graph, LocalDate.of(2026, 3, 15)).totalSeconds()).isEqualTo(1200L);
        assertThat(graph.totalSeconds()).isEqualTo(1800L);
        assertThat(graph.activeDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("범위 밖(1년보다 과거) 날짜는 집계에 포함되지 않는다")
    void outOfRange_isIgnored() {
        Map<LocalDate, Long> data = Map.of(LocalDate.of(2000, 1, 1), 9999L);

        ContributionGraph graph = ContributionGraphBuilder.build(data, TODAY, GOAL);

        assertThat(graph.totalSeconds()).isZero();
        assertThat(graph.activeDays()).isZero();
    }

    @Test
    @DisplayName("월 라벨이 있고 열 인덱스 오름차순이며 '6월' 형식이다")
    void monthLabels_present_andOrdered() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY, GOAL);

        assertThat(graph.monthLabels()).isNotEmpty();
        assertThat(graph.monthLabels())
                .extracting(ContributionGraph.MonthLabel::weekIndex)
                .isSorted();
        assertThat(graph.monthLabels())
                .allSatisfy(m -> assertThat(m.label()).matches("\\d{1,2}월"));
    }

    @Test
    @DisplayName("REQ-01 · 1년 365일 내내 이웃 월 라벨은 3열 이상 떨어진다")
    void monthLabels_neverCloserThanThreeWeeks() {
        for (LocalDate day = LocalDate.of(2026, 1, 1); !day.isAfter(LocalDate.of(2026, 12, 31)); day = day.plusDays(1)) {
            List<ContributionGraph.MonthLabel> labels = ContributionGraphBuilder.build(Map.of(), day, GOAL).monthLabels();
            for (int i = 1; i < labels.size(); i++) {
                assertThat(labels.get(i).weekIndex() - labels.get(i - 1).weekIndex())
                        .as("%s: %s → %s", day, labels.get(i - 1), labels.get(i))
                        .isGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("REQ-02 · 이번 달이 1·2주면 이번 달 라벨을 빼고 9월은 제 열에 남는다")
    void monthLabels_dropCurrentMonth_keepPreviousInPlace() {
        // 2026-10-06(화): 이번 주 일요일 10-04 → 10월 1열, 9월은 1열부터
        assertThat(ContributionGraphBuilder.build(Map.of(), LocalDate.of(2026, 10, 6), GOAL).monthLabels().get(0))
                .isEqualTo(new ContributionGraph.MonthLabel(1, "9월"));
        // 2026-10-13(화, 스토어 스크린샷 날): 10월 2열 → 9월은 2열부터
        assertThat(ContributionGraphBuilder.build(Map.of(), LocalDate.of(2026, 10, 13), GOAL).monthLabels().get(0))
                .isEqualTo(new ContributionGraph.MonthLabel(2, "9월"));
    }

    @Test
    @DisplayName("REQ-03 · 이번 달이 3주가 되는 날 10월이 0열에 다시 선다")
    void monthLabels_keepCurrentMonth_atThreeWeeks() {
        // 2026-10-18(일): 10-18 · 10-11 · 10-04 → 10월 3열, 9월은 3열부터
        assertThat(ContributionGraphBuilder.build(Map.of(), LocalDate.of(2026, 10, 18), GOAL).monthLabels().subList(0, 2))
                .containsExactly(new ContributionGraph.MonthLabel(0, "10월"), new ContributionGraph.MonthLabel(3, "9월"));
    }

    // ───────────────────────────── 연속 일수(streak) ─────────────────────────────
    // "잔디 심은 날" = 읽은 날(seconds>0). 오늘부터 거꾸로 끊기지 않고 이어진 일수.
    // 오늘 유예: 오늘 아직 안 읽었으면 어제부터 센다(자정마다 0으로 리셋 방지). 끊기면 0.

    @Test
    @DisplayName("연속: 데이터 없으면 0")
    void streak_empty_isZero() {
        assertThat(ContributionGraphBuilder.currentStreak(Map.of(), TODAY)).isZero();
    }

    @Test
    @DisplayName("연속: 오늘만 읽으면 1")
    void streak_todayOnly_isOne() {
        assertThat(ContributionGraphBuilder.currentStreak(Map.of(TODAY, 60L), TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("연속: 오늘+어제 읽으면 2")
    void streak_todayAndYesterday_isTwo() {
        Map<LocalDate, Long> data = Map.of(TODAY, 60L, TODAY.minusDays(1), 60L);
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("연속: 오늘 아직 안 읽었어도 어제까지 이어졌으면 유예로 유지(어제+그제=2)")
    void streak_todayNotYetRead_graceFromYesterday() {
        Map<LocalDate, Long> data = Map.of(TODAY.minusDays(1), 60L, TODAY.minusDays(2), 60L);
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("연속: 오늘·어제 모두 비고 그제만 읽었으면 끊겨서 0")
    void streak_brokenBeforeYesterday_isZero() {
        Map<LocalDate, Long> data = Map.of(TODAY.minusDays(2), 60L);
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isZero();
    }

    @Test
    @DisplayName("연속: 중간 구멍은 끊는다 — 오늘 읽고 어제 비고 그제 읽으면 1")
    void streak_gapBreaks_countsOnlyToday() {
        Map<LocalDate, Long> data = Map.of(TODAY, 60L, TODAY.minusDays(2), 60L);
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("연속: 0초인 날은 안 읽은 것 — 끊는다")
    void streak_zeroSecondsDoesNotCount() {
        Map<LocalDate, Long> data = Map.of(TODAY, 0L, TODAY.minusDays(1), 60L);
        // 오늘=0초(안 읽음)라 유예로 어제부터 → 어제 1일
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("연속: 14일 연속이면 14")
    void streak_fourteenInARow() {
        java.util.Map<LocalDate, Long> data = new java.util.HashMap<>();
        for (int i = 0; i < 14; i++) {
            data.put(TODAY.minusDays(i), 60L);
        }
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(14);
    }

    @Test
    @DisplayName("연속: build()가 만든 그래프의 currentStreak가 streak를 반영한다")
    void build_populatesStreak() {
        java.util.Map<LocalDate, Long> data = new java.util.HashMap<>();
        for (int i = 0; i < 5; i++) {
            data.put(TODAY.minusDays(i), 60L);
        }
        ContributionGraph graph = ContributionGraphBuilder.build(data, TODAY, GOAL);

        assertThat(graph.currentStreak()).isEqualTo(5);
    }

    // ── 목표 연속(REQ-88) — 「채운 날」 = 잔디 4단계(초 > 0 ∧ 초 ≥ 그날 목표)가 이어진 수 ──────────

    private static final long MIN = 60L;

    private static int goalStreak(Map<LocalDate, Long> data, GoalSchedule schedule) {
        return ContributionGraphBuilder.currentGoalStreak(data, TODAY, schedule::goalFor);
    }

    @Test
    @DisplayName("REQ-88 · 목표를 채운 날만 이어진다")
    void goalStreak_onlyMetDaysContinue() {
        // 30분 목표에 어제 29분 — 읽었지만 못 채운 날이 연속을 끊는다(1초 연속이면 3).
        Map<LocalDate, Long> data = Map.of(TODAY, 40 * MIN, TODAY.minusDays(1), 29 * MIN, TODAY.minusDays(2), 40 * MIN);
        GoalSchedule thirty = GoalSchedule.of(Map.of(), 30 * MIN);

        assertThat(goalStreak(data, thirty)).isEqualTo(1);
        assertThat(ContributionGraphBuilder.currentStreak(data, TODAY)).isEqualTo(3); // 대조: 1초 연속은 그대로
    }

    @Test
    @DisplayName("REQ-88 · 오늘 아직이면 어제부터 센다")
    void goalStreak_todayNotYetMet_countsFromYesterday() {
        // 오늘 10분(읽었지만 30분 미달) — 오늘을 끊긴 날로 보지 않고 어제부터 센다.
        Map<LocalDate, Long> data = Map.of(TODAY, 10 * MIN, TODAY.minusDays(1), 40 * MIN, TODAY.minusDays(2), 40 * MIN);
        GoalSchedule thirty = GoalSchedule.of(Map.of(), 30 * MIN);

        assertThat(goalStreak(data, thirty)).isEqualTo(2);
        assertThat(goalStreak(Map.of(TODAY.minusDays(2), 40 * MIN), thirty)).isZero(); // 어제도 비면 끊긴다
    }

    @Test
    @DisplayName("REQ-88 · 목표 0이면 읽은 날이 채운 날이다")
    void goalStreak_zeroGoal_readDaysCount() {
        Map<LocalDate, Long> data = Map.of(TODAY.minusDays(1), 1L, TODAY.minusDays(2), 10L, TODAY.minusDays(4), 10L);
        GoalSchedule none = GoalSchedule.of(Map.of(), 0L);

        assertThat(goalStreak(data, none)).isEqualTo(2); // 0초인 그끄제(-3)에서 끊긴다
    }

    @Test
    @DisplayName("REQ-88 · 목표를 바꿔도 지난날은 그날 목표로 본다")
    void goalStreak_raisedGoal_pastDaysKeepTheirGoal() {
        // 사흘 전 30분 → 오늘 60분으로 올림. 어제·그제 40분은 그날 목표(30분)로 채운 날이다(소급 없음).
        Map<LocalDate, Long> data = Map.of(TODAY.minusDays(1), 40 * MIN, TODAY.minusDays(2), 40 * MIN);
        GoalSchedule raised = GoalSchedule.of(Map.of(TODAY.minusDays(3), 30 * MIN, TODAY, 60 * MIN), 60 * MIN);

        assertThat(goalStreak(data, raised)).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-88 · 오늘 목표를 내리면 오늘이 채운 날이 된다")
    void goalStreak_loweredTodayGoal_todayBecomesMet() {
        // 어제까지 60분, 오늘 30분으로 내림(upsert). 오늘 40분은 새 목표로 채운 날이다.
        Map<LocalDate, Long> data = Map.of(TODAY, 40 * MIN, TODAY.minusDays(1), 70 * MIN);
        GoalSchedule lowered = GoalSchedule.of(Map.of(TODAY.minusDays(5), 60 * MIN, TODAY, 30 * MIN), 30 * MIN);

        assertThat(goalStreak(data, lowered)).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-88 · 첫 목표 변경 전 날은 지금 목표로 본다")
    void goalStreak_beforeFirstChange_usesFallbackGoal() {
        // 이력은 어제(30분)부터. 그제 40분은 폴백(지금 목표 60분)으로 보아 못 채운 날 — 「읽은 날이면 채움」이 아니다.
        Map<LocalDate, Long> data = Map.of(TODAY.minusDays(1), 40 * MIN, TODAY.minusDays(2), 40 * MIN);
        GoalSchedule history = GoalSchedule.of(Map.of(TODAY.minusDays(1), 30 * MIN), 60 * MIN);

        assertThat(goalStreak(data, history)).isEqualTo(1);
    }

    private static ContributionDay findCell(ContributionGraph graph, LocalDate date) {
        return graph.weeks().stream()
                .flatMap(List::stream)
                .filter(c -> date.equals(c.date()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("그리드에서 날짜를 찾지 못함: " + date));
    }
}
