import type { ContributionDay } from './types'

/**
 * 진행바·달성 판정·잔디 등 순수 계산 — vitest(node 환경)에서 컴포넌트 mount 없이 테스트 가능.
 *
 * 계획 §3-B/D: 진행바·달성은 서버 스냅샷이 아니라 클라이언트 라이브 remainingNow 기준.
 */

export function computeProgress(
    remainingNow: number,
    floor: number,
    goal: number,
    carryover: boolean
): { todayRead: number; pct: number; pctStr: string; isAchieved: boolean } {
    if (goal <= 0) {
        return { todayRead: 0, pct: 100, pctStr: '100%', isAchieved: true }
    }
    const todayDebtLive = carryover ? remainingNow - floor : remainingNow
    const todayRead = goal - todayDebtLive
    const pct = Math.min(100, Math.max(0, Math.round((todayRead / goal) * 100)))
    const isAchieved = todayDebtLive <= 0
    return { todayRead, pct, pctStr: `${pct}%`, isAchieved }
}

/** M:SS, 3600초 이상은 H:MM:SS 폴백. 음수·NaN → "00:00". */
export function fmtMSS(sec: number): string {
    if (isNaN(sec) || sec < 0) return '00:00'
    const s = Math.floor(sec)
    if (s >= 3600) {
        const hh = String(Math.floor(s / 3600)).padStart(2, '0')
        const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
        const ss = String(s % 60).padStart(2, '0')
        return `${hh}:${mm}:${ss}`
    }
    const mm = String(Math.floor(s / 60)).padStart(2, '0')
    const ss = String(s % 60).padStart(2, '0')
    return `${mm}:${ss}`
}

/**
 * 잔디 셀 색조(세이지 5단계) 또는 'empty'.
 * level 0~4 → s1~s5(목표 미달→달성). date=null → 'empty'(투명).
 * manual 플래그는 CSS inset shadow로 처리 — 여기선 tone만 반환.
 */
export function cellTone(cell: ContributionDay): 'empty' | 's1' | 's2' | 's3' | 's4' | 's5' {
    if (cell.date === null) return 'empty'
    return `s${cell.level + 1}` as 's1' | 's2' | 's3' | 's4' | 's5'
}

/** name null/빈 제외. 컴포넌트에서 .slice(0,3) + +N 처리. */
export function visibleAuthors(
    owned: Array<{ name: string | null; emoji: string }>
): Array<{ name: string; emoji: string }> {
    return owned.filter(a => a.name != null && a.name.trim().length > 0) as Array<{ name: string; emoji: string }>
}

/** streak > 0일 때만 칩 표시. */
export function showStreakChip(streak: number): boolean {
    return streak > 0
}

/** 디자인 §0 확정: 헤더는 loginId 표시. nickname은 향후 확장용 폴백 파라미터. */
export function displayName(loginId: string, _nickname: string | null): string {
    return loginId
}
