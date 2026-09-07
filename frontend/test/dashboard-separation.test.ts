// @vitest-environment jsdom
// DashboardApp — 모드가 「쓰는 카드」·타일·정원까지 끌고 간다(설계 §2.2). 히어로만 바뀌던 옛 상태의 뒷절반.
//
// 2026-09-07에 잔디를 걷으면서 이 파일의 첫째 축이 잔디 → **여백/백지복습 카드**로 바뀌었다. 축만 바뀌고
// 재는 것은 같다: 「모드가 화면 전체를 끌고 가는가」. 옛 (b)~(e)에 있던 공부 잔디 fetch 수명(프리페치·
// 캐시·stop 뒤 재조회)은 그 왕복 자체가 사라져 함께 걷었다 — 없어진 동작에 대한 테스트는 계측기가 아니라
// 화석이다. 「그 왕복을 정말 안 쏘는가」는 아래 (b)와 dashboard-study-book.test.ts (e)가 함께 잰다.
//
// 「is-study가 붙었다」 같은 불리언만 보면 「공부 화면을 그렸다」와 「독서 화면에 파란 클래스만 붙였다」가
// 같은 값을 낸다 — 그래서 카드는 클래스가 아니라 **안에 뭐가 들었는지**(pill 문구·입력칸)로 판별한다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-09-03', totalSeconds: 0, level: 0, manual: false }]],
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

const AGENDA = {
    today: '2026-09-07', aiAccess: 'NONE', aiAccessAt: null, aiEnabled: false,
    remaining: { plan: 1, transcribe: 0, analyze: 0 }, items: [], recalls: [],
};
const MARGIN = {
    book: { id: 1, title: '데미안', author: '헤세', coverUrl: null },
    ownerNickname: '테스터', self: true, entries: [],
};

let dashboardPayload: Record<string, unknown> = DASHBOARD;
let agendaOk = true;

function fetchImpl(url: string) {
    // 미지 URL 폴백에 기대지 않는다 — 모드마다 새로 생기는 경로는 명시 분기로 잡아 우연 통과를 막는다.
    if (url.includes('/api/study/agenda')) {
        return Promise.resolve({
            ok: agendaOk, status: agendaOk ? 200 : 500, statusText: 'err',
            json: async () => AGENDA, text: async () => '',
        });
    }
    if (url.includes('/api/study/recall')) {
        return Promise.resolve({ ok: false, status: 404, json: async () => ({}), text: async () => '' });
    }
    if (url.includes('/api/stories/of/')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => MARGIN });
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
const agendaCalls = () => urls().filter(u => u.includes('/api/study/agenda')).length;

beforeEach(() => {
    dashboardPayload = DASHBOARD;
    agendaOk = true;
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

describe('DashboardApp — 모드가 쓰는 카드·타일·정원을 끌고 간다', () => {
    test('(a) 독서 기본: 정원이 있고 여백 카드가 서며 공부 원장을 부르지 않는다', async () => {
        const w = await mountDashboard();
        await flushPromises();

        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');
        expect(w.find('.dash-recall-card').exists()).toBe(false);
        expect(tileHrefs(w)).toEqual(['/books', '/u/tester', '/personality']);
        expect(agendaCalls()).toBe(0);
    });

    test('(b) 공부로 바꾸면 백지복습 카드로 갈리고, 정원이 사라지고, 타일이 공부 세트가 된다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(agendaCalls()).toBe(1);
        // 공부 모드로 들어가도 옛 잔디 왕복은 없다 — 백지복습 1건이 그 자리를 대신한다(왕복 수 동일).
        expect(urls().filter(u => u.includes('/api/study/history'))).toHaveLength(0);
        expect(w.find('.dash-recall-card').classes()).toContain('is-study');
        expect(w.find('.dash-nav').classes()).toContain('is-study');
        // 클래스만이 아니라 실제로 그 패널을 그렸다 — 본문 칸이 있어야 「백지복습을 그렸다」다.
        expect(w.find('[data-testid="recall-body"]').exists()).toBe(true);
        expect(w.find('.dash-margin-card').exists()).toBe(false);
        expect(w.find('.dash-garden').exists()).toBe(false);
        // 공부 기록으로 가는 문은 **타일 하나뿐**이다 — 카드 머리의 중복 링크는 걷었다(2026-09-07).
        // 이 1이 recall-card 쪽 「카드엔 없다」의 양성 대조군이다(둘 다 사라지면 여기가 죽는다).
        expect(w.findAll('a[href="/study/history"]')).toHaveLength(1);
        expect(w.findAll('a[href="/history"]')).toHaveLength(0);
        expect(tileHrefs(w)).toEqual(['/study/books', '/study', '/study/history']);
    });

    test('(c) 저장값이 study면 마운트하자마자 백지복습이다 — 독서를 한 번 그렸다 넘어가지 않는다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        const w = await mountDashboard();

        expect(w.find('.dash-margin-card').exists()).toBe(false);
        await flushPromises();
        expect(w.find('.dash-recall-card').exists()).toBe(true);
        expect(agendaCalls()).toBe(1);
    });

    test('(d) 독서로 돌아오면 정원·여백 카드가 함께 복귀한다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(w.find('.dash-recall-card').exists()).toBe(true);

        await modeBtn(w, '독서').trigger('click');
        await flushPromises();
        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');
        expect(w.find('.dash-recall-card').exists()).toBe(false);
    });

    test('(e) 공부 일정을 못 받으면 자리 문구로 알리되 모드를 되돌리지 않는다', async () => {
        agendaOk = false;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(w.text()).toContain('불러오지 못했');
        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-garden').exists()).toBe(false);
    });

    test('(f) 독서 stop으로 히어로가 공부로 넘어가면 카드·정원도 함께 넘어간다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        await flushPromises();
        // 서버 진실이 이긴다 — 독서 측정 중엔 독서 화면이다.
        expect(w.find('.dash-garden').exists()).toBe(true);
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');

        await btnWith(w, '측정 종료')!.trigger('click');
        await flushPromises();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-recall-card .dash-pill').text()).toBe('백지복습');
        expect(w.find('.dash-margin-card').exists()).toBe(false);
        expect(w.find('.dash-garden').exists()).toBe(false);
        expect(tileHrefs(w)).toEqual(['/study/books', '/study', '/study/history']);
    });
});
