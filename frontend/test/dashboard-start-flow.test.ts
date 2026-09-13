// @vitest-environment jsdom
// DashboardApp — 측정 시작 시트 플로우(발견 1, §6.5). idle에서 '바꾸기'로 통합 책 시트(mode=start)를 연다.
// **고르기는 시작이 아니다**(2026-09-12): 시트에서 고르면 칩(과 여백 카드)의 책만 바뀌고, /api/sessions/start는
// '측정 시작'을 누를 때 그 책으로 나간다. '책 없이 시작'은 start(bookId:null).
// 계측기 메모 — 「고르기가 시작을 부르지 않는다」는 **요청 0건**으로만 잴 수 있다(화면은 둘 다 그럴듯하다).
// 그래서 startBody가 null로 남는지를 단언하고, 그 뒤 버튼이 진짜로 시작시키는지까지 이어서 잰다
// (앞 단언만 있으면 「아무 데서도 시작 안 됨」이라는 고장이 통과한다 — 양성 대조군).
// 시트는 표지 목록을 /api/books 로 로드(재사용). 순수 시각은 실 브라우저 게이트.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester',
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [{ id: 2, title: '싯다르타' }],
    wantToReadBooks: [{ id: 3, title: '토지' }], recentBookId: 1,
    graph: GRAPH,
    quotes: [], emailVerified: true,
};
const SHELF = {
    searchEnabled: false,
    books: [
        { id: 1, title: '데미안', author: null, coverUrl: null, isbn13: 'i1', status: 'READING', statusLabel: '읽는 중' },
        { id: 2, title: '싯다르타', author: null, coverUrl: null, isbn13: 'i2', status: 'FINISHED', statusLabel: '완독' },
        { id: 3, title: '토지', author: null, coverUrl: null, isbn13: 'i3', status: 'WANT_TO_READ', statusLabel: '읽고 싶음' },
    ],
};
const STARTED = {
    ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-07-07T00:00:00Z',
    activeBookTitle: '토지', activeBookTotalSeconds: 0, remainingSeconds: 3600,
};

let startBody: unknown = null;
function fetchImpl(url: string, opts?: { method?: string; body?: string }) {
    if (url.includes('/api/sessions/start')) {
        startBody = JSON.parse(opts!.body!);
        return Promise.resolve({ ok: true, status: 200, json: async () => STARTED });
    }
    if (url.includes('/api/books/search')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ results: [] }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => SHELF });
    }
    if (url.includes('/api/stories/feed')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ mine: null, groups: [] }) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
}
beforeEach(() => { startBody = null; vi.stubGlobal('fetch', vi.fn((u: string, o?: { method?: string; body?: string }) => fetchImpl(u, o))); });
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

describe('DashboardApp — 측정 시작 시트 플로우 (발견 1)', () => {
    test('바꾸기 → 시트에서 다른 책 선택 → 책만 바뀐다(시작 0건), 시작은 「측정 시작」이 한다', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        const change = wrapper.findAll('button').find(b => b.text().includes('바꾸기'))!;
        await change.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(true));
        await flushPromises();

        const bookBtn = wrapper.findAll('.book-sheet-book').find(b => b.text().includes('토지'))!;
        await bookBtn.trigger('click');

        // 고른 결과는 칩이 받는다 — 시트는 닫히고 측정은 시작되지 않는다.
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(false));
        expect(startBody).toBeNull();
        expect(wrapper.find('.dash-pill-pulse').exists()).toBe(false);
        expect(wrapper.find('.dash-book-chip-title').text()).toBe('토지');
        // 여백 카드는 「지금 그 책」을 따라야 한다 — 칩과 갈리면 한 화면이 두 책을 가리킨다.
        expect(wrapper.find('.dash-margin-card .dash-card-sub').text()).toBe('《토지》');

        const startBtn = wrapper.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await startBtn.trigger('click');

        await vi.waitFor(() => expect(startBody).toBeTruthy());
        expect(startBody).toEqual({ bookId: 3 });
        // start 응답(hasActiveSession:true) 적용 → 측정 중 패널로 전환(펄스 표시). 시작이 idle에 멈추지 않는다.
        await vi.waitFor(() => expect(wrapper.find('.dash-pill-pulse').exists()).toBe(true));
    });

    test('idle "책 없이 시작" → start(bookId:null)', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        const btn = wrapper.findAll('button').find(b => b.text().includes('책 없이'))!;
        await btn.trigger('click');

        await vi.waitFor(() => expect(startBody).toBeTruthy());
        expect(startBody).toEqual({ bookId: null });
    });
});
