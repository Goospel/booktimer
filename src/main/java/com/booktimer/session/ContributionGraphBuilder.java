package com.booktimer.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

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
     * @param secondsByDate 날짜→총 독서 초 (없는 날은 0으로 간주)
     * @param today         유저 타임존 기준 오늘
     * @param goalSeconds   하루 목표 초(유저의 평면 증가값) — 색 농도를 이 목표 대비 달성 비율로 정한다.
     *                      0이면 "목표 없음"으로 보고 읽은 날은 모두 최고 농도(lv4)가 된다.
     * @return 53주 × 7요일 그리드 모델
     */
    public static ContributionGraph build(Map<LocalDate, Long> secondsByDate, LocalDate today, long goalSeconds) {
        return build(secondsByDate, today, date -> goalSeconds);
    }

    /**
     * 날짜별 목표(per-day)로 잔디를 만든다 — 각 칸의 색 농도를 <b>그날 유효했던 목표</b> 대비로 정한다.
     *
     * <p>하루 목표를 현재 평면값 하나로 잡으면 사용자가 목표를 올렸을 때 옛 목표를 채운 과거 날의 색이
     * 새 목표 기준으로 내려가 소급해 다시 칠해진다(N-059의 잔디 판). {@code goalForDate}가
     * {@link com.booktimer.timer.GoalSchedule}로 날짜별 목표를 돌려주면 과거 색이 동결된다.
     *
     * @param goalForDate 날짜→그날 목표(초) 리졸버. 0을 돌려주면 그 칸은 "목표 없음"(읽으면 lv4).
     */
    public static ContributionGraph build(Map<LocalDate, Long> secondsByDate, LocalDate today,
                                          ToLongFunction<LocalDate> goalForDate) {
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
                week.add(new ContributionDay(date, seconds, levelFor(seconds, goalForDate.applyAsLong(date))));
                total += seconds;
                if (seconds > 0) {
                    activeDays++;
                }
            }
            weeks.add(week);
        }

        // 최근이 왼쪽으로 오도록 열 순서를 뒤집는다 — 좁은 화면에서 스크롤 없이 최근 날짜가 바로 보이게.
        // (월 라벨은 뒤집힌 순서 위에서 다시 계산해 열과 정렬을 맞춘다.)
        Collections.reverse(weeks);

        return new ContributionGraph(weeks, monthLabels(weeks), total, activeDays);
    }

    /**
     * 독서 초를 하루 목표 대비 달성 비율로 색 농도 0~4로 매핑한다.
     * <p>경계는 "이하 포함"(위 레벨로): 25%·50%는 각각 1·2, 정확히 100%는 4.
     * 부동소수 없이 <b>교차곱</b>으로 정수 비교한다. 목표가 0이면 읽은 날(초>0)은 모두 4로 떨어진다
     * (div-by-zero 없음 — "목표 없음 = 읽기만 하면 만점").
     */
    static int levelFor(long seconds, long goalSeconds) {
        if (seconds <= 0) {
            return 0;                       // 안 읽음
        }
        if (seconds * 4 <= goalSeconds) {
            return 1;                       // ~25%
        }
        if (seconds * 2 <= goalSeconds) {
            return 2;                       // ~50%
        }
        if (seconds < goalSeconds) {
            return 3;                       // ~100% 미만
        }
        return 4;                           // 100% 이상(목표 달성/초과). goalSeconds=0도 여기로.
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
