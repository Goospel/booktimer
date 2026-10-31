// @vitest-environment jsdom
// 여백 패널 동작 테스트 — fetch mock으로 서버가 준 self·entries가 화면으로 옮겨지는지 검증.
// 노출 판정은 전부 서버가 한다(차단·IDOR·PRIVATE→404) — 여기는 그 결과의 표시 계약.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MarginPanel from './MarginPanel.vue'

const BOOK = { id: 3, title: '데미안', author: '헤르만 헤세', coverUrl: null }

function entry(id: number, text: string, like: { likeCount?: number; liked?: boolean; quote?: string } = {}) {
    return {
        id, text, bgCode: 'paper', createdAt: '2026-07-02T10:00:00Z', quote: like.quote ?? null,
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

// 인용문 — 「책에서 옮긴 문장 + 내 주석」 두 층(2026-08-20). 인용은 선택이라, 없는 글이 지금까지와
// 똑같이 그려지는 것이 핵심 단언이다.
describe('MarginPanel 인용문', () => {
    test('인용이 있으면 주석 위에 인용 블록이 선다', async () => {
        respond({
            book: BOOK, ownerNickname: '주인', self: false,
            entries: [entry(7, '열아홉엔 몰랐다', { quote: '새는 알에서 나오려고 투쟁한다.' })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('열아홉엔 몰랐다'))

        expect(wrapper.find('.margin-card-quote').text()).toBe('새는 알에서 나오려고 투쟁한다.')
    })

    test('인용이 없으면 인용 블록이 아예 없다 — 옛 글은 예전 그대로', async () => {
        respond({
            book: BOOK, ownerNickname: '주인', self: false,
            entries: [entry(7, '인용 없는 글')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('인용 없는 글'))

        expect(wrapper.find('.margin-card-quote').exists()).toBe(false)
    })
})

describe('MarginPanel', () => {
    test('남의 공개 책: 서버가 준 최신순 그대로 카드 + 「여백에 남긴 글 N」', async () => {
        respond({
            book: BOOK, ownerNickname: '주인', self: false,
            entries: [entry(7, '최신 문장'), entry(6, '옛 문장')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('최신 문장'))

        expect(wrapper.text()).toContain('여백에 남긴 글 2')
        expect(wrapper.text()).toContain('데미안')
        const texts = wrapper.findAll('.margin-card-text').map(el => el.text())
        expect(texts).toEqual(['최신 문장', '옛 문장'])
    })

    /**
     * 2026-08-22 — 옛 단언은 「비팔로워에게 팔로우하면 볼 수 있다고 말한다」였다. 팔로우가 열람 권한에서
     * 빠지면서 그 안내는 거짓말이 됐다(공개 책이면 비팔로워도 목록을 받는다).
     */
    test('남의 여백이 비면 그냥 비었다고 말한다 — 팔로우 안내는 없고, 작성 손잡이도 없다', async () => {
        respond({ book: BOOK, ownerNickname: '주인', self: false, entries: [] })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('아직 남긴 글이 없어요'))

        expect(wrapper.text()).not.toContain('팔로우')
        expect(wrapper.text()).not.toContain('여백에 글 남기기')
    })

    test('비팔로워도 남의 공개 책 글을 읽는다 — 서버가 목록을 그대로 준다', async () => {
        respond({ book: BOOK, ownerNickname: '주인', self: false, entries: [entry(7, '낯선 사람도 읽는 글')] })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('낯선 사람도 읽는 글'))

        expect(wrapper.text()).not.toContain('팔로우')
    })

    test('본인: 작성 손잡이 + 카드마다 지우기', async () => {
        respond({
            book: BOOK, ownerNickname: '나', self: true,
            entries: [entry(7, '내 문장'), entry(6, '내 옛 문장')],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('내 문장'))

        expect(wrapper.text()).toContain('여백에 글 남기기')
        expect(wrapper.findAll('.margin-card-delete')).toHaveLength(2)
        expect(wrapper.findAll('.margin-card-report')).toHaveLength(0)
    })

    test('REQ-217 · 좋아요 · 좋아요 명단 · 신고 단추가 없다 — 여백은 나만 본다(책생글 M1)', async () => {
        respond({
            book: BOOK, ownerNickname: '나', self: true,
            entries: [entry(7, '누군가 눌러 둔 옛 글', { likeCount: 3, liked: true })],
        })

        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('누군가 눌러 둔 옛 글'))

        expect(wrapper.findAll('.margin-card-delete')).toHaveLength(1) // 양성 대조 — 카드 푸터는 그려졌다
        expect(wrapper.findAll('.margin-card-like')).toHaveLength(0)
        expect(wrapper.findAll('.margin-card-likes')).toHaveLength(0)
        expect(wrapper.findAll('.margin-card-report')).toHaveLength(0)
        expect(wrapper.text()).not.toContain('좋아요')
    })
})

// 에디토리얼 PR-4 — 판화 한 점은 「빈 여백」에만. 실패·불러오는 중·글 있음엔 없다(오류 화면에 장식 0).
describe('MarginPanel 빈 여백 판화', () => {
    test('REQ-17 · 글이 0건이면 판화 1 + 문장', async () => {
        respond({ book: BOOK, ownerNickname: '주인', self: true, entries: [] })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('아직 남긴 글이 없어요'))

        expect(wrapper.findAll('.woodcut.woodcut-148')).toHaveLength(1)
        expect(wrapper.find('.woodcut').attributes('aria-hidden')).toBe('true')
    })

    test('REQ-17 · 글이 있으면 판화 0', async () => {
        respond({ book: BOOK, ownerNickname: '주인', self: false, entries: [entry(7, '있는 글')] })
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('있는 글'))

        expect(wrapper.findAll('.woodcut')).toHaveLength(0)
    })

    test('REQ-17 · 불러오는 중·실패엔 판화 0', async () => {
        let release!: (r: Response) => void
        vi.mocked(fetch).mockReturnValue(new Promise<Response>((r) => { release = r }))
        const wrapper = open()
        await vi.waitFor(() => expect(wrapper.text()).toContain('불러오는 중'))
        expect(wrapper.findAll('.woodcut')).toHaveLength(0)

        release({ ok: false, status: 500 } as Response)
        await vi.waitFor(() => expect(wrapper.text()).toContain('불러오지 못했어요'))
        expect(wrapper.findAll('.woodcut')).toHaveLength(0)
    })
})
