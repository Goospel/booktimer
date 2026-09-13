export type TimerMode = 'reading' | 'study'

/** 미니앱과 같은 키 — 오리진이 달라 값이 공유되진 않지만 이름은 하나로. */
export const MODE_KEY = 'booktimer.timerMode'

/** localStorage 접근 자체가 throw하는 환경(사파리 프라이빗·차단 설정)이 있어 감싼다. */
function safeStorage(): Storage | null {
    try {
        return typeof localStorage === 'undefined' ? null : localStorage
    } catch {
        return null
    }
}

/** 저장된 모드 — 미지값·저장소 없음·접근 예외는 모두 'reading'(독서가 기본 원장). */
export function readMode(storage: Pick<Storage, 'getItem'> | null = safeStorage()): TimerMode {
    try {
        return storage?.getItem(MODE_KEY) === 'study' ? 'study' : 'reading'
    } catch {
        return 'reading'
    }
}

/** 모드 저장 — 실패는 삼킨다(세션 안에서는 ref가 모드를 들고 있어 토글은 계속 동작한다). */
export function writeMode(mode: TimerMode, storage: Pick<Storage, 'setItem'> | null = safeStorage()): void {
    try {
        storage?.setItem(MODE_KEY, mode)
    } catch {
        /* 저장 못 해도 화면은 돈다 */
    }
}

/** 서버 진실이 저장값을 이긴다 — 독서 진행 > 공부 진행 > 저장값 (미니앱 App.tsx effectiveMode 1:1). */
export function effectiveMode(readingActive: boolean, studyActive: boolean, stored: TimerMode): TimerMode {
    if (readingActive) return 'reading'
    if (studyActive) return 'study'
    return stored
}

/** 복귀 재조회 스로틀 — 미니앱 App.tsx의 REFRESH_THROTTLE_MS와 같은 값·같은 규칙. */
export const REFRESH_THROTTLE_MS = 60_000

/**
 * 마지막 조회(lastAt)로부터 스로틀 창이 지났으면 true.
 * force는 409("내 화면이 낡았다") 경로 전용 — 창 안이어도 즉시 다시 받는다.
 */
export function shouldRefresh(lastAt: number, now: number, force = false): boolean {
    return force || now - lastAt >= REFRESH_THROTTLE_MS
}
