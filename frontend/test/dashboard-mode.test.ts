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
    nickname: '테스터', loginId: 'tester',
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
    graph: GRAPH,
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
        expect(w.find('[data-testid="focus-session"] .dash-kv-v').text()).toBe('01:05');

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

    // 공부 측정 중(독서등)엔 스위치를 그리지 않는다 — 밤 화면엔 합쳐진 카드만 남긴다(2026-09-15 결정 6).
    // 독서 측정 중의 잠금·힌트는 (l).
    test('(c) 공부 측정 중이면 저장값과 무관하게 공부 모드이고 토글이 없다 — 종료하면 토글이 돌아온다', async () => {
        dashboardPayload = { ...DASHBOARD, study: STUDY_ACTIVE };
        const w = await mountDashboard();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(btnWith(w, '측정 종료')).toBeTruthy();
        expect(w.find('.dash-mode-toggle').exists()).toBe(false);

        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.dash-mode-toggle').exists()).toBe(true));
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

    // (j) 스위치는 카드 밖 — 히어로보다 DOM 앞이고 한 개뿐이다(Tab 순서 = 헤더 → 스위치 → 타이머). 두 모드 모두.
    test('(j) 스위치는 카드 밖에 한 개 — 히어로보다 DOM 앞', async () => {
        const w = await mountDashboard();
        const before = () => {
            expect(w.findAll('.dash-mode-toggle')).toHaveLength(1);
            // FOLLOWING(4) 정확히 — CONTAINS(8)|PRECEDING(2)=10이면 아직 카드 안이다
            expect(w.find('.dash-mode-toggle').element.compareDocumentPosition(w.find('.dash-timer-hero').element))
                .toBe(Node.DOCUMENT_POSITION_FOLLOWING);
        };
        before();
        await modeBtn(w, '공부').trigger('click');
        before();
    });

    // (k) 카드 밖에서 공부 잉크를 받는 유일한 경로 — wrap의 is-study(카드 스코프 토큰 스왑 관용구)
    test('(k) 공부 모드면 스위치 wrap에 is-study, 독서면 없다', async () => {
        const w = await mountDashboard();
        expect(w.find('.dash-mode-toggle-wrap').classes()).not.toContain('is-study');
        await modeBtn(w, '공부').trigger('click');
        expect(w.find('.dash-mode-toggle-wrap').classes()).toContain('is-study');
        await modeBtn(w, '독서').trigger('click');
        expect(w.find('.dash-mode-toggle-wrap').classes()).not.toContain('is-study');
    });

    test('(l) 독서 측정 중: 스위치는 남고 잠기며, 누르면 힌트가 role=status로 뜬다 — 회귀 가드(옮기기 전에도 GREEN)', async () => {
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        expect(modeBtn(w, '공부').attributes('aria-disabled')).toBe('true');
        expect(w.find('.dash-mode-hint').exists()).toBe(false);          // 음성 대조 — 누르기 전엔 없다
        await modeBtn(w, '공부').trigger('click');
        expect(w.find('.dash-timer-hero').classes()).not.toContain('is-study');   // 모드 안 바뀜
        expect(w.find('.dash-mode-hint[role="status"]').text()).toBe('측정을 끝내면 바꿀 수 있어요');
    });

    // 비홈 스위치는 측정 상태를 모른다 — 다른 화면에서 반대 모드를 눌러 왔는데 서버 진실이 되돌렸으면
    // 홈이 로드 때 이유를 한 번 말한다(설계 2026-09-17 D2 ③).
    test('(m) 저장값 study + 독서 측정 중으로 열리면 누르지 않아도 힌트가 뜨고 독서가 눌려 있다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        await flushPromises();
        expect(w.find('.dash-mode-hint[role="status"]').text()).toBe('측정을 끝내면 바꿀 수 있어요');
        expect(modeBtn(w, '독서').attributes('aria-pressed')).toBe('true');
    });

    test('(n) 음성 대조 — 저장값 reading + 독서 측정 중이면 불일치가 없어 힌트가 없다', async () => {
        localStorage.setItem('booktimer.timerMode', 'reading');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        await flushPromises();
        expect(w.find('.dash-mode-hint').exists()).toBe(false);
    });

    test('(o) 음성 대조 — 저장값 study + 측정 없음이면 저장값과 열린 모드가 같아 힌트가 없다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        const w = await mountDashboard();
        await flushPromises();
        expect(w.find('.dash-mode-hint').exists()).toBe(false);
    });
});
