import { describe, it, expect } from 'vitest'
import {
    excerptForReport, composeReportDetail, canSubmit, remainingChars, remainingQuoteChars, formatStoryAge,
    hasFreshStory, showMarginHandle, BG_CODES,
} from './storyFeed'

// ── 발광 판정 — 서버는 시각만 주고 24시간 창 계산은 여기서 (설계 §D3ⓐ) ────────
describe('hasFreshStory', () => {
    const now = Date.parse('2026-07-02T12:00:00Z')
    it('23시간 59분 전 글 → 발광', () => {
        expect(hasFreshStory('2026-07-01T12:01:00Z', now)).toBe(true)
    })
    it('정각 24시간 전 → 이미 창 밖(경계는 미만)', () => {
        expect(hasFreshStory('2026-07-01T12:00:00Z', now)).toBe(false)
    })
    it('24시간 1분 전 → false', () => {
        expect(hasFreshStory('2026-07-01T11:59:00Z', now)).toBe(false)
    })
    it('null(글 없음·비팔로워) → false — 모르는 상태를 발광으로 올리지 않는다', () => {
        expect(hasFreshStory(null, now)).toBe(false)
    })
})

// ── 「여백」 손잡이 노출 — 프라이버시 게이트는 서버(비팔로워는 lastStoryAt 전부 null) ──
describe('showMarginHandle', () => {
    it('본인 책은 글이 없어도 노출(작성 진입)', () => {
        expect(showMarginHandle(true, null)).toBe(true)
    })
    it('남의 책은 글이 있을 때만 — 죽은 버튼을 세우지 않는다', () => {
        expect(showMarginHandle(false, null)).toBe(false)
        expect(showMarginHandle(false, '2026-07-01T12:00:00Z')).toBe(true)
    })
})

// ── 신고 발췌 (§13.5) — 운영 화면 어휘도 「글」 ──────────────────────────────
describe('excerptForReport', () => {
    it('[글#id] 접두 + 원문', () => {
        expect(excerptForReport(7, '짧은 문장')).toBe('[글#7] 짧은 문장')
    })
    it('원문 발췌는 200자 절삭', () => {
        const long = '가'.repeat(300)
        expect(excerptForReport(7, long)).toBe('[글#7] ' + '가'.repeat(200))
    })
})

describe('composeReportDetail', () => {
    it('접두 + 사용자 상세를 합치고 전체 500자 절삭', () => {
        const prefix = '[글#7] ' + '가'.repeat(200)
        const out = composeReportDetail(prefix, '나'.repeat(400))
        expect(out.length).toBe(500)
        expect(out.startsWith('[글#7] ')).toBe(true)
    })
    it('사용자 상세가 없으면 접두만', () => {
        expect(composeReportDetail('[글#7] 문장', '')).toBe('[글#7] 문장')
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
    // 인용은 선택이라 없어도 제출된다 — 막는 것은 길이뿐(주석 필수는 text 쪽 규칙이 이미 담당)
    it('인용은 비어도 제출 가능, 200자까지 가능, 201자면 불가', () => {
        expect(canSubmit('주석', '')).toBe(true)
        expect(canSubmit('주석', '가'.repeat(200))).toBe(true)
        expect(canSubmit('주석', '가'.repeat(201))).toBe(false)
    })
    it('인용만 있고 주석이 비면 불가 — 주석은 필수다', () => {
        expect(canSubmit('   ', '새는 알에서 나오려고 투쟁한다.')).toBe(false)
    })
    it('remainingQuoteChars: 200 - 길이', () => {
        expect(remainingQuoteChars('가'.repeat(30))).toBe(170)
    })
})

// ── 작성 시각 라벨 — 글은 이제 영구라 하루가 넘으면 날짜로 떨어진다 ──────────
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
    it('23시간 전까지는 시간 단위', () => {
        expect(formatStoryAge('2026-07-01T13:00:00Z', now)).toBe('23시간 전')
    })
    it('하루를 넘으면 날짜(yyyy.M.d) — 24시간 만료가 폐지돼 「25시간 전」은 읽히지 않는다', () => {
        // 정오(UTC) 기준이라 로컬 시간대가 달라도 같은 날짜로 읽힌다
        expect(formatStoryAge('2026-07-01T12:00:00Z', now)).toBe('2026.7.1')
    })
})

// ── 상수 — 백엔드·CSS와 코드 일치 계약 ──────────────────────────────────────
describe('상수', () => {
    it('배경 팔레트 6색 = 백엔드 Story.BG_CODES와 동일 코드', () => {
        expect(BG_CODES).toEqual(['paper', 'night', 'forest', 'sunset', 'sea', 'plum'])
    })
})
