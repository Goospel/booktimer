package com.booktimer.session;

import java.util.ArrayList;
import java.util.List;

/**
 * 누적 부채 추적 결과 (진단·관찰성 뷰). 날짜 오름차순(창 시작일 index 0 → 기준일 마지막).
 *
 * <p>실제 부채 계산과 단일 출처를 공유한다 — {@link #toWeeklyDebt()}가 기존 {@link WeeklyDebt}를 유도하므로
 * 진단 뷰와 라이브 대시보드가 동일 경로를 거쳐 drift가 원천적으로 불가능하다.
 */
public record WeeklyDebtTrace(List<DayDebtTrace> days) {

    /**
     * 이 trace에서 기존 {@link WeeklyDebt}를 유도한다.
     * {@link WeeklyDebtCalculator#compute}와 동치 — 리팩터 회귀 앵커.
     */
    public WeeklyDebt toWeeklyDebt() {
        DayDebtTrace today = days.get(days.size() - 1);
        List<DayDebt> missed = new ArrayList<>();
        for (int i = days.size() - 2; i >= 0; i--) {
            DayDebtTrace d = days.get(i);
            if (d.remainingSeconds() >= WeeklyDebtCalculator.MIN_MISSED_DEBT_SECONDS) {
                missed.add(new DayDebt(d.date(), d.remainingSeconds()));
            }
        }
        return new WeeklyDebt(today.remainingSeconds(), today.goalSeconds(), today.readSeconds(), List.copyOf(missed));
    }

    /** 총 부채(초) = 오늘 부채 + 빠뜨린 날 합. */
    public long totalDebtSeconds() {
        return toWeeklyDebt().totalDebtSeconds();
    }
}
