/** 복귀 재조회 스로틀 — 미니앱 App.tsx의 REFRESH_THROTTLE_MS와 같은 값·같은 규칙. */
export const REFRESH_THROTTLE_MS = 60_000

/**
 * 마지막 조회(lastAt)로부터 스로틀 창이 지났으면 true.
 * force는 409("내 화면이 낡았다") 경로 전용 — 창 안이어도 즉시 다시 받는다.
 */
export function shouldRefresh(lastAt: number, now: number, force = false): boolean {
    return force || now - lastAt >= REFRESH_THROTTLE_MS
}
