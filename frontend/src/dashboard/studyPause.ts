import type { AwayInterval } from './studyAway'

/**
 * 공부 측정 일시정지 원장(설계 2026-10-20-study-timer-keyboard-shortcut).
 * 멈춘 구간을 모았다가 종료 때 자리 비움 구간과 함께 stop 본문 awayIntervals로 보낸다 — 서버는 출처를 가리지 않고
 * 겹침을 합쳐 뺀다. 자리 비움 원장(bt.study.away)과 따로 둔다: 그쪽은 컴퓨터·켠 사람만 추적하고, 일시정지는 모든 기기다.
 */
export const PAUSE_KEY = 'bt.study.pause'
export interface PauseLedger { startedAt: string; intervals: AwayInterval[]; pausedFrom: string | null }

/** 측정 세션이 다르면 빈 원장(studyAway.ledgerFor와 같은 규칙). */
export function pauseFor(stored: PauseLedger | null, startedAt: string): PauseLedger {
    return stored && stored.startedAt === startedAt ? stored : { startedAt, intervals: [], pausedFrom: null }
}

/** [from, now] — from < now(밀리초)일 때만. 0ms·역행 구간은 서버가 400으로 막는다. */
function closeAt(from: string, nowMs: number): AwayInterval | null {
    return Date.parse(from) < nowMs ? { from, to: new Date(nowMs).toISOString() } : null
}

/** 멈춤 ↔ 다시. 다시 누를 때 [pausedFrom, now]를 닫는다. */
export function togglePause(l: PauseLedger, nowMs: number): PauseLedger {
    if (!l.pausedFrom) return { ...l, pausedFrom: new Date(nowMs).toISOString() }
    const closed = closeAt(l.pausedFrom, nowMs)
    return { ...l, intervals: closed ? [...l.intervals, closed] : l.intervals, pausedFrom: null }
}

/** stop 본문용 — 멈춘 채면 지금까지를 닫아 붙인 목록. 원장은 바꾸지 않는다(실패하면 계속 멈춰 있어야 한다). */
export function pauseIntervalsAt(l: PauseLedger | null, nowMs: number): AwayInterval[] {
    if (!l) return []
    const open = l.pausedFrom ? closeAt(l.pausedFrom, nowMs) : null
    return open ? [...l.intervals, open] : l.intervals
}

// 저장소가 막혀도(시크릿·차단·용량) 그 탭 메모리로 간다 — studyAway.loadLedger/saveLedger와 같은 꼴.
export function loadPause(): PauseLedger | null {
    try {
        const v = JSON.parse(localStorage.getItem(PAUSE_KEY) ?? 'null')
        return v && typeof v.startedAt === 'string' && Array.isArray(v.intervals) ? v as PauseLedger : null
    } catch {
        return null
    }
}

export function savePause(l: PauseLedger | null): void {
    try {
        if (l) localStorage.setItem(PAUSE_KEY, JSON.stringify(l))
        else localStorage.removeItem(PAUSE_KEY)
    } catch {
        /* 저장 실패 — 이 탭 메모리의 원장으로 계속 간다 */
    }
}

/**
 * 물리 키 자리(code)만 본다 — 한글 입력 중 key는 'ㅔ', 맥 Option은 'π', 조합 중엔 'Process'다.
 * 조합 중(isComposing·229)을 거르지 않는다: 한국어는 마지막 음절이 거의 늘 조합 중이라 거르면 키가 대부분 안 먹는다.
 * Ctrl을 빼는 것은 윈도 AltGr(= Ctrl+Alt) 때문이다.
 */
export function isPauseKey(e: Pick<KeyboardEvent, 'code' | 'altKey' | 'ctrlKey' | 'metaKey' | 'shiftKey'>): boolean {
    return e.code === 'KeyP' && e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey
}

/** 보이는 힌트 — 아이패드 사파리는 기본 데스크톱 모드라 platform이 'MacIntel'이다. */
export function pauseKeyLabel(platform: string): 'Alt+P' | 'Option+P' {
    return /Mac|iPhone|iPad|iPod/.test(platform) ? 'Option+P' : 'Alt+P'
}
