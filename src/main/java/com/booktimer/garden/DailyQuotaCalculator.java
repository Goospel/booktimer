package com.booktimer.garden;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * 일별 목표 달성일 수 순수 계산 (DB·시간 무관).
 *
 * <p>먹이 루프의 EARN 측 — "달성일 수(metDayCount)"를 유도한다.
 * "달성" 정의는 {@link com.booktimer.session.WeeklyDebtCalculator}의 "그날 부채 0" 기준과 동일:
 * {@code readSeconds >= goalSeconds}인 날 수.
 * baseline 이전(가입 전)과 목표 ≤ 0인 날은 제외한다.
 */
public final class DailyQuotaCalculator {

    private DailyQuotaCalculator() {}

    /**
     * 그날 읽은 초 ≥ 그날 목표인 날 수(baseline 이전 제외, 목표 0 이하 제외).
     *
     * @param secondsByDate 날짜 → 그날 읽은 초(완료 세션 합). 키가 없는 날은 집계에서 제외.
     * @param goalFor       날짜 → 그날 유효한 목표(초). 반환 ≤ 0이면 그날 달성 불가.
     * @param baseline      이 날짜 이전은 계산에서 제외(가입/첫 목표 시점).
     * @return 달성일 수(0 이상)
     */
    public static int metDayCount(Map<LocalDate, Long> secondsByDate,
                                   ToLongFunction<LocalDate> goalFor,
                                   LocalDate baseline) {
        int count = 0;
        for (Map.Entry<LocalDate, Long> entry : secondsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(baseline)) continue;
            long goal = goalFor.applyAsLong(date);
            if (goal <= 0) continue;
            if (entry.getValue() >= goal) count++;
        }
        return count;
    }
}
