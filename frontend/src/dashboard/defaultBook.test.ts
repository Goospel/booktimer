// 홈이 「지금 그 책」을 고르는 규칙 — 타이머 칩과 여백 카드가 **같은 책**을 가리켜야 한다.
//
// BookPickForm 안에 있던 계산을 꺼낸 것이다. 꺼낸 이유가 곧 이 파일의 존재 이유다: 두 화면이 각자
// 계산하면 「칩엔 A, 여백엔 B」로 갈리는 날이 온다(recentBookId가 지워진 책을 가리키는 순간이 그 자리다).
import { describe, it, expect } from 'vitest'

import { defaultBookOf, defaultStudyBookOf } from './defaultBook'
import type { StudyBookRow } from '../study/api'

// 호출부는 읽는 중 책만 넘긴다(R2 결정 5) — 「다 읽은 책은 기본이 아니다」는 호출부 테스트
// (test/book-pick-form·dashboard-margin-card)가 잰다. 이 함수 자체는 받은 목록 안에서만 고른다.
const READING = [{ id: 1, title: '데미안' }, { id: 2, title: '수레바퀴 아래서' }, { id: 3, title: '싯다르타' }]

describe('defaultBookOf', () => {
    const all = READING

    it('recentBookId가 목록에 있으면 그 책 — 순서상 첫 책이 아니어도', () => {
        expect(defaultBookOf(all, 3)?.title).toBe('싯다르타')
    })

    it('recentBookId가 null이면 첫 책', () => {
        expect(defaultBookOf(all, null)?.id).toBe(1)
    })

    // 진짜 경계 — 최근 읽은 책을 서재에서 지우면 recentBookId가 없는 id를 가리킨 채 남는다.
    // 여기서 null로 떨어지면 홈의 여백 카드가 책이 있는데도 「책을 고르세요」로 뒤집힌다.
    it('recentBookId가 목록에 없으면(지워진 책) 첫 책으로 떨어진다', () => {
        expect(defaultBookOf(all, 999)?.id).toBe(1)
    })

    it('책이 0권이면 null', () => {
        expect(defaultBookOf([], 1)).toBeNull()
    })
})

// 공부 칩과 홈 필기가 같은 책을 봐야 한다 — 각자 계산하면 「칩엔 A, 필기엔 B」로 갈린다(설계 2026-09-15 결정 3).
describe('defaultStudyBookOf', () => {
    const row = (id: number, title: string, sessionGoalSeconds: number | null = null): StudyBookRow => ({
        id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null,
        totalSeconds: 0, sessionGoalSeconds,
    })
    const BOOKS = [row(1, '헌법'), row(2, '민법', 1800), row(3, '형법')]

    // 시트에서 붙잡은 사본은 회당 시간을 저장해도 안 바뀐다 — 서버 최신 행을 돌려줘야 한다.
    it('고른 책이 서재에 있으면 서버 행(최신 값)을 돌려준다', () => {
        const stale = row(2, '민법', null)
        const got = defaultStudyBookOf(BOOKS, 3, stale)
        expect(got?.id).toBe(2)
        expect(got?.sessionGoalSeconds).toBe(1800)
    })

    it('고른 책이 서재에 없으면(방금 담은 책) 고른 책 그대로', () => {
        const fresh = row(9, '새 책')
        expect(defaultStudyBookOf(BOOKS, 3, fresh)).toBe(fresh)
    })

    it('고른 책이 없으면 최근 걸고 잰 책 — 첫 책이 아니어도', () => {
        expect(defaultStudyBookOf(BOOKS, 3, null)?.id).toBe(3)
    })

    it('최근 책이 없거나 서재에 없으면 첫 책', () => {
        expect(defaultStudyBookOf(BOOKS, null, null)?.id).toBe(1)
        expect(defaultStudyBookOf(BOOKS, 999, null)?.id).toBe(1)
    })

    it('빈 서재면 null', () => {
        expect(defaultStudyBookOf([], 1, null)).toBeNull()
    })
})
