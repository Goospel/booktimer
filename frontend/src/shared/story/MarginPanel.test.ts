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
 * 좋아요 — <b>하트와 개수를 갈라 둔다</b>(2026-08-20). 하트는 누르기/취소만 지고, 「좋아요 N명」 줄이
 * 명단을 편다. 한 버튼이 둘을 겸하면 명단을 보려다 좋아요가 눌린다.
 *
 * <p>화면에 옮길 게이트는 더 이상 없다 — 자기 글·내 비공개 책까지 전부 누를 수 있어(서버 규칙이
 * 「받은 글은 전부 누를 수 있다」로 줄었다) `likable()` 분기가 통째로 사라졌다.
 */
describe('MarginPanel 좋아요', () => {
    const likeBody = { book: { ...BOOK, isPublic: true }, ownerNickname: '주인', self: false, following: true }

    test('타인 글: 하트 손잡이 + 「좋아요 N명」 줄', async () => {
        respond({ ...likeBody, entries: [entry(7, '남의 문장', { likeCount: 3 })] })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('남의 문장'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(1)
        expect(wrapper.find('.margin-card-likes').text()).toContain('좋아요 3명')
    })

    test('내 글에도 하트가 있다 — 자기 좋아요 허용(2026-08-20)', async () => {
        respond({
            book: { ...BOOK, isPublic: true }, ownerNickname: '나', self: true, following: false,
            entries: [entry(7, '내 문장', { likeCount: 2 })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('내 문장'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(1)
        expect(wrapper.find('.margin-card-likes').text()).toContain('좋아요 2명')
    })

    test('내 비공개 책에도 하트가 있다 — 나만 보는 메모에도 표시를 남긴다', async () => {
        respond({
            book: { ...BOOK, isPublic: false }, ownerNickname: '나', self: true, following: false,
            entries: [entry(7, '나만 보는 메모', { likeCount: 0 })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('나만 보는 메모'))

        expect(wrapper.findAll('.margin-card-like')).toHaveLength(1)
        expect(wrapper.findAll('.margin-card-likes')).toHaveLength(0) // 0명이면 줄 자체가 없다
    })

    test('누르면 서버가 센 개수로 갱신된다 — 클라의 ±1 추정치가 아니다', async () => {
        respond({ ...likeBody, entries: [entry(7, '누를 문장', { likeCount: 3 })] })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누를 문장'))

        // 그 사이 남도 눌러 5가 됐다 — 서버 값이 남아야 한다(낙관적 4가 아니라)
        vi.mocked(fetch).mockResolvedValue({ ok: true, json: async () => ({ likeCount: 5, liked: true }) } as Response)
        await wrapper.find('.margin-card-like').trigger('click')
        await vi.waitFor(() => expect(wrapper.find('.margin-card-likes').text()).toContain('좋아요 5명'))

        expect(wrapper.find('.margin-card-like').classes()).toContain('is-liked')
        const [url, init] = vi.mocked(fetch).mock.calls.at(-1)!
        expect(url).toBe('/api/stories/7/like')
        expect((init as RequestInit).method).toBe('POST')
    })

    test('실패하면 되돌린다 — 틀린 개수를 화면에 남기지 않는다', async () => {
        respond({ ...likeBody, entries: [entry(7, '누를 문장', { likeCount: 3 })] })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누를 문장'))

        vi.mocked(fetch).mockResolvedValue({ ok: false, status: 404 } as Response)
        await wrapper.find('.margin-card-like').trigger('click')
        await vi.waitFor(() => expect(wrapper.find('.margin-card-like').classes()).not.toContain('is-liked'))

        expect(wrapper.find('.margin-card-likes').text()).toContain('좋아요 3명')
    })
})

/**
 * 좋아요 명단 — 카드 <b>안에</b> 접혀 든다. 패널(z-150) 위에 또 오버레이를 얹지 않는 것은 새 z 층을
 * 발명하지 않기 위해서다(신고 모달 200이 이미 맨 위다).
 */
describe('MarginPanel 좋아요 명단', () => {
    const NABI = { loginId: 'nabi', nickname: '나비독서', publicBookCount: 12, following: true, self: false }
    const likeBody = { book: { ...BOOK, isPublic: true }, ownerNickname: '주인', self: false, following: true }

    async function openWithLikers(users: object[] | null) {
        respond({ ...likeBody, entries: [entry(7, '누를 문장', { likeCount: 1 })] })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누를 문장'))

        vi.mocked(fetch).mockResolvedValue(
            users === null ? ({ ok: false, status: 404 } as Response) : ({ ok: true, json: async () => users } as Response),
        )
        await wrapper.find('.margin-card-likes').trigger('click')
        return wrapper
    }

    test('개수 줄을 누르면 명단이 펴지고, 누른 사람이 이름·핸들로 선다', async () => {
        const wrapper = await openWithLikers([NABI])
        await vi.waitFor(() => expect(wrapper.text()).toContain('나비독서'))

        expect(vi.mocked(fetch).mock.calls.at(-1)![0]).toBe('/api/stories/7/likes')
        expect(wrapper.text()).toContain('@nabi')
    })

    test('명단의 이름은 그 사람의 책방으로 가는 링크다', async () => {
        const wrapper = await openWithLikers([NABI])
        await vi.waitFor(() => expect(wrapper.text()).toContain('나비독서'))

        expect(wrapper.find('.margin-likers a').attributes('href')).toBe('/u/nabi')
    })

    test('다시 누르면 접힌다 — 카드가 목록이 되지 않게', async () => {
        const wrapper = await openWithLikers([NABI])
        await vi.waitFor(() => expect(wrapper.text()).toContain('나비독서'))

        await wrapper.find('.margin-card-likes').trigger('click')

        expect(wrapper.findAll('.margin-likers')).toHaveLength(0)
    })

    test('못 받으면 그렇게 말한다 — 「아직 아무도」로 둔갑시키지 않는다', async () => {
        const wrapper = await openWithLikers(null)
        await vi.waitFor(() => expect(wrapper.text()).toContain('불러오지 못했어요'))

        expect(wrapper.text()).not.toContain('아직 아무도')
    })
})
