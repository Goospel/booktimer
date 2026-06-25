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

/**
 * name null/빈 제외. 제네릭으로 입력 객체의 모든 필드(emoji·spriteId·code 등)를 보존해
 * 무대 SVG 캐릭터 렌더(spriteId)에 그대로 흐르게 한다. name은 non-null로 좁혀 반환.
 */
export function visibleAuthors<T extends { name: string | null }>(owned: T[]): Array<T & { name: string }> {
    return owned.filter(a => a.name != null && a.name.trim().length > 0) as Array<T & { name: string }>
}

/** streak > 0일 때만 칩 표시. */
export function showStreakChip(streak: number): boolean {
    return streak > 0
}

/** 디자인 §0 확정: 헤더는 loginId 표시. nickname은 향후 확장용 폴백 파라미터. */
export function displayName(loginId: string, _nickname: string | null): string {
    return loginId
}

/**
 * 타이머 우패널 3상태. 측정 중이면 항상 measuring(종료 버튼 보존) — 달성은 좌측 pill·진행바로
 * 표시한다(사용자 결정 2026-06-24: 측정 중 달성해도 패널 전환 안 함, 종료 흐름 보존).
 */
export function panelState(hasActiveSession: boolean, isAchieved: boolean): 'idle' | 'measuring' | 'achieved' {
    if (hasActiveSession) return 'measuring'
    return isAchieved ? 'achieved' : 'idle'
}

/** 아바타 이니셜 — 트림 후 첫 글자. 빈 값이면 '?'(헤더 loginId·방문 작가 name 공용). */
export function avatarInitial(s: string): string {
    const t = (s ?? '').trim()
    return t ? t.charAt(0) : '?'
}

/** 진행바 분모 라벨. 0 이하 → '목표 없음'. 60분 이상은 '시간(+분)', 정각이면 분 생략. */
export function goalLabel(goalSeconds: number): string {
    if (goalSeconds <= 0) return '목표 없음'
    const m = Math.round(goalSeconds / 60)
    if (m >= 60) {
        const h = Math.floor(m / 60)
        const rm = m % 60
        return rm > 0 ? `${h}시간 ${rm}분` : `${h}시간`
    }
    return `${m}분`
}
