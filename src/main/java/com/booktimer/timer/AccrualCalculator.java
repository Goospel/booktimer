package com.booktimer.timer;

/**
 * Lazy 누적 순수 계산 로직 (DB 무관).
 *
 * <p>"매일 목표 시간이 증가값만큼 늘고, 안 채운 잔여가 다음 날로 이월된다"는
 * 핵심 규칙(N-001)의 계산부. 사용자 접속 시 경과 일수만큼 소급 누적한다.
 *
 * <p>규칙: {@code remaining += daysElapsed × increment} 후 {@code min(remaining, cap)}.
 */
public final class AccrualCalculator {

    private AccrualCalculator() {
    }

    /**
     * 경과 일수만큼 잔여(초)를 누적하고 cap으로 클램프한다.
     *
     * @param remainingSeconds 현재 잔여(초), 0 이상
     * @param incrementSeconds 하루 증가값(초), 0 이상
     * @param capSeconds       누적 상한(초), 0 이상
     * @param daysElapsed      경과 일수. 0 이하면 잔여를 그대로 둔다(시계 역행 방어).
     * @return 새 잔여(초), 항상 cap 이하
     * @throws IllegalArgumentException increment/cap/remaining 이 음수인 경우
     */
    public static long accrue(long remainingSeconds, long incrementSeconds,
                              long capSeconds, long daysElapsed) {
        if (remainingSeconds < 0) {
            throw new IllegalArgumentException("remainingSeconds must be >= 0: " + remainingSeconds);
        }
        if (incrementSeconds < 0) {
            throw new IllegalArgumentException("incrementSeconds must be >= 0: " + incrementSeconds);
        }
        if (capSeconds < 0) {
            throw new IllegalArgumentException("capSeconds must be >= 0: " + capSeconds);
        }
        if (daysElapsed <= 0) {
            return Math.min(remainingSeconds, capSeconds);
        }
        long accrued = remainingSeconds + daysElapsed * incrementSeconds;
        return Math.min(accrued, capSeconds);
    }
}
