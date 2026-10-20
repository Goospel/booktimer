/**
 * 공부 측정 중 탭을 떠난 구간 원장(설계 2026-09-28-web-study-away-time §3 PR-2).
 * 떠난 순간~돌아온 순간이 유예(30초) 이상이면 멈춘 구간으로 모았다가 stop 본문 awayIntervals로 보낸다 —
 * 서버가 자정 분할 조각마다 겹친 만큼 durationSeconds에서 뺀다. 화면은 elapsed에서 awaySeconds를 뺀 값을 그린다.
 */
export const AWAY_GRACE_MS = 30_000
export const AWAY_KEY = 'bt.study.away'

export interface AwayInterval { from: string; to: string }   // ISO
export interface AwayLedger { startedAt: string; intervals: AwayInterval[]; pendingFrom: string | null }

/**
 * 측정 세션 식별자(activeStartedAt)의 정규 표기(밀리초 ISO). 서버는 같은 순간을 출처마다 다른 정밀도로 준다 —
 * 시작 응답은 JVM Instant(100ns 9자리), 재조회는 DB 값(µs 6자리). 그대로 비교하면 복귀 재조회가 「다른 측정」으로
 * 보여 방금 닫은 구간을 지운다(실 브라우저 실측 2026-09-29). 읽을 수 없는 값은 그대로 둔다(던지지 않는다).
 */
export function canonicalIso(s: string): string {
    const t = Date.parse(s)
    return isNaN(t) ? s : new Date(t).toISOString()
}

/** 측정 세션(startedAt)이 다르면 빈 원장 — 다른 기기에서 끝나고 새로 시작된 세션에 옛 구간이 붙지 않게. */
export function ledgerFor(stored: AwayLedger | null, startedAt: string): AwayLedger {
    return stored && stored.startedAt === startedAt ? stored : { startedAt, intervals: [], pendingFrom: null }
}

/** 이미 떠나 있으면 처음 떠난 시각을 지킨다. */
export function markHidden(l: AwayLedger, nowMs: number): AwayLedger {
    return l.pendingFrom ? l : { ...l, pendingFrom: new Date(nowMs).toISOString() }
}

/** 유예 미만이면 버리고 closed = null. */
export function markVisible(l: AwayLedger, nowMs: number): { ledger: AwayLedger; closed: AwayInterval | null } {
    if (!l.pendingFrom) return { ledger: l, closed: null }
    const from = Date.parse(l.pendingFrom)
    if (!(nowMs - from >= AWAY_GRACE_MS)) return { ledger: clearPending(l), closed: null }
    const closed = { from: l.pendingFrom, to: new Date(nowMs).toISOString() }
    return { ledger: { ...l, intervals: [...l.intervals, closed], pendingFrom: null }, closed }
}

/** pagehide — 같은 탭에서 필기 화면으로 가거나 탭을 닫는 것은 이탈로 치지 않는다. */
export function clearPending(l: AwayLedger): AwayLedger {
    return { ...l, pendingFrom: null }
}

export function undo(l: AwayLedger, iv: AwayInterval): AwayLedger {
    return { ...l, intervals: l.intervals.filter(x => x.from !== iv.from || x.to !== iv.to) }
}

export function awaySeconds(l: AwayLedger | null): number {
    if (!l) return 0
    return l.intervals.reduce((s, iv) => s + Math.floor((Date.parse(iv.to) - Date.parse(iv.from)) / 1000), 0)
}

// 저장소가 막혀도(시크릿·차단·용량) 기능만 조용히 빠진다 — 타이머는 그대로 돈다.
export function loadLedger(): AwayLedger | null {
    try {
        const v = JSON.parse(localStorage.getItem(AWAY_KEY) ?? 'null')
        return v && typeof v.startedAt === 'string' && Array.isArray(v.intervals) ? v as AwayLedger : null
    } catch {
        return null
    }
}

export function saveLedger(l: AwayLedger | null): void {
    try {
        if (l) localStorage.setItem(AWAY_KEY, JSON.stringify(l))
        else localStorage.removeItem(AWAY_KEY)
    } catch {
        /* 저장 실패 — 이 탭 메모리의 원장으로 계속 간다 */
    }
}

/** 기기별 선택(설계 §9) — 'on'일 때만 추적한다. 원장(AWAY_KEY)과 따로 둔다: 원장은 측정마다 갈리고 선택은 기기에 남는다. */
export const AWAY_PREF_KEY = 'bt.study.awayPause'

/** 'on'만 켬 — 기록 없음·모르는 값·저장소 막힘은 모두 꺼짐(기본값, §9.4-3). */
export function loadAwayOn(): boolean {
    try {
        return localStorage.getItem(AWAY_PREF_KEY) === 'on'
    } catch {
        return false
    }
}

/** 끌 때도 'off'를 적는다 — 기본값이 바뀌어도 직접 끈 사람은 끈 채 남게. 저장 실패는 삼킨다(그 탭은 메모리 값으로 간다). */
export function saveAwayOn(on: boolean): void {
    try {
        localStorage.setItem(AWAY_PREF_KEY, on ? 'on' : 'off')
    } catch {
        /* 저장 실패 — 이 탭에서만 유지된다 */
    }
}
