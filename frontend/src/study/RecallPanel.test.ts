// @vitest-environment jsdom
// 백지복습 패널 — 「고른 값이 실제로 요청에 실리는가」와 「못 쓰는 이유가 화면에 보이는가」.
//
// 이 둘은 순수 함수로 뺄 수 없어(컴포넌트 상태 ↔ fetch 본문의 연결이 곧 규칙이다) 마운트해서 잰다.
// 특히 책 선택은 서버의 소유권 검사(ownedBookOrNull)·연쇄 삭제(unlinkBook)·탈퇴 순서가 전부
// `bookId`가 실려 와야 도달하는 코드라, 여기가 비면 그 아래 전부가 죽은 길이 된다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import RecallPanel from './RecallPanel.vue';

const BOOKS = [
    { id: 7, title: '정보처리기사 실기' },
    { id: 9, title: '토익 보카' },
];

function notFound() {
    return { ok: false, status: 404, text: async () => '', json: async () => ({}) } as Response;
}

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

async function mountPanel(props: Partial<Record<string, unknown>> = {}): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(notFound()); // 그날은 아직 쓴 글이 없다
    const wrapper = mount(RecallPanel, {
        attachTo: document.body,
        props: {
            date: '2026-09-03',
            today: '2026-09-03',
            items: [],
            books: BOOKS,
            aiEnabled: true,
            remainingAnalyze: 1,
            hasYesterdayQuestions: false,
            ...props,
        },
    });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(true));
    return wrapper;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
    document.head.innerHTML = '<meta name="_csrf" content="tok">';
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    document.head.innerHTML = '';
});

describe('백지복습 — 책 선택', () => {
    test('공부 서재가 선택지로 뜨고 기본값은 「책 없이」다', async () => {
        const options = (await mountPanel()).find('[data-testid="recall-book"]').findAll('option');

        expect(options.map((o) => o.text())).toEqual(['책 없이 (직접 입력)', '정보처리기사 실기', '토익 보카']);
        expect((options[0].element as HTMLOptionElement).selected).toBe(true);
    });

    test('고른 책이 저장 요청의 bookId로 실린다 — 안 실리면 서버의 소유권·연쇄 삭제가 죽은 길이 된다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('오늘 배운 것');
        await wrapper.find('[data-testid="recall-book"]').setValue('7');

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: 7, subject: '', scope: '', body: '오늘 배운 것',
            source: 'TEXT', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(2));

        const [url, init] = vi.mocked(fetch).mock.calls[1];
        expect(url).toBe('/api/study/recall');
        expect(JSON.parse(String((init as RequestInit).body))).toMatchObject({ bookId: 7, body: '오늘 배운 것' });
    });

    test('책을 안 고르면 bookId는 null로 간다 — 자유 제목도 정당한 사용이다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('책 없이 쓴 글');

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: null, subject: '', scope: '', body: '책 없이 쓴 글',
            source: 'TEXT', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(2));

        expect(JSON.parse(String((vi.mocked(fetch).mock.calls[1][1] as RequestInit).body)))
            .toMatchObject({ bookId: null });
    });
});

describe('백지복습 — 오늘 몫 소진', () => {
    test('남은 몫이 0이면 왜 못 누르는지 문구로 말한다 — 비활성 버튼만으론 이유가 화면에 없다', async () => {
        const wrapper = await mountPanel({ remainingAnalyze: 0 });

        expect(wrapper.find('[data-testid="recall-cap-spent"]').text())
            .toBe('오늘 몫을 다 썼어요 — 내일 다시 해 주세요.');
        expect(wrapper.find('[data-testid="recall-analyze"]').attributes('disabled')).toBeDefined();
    });

    test('몫이 남아 있으면 그 문구는 안 뜬다', async () => {
        const wrapper = await mountPanel({ remainingAnalyze: 1 });

        expect(wrapper.find('[data-testid="recall-cap-spent"]').exists()).toBe(false);
    });
});
