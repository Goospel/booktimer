// @vitest-environment jsdom
// DashboardApp — 독서/공부 모드 토글과 공부 원장 배선.
// 서버 진실(진행 중 측정)이 저장값을 이기고, 측정 중에는 토글이 잠긴다(왜 못 바꾸는지 말할 기회는 남긴다).
// 색·위치·겹침은 jsdom이 레이아웃을 안 하므로 실 브라우저 게이트(설계 §6 U3~U5)가 본다.
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
const STUDY_ACTIVE = { hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z', todaySeconds: 60, goalSeconds: 3600 };

let dashboardPayload: Record<string, unknown> = DASHBOARD;
let studyStartStatus = 200;
let studyStartResponse: Record<string, unknown> = STUDY_ACTIVE;
// 왕복을 붙잡아 두는 게이트 — "응답 대기 중" 상태를 관측하려면 응답을 늦출 수 있어야 한다.
let studyStartGate: Promise<void> | null = null;

function fetchImpl(url: string) {
    if (url.includes('/api/study/start')) {
        const res = { ok: studyStartStatus === 200, status: studyStartStatus, json: async () => studyStartResponse };
        return studyStartGate ? studyStartGate.then(() => res) : Promise.resolve(res);
    }
    if (url.includes('/api/study/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ hasActiveSession: false, activeStartedAt: null, todaySeconds: 120, goalSeconds: 3600 }) });
    }
    if (url.includes('/api/sessions/start')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ searchEnabled: false, books: [] }) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...dashboardPayload }) });
}
const fetchMock = () => fetch as unknown as { mock: { calls: unknown[][] } };
const urls = () => fetchMock().mock.calls.map(c => String(c[0]));
const dashboardCalls = () => urls().filter(u => u.includes('/api/dashboard')).length;

beforeEach(() => {
    dashboardPayload = DASHBOARD;
    studyStartStatus = 200;
    studyStartResponse = STUDY_ACTIVE;
    studyStartGate = null;
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn((u: string) => fetchImpl(u)));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); localStorage.clear(); document.body.innerHTML = ''; });

async function mountDashboard() {
    const wrapper = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));
    return wrapper;
}
const modeBtn = (w: ReturnType<typeof mount>, label: string) =>
    w.findAll('.dash-mode-toggle button').find(b => b.text() === label)!;
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('button').find(b => b.text().includes(text));

describe('DashboardApp — 모드 토글', () => {
    test('(a) 응답에 study가 없으면 독서 모드로 열리고 토글이 선다(옛 서버·옛 픽스처 폴백)', async () => {
        const w = await mountDashboard();
        expect(w.find('.dash-timer-hero').classes()).not.toContain('is-study');
        expect(w.find('.dash-mode-toggle').exists()).toBe(true);
        expect(modeBtn(w, '독서').attributes('aria-pressed')).toBe('true');
        expect(modeBtn(w, '공부').attributes('aria-pressed')).toBe('false');
    });

    test('(b) 공부로 바꾸면 저장되고, 시작·종료가 공부 원장으로만 간다', async () => {
        // 시작 응답의 activeStartedAt이 65초 전 — 카드가 props 변화를 따라가야만 01:05가 나온다
        // (props→ref watch가 없으면 경과가 0에 굳어 00:00).
        studyStartResponse = { ...STUDY_ACTIVE, activeStartedAt: new Date(Date.now() - 65_200).toISOString() };
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(localStorage.getItem('booktimer.timerMode')).toBe('study');

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await flushPromises();

        // 양성 + 음성 쌍 — 공부 원장에만 닿고 독서 원장은 건드리지 않는다.
        expect(urls().some(u => u.includes('/api/study/start'))).toBe(true);
        expect(urls().some(u => u.includes('/api/sessions/start'))).toBe(false);
        await vi.waitFor(() => expect(btnWith(w, '측정 종료')).toBeTruthy());
        expect(w.find('.dash-session-time').text()).toBe('01:05');

        await btnWith(w, '측정 종료')!.trigger('click');
        await flushPromises();

        expect(urls().some(u => u.includes('/api/study/stop'))).toBe(true);
        expect(urls().some(u => u.includes('/api/sessions/stop'))).toBe(false);
        await vi.waitFor(() => expect(btnWith(w, '공부 측정 시작')).toBeTruthy());
    });

    test('(h) 저장값이 study면 진행 중 측정이 없어도 공부 모드로 열린다(E14 새로고침)', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        const w = await mountDashboard();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(btnWith(w, '공부 측정 시작')).toBeTruthy();
    });

    test('(i) 시작 응답을 기다리는 동안에도 토글이 잠긴다(반대 카드가 남의 "시작하는 중…"을 쓰지 않게)', async () => {
        let release: () => void = () => { };
        studyStartGate = new Promise<void>(r => { release = r; });
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await flushPromises();

        // 아직 measuring은 false다(응답 전) — 잠금이 starting까지 보지 않으면 여기서 열려 있다.
        expect(modeBtn(w, '독서').attributes('aria-disabled')).toBe('true');
        release();
        await flushPromises();
    });

    test('(c) 공부 측정 중이면 저장값과 무관하게 공부 모드로 잠긴다(힌트로 이유를 말한다)', async () => {
        dashboardPayload = { ...DASHBOARD, study: STUDY_ACTIVE };
        const w = await mountDashboard();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(btnWith(w, '측정 종료')).toBeTruthy();
        expect(modeBtn(w, '독서').attributes('aria-disabled')).toBe('true');

        await modeBtn(w, '독서').trigger('click');
        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-mode-hint').text()).toContain('측정을 끝내면 바꿀 수 있어요');
    });

    test('(d) 독서 측정 중이면 저장값이 study여도 독서 모드다(서버 진실이 이긴다)', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();

        expect(w.find('.dash-timer-hero').classes()).not.toContain('is-study');
        expect(w.find('.dash-pill-pulse').exists()).toBe(true);
    });

    test('(e) 공부 시작 409 → 문구 + 강제 재조회로 화면이 진행 중 원장을 따라잡는다', async () => {
        studyStartStatus = 409;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        expect(dashboardCalls()).toBe(1);

        // 다른 기기에서 이미 공부 중 — 재조회가 그 원장을 실어 온다.
        dashboardPayload = { ...DASHBOARD, study: STUDY_ACTIVE };
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));

        expect(w.find('.alert-error').text()).toContain('다른 곳에서 이미 측정 중');
        expect(dashboardCalls()).toBe(2);
        await vi.waitFor(() => expect(btnWith(w, '측정 종료')).toBeTruthy());
    });

    test('(g) 복귀 재조회가 실어 온 공부 진행이 화면에 반영된다(다른 기기에서 시작)', async () => {
        vi.useFakeTimers();
        const w = await mountDashboard();
        expect(w.find('.dash-timer-hero').classes()).not.toContain('is-study');

        dashboardPayload = { ...DASHBOARD, study: STUDY_ACTIVE };
        vi.advanceTimersByTime(60_000);
        document.dispatchEvent(new Event('visibilitychange'));
        await flushPromises();
        await flushPromises();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(btnWith(w, '측정 종료')).toBeTruthy();
    });
});
