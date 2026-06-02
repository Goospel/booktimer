package com.booktimer.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 일자별 독서 시간을 GitHub 잔디 형태의 {@link ContributionGraph}로 펼치는 순수 빌더.
 *
 * <p>스프링 의존 없이 날짜 계산만 한다 — 빠른 단위테스트 대상. "오늘"은 호출자가 유저 타임존
 * 기준으로 계산해 넘긴다(Clock 주입은 서비스 레이어 책임).
 */
public final class ContributionGraphBuilder {

    private ContributionGraphBuilder() {
    }

    /** GitHub과 동일하게 53주를 보인다. */
    static final int WEEKS = 53;

    /**
     * 색 농도 임계(초) — 이 값 <b>이하</b>면 해당 레벨. 0초는 level 0.
     * level 1: ~15분, 2: ~30분, 3: ~60분, 4: 초과. (색은 추후 조정 — 상수로 분리)
     */
    static final long[] LEVEL_THRESHOLDS_SECONDS = {15 * 60, 30 * 60, 60 * 60};

    /**
     * @param secondsByDate 날짜→총 독서 초 (없는 날은 0으로 간주)
     * @param today         유저 타임존 기준 오늘
     * @return 53주 × 7요일 그리드 모델
     */
    public static ContributionGraph build(Map<LocalDate, Long> secondsByDate, LocalDate today) {
        // 일요일=0 ... 토요일=6. 오늘이 속한 주의 일요일이 맨 오른쪽 열의 시작.
        int todayOffset = today.getDayOfWeek().getValue() % 7;
        LocalDate lastSunday = today.minusDays(todayOffset);
        LocalDate firstSunday = lastSunday.minusWeeks(WEEKS - 1L);

        List<List<ContributionDay>> weeks = new ArrayList<>(WEEKS);
        long total = 0L;
        int activeDays = 0;

        for (int w = 0; w < WEEKS; w++) {
            List<ContributionDay> week = new ArrayList<>(7);
            for (int row = 0; row < 7; row++) {
                LocalDate date = firstSunday.plusWeeks(w).plusDays(row);
                if (date.isAfter(today)) {
                    week.add(ContributionDay.placeholder()); // 미래 칸은 빈 칸
                    continue;
                }
                long seconds = Math.max(0L, secondsByDate.getOrDefault(date, 0L));
                week.add(new ContributionDay(date, seconds, levelFor(seconds)));
                total += seconds;
                if (seconds > 0) {
                    activeDays++;
                }
            }
            weeks.add(week);
        }

        return new ContributionGraph(weeks, monthLabels(weeks), total, activeDays);
    }

    /** 독서 초를 색 농도 0~4로. 임계는 <b>이하</b> 포함. */
    static int levelFor(long seconds) {
        if (seconds <= 0) {
            return 0;
        }
        if (seconds <= LEVEL_THRESHOLDS_SECONDS[0]) {
            return 1;
        }
        if (seconds <= LEVEL_THRESHOLDS_SECONDS[1]) {
            return 2;
        }
        if (seconds <= LEVEL_THRESHOLDS_SECONDS[2]) {
            return 3;
        }
        return 4;
    }

    /** 월이 바뀌는 첫 열 위에 "M월" 라벨을 둔다(첫 열 포함). 행 0(일요일)은 항상 실제 날짜다. */
    private static List<ContributionGraph.MonthLabel> monthLabels(List<List<ContributionDay>> weeks) {
        List<ContributionGraph.MonthLabel> labels = new ArrayList<>();
        int prevMonth = -1;
        for (int w = 0; w < weeks.size(); w++) {
            LocalDate ref = weeks.get(w).get(0).date();
            int month = ref.getMonthValue();
            if (month != prevMonth) {
                labels.add(new ContributionGraph.MonthLabel(w, month + "월"));
                prevMonth = month;
            }
        }
        return labels;
    }
}
