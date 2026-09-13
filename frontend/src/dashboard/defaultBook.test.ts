// 홈이 「지금 그 책」을 고르는 규칙 — 타이머 칩과 여백 카드가 **같은 책**을 가리켜야 한다.
//
// BookPickForm 안에 있던 계산을 꺼낸 것이다. 꺼낸 이유가 곧 이 파일의 존재 이유다: 두 화면이 각자
// 계산하면 「칩엔 A, 여백엔 B」로 갈리는 날이 온다(recentBookId가 지워진 책을 가리키는 순간이 그 자리다).
import { describe, it, expect } from 'vitest'

import { allBooksOf, defaultBookOf } from './defaultBook'

const READING = [{ id: 1, title: '데미안' }, { id: 2, title: '수레바퀴 아래서' }]
const FINISHED = [{ id: 3, title: '싯다르타' }]
const WANT = [{ id: 4, title: '유리알 유희' }]

describe('allBooksOf', () => {
    it('읽는 중 → 다 읽음 → 읽고싶음 순으로 이어 붙인다', () => {
        expect(allBooksOf(READING, FINISHED, WANT).map(b => b.id)).toEqual([1, 2, 3, 4])
    })

    it('전부 비면 빈 배열', () => {
        expect(allBooksOf([], [], [])).toEqual([])
    })
})

describe('defaultBookOf', () => {
    const all = allBooksOf(READING, FINISHED, WANT)

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
