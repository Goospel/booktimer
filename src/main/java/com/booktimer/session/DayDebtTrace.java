package com.booktimer.session;

import java.time.LocalDate;

/**
 * 7일 윈도우 하루치 계산 추적 (관찰성·진단용). 초 단위 — 표시 환산(÷60)은 뷰 계층에서.
 *
 * @param date                 유저 타임존 기준 일자
 * @param isToday              이 날이 trace의 기준일(asOf)인지 — index == WINDOW_DAYS-1과 동일
 * @param goalSeconds          그날 목표(초). 0이면 가입 전 날(뱅킹 대상 아님)
 * @param readSeconds          그날 읽은 양(초)
 * @param rawDeficitSeconds    재분배 전 원시 부채 = max(0, goal-read). goal=0이면 0
 * @param rawSurplusSeconds    재분배 전 원시 초과 = max(0, read-goal). goal=0이면 0
 * @param forgivenSubMinute    과거 날에서 rawDeficit∈(0,60)이라 0으로 처리됨(1분 미만 용서)
 * @param waived               리워드 광고 보상({@link ReadingGoalWaiver})으로 이 날 부채가 소거됨.
 *                             진단 뷰에서 "부채가 있었는데 remaining=0"이 버그로 보이지 않게 정직하게 남긴다
 * @param remainingSeconds     backward-only 재분배 후 잔여 부채
 * @param surplusConsumedSeconds 이 날 초과분 중 과거 날 상환에 쓴 양 = rawSurplus - surplusLeft
 */
public record DayDebtTrace(
        LocalDate date,
        boolean isToday,
        long goalSeconds,
        long readSeconds,
        long rawDeficitSeconds,
        long rawSurplusSeconds,
        boolean forgivenSubMinute,
        boolean waived,
        long remainingSeconds,
        long surplusConsumedSeconds
) {
}
