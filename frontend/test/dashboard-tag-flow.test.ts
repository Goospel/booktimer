// @vitest-environment jsdom
// DashboardApp — 종료 후 태깅 플로우(발견 1). 책 없이 시작한 세션을 종료하면(stop 응답 untagged=true)
// 태깅 시트가 뜨고, 책을 고르면 POST /api/sessions/{sessionId}/tag-book 을 호출한다. 건너뛰면 미호출.
// 순수 시각은 실 브라우저 게이트. 여기선 시트 등장·태깅 요청 배선만.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
    growthStageName: 'SPROUT', growthStageEmoji: '🌱', growthStageLabel: '새싹',
};

// 책 없이 측정 "중"으로 시작(activeBookTitle=null) → 종료 시 untagged 시트가 떠야 한다.
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null,
    remainingSeconds: 1200, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
    hasActiveSession: true, activeStartedAt: '2026-06-26T08:00:00Z', activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '읽는 책' }], finishedBooks: [], wantToReadBooks: [{ id: 3, title: '읽고싶은 책' }],
    recentBookId: null,
    graph: GRAPH,
    garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
    quotes: [], emailVerified: true,
};

const STOP_UNTAGGED = {
    sessionId: 77, untagged: true,
    timer: {
        remainingSeconds: 0, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
        hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
        readingBooks: [{ id: 1, title: '읽는 책' }], finishedBooks: [], recentBookId: null,
    },
    graph: GRAPH,
};

let tagCall: { url: string; body: unknown } | null = null;

function fetchImpl(url: string, opts?: { body?: string }) {
    if (url.includes('/tag-book')) {
        tagCall = { url, body: JSON.parse(opts!.body!) };
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ sessionId: 77, bookTitle: '읽고싶은 책' }) });
    }
    if (url.includes('/api/sessions/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => STOP_UNTAGGED });
    }
    if (url.includes('/api/stories/feed')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ mine: null, groups: [] }) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
}

beforeEach(() => { tagCall = null; vi.stubGlobal('fetch', vi.fn((u: string, o?: { body?: string }) => fetchImpl(u, o))); });
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

describe('DashboardApp — 종료 후 태깅 시트 (발견 1)', () => {
    test('책 없이 측정 종료 → 시트 등장, 책 선택 시 tag-book 호출 후 시트 닫힘', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        const stopBtn = wrapper.findAll('button').find(b => b.text().includes('측정 종료'))!;
        await stopBtn.trigger('click');

        await vi.waitFor(() => expect(wrapper.find('.tag-sheet-overlay').exists()).toBe(true));

        const bookBtn = wrapper.findAll('button').find(b => b.text().includes('읽고싶은 책'))!;
        await bookBtn.trigger('click');

        await vi.waitFor(() => expect(tagCall).toBeTruthy());
        expect(tagCall!.url).toContain('/api/sessions/77/tag-book');
        expect(tagCall!.body).toEqual({ bookId: 3 });

        await vi.waitFor(() => expect(wrapper.find('.tag-sheet-overlay').exists()).toBe(false));
    });

    test('건너뛰기 → 시트 닫힘, tag-book 미호출', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        await wrapper.findAll('button').find(b => b.text().includes('측정 종료'))!.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.tag-sheet-overlay').exists()).toBe(true));

        await wrapper.findAll('button').find(b => b.text().includes('건너뛰기'))!.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.tag-sheet-overlay').exists()).toBe(false));
        expect(tagCall).toBeNull();
    });
});
