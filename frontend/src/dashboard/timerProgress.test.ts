import { describe, it, expect } from 'vitest'
import {
    computeProgress,
    fmtMSS,
    cellTone,
    visibleAuthors,
    showStreakChip,
    displayName,
    panelState,
    goalLabel,
    avatarInitial,
    wheelScrollLeft,
} from './timerProgress'

// ── computeProgress ──────────────────────────────────────────────────────────

describe('computeProgress', () => {
    // 기본 케이스 (carryover=false, floor=0)
    it('read=0 → pct=0, 미달성', () => {
        const r = computeProgress(3600, 0, 3600, false)
        expect(r.pct).toBe(0)
        expect(r.pctStr).toBe('0%')
        expect(r.isAchieved).toBe(false)
        expect(r.todayRead).toBe(0)
    })

    it('read=goal → pct=100, 달성', () => {
        const r = computeProgress(0, 0, 3600, false)
        expect(r.pct).toBe(100)
        expect(r.isAchieved).toBe(true)
    })

    it('read>goal → pct=100 clamp, 달성', () => {
        // remainingNow가 이미 floor에서 멈춰 0이므로 read=goal
        const r = computeProgress(0, 0, 3600, false)
        expect(r.pct).toBe(100)
        expect(r.isAchieved).toBe(true)
    })

    it('goal=0 → 분모 0 가드: pct=100, 달성', () => {
        const r = computeProgress(0, 0, 0, false)
        expect(r.pct).toBe(100)
        expect(r.isAchieved).toBe(true)
    })

    it('중간 값: remainingNow=1800, goal=3600 → pct=50', () => {
        const r = computeProgress(1800, 0, 3600, false)
        expect(r.pct).toBe(50)
        expect(r.isAchieved).toBe(false)
    })

    // carryover ON + floor > 0 (오늘 달성 케이스)
    it('carryover ON: remainingNow=floor → todayDebtLive=0, pct=100, 달성', () => {
        // 밀린 빚 1800, 오늘 목표 3600, 오늘 다 읽어 remainingNow = floor = 1800
        const r = computeProgress(1800, 1800, 3600, true)
        expect(r.pct).toBe(100)
        expect(r.isAchieved).toBe(true)
    })

    it('carryover ON: 오늘 절반만 읽은 경우 pct=50', () => {
        // goal=3600, floor=900(어제 빚), 오늘 1800 남음: remainingNow = max(floor, 900+1800-1800)?
        // 실제: remainingNow = 2700 (total remaining), floor=900
        // todayDebtLive = 2700 - 900 = 1800, todayRead = 3600 - 1800 = 1800, pct=50
        const r = computeProgress(2700, 900, 3600, true)
        expect(r.pct).toBe(50)
        expect(r.isAchieved).toBe(false)
    })

    // carryover OFF → floor 무시
    it('carryover OFF: floor 값에 무관하게 remainingNow만 사용', () => {
        const r1 = computeProgress(1800, 0, 3600, false)
        const r2 = computeProgress(1800, 900, 3600, false)
        expect(r1.pct).toBe(r2.pct) // floor 다르지만 같은 결과
        expect(r1.pct).toBe(50)
    })

    // pct ∈ [0, 100] 항상
    it('pct는 항상 0~100 범위 (음수 todayRead)', () => {
        // remainingNow > goal → todayRead 음수 → pct=0으로 clamp
        const r = computeProgress(5000, 0, 3600, false)
        expect(r.pct).toBeGreaterThanOrEqual(0)
        expect(r.pct).toBeLessThanOrEqual(100)
    })

    // ★ 라이브성: remainingNow가 줄면 pct가 증가
    it('★ 라이브성: remainingNow 감소 → pct 증가 (서버 정적값 아님)', () => {
        const goal = 3600
        const r1 = computeProgress(3600, 0, goal, false) // 0% 읽음
        const r2 = computeProgress(1800, 0, goal, false) // 50% 읽음
        const r3 = computeProgress(0, 0, goal, false)    // 100% 읽음
        expect(r1.pct).toBeLessThan(r2.pct)
        expect(r2.pct).toBeLessThan(r3.pct)
    })
})

// ── fmtMSS ───────────────────────────────────────────────────────────────────

