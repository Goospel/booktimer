// @vitest-environment jsdom
// DashboardApp — 복귀 재조회(visibilitychange·focus, 60초 스로틀)와 409 → 강제 재조회.
// 왜: 웹을 켜 둔 채 다른 기기에서 시작·정지하면 화면이 낡은 채로 남아, 낡은 idle의 "시작"을 눌러 409가 난다.
// 순수 시각은 실 브라우저 게이트 — 여기선 요청 횟수와 상태 반영 배선만 잰다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null,
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
    graph: GRAPH, garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
    quotes: [], emailVerified: true,
};

// 다음 /api/dashboard 응답 — 테스트가 갈아끼워 "다른 기기에서 바뀐 서버 상태"를 흉내낸다.
let dashboardPayload: Record<string, unknown> = DASHBOARD;
let startStatus = 200;

function fetchImpl(url: string) {
    if (url.includes('/api/sessions/start')) {
        return Promise.resolve({ ok: startStatus === 200, status: startStatus, json: async () => ({ ...DASHBOARD }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ searchEnabled: false, books: [] }) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...dashboardPayload }) });
}
const fetchMock = () => fetch as unknown as { mock: { calls: unknown[][] } };
const dashboardCalls = () => fetchMock().mock.calls.filter(c => String(c[0]).includes('/api/dashboard')).length;

beforeEach(() => {
    dashboardPayload = DASHBOARD;
    startStatus = 200;
    vi.stubGlobal('fetch', vi.fn((u: string) => fetchImpl(u)));
    // jsdom 기본값에 기대지 않는다 — 복귀 가드가 보는 값을 명시적으로 고정.
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); document.body.innerHTML = ''; });

async function mountDashboard() {
    const wrapper = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));
    return wrapper;
}

describe('DashboardApp — 복귀 재조회', () => {
    test('60초 안의 복귀는 스로틀되고, 60초가 지나면 재조회한다(focus도 같은 스로틀을 공유)', async () => {
        vi.useFakeTimers();
        await mountDashboard();
        expect(dashboardCalls()).toBe(1);

        document.dispatchEvent(new Event('visibilitychange'));
        await flushPromises();
        expect(dashboardCalls()).toBe(1);

        vi.advanceTimersByTime(60_000);
        document.dispatchEvent(new Event('visibilitychange'));
        await flushPromises();
        expect(dashboardCalls()).toBe(2);

        window.dispatchEvent(new Event('focus'));
        await flushPromises();
        expect(dashboardCalls()).toBe(2);
    });

    test('복귀 재조회가 실어 온 상태가 화면에 반영된다(다른 기기에서 시작 → 측정 중 패널)', async () => {
        vi.useFakeTimers();
        const wrapper = await mountDashboard();
        expect(wrapper.find('.dash-pill-pulse').exists()).toBe(false);

        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        vi.advanceTimersByTime(60_000);
        document.dispatchEvent(new Event('visibilitychange'));
        await flushPromises();
        await flushPromises();

        expect(wrapper.find('.dash-pill-pulse').exists()).toBe(true);
    });

    test('탭이 보이지 않으면 재조회하지 않는다', async () => {
        vi.useFakeTimers();
        await mountDashboard();
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });

        vi.advanceTimersByTime(60_000);
        document.dispatchEvent(new Event('visibilitychange'));
        await flushPromises();
        expect(dashboardCalls()).toBe(1);
    });
});

describe('DashboardApp — 409는 "내 화면이 낡았다"는 신호', () => {
    test('독서 시작 409 → 문구 + 즉시 강제 재조회(스로틀 무시)', async () => {
        startStatus = 409;
        const wrapper = await mountDashboard();
        expect(dashboardCalls()).toBe(1);

        // 다른 기기에서 이미 측정 중 — 재조회가 그 원장을 실어 온다.
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const startBtn = wrapper.findAll('button').find(b => b.text().includes('책 없이'))!;
        await startBtn.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.alert-error').exists()).toBe(true));

        expect(wrapper.find('.alert-error').text()).toContain('다른 곳에서 이미 측정 중');
        // 마운트 1 + 강제 재조회 1 — 마운트 직후라 스로틀 창 안인데도 돌았다.
        expect(dashboardCalls()).toBe(2);
        await vi.waitFor(() => expect(wrapper.find('.dash-pill-pulse').exists()).toBe(true));
    });
});
