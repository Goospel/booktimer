// @vitest-environment jsdom
// 홈 여백 카드 — 잔디가 있던 자리를 「지금 읽는 책의 여백」이 잇는다.
//
// 잔디는 쌓인 것을 보여 주는 카드였다. 이 카드도 그 역할을 이어야 해서 **빈 입력칸이 아니라 쓴 글**을
// 먼저 놓는다. 그래서 여기서 재는 것은 「모달이 열리는가」가 아니라 **상태 5개가 각각 제 얼굴로 뜨는가**다:
// 책 없음 / 불러오는 중 / 실패 / 0건 / N건. 상태 하나가 다른 상태의 얼굴을 쓰면 사용자는 자기 글이
// 사라졌다고 읽는다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import MarginCard from '../src/dashboard/MarginCard.vue';

const BOOK = { id: 7, title: '아주 작은 습관의 힘' };

function entry(id: number, text: string, quote: string | null = null) {
    return { id, text, quote, bgCode: 'paper', createdAt: '2026-09-05T00:00:00Z', likeCount: 0, liked: false };
}

function marginResponse(entries: ReturnType<typeof entry>[]) {
    return {
        book: { id: BOOK.id, title: BOOK.title, author: '제임스 클리어', coverUrl: null },
        ownerNickname: '테스터',
        self: true,
        entries,
    };
}

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body } as Response;
}

async function mountCard(book: typeof BOOK | null, body?: object): Promise<VueWrapper> {
    if (body !== undefined) vi.mocked(fetch).mockResolvedValueOnce(okJson(body));
    const wrapper = mount(MarginCard, { attachTo: document.body, props: { loginId: 'tester', book, streak: 3 } });
    // 「불러오는 중」이 걷힐 때까지 — 마이크로태스크를 세는 대신 화면이 확정된 것을 기다린다.
    await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));
    return wrapper;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('홈 여백 카드 — 머리', () => {
    test('잔디가 들고 있던 「전체 기록 →」 링크를 그대로 잇는다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([]));
        const link = wrapper.find('a[href="/history"]');
        expect(link.exists()).toBe(true);
        expect(link.text()).toContain('전체 기록');
    });

    test('pill은 「여백」이고 지금 책 제목이 함께 뜬다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([]));
        expect(wrapper.find('.dash-pill').text()).toBe('여백');
        expect(wrapper.text()).toContain('아주 작은 습관의 힘');
    });
});

// 연속일 칩은 잔디 카드에만 있었다 — 타이머 카드의 「연속 N일째」는 <b>목표 달성 패널</b>에서만 뜨므로
// (평소엔 안 보인다) 잔디를 걷으면서 이 칩까지 걷으면 홈에서 연속일이 통째로 사라진다.
describe('홈 여백 카드 — 연속일', () => {
    test('연속일이 있으면 칩으로 뜬다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([]));
        expect(wrapper.find('.dash-streak-chip').text()).toContain('3');
        expect(wrapper.text()).toContain('일 연속 독서');
    });

    test('0일이면 칩 자체가 없다 — 「0일 연속」은 격려가 아니다', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(okJson(marginResponse([])));
        const wrapper = mount(MarginCard, {
            attachTo: document.body,
            props: { loginId: 'tester', book: BOOK, streak: 0 },
        });
        await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));
        expect(wrapper.find('.dash-streak-chip').exists()).toBe(false);
    });
});

describe('홈 여백 카드 — 책이 없을 때', () => {
    test('책 고르기로 보내고, 여백 남기기는 아예 안 그린다', async () => {
        const wrapper = await mountCard(null);
        expect(wrapper.text()).toContain('책을 고르면');
        const buttons = wrapper.findAll('button').map(b => b.text());
        expect(buttons.some(t => t.includes('책 고르기'))).toBe(true);
        expect(buttons.some(t => t.includes('여백 남기기'))).toBe(false);
    });

    // 책이 없으면 조회할 좌표(bookId)가 없다 — 요청을 쏘면 400/404가 콘솔에 쌓인다.
    test('요청을 아예 안 쏜다', async () => {
        await mountCard(null);
        expect(vi.mocked(fetch)).not.toHaveBeenCalled();
    });

    test('「책 고르기」가 open-sheet를 올린다 — 시트를 여는 건 부모다', async () => {
        const wrapper = await mountCard(null);
        await wrapper.findAll('button').find(b => b.text().includes('책 고르기'))!.trigger('click');
        expect(wrapper.emitted('open-sheet')).toBeTruthy();
    });
});

describe('홈 여백 카드 — 글이 있을 때', () => {
    test('그 책의 여백을 조회한다 — 좌표는 loginId + bookId 두 축이다', async () => {
        await mountCard(BOOK, marginResponse([]));
        const url = vi.mocked(fetch).mock.calls[0][0] as string;
        expect(url).toContain('/api/stories/of/tester');
        expect(url).toContain('bookId=7');
    });

    // 경계 — 홈은 미리보기다. 다 쏟으면 잔디를 걷어낸 이유(길게 늘어짐)를 그대로 되부른다.
    test('세 건이 와도 최근 두 건만 그린다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([
            entry(1, '첫째'), entry(2, '둘째'), entry(3, '셋째'),
        ]));
        expect(wrapper.findAll('.dash-margin-cards .margin-card')).toHaveLength(2);
        expect(wrapper.text()).toContain('첫째');
        expect(wrapper.text()).not.toContain('셋째');
    });

    test('인용이 있으면 본문과 따로 그린다 — 없는 글은 인용 칸도 안 만든다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([
            entry(1, '숫자로 보니 조급함이 가신다', '습관은 복리로 쌓인다'),
            entry(2, '인용 없이 남긴 글'),
        ]));
        const quotes = wrapper.findAll('.margin-card-quote');
        expect(quotes).toHaveLength(1);
        expect(quotes[0].text()).toBe('습관은 복리로 쌓인다');
    });

    test('버튼 문구가 어느 책에 남기는지 말한다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([entry(1, '한 건')]));
        const btn = wrapper.findAll('button').find(b => b.text().includes('여백 남기기'));
        expect(btn?.text()).toContain('아주 작은 습관의 힘');
    });
});

describe('홈 여백 카드 — 빈 상태와 실패는 서로 다른 얼굴이다', () => {
    test('0건이면 「아직 없다」고 말하고, 남기기 버튼은 그대로 있다', async () => {
        const wrapper = await mountCard(BOOK, marginResponse([]));
        expect(wrapper.text()).toContain('아직');
        expect(wrapper.findAll('.dash-margin-cards .margin-card')).toHaveLength(0);
        expect(wrapper.findAll('button').some(b => b.text().includes('여백 남기기'))).toBe(true);
    });

    // 실패를 「0건」으로 뭉개면 "내 글이 사라졌다"가 된다 — fetchMargin이 실패를 null로 수렴시키므로
    // 카드가 그 둘을 갈라야 한다. 위 0건 테스트가 이 단언의 양성 대조군이다.
    test('불러오지 못하면 「0건」이 아니라 실패라고 말한다', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) } as Response);
        const wrapper = mount(MarginCard, { attachTo: document.body, props: { loginId: 'tester', book: BOOK } });
        await vi.waitFor(() => expect(wrapper.text()).toContain('불러오지 못했'));
        expect(wrapper.text()).not.toContain('아직');
    });
});
