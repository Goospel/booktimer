package com.booktimer.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 잔디 그리드 빌더 경계값 테스트 — 순수 날짜 계산이라 스프링 없이 빠르게 검증한다.
 *
 * <p>GitHub 잔디와 동일 배치: 53주 열 × 7요일 행(일요일 top → 토요일 bottom), 오늘 주가 맨 오른쪽,
 * 미래 칸은 placeholder. 색 레벨은 시간(분) 임계로 0~4.
 */
class ContributionGraphBuilderTest {

    // 화요일
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);

    /** 일요일=0 ... 토요일=6 (그리드 행 인덱스). */
    private static int sundayOffset(LocalDate d) {
        return d.getDayOfWeek().getValue() % 7;
    }

    @Test
    @DisplayName("그리드는 53주 × 각 주 7칸이다")
    void grid_is_53_weeks_of_7() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY);

        assertThat(graph.weeks()).hasSize(53);
        assertThat(graph.weeks()).allSatisfy(week -> assertThat(week).hasSize(7));
    }

    @Test
    @DisplayName("각 주의 첫 칸(행 0)은 일요일이다")
    void firstRow_isSunday() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY);

        for (List<ContributionDay> week : graph.weeks()) {
            ContributionDay first = week.get(0);
            if (!first.isPlaceholder()) {
                assertThat(first.date().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            }
        }
    }

    @Test
    @DisplayName("오늘은 맨 오른쪽 주의 요일 위치에 있고 placeholder가 아니다")
    void today_isInLastWeek() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY);

        ContributionDay todayCell = graph.weeks().get(52).get(sundayOffset(TODAY));
        assertThat(todayCell.isPlaceholder()).isFalse();
        assertThat(todayCell.date()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("오늘 이후(미래) 칸은 placeholder다")
    void future_isPlaceholder() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY);

        List<ContributionDay> lastWeek = graph.weeks().get(52);
        int todayOffset = sundayOffset(TODAY); // 화요일=2 → 그 뒤(수~토)는 미래
        for (int row = todayOffset + 1; row < 7; row++) {
            assertThat(lastWeek.get(row).isPlaceholder())
                    .as("행 %d은 미래라 placeholder여야 한다", row)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("색 레벨: 0분=0, ~15분=1, ~30분=2, ~60분=3, 초과=4 (경계 포함)")
    void levels_byMinutes() {
        LocalDate d = LocalDate.of(2026, 3, 15); // 범위 내 일요일 근처 임의 날짜
        assertThat(levelOf(0L, d)).isEqualTo(0);
        assertThat(levelOf(10 * 60L, d)).isEqualTo(1);
        assertThat(levelOf(15 * 60L, d)).as("15분 경계는 1").isEqualTo(1);
        assertThat(levelOf(16 * 60L, d)).isEqualTo(2);
        assertThat(levelOf(30 * 60L, d)).as("30분 경계는 2").isEqualTo(2);
        assertThat(levelOf(60 * 60L, d)).as("60분 경계는 3").isEqualTo(3);
        assertThat(levelOf(60 * 60L + 1, d)).isEqualTo(4);
    }

    private int levelOf(long seconds, LocalDate date) {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(date, seconds), TODAY);
        return findCell(graph, date).level();
    }

    @Test
    @DisplayName("해당 날짜 칸에 총초가 반영되고, 합계/활동일이 집계된다")
    void aggregates_totalsAndActiveDays() {
        Map<LocalDate, Long> data = Map.of(
                LocalDate.of(2026, 3, 15), 1200L,
                LocalDate.of(2026, 4, 10), 600L);

        ContributionGraph graph = ContributionGraphBuilder.build(data, TODAY);

        assertThat(findCell(graph, LocalDate.of(2026, 3, 15)).totalSeconds()).isEqualTo(1200L);
        assertThat(graph.totalSeconds()).isEqualTo(1800L);
        assertThat(graph.activeDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("범위 밖(1년보다 과거) 날짜는 집계에 포함되지 않는다")
    void outOfRange_isIgnored() {
        Map<LocalDate, Long> data = Map.of(LocalDate.of(2000, 1, 1), 9999L);

        ContributionGraph graph = ContributionGraphBuilder.build(data, TODAY);

        assertThat(graph.totalSeconds()).isZero();
        assertThat(graph.activeDays()).isZero();
    }

    @Test
    @DisplayName("월 라벨이 있고 열 인덱스 오름차순이며 '6월' 형식이다")
    void monthLabels_present_andOrdered() {
        ContributionGraph graph = ContributionGraphBuilder.build(Map.of(), TODAY);

        assertThat(graph.monthLabels()).isNotEmpty();
        assertThat(graph.monthLabels())
                .extracting(ContributionGraph.MonthLabel::weekIndex)
                .isSorted();
        assertThat(graph.monthLabels())
                .allSatisfy(m -> assertThat(m.label()).matches("\\d{1,2}월"));
    }

    private static ContributionDay findCell(ContributionGraph graph, LocalDate date) {
        return graph.weeks().stream()
                .flatMap(List::stream)
                .filter(c -> date.equals(c.date()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("그리드에서 날짜를 찾지 못함: " + date));
    }
}