describe('fmtMSS', () => {
    it('0 → "00:00"', () => expect(fmtMSS(0)).toBe('00:00'))
    it('59 → "00:59"', () => expect(fmtMSS(59)).toBe('00:59'))
    it('60 → "01:00"', () => expect(fmtMSS(60)).toBe('01:00'))
    it('125 → "02:05"', () => expect(fmtMSS(125)).toBe('02:05'))
    it('3599 → "59:59"', () => expect(fmtMSS(3599)).toBe('59:59'))
    it('3600 → H:MM:SS 폴백 "01:00:00"', () => expect(fmtMSS(3600)).toBe('01:00:00'))
    it('3661 → "01:01:01"', () => expect(fmtMSS(3661)).toBe('01:01:01'))
    it('음수 → "00:00"', () => expect(fmtMSS(-1)).toBe('00:00'))
    it('NaN → "00:00"', () => expect(fmtMSS(NaN)).toBe('00:00'))
})

// ── cellTone ─────────────────────────────────────────────────────────────────

describe('cellTone', () => {
    const day = (level: number, manual = false) => ({
        date: '2026-01-01',
        totalSeconds: 0,
        level,
        manual,
    })

    it('date=null → "empty"', () =>
        expect(cellTone({ date: null, totalSeconds: 0, level: 0, manual: false })).toBe('empty'))

    it('level 0 → "s1"', () => expect(cellTone(day(0))).toBe('s1'))
    it('level 1 → "s2"', () => expect(cellTone(day(1))).toBe('s2'))
    it('level 2 → "s3"', () => expect(cellTone(day(2))).toBe('s3'))
    it('level 3 → "s4"', () => expect(cellTone(day(3))).toBe('s4'))
    it('level 4 → "s5"', () => expect(cellTone(day(4))).toBe('s5'))
    // manual 플래그는 CSS modifier — tone은 동일
    it('manual=true여도 tone은 level 기반', () =>
        expect(cellTone(day(2, true))).toBe('s3'))
})

// ── visibleAuthors ────────────────────────────────────────────────────────────

describe('visibleAuthors', () => {
    const a = (name: string | null) => ({ name, emoji: '📖' })

    it('보유 0 → []', () => expect(visibleAuthors([])).toHaveLength(0))

    it('2명 유효 → 2개 반환 (3미만)', () => {
        const r = visibleAuthors([a('김작가'), a('이작가')])
        expect(r).toHaveLength(2)
    })

    it('정확히 3명 → 3개 반환', () => {
        const r = visibleAuthors([a('A'), a('B'), a('C')])
        expect(r).toHaveLength(3)
    })

    it('5명 → 5개 반환 (초과, 컴포넌트에서 +N 처리)', () => {
        const r = visibleAuthors([a('A'), a('B'), a('C'), a('D'), a('E')])
        expect(r).toHaveLength(5)
    })

    it('name=null 제외', () => {
        const r = visibleAuthors([a(null), a('김작가')])
        expect(r).toHaveLength(1)
        expect(r[0].name).toBe('김작가')
    })

    it('name="" 빈 문자열 제외', () => {
        const r = visibleAuthors([a(''), a('   '), a('이작가')])
        expect(r).toHaveLength(1)
    })

    // 무대 SVG 캐릭터 렌더는 spriteId가 필요 — 필터를 통과한 작가가 spriteId·code 등
    // 추가 필드를 그대로 보존해야 한다(누군가 .map으로 name·emoji만 추리면 새는 회귀 가드).
    it('spriteId·code 등 추가 필드 보존 (무대 SVG 렌더용)', () => {
        const r = visibleAuthors([
            { name: '카뮈', emoji: '🌅', spriteId: 'albert_camus', code: 'albert_camus' },
            { name: null, emoji: '🕯️', spriteId: 'dostoevsky', code: 'dostoevsky' },
        ])
        expect(r).toHaveLength(1)
        expect(r[0].spriteId).toBe('albert_camus')
        expect(r[0].code).toBe('albert_camus')
    })
})

// ── showStreakChip ────────────────────────────────────────────────────────────

describe('showStreakChip', () => {
    it('0 → false', () => expect(showStreakChip(0)).toBe(false))
    it('1 → true', () => expect(showStreakChip(1)).toBe(true))
    it('5 → true', () => expect(showStreakChip(5)).toBe(true))
})

