/**
 * 공부 히어로 게이지 — 미니앱 `Home.tsx` studyProgress 포팅.
 *
 * 독서 `computeProgress`와 분리한 이유: 공부엔 이월·부채 원장이 없어(`StudyApiController`가
 * `ReadingGoalService.record`를 부르지 않는다) 볼 것이 「오늘 잰 시간 / 목표」 둘뿐이다.
 * 독서 함수에 분기를 더하면 그 기본값이 최다 사용 화면에 닿아 계측기가 통과 쪽으로 고장 난다.
 *
 * @param goalSeconds 하루 목표 초. 0 이하 = 「목표 없음」(정당한 상태) → 게이지를 그리지 않고
 *                    achieved도 false다 — 독서 `computeProgress(goal<=0)`가 100%·달성으로 치는 것과
 *                    반대다(독서의 0은 원장을 깨는 값이라 「없음」이 아니다).
 * @param doneSeconds 오늘 공부한 초(완료 세션 합 + 측정 중 경과).
 */
export function studyProgress(goalSeconds: number, doneSeconds: number)
    : { remaining: number; pct: number; pctStr: string; achieved: boolean; overflow: number } {
    const goal = Math.max(0, goalSeconds)
    const done = Math.max(0, doneSeconds)
    if (goal <= 0) return { remaining: 0, pct: 0, pctStr: '0%', achieved: false, overflow: 0 }
    // 내림이다 — 반올림하면 99.97%가 100%가 되어 게이지가 가득 찬 채 「달성 아님」이 된다.
    const pct = Math.min(100, Math.floor((done / goal) * 100))
    return {
        remaining: Math.max(0, goal - done),
        pct,
        pctStr: `${pct}%`,
        achieved: done >= goal,
        overflow: Math.max(0, done - goal),
    }
}

/** 분 입력 → 초. NaN·빈칸·음수·소수를 0 이상 정수 분으로 눌러 서버 400(음수)을 문 앞에서 막는다. */
export function minutesToGoalSeconds(minutes: unknown): number {
    const m = Math.floor(Number(minutes))
    return (Number.isFinite(m) && m > 0 ? m : 0) * 60
}
