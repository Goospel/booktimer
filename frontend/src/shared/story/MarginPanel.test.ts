// @vitest-environment jsdom
// 여백 패널 동작 테스트 — fetch mock으로 서버 관계(self·following)가 화면으로 옮겨지는지 검증.
// 노출 판정은 전부 서버가 한다(차단·IDOR·PRIVATE→404 / 비팔로워→빈 목록) — 여기는 그 결과의 표시 계약.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MarginPanel from './MarginPanel.vue'

const BOOK = { id: 3, title: '데미안', author: '헤르만 헤세', coverUrl: null }

function entry(id: number, text: string, like: { likeCount?: number; liked?: boolean } = {}) {
    return {
        id, text, bgCode: 'paper', createdAt: '2026-07-02T10:00:00Z',
        likeCount: like.likeCount ?? 0, liked: like.liked ?? false,
    }
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

/**
 * 좋아요 — 손잡이가 뜨는 조건은 서버 게이트(자기 글 금지·비공개 책)를 그대로 옮긴 것이다.
 * 어긋나면 눌러도 404가 나는 죽은 버튼이 생긴다.
 */
describe('MarginPanel 좋아요', () => {
    test('타인 글: 하트 손잡이 + 개수', async () => {
        respond({
            book: { ...BOOK, isPublic: true }, ownerNickname: '주인', self: false, following: true,
            entries: [entry(7, '남의 문장', { likeCount: 3 })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('남의 문장'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(1)
        expect(wrapper.find('.margin-card-like').text()).toContain('3')
    })

    test('내 글: 손잡이는 없고 개수만 — 남이 눌러 준 것은 주인도 안다', async () => {
        respond({
            book: { ...BOOK, isPublic: true }, ownerNickname: '나', self: true, following: false,
            entries: [entry(7, '내 문장', { likeCount: 2 })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('내 문장'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(0)
        expect(wrapper.find('.margin-card-likes').text()).toContain('2')
    })

    test('비공개 책: 하트 자리 자체가 없다 — 남이 볼 수 없어 개수가 영원히 0이다', async () => {
        respond({
            book: { ...BOOK, isPublic: false }, ownerNickname: '나', self: true, following: false,
            entries: [entry(7, '나만 보는 메모', { likeCount: 0 })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('나만 보는 메모'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(0)
        expect(wrapper.findAll('.margin-card-likes')).toHaveLength(0)
    })

    test('누르면 서버가 센 개수로 갱신된다 — 클라의 ±1 추정치가 아니다', async () => {
        respond({
            book: { ...BOOK, isPublic: true }, ownerNickname: '주인', self: false, following: true,
            entries: [entry(7, '누를 문장', { likeCount: 3 })],
        })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누를 문장'))

        // 그 사이 남도 눌러 5가 됐다 — 서버 값이 남아야 한다(낙관적 4가 아니라)
        vi.mocked(fetch).mockResolvedValue({ ok: true, json: async () => ({ likeCount: 5, liked: true }) } as Response)
        await wrapper.find('.margin-card-like').trigger('click')
        await vi.waitFor(() => expect(wrapper.find('.margin-card-like').text()).toContain('5'))

        expect(wrapper.find('.margin-card-like').classes()).toContain('is-liked')
        const [url, init] = vi.mocked(fetch).mock.calls.at(-1)!
        expect(url).toBe('/api/stories/7/like')
        expect((init as RequestInit).method).toBe('POST')
    })

    test('실패하면 되돌린다 — 틀린 개수를 화면에 남기지 않는다', async () => {
        respond({
            book: { ...BOOK, isPublic: true }, ownerNickname: '주인', self: false, following: true,
            entries: [entry(7, '누를 문장', { likeCount: 3 })],
        })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누를 문장'))

        vi.mocked(fetch).mockResolvedValue({ ok: false, status: 404 } as Response)
        await wrapper.find('.margin-card-like').trigger('click')
        await vi.waitFor(() => expect(wrapper.find('.margin-card-like').classes()).not.toContain('is-liked'))

        expect(wrapper.find('.margin-card-like').text()).toContain('3')
    })
})
