export type TimerMode = 'reading' | 'study'

/**
 * 미니앱과 같은 키 — 오리진이 달라 값이 공유되진 않지만 이름은 하나로.
 * 리터럴은 `fragments/side-rails` 인라인 부트·`static/js/rail.js`에도 따로 적힌다(둘 다 이 모듈을
 * import 못 한다) — 동기는 timer-mode.test.ts가 지킨다.
 */
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

/**
 * 홈 모드 → 양옆 바 흐림 상태(SSR `fragments/side-rails`의 #side-rails[data-mode]).
 * 바가 없는 문서(테스트·바 없는 페이지)면 no-op.
 */
export function syncRailMode(doc: Pick<Document, 'getElementById'>, mode: TimerMode): void {
    doc.getElementById('side-rails')?.setAttribute('data-mode', mode)
}

/**
 * 독서등 — 공부 측정 중 홈이 밤이 되고 합쳐진 카드만 낮 종이로 남는다(설계 2026-09-15-study-focus-lamp).
 * 리터럴은 `fragments/side-rails` 인라인 부트에도 따로 적힌다 — 동기는 timer-mode.test.ts가 지킨다.
 */
export const LAMP_CLASS = 'study-lamp'
export const LAMP_KEY = 'booktimer.studyLamp'

/** 합침 = 독서등 = 공부 모드에서 공부 측정 중(미니앱 lampOn과 같은 꼴). */
export function studyFocusOn(mode: TimerMode, studyActive: boolean): boolean {
    return mode === 'study' && studyActive
}

/** body 클래스 + 첫 페인트 힌트(새로고침 때 부트가 읽는다). 저장 실패는 삼킨다(writeMode와 같은 규칙). */
export function syncStudyLamp(
    doc: Pick<Document, 'body'>,
    on: boolean,
    storage: Pick<Storage, 'setItem' | 'removeItem'> | null = safeStorage(),
): void {
    doc.body.classList.toggle(LAMP_CLASS, on)
    try {
        if (on) storage?.setItem(LAMP_KEY, '1')
        else storage?.removeItem(LAMP_KEY)
    } catch {
        /* 힌트를 못 남겨도 화면은 돈다 — 새로고침 때 한 번 깜빡일 뿐이다 */
    }
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
