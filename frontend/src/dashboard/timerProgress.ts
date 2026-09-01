import type { ContributionDay } from './types'

/**
 * 진행바·달성 판정·잔디 등 순수 계산 — vitest(node 환경)에서 컴포넌트 mount 없이 테스트 가능.
 *
 * 계획 §3-B/D: 진행바·달성은 서버 스냅샷이 아니라 클라이언트 라이브 remainingNow 기준.
 */

/**
 * @param todayRead 오늘 읽은 초(서버 `todayReadSeconds` + 라이브 경과). **부채에서 역산하지 않는다** —
 *                  서버 부채는 `max(0, 목표 − 읽은 양)`이라 0에서 바닥을 쳐, 역산하면 표시값이 목표에서
 *                  천장을 친다. 목표를 넘겨 읽는 동안엔 라이브 `remainingNow`가 음수로 밀려 제대로 올라가지만
 *                  **중지하는 순간 바닥친 스냅샷이 다시 와 정확히 목표값으로 되돌아갔다**(실사용자 제보).
 *                  초과분은 과거 날 상환에 소비돼 응답에 흔적이 없어 역산이 불가능하다.
 */
export function computeProgress(
    remainingNow: number,
    floor: number,
    goal: number,
    carryover: boolean,
    todayRead: number
): { todayRead: number; remainingToGoal: number; pct: number; pctStr: string; isAchieved: boolean } {
    if (goal <= 0) {
        return { todayRead, remainingToGoal: 0, pct: 100, pctStr: '100%', isAchieved: true }
    }
    // 목표까지 남은 초(히어로 보조·진행바 메타 우측)는 그대로 부채 스냅샷에서 만든다 — 카운트다운이라
    // 0에서 멈추는 게 맞다. 카운트업(todayRead)만 출처가 갈린다.
    const todayDebtLive = carryover ? remainingNow - floor : remainingNow
    const remainingToGoal = Math.max(0, todayDebtLive)
    const pct = Math.min(100, Math.max(0, Math.round((todayRead / goal) * 100)))
    const isAchieved = todayRead >= goal
    return { todayRead, remainingToGoal, pct, pctStr: `${pct}%`, isAchieved }
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

/**
 * 화면 정중앙에 온 작가 인덱스. 무대 좌우 중앙 패딩(calc(50% - 반칸)) 덕에
 * scrollLeft = i·step 이면 i번째 작가가 중앙이므로 round(scrollLeft/step)로 역산하고
 * [0, count-1]로 clamp한다. count·step이 0 이하면 0(가드). 이름 라벨이 이 값으로
 * "지금 중앙 작가"를 표시한다.
 */
export function centeredIndex(scrollLeft: number, step: number, count: number): number {
    if (count <= 0 || step <= 0) return 0
    const i = Math.round(scrollLeft / step)
    return Math.min(count - 1, Math.max(0, i))
}

/**
 * 명언 슬롯머신 로테이션의 다음 인덱스 = (current+1) % count. 끝에서 0으로 순환한다.
 * count가 0 이하면 0(빈 목록 가드). 범위 밖 current도 모듈로로 안전하게 감싼다.
 */
export function nextQuoteIndex(current: number, count: number): number {
    if (count <= 0) return 0
    return (current + 1) % count
}

/**
 * 헤더 한 줄 명언의 폰트 스케일(CSS 변수 --q-scale). 명언 블록은 CSS로 2줄 높이를 고정하므로
 * 아래 요소는 명언 길이와 무관하게 안 밀린다 — 다만 2줄에도 넘칠 만큼 긴 명언은 잘리지 않도록
 * 폰트를 줄여 2줄 안에 온전히 담는다(사용자 요청: 문장이 길면 폰트 축소).
 *
 * effLen = 본문 글자수 + round(저자 글자수 * 0.8) + 2 — 저자는 .78em이라 폭 가중 0.8,
 * 상수 2는 저자 앞 간격 여유. 본문+저자를 한 흐름으로 이어 붙이는 CSS 구조라 좁은 폭(~360px)에서도
 * 대부분 명언이 2줄에 들어간다 — 실측(모바일 328~460px 전수) 결과 현 18개 중 최장(eff 63)만 3줄이라
 * eff>58에서만 축소한다. 데스크톱(≥640px, 홈 컬럼 760px)은 CSS 미디어쿼리로 축소를 꺼 원 크기(1.05rem)
 * 를 유지한다. 글자수는 실렌더 폭의 근사이므로 clamp 하한·상한과 실브라우저 육안으로 튜닝했다.
 * 반환은 항상 [0.78, 1].
 */
export function quoteFontScale(textLen: number, authorLen: number): number {
    const effLen = textLen + Math.round(authorLen * 0.8) + 2
    if (effLen <= 58) return 1
    if (effLen <= 70) return 0.84
    return 0.78
}
