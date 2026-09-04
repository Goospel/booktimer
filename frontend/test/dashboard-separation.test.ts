// @vitest-environment jsdom
// DashboardApp — 모드가 잔디·타일·정원까지 끌고 간다(설계 §2.2). 히어로만 바뀌던 옛 상태의 뒷절반.
//
// 픽스처 두 벌의 level이 서로 다르다(독서 0 / 공부 3) — 어느 응답을 그렸는지가 셀 클래스로 판별된다.
// 「is-study가 붙었다」 같은 불리언만 보면 「공부 응답을 그렸다」와 「독서 응답에 파란 클래스만 붙였다」가
// 같은 값을 낸다. 공부 잔디 주는 2개다: weeks[0]=최신 주(왼쪽)라는 서버 규약을 첫 셀로 밟는다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-09-03', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
// level 3 → .s4. 둘째 주(level 0 → .s1)와 달라서 순서가 뒤집히면 첫 셀 단언이 죽는다.
const STUDY_GRAPH = {
    weeks: [
        [{ date: '2026-09-04', totalSeconds: 10800, level: 3, manual: false }],
        [{ date: '2026-08-28', totalSeconds: 0, level: 0, manual: false }],
    ],
    monthLabels: [], totalSeconds: 10800, activeDays: 1, currentStreak: 1,
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
let studyHistoryOk = true;

function fetchImpl(url: string) {
    // 미지 URL 폴백에 기대지 않는다 — 공부 잔디 경로는 명시 분기로 잡아 우연 통과를 막는다.
    if (url.includes('/api/study/history')) {
        return Promise.resolve({
            ok: studyHistoryOk, status: studyHistoryOk ? 200 : 500, statusText: 'err',
            json: async () => ({ graph: STUDY_GRAPH, months: [] }),
        });
    }
    if (url.includes('/api/study/start')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => STUDY_ACTIVE });
    }
    if (url.includes('/api/study/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ hasActiveSession: false, activeStartedAt: null, todaySeconds: 120, goalSeconds: 3600 }) });
    }
    if (url.includes('/api/sessions/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ sessionId: 7, untagged: false, timer: { ...DASHBOARD, hasActiveSession: false, activeStartedAt: null }, graph: GRAPH }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ searchEnabled: false, books: [] }) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...dashboardPayload }) });
}
const urls = () => (fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls.map(c => String(c[0]));
const studyHistoryCalls = () => urls().filter(u => u.includes('/api/study/history')).length;

beforeEach(() => {
    dashboardPayload = DASHBOARD;
    studyHistoryOk = true;
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn((u: string) => fetchImpl(u)));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); document.body.innerHTML = ''; });

async function mountDashboard() {
    const wrapper = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));
    return wrapper;
}
const modeBtn = (w: ReturnType<typeof mount>, label: string) =>
    w.findAll('.dash-mode-toggle button').find(b => b.text() === label)!;
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('button').find(b => b.text().includes(text));
const tileHrefs = (w: ReturnType<typeof mount>) => w.findAll('.dash-nav-tile').map(a => a.attributes('href'));

describe('DashboardApp — 모드가 잔디·타일·정원을 끌고 간다', () => {
    test('(a) 독서 기본: 정원이 있고 잔디는 독서이며 공부 원장을 부르지 않는다', async () => {
        const w = await mountDashboard();

        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-grass-card .dash-pill').text()).toBe('독서 기록');
        expect(w.find('.dash-grass-card').classes()).not.toContain('is-study');
        expect(tileHrefs(w)).toEqual(['/books', '/u/tester', '/personality']);
        expect(studyHistoryCalls()).toBe(0);
    });

    test('(b) 공부로 바꾸면 공부 잔디를 받아 그리고, 정원이 사라지고, 타일이 공부 세트가 된다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(studyHistoryCalls()).toBe(1);
        expect(w.find('.dash-grass-card').classes()).toContain('is-study');
        expect(w.find('.dash-nav').classes()).toContain('is-study');
        // 공부 응답을 실제로 그렸다 — 독서 픽스처는 level 0(.s1)뿐이라 .s4가 나올 수 없다.
        expect(w.findAll('.dash-grass-grid .dash-grass-cell')[0].classes()).toContain('s4');
        expect(w.find('.dash-garden').exists()).toBe(false);
        // 잔디 링크 + 타일 = 2개. 독서 기록으로 가는 문은 남지 않는다.
        expect(w.findAll('a[href="/study/history"]')).toHaveLength(2);
        expect(w.findAll('a[href="/history"]')).toHaveLength(0);
        expect(tileHrefs(w)).toEqual(['/study', '/study/history']);
    });

    test('(c) 저장값이 study면 마운트와 동시에 공부 잔디를 프리페치한다(재로드 대기 0)', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        const w = await mountDashboard();

        expect(studyHistoryCalls()).toBe(1);
        await flushPromises();
        expect(w.find('.dash-grass-card').classes()).toContain('is-study');
    });

    test('(d) 모드를 오가도 재요청하지 않는다(페이지 수명 캐시) — 독서로 돌아오면 정원·독서 잔디 복귀', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(studyHistoryCalls()).toBe(1);

        await modeBtn(w, '독서').trigger('click');
        await flushPromises();
        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-grass-card .dash-pill').text()).toBe('독서 기록');
        expect(studyHistoryCalls()).toBe(1);

        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(studyHistoryCalls()).toBe(1);
    });

    test('(e) 공부 측정 종료 뒤 잔디를 다시 받는다(잔디가 변하는 순간)', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(studyHistoryCalls()).toBe(1);

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await flushPromises();
        await vi.waitFor(() => expect(btnWith(w, '측정 종료')).toBeTruthy());
        await btnWith(w, '측정 종료')!.trigger('click');
        await flushPromises();

        expect(studyHistoryCalls()).toBe(2);
    });

    test('(f) 공부 잔디 실패는 자리 문구로 알리되 모드를 되돌리지 않는다', async () => {
        studyHistoryOk = false;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(w.text()).toContain('공부 기록을 불러오지 못했어요');
        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-garden').exists()).toBe(false);
    });

    test('(g) 독서 stop으로 히어로가 공부로 넘어가면 잔디·정원도 함께 넘어간다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        // 서버 진실이 이긴다 — 독서 측정 중엔 독서 화면이다.
        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-grass-card .dash-pill').text()).toBe('독서 기록');

        await btnWith(w, '측정 종료')!.trigger('click');
        await flushPromises();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-grass-card .dash-pill').text()).toBe('공부 기록');
        expect(w.find('.dash-garden').exists()).toBe(false);
        expect(tileHrefs(w)).toEqual(['/study', '/study/history']);
    });
});
