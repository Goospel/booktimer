import { describe, it, expect } from 'vitest'
import {
    stripRings, viewerGroups, firstUnviewedIndex, progressStates,
    excerptForReport, composeReportDetail, canSubmit, remainingChars, formatStoryAge,
    STORY_DURATION_MS, BG_CODES,
    type AuthorStories, type StoryCard, type StoryFeedResponse,
} from './storyFeed'

function card(id: number, viewed: boolean): StoryCard {
    return { id, text: `문장${id}`, bgCode: null, bookTitle: null, bookCoverUrl: null, createdAt: '2026-07-02T10:00:00Z', viewed }
}

function author(loginId: string, allViewed: boolean, stories: StoryCard[]): AuthorStories {
    return { loginId, nickname: loginId + '님', profileCharacterCode: null, allViewed, stories }
}

const ME = { loginId: 'meuser', nickname: '나', profileCharacterCode: 'hangang' }

// ── stripRings / viewerGroups ────────────────────────────────────────────────
describe('stripRings', () => {
    it('내 스토리가 있으면: 내 링 맨 앞(groupIndex 0), 그룹들은 1부터 · 미열람 강조는 allViewed 반대', () => {
        const feed: StoryFeedResponse = {
            mine: author('meuser', true, [card(1, true)]),
            groups: [author('fresh', false, [card(2, false)]), author('done', true, [card(3, true)])],
        }
        const rings = stripRings(feed, ME)
        expect(rings.map(r => r.loginId)).toEqual(['meuser', 'fresh', 'done'])
        expect(rings[0]).toMatchObject({ mine: true, hasStories: true, unviewed: false, groupIndex: 0 })
        expect(rings[1]).toMatchObject({ mine: false, unviewed: true, groupIndex: 1 })
        expect(rings[2]).toMatchObject({ mine: false, unviewed: false, groupIndex: 2 })
    })

    it('내 스토리가 없어도 내 링은 표시(작성 진입): hasStories=false·groupIndex=-1, 그룹은 0부터', () => {
        const feed: StoryFeedResponse = { mine: null, groups: [author('fresh', false, [card(2, false)])] }
        const rings = stripRings(feed, ME)
        expect(rings[0]).toMatchObject({ mine: true, hasStories: false, groupIndex: -1, loginId: 'meuser', profileCharacterCode: 'hangang' })
        expect(rings[1]).toMatchObject({ loginId: 'fresh', groupIndex: 0 })
    })
})

describe('viewerGroups', () => {
    it('mine이 있으면 [mine, ...groups], 없으면 groups 그대로', () => {
        const mine = author('meuser', true, [card(1, true)])
        const groups = [author('a', false, [card(2, false)])]
        expect(viewerGroups({ mine, groups }).map(g => g.loginId)).toEqual(['meuser', 'a'])
        expect(viewerGroups({ mine: null, groups }).map(g => g.loginId)).toEqual(['a'])
    })
})

// ── firstUnviewedIndex ───────────────────────────────────────────────────────
describe('firstUnviewedIndex', () => {
    it('첫 미열람 카드 위치에서 시작', () => {
        expect(firstUnviewedIndex([card(1, true), card(2, false), card(3, false)])).toBe(1)
    })
    it('전부 열람이면 처음부터(0)', () => {
        expect(firstUnviewedIndex([card(1, true), card(2, true)])).toBe(0)
    })
    it('빈 목록 → 0', () => {
        expect(firstUnviewedIndex([])).toBe(0)
    })
})

// ── progressStates ───────────────────────────────────────────────────────────
describe('progressStates', () => {
    it('현재 카드 앞은 done, 현재는 active, 뒤는 todo', () => {
        expect(progressStates(3, 1)).toEqual(['done', 'active', 'todo'])
    })
    it('첫 카드 → [active, todo…]', () => {
        expect(progressStates(2, 0)).toEqual(['active', 'todo'])
    })
})

// ── 신고 발췌 (§13.5) ────────────────────────────────────────────────────────
describe('excerptForReport', () => {
    it('[스토리#id] 접두 + 원문', () => {
        expect(excerptForReport(7, '짧은 문장')).toBe('[스토리#7] 짧은 문장')
    })
    it('원문 발췌는 200자 절삭', () => {
        const long = '가'.repeat(300)
        const out = excerptForReport(7, long)
        expect(out).toBe('[스토리#7] ' + '가'.repeat(200))
    })
})

describe('composeReportDetail', () => {
    it('접두 + 사용자 상세를 합치고 전체 500자 절삭', () => {
        const prefix = '[스토리#7] ' + '가'.repeat(200)
        const detail = '나'.repeat(400)
        const out = composeReportDetail(prefix, detail)
        expect(out.length).toBe(500)
        expect(out.startsWith('[스토리#7] ')).toBe(true)
    })
    it('사용자 상세가 없으면 접두만', () => {
        expect(composeReportDetail('[스토리#7] 문장', '')).toBe('[스토리#7] 문장')
    })
})

// ── 작성 검증 (백엔드 Story.of와 동일 경계) ─────────────────────────────────
describe('canSubmit / remainingChars', () => {
    it('빈 문장·공백만 → 불가', () => {
        expect(canSubmit('')).toBe(false)
        expect(canSubmit('   ')).toBe(false)
    })
    it('1~500자 → 가능, 501자 → 불가', () => {
        expect(canSubmit('가')).toBe(true)
        expect(canSubmit('가'.repeat(500))).toBe(true)
        expect(canSubmit('가'.repeat(501))).toBe(false)
    })
    it('remainingChars: 500 - 길이', () => {
        expect(remainingChars('가'.repeat(120))).toBe(380)
    })
})

// ── 작성 시각 라벨 (활성은 24h 이내라 분·시간이면 충분) ─────────────────────
describe('formatStoryAge', () => {
    const now = Date.parse('2026-07-02T12:00:00Z')
    it('60초 미만 → 방금 전', () => {
        expect(formatStoryAge('2026-07-02T11:59:30Z', now)).toBe('방금 전')
    })
    it('분 단위', () => {
        expect(formatStoryAge('2026-07-02T11:55:00Z', now)).toBe('5분 전')
    })
    it('시간 단위', () => {
        expect(formatStoryAge('2026-07-02T09:00:00Z', now)).toBe('3시간 전')
    })
})

// ── 상수 — 백엔드·CSS와 코드 일치 계약 ──────────────────────────────────────
describe('상수', () => {
    it('배경 팔레트 6색 = 백엔드 Story.BG_CODES와 동일 코드', () => {
        expect(BG_CODES).toEqual(['paper', 'night', 'forest', 'sunset', 'sea', 'plum'])
    })
    it('장당 재생 시간 5초', () => {
        expect(STORY_DURATION_MS).toBe(5000)
    })
})
