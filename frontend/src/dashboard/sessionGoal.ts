/**
 * 공부 책별 「회당 시간」 — 이번 측정이 그 책의 회당 시간에 닿았는가(설계 §4.5, 미니앱 `sessionGoal.ts`와 같은 부등식).
 *
 * 기준은 측정 중인 책의 **현재 값**이다(세션 스냅샷 없음) — 측정 중에 값을 바꾸면 그 순간부터 새 값으로 다시 잰다.
 * 서버 푸시(`StudyGoalPushService`)도 `startedAt + goal ≤ now`로 같은 순간을 가리킨다.
 */
export type SessionGoalView =
    | { kind: 'stopwatch' }
    | { kind: 'countdown'; goal: number; remaining: number }
    | { kind: 'reached'; goal: number; overflow: number }

/**
 * @param goal    책의 회당 시간(초). null·0 이하 = 안 정함 → 제한 없는 스톱워치.
 * @param elapsed 이번 측정 경과(초). 음수(기기 시계가 느림)는 0으로 눌린다.
 */
export function sessionGoalView(goal: number | null | undefined, elapsed: number): SessionGoalView {
    if (goal == null || goal <= 0) return { kind: 'stopwatch' }
    const e = Math.max(0, elapsed)
    return e >= goal
        ? { kind: 'reached', goal, overflow: e - goal }
        : { kind: 'countdown', goal, remaining: goal - e }
}

/** 분 입력 → 초. 빈칸·NaN·0·음수는 null(= 회당 시간 없이) — 서버가 0을 400으로 막으니 문 앞에서 해제로 모은다. */
export function minutesToSessionGoal(minutes: unknown): number | null {
    const m = Math.floor(Number(minutes))
    return Number.isFinite(m) && m > 0 ? m * 60 : null
}
