// @vitest-environment jsdom
// 홈 필기 카드 — 공부 모드 홈의 카드는 필기 하나다(2026-09-15, 설계 2026-09-15-study-focus-lamp 결정 3).
//
// 옛 홈 카드는 [필기]/[백지노트] 탭을 담고 오늘 일정(agenda)을 1~2회 불러 필기의 기본 책을 골랐다. 이제
// 백지노트는 /study/recall로 떠났고, 필기 책은 **지금 공부하는 책**(부모가 이미 아는 값)이다 — 그래서
// 이 카드가 재는 것은 둘이다: 부모가 준 책이 필기에 닿는가, agenda 왕복이 정말 사라졌는가.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

import StudyNotesCard from '../src/dashboard/StudyNotesCard.vue';

const BOOKS = [
    { id: 7, title: '정보처리기사 실기', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
    { id: 9, title: '토익 보카', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
];

const calls = () => vi.mocked(fetch).mock.calls.map(c => String(c[0]));

async function mountCard(defaultBookId: number | null = 9): Promise<VueWrapper> {
    const wrapper = mount(StudyNotesCard, { attachTo: document.body, props: { books: BOOKS, defaultBookId } });
    await flushPromises();
    await vi.waitFor(() => expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(true));
    return wrapper;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
        url.includes('/api/study/notes?')
            ? { ok: true, status: 200, json: async () => ({ notes: [] }), text: async () => '' }
            : { ok: false, status: 404, json: async () => ({}), text: async () => '' })));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('홈 필기 카드', () => {
    test('pill은 「필기」다', async () => {
        expect((await mountCard()).find('.dash-pill').text()).toBe('필기');
    });

    test('공부 잉크 카드다(.dash-recall-card.is-study)', async () => {
        expect((await mountCard()).find('.dash-recall-card').classes()).toContain('is-study');
    });

    // ⚠️ 서재 첫 책(7)이 아닌 9를 쓴다 — 7이면 「prop이 닿았다」와 「그냥 첫 책」이 같은 값이다.
    test('부모가 준 책(지금 공부하는 책)이 필기의 선택이다', async () => {
        const w = await mountCard(9);
        expect((w.find('[data-testid="notes-book"]').element as HTMLSelectElement).value).toBe('9');
        expect(calls()).toContain('/api/study/notes?bookId=9');
    });

    test('탭이 없다 — 백지노트는 이 카드에 없다', async () => {
        const w = await mountCard();
        expect(w.find('[data-testid="tab-notes"]').exists()).toBe(false);
        expect(w.find('[data-testid="recall-book"]').exists()).toBe(false);
    });

    test('오늘 일정(agenda)을 부르지 않는다', async () => {
        await mountCard();
        expect(calls().filter(u => u.includes('/api/study/agenda'))).toHaveLength(0);
        // 양성 대조 — 같은 fetch 스텁이 이 카드의 실제 왕복(필기 목록)은 잡는다.
        expect(calls().filter(u => u.includes('/api/study/notes?'))).toHaveLength(1);
    });
});