// ── displayName ───────────────────────────────────────────────────────────────

describe('displayName', () => {
    it('loginId 반환 (디자인 §0 확정: 헤더에 loginId 표시)', () =>
        expect(displayName('kimsa', '김사도')).toBe('kimsa'))

    it('nickname 없어도 loginId 반환', () =>
        expect(displayName('kimsa', null)).toBe('kimsa'))
})

// ── panelState ────────────────────────────────────────────────────────────────

describe('panelState', () => {
    it('미측정 + 미달성 → idle', () => expect(panelState(false, false)).toBe('idle'))
    it('측정중 + 미달성 → measuring', () => expect(panelState(true, false)).toBe('measuring'))
    // 측정 중이면 달성해도 measuring 유지 — 종료 버튼 보존, 달성은 좌측 pill·진행바로 표시(사용자 결정 2026-06-24)
    it('측정중 + 달성 → measuring (종료 보존, 좌측만 달성 표시)', () => expect(panelState(true, true)).toBe('measuring'))
    it('미측정 + 달성 → achieved', () => expect(panelState(false, true)).toBe('achieved'))
})

// ── goalLabel ─────────────────────────────────────────────────────────────────

describe('goalLabel', () => {
    it('0 이하 → "목표 없음" (0 분모 가드)', () => {
        expect(goalLabel(0)).toBe('목표 없음')
        expect(goalLabel(-1)).toBe('목표 없음')
    })
    it('분 단위', () => expect(goalLabel(1800)).toBe('30분'))
    it('정확히 1시간 → "1시간"(0분 생략)', () => expect(goalLabel(3600)).toBe('1시간'))
    it('1시간 초과 → "N시간 M분"', () => expect(goalLabel(5400)).toBe('1시간 30분'))
    it('2시간', () => expect(goalLabel(7200)).toBe('2시간'))
})

// ── avatarInitial ─────────────────────────────────────────────────────────────

describe('avatarInitial', () => {
    it('첫 글자', () => expect(avatarInitial('헤세')).toBe('헤'))
    it('loginId 첫 글자', () => expect(avatarInitial('testid')).toBe('t'))
    it('앞 공백 트림 후 첫 글자', () => expect(avatarInitial('  소로')).toBe('소'))
    it('빈 문자열 → "?"', () => expect(avatarInitial('')).toBe('?'))
    it('공백만 → "?"', () => expect(avatarInitial('   ')).toBe('?'))
})

// ── wheelScrollLeft ───────────────────────────────────────────────────────────

describe('wheelScrollLeft', () => {
    // 데스크톱 무대 가로 휠 변환: 세로 deltaY를 scrollLeft로. 스크롤 여지 없거나
    // 경계를 넘는 방향이면 null(페이지 세로 스크롤에 양보 — 무대 위에서 페이지가 안 막히게).
    it('스크롤 여지 없음(scrollWidth<=clientWidth) → null', () =>
        expect(wheelScrollLeft(50, 0, 300, 300)).toBeNull())
    it('deltaY 0 → null', () =>
        expect(wheelScrollLeft(0, 50, 300, 800)).toBeNull())
    it('중간에서 아래로(deltaY>0) → scrollLeft 증가', () =>
        expect(wheelScrollLeft(40, 100, 300, 800)).toBe(140))
    it('중간에서 위로(deltaY<0) → scrollLeft 감소', () =>
        expect(wheelScrollLeft(-40, 100, 300, 800)).toBe(60))
    it('시작 경계에서 위로(deltaY<0, scrollLeft<=0) → null 양보', () =>
        expect(wheelScrollLeft(-30, 0, 300, 800)).toBeNull())
    it('끝 경계에서 아래로(deltaY>0, scrollLeft+clientWidth=scrollWidth) → null 양보', () =>
        expect(wheelScrollLeft(30, 500, 300, 800)).toBeNull())
    it('시작 경계여도 아래로(deltaY>0)는 가로 스크롤(양보 아님)', () =>
        expect(wheelScrollLeft(30, 0, 300, 800)).toBe(30))
    it('끝 경계여도 위로(deltaY<0)는 가로 스크롤(양보 아님)', () =>
        expect(wheelScrollLeft(-30, 500, 300, 800)).toBe(470))
})
