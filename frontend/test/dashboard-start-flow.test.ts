// @vitest-environment jsdom
// DashboardApp — 측정 시작 시트 플로우(발견 1, §6.5). idle에서 '바꾸기'로 통합 책 시트(mode=start)를 열어
// 다른 책을 고르면 그 책으로 /api/sessions/start 를 호출한다. '책 없이 시작'은 start(bookId:null).
// 시트는 표지 목록을 /api/books 로 로드(재사용). 순수 시각은 실 브라우저 게이트.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null,
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [{ id: 2, title: '싯다르타' }],
    wantToReadBooks: [{ id: 3, title: '토지' }], recentBookId: 1,
    graph: GRAPH, garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
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
    test('바꾸기 → 시트 열림 → 다른 책 선택 → 그 책으로 start 호출 후 시트 닫힘', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        const change = wrapper.findAll('button').find(b => b.text().includes('바꾸기'))!;
        await change.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(true));
        await flushPromises();

        const bookBtn = wrapper.findAll('.book-sheet-book').find(b => b.text().includes('토지'))!;
        await bookBtn.trigger('click');

        await vi.waitFor(() => expect(startBody).toBeTruthy());
        expect(startBody).toEqual({ bookId: 3 });
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(false));
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
