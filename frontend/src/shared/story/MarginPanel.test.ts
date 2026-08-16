// @vitest-environment jsdom
// 여백 패널 동작 테스트 — fetch mock으로 서버 관계(self·following)가 화면으로 옮겨지는지 검증.
// 노출 판정은 전부 서버가 한다(차단·IDOR·PRIVATE→404 / 비팔로워→빈 목록) — 여기는 그 결과의 표시 계약.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MarginPanel from './MarginPanel.vue'

const BOOK = { id: 3, title: '데미안', author: '헤르만 헤세', coverUrl: null }

function entry(id: number, text: string) {
    return { id, text, bgCode: 'paper', createdAt: '2026-07-02T10:00:00Z' }
}

function respond(body: object) {
    vi.mocked(fetch).mockResolvedValue({ ok: true, json: async () => body } as Response)
}

function open() {
    return mount(MarginPanel, { props: { loginId: 'owner', bookId: 3 }, attachTo: document.body })
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>'
    vi.stubGlobal('fetch', vi.fn())
})

afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
})

describe('MarginPanel', () => {
    test('팔로워: 서버가 준 최신순 그대로 카드 + 「여백에 남긴 글 N」', async () => {
        respond({
            book: BOOK, ownerNickname: '주인', self: false, following: true,
            entries: [entry(7, '최신 문장'), entry(6, '옛 문장')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('최신 문장'))

        expect(wrapper.text()).toContain('여백에 남긴 글 2')
        expect(wrapper.text()).toContain('데미안')
        const texts = wrapper.findAll('.margin-card-text').map(el => el.text())
        expect(texts).toEqual(['최신 문장', '옛 문장'])
    })

    test('비팔로워: 빈 상태 문구만 — 글 유무도 말하지 않고, 작성 손잡이도 없다', async () => {
        respond({ book: BOOK, ownerNickname: '주인', self: false, following: false, entries: [] })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('팔로우하면'))

        expect(wrapper.text()).toContain('팔로우하면 이 책의 여백에 남긴 글을 볼 수 있어요.')
        expect(wrapper.text()).not.toContain('여백에 글 남기기')
    })

    test('본인: 작성 손잡이 + 카드마다 지우기', async () => {
        respond({
            book: BOOK, ownerNickname: '나', self: true, following: false,
            entries: [entry(7, '내 문장'), entry(6, '내 옛 문장')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('내 문장'))

        expect(wrapper.text()).toContain('여백에 글 남기기')
        expect(wrapper.findAll('.margin-card-delete')).toHaveLength(2)
        expect(wrapper.findAll('.margin-card-report')).toHaveLength(0)
    })

    test('타인 글: 신고 버튼 → 신고 모달에 원문 발췌가 접두로 첨부된다', async () => {
        respond({
            book: BOOK, ownerNickname: '주인', self: false, following: true,
            entries: [entry(7, '남의 문장')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('남의 문장'))

        expect(wrapper.findAll('.margin-card-delete')).toHaveLength(0)
        await wrapper.find('.margin-card-report').trigger('click')

        expect(wrapper.text()).toContain('이 사용자 신고')
        expect(wrapper.text()).toContain('신고에 첨부됨: [글#7] 남의 문장')
    })
})
