// @vitest-environment jsdom
// DashboardApp — 모드가 「쓰는 카드」·타일까지 끌고 간다(설계 §2.2). 히어로만 바뀌던 옛 상태의 뒷절반.
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
    nickname: '테스터', loginId: 'tester',
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
    graph: GRAPH,
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

/** 공부 서재 3권 — 기본 책 판별용으로 첫 책(7)·최근 책(11)·측정 중인 책(9)이 전부 다르다. */
const studyBook = (id: number, title: string) =>
    ({ id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 });
const STUDY_BOOKS = [studyBook(7, '정보처리기사 실기'), studyBook(9, '토익 보카'), studyBook(11, '헌법')];
const STUDY_IDLE_WITH_BOOKS = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 0,
    activeBook: null, recentBookId: 11, books: STUDY_BOOKS, untaggedSessionId: null,
};

let dashboardPayload: Record<string, unknown> = DASHBOARD;
let notesOk = true;
// /api/dashboard 응답을 붙잡아 두는 게이트 — 「응답 전엔 body 클래스를 건드리지 않는다」를 관측하려면 늦출 수 있어야 한다.
let dashboardGate: Promise<void> | null = null;

function fetchImpl(url: string) {
    // 미지 URL 폴백에 기대지 않는다 — 모드마다 새로 생기는 경로는 명시 분기로 잡아 우연 통과를 막는다.
    if (url.includes('/api/study/agenda')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => AGENDA, text: async () => '' });
    }
    if (url.includes('/api/study/notes?')) {
        return Promise.resolve({
            ok: notesOk, status: notesOk ? 200 : 500, statusText: 'err',
            json: async () => ({ notes: [] }), text: async () => '',
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
    const res = { ok: true, status: 200, json: async () => ({ ...dashboardPayload }) };
    return dashboardGate ? dashboardGate.then(() => res) : Promise.resolve(res);
}
const urls = () => (fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls.map(c => String(c[0]));
const agendaCalls = () => urls().filter(u => u.includes('/api/study/agenda')).length;
const notesListCalls = () => urls().filter(u => u.includes('/api/study/notes?'));

beforeEach(() => {
    dashboardPayload = DASHBOARD;
    notesOk = true;
    dashboardGate = null;
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

describe('DashboardApp — 모드가 쓰는 카드를 끌고 간다', () => {
    test('(a) 독서 기본: 여백 카드가 서고 서재 패널은 없으며 공부 원장을 부르지 않는다', async () => {
        const w = await mountDashboard();
        await flushPromises();

        // 서재 캐릭터 폐기 제거 가드 — 독서 모드(패널이 있던 유일한 자리)에도 더는 없다.
        expect(w.find('.dash-garden').exists()).toBe(false);
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');
        expect(w.find('.dash-recall-card').exists()).toBe(false);
        expect(agendaCalls()).toBe(0);
    });

    // 픽스처는 공부 서재 0권(DASHBOARD에 study 없음) — 필기 카드는 편집기 대신 「공부 서재 열기」를 그린다.
    test('(b) 공부로 바꾸면 필기 카드로 갈린다 — 서재 0권', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        // 홈은 더 이상 오늘 일정을 부르지 않는다 — 필기 책은 부모가 아는 값(2026-09-15). 옛 잔디 왕복도 없다.
        expect(agendaCalls()).toBe(0);
        expect(urls().filter(u => u.includes('/api/study/history'))).toHaveLength(0);
        expect(w.find('.dash-recall-card').classes()).toContain('is-study');
        // 클래스만이 아니라 실제로 그 패널을 그렸다 — 서재 0권 안내가 있어야 「필기를 그렸다」다.
        expect(w.find('[data-testid="notes-no-books"]').exists()).toBe(true);
        expect(w.find('.dash-margin-card').exists()).toBe(false);
        // 공부 기록으로 가는 문은 홈 본문에 **없다** — 빠른 이동 타일은 SSR 양옆 바(fragments/side-rails)로 옮겼다(2026-09-15).
        // 양성 대조군 — 같은 마운트·같은 셀렉터 꼴로 공부 카드 안의 실제 링크는 잡힌다: 2 = 타이머 카드 「공부 서재에 책 담기」
        // + 필기 카드 「공부 서재 열기」(둘 다 서재 0권이라 뜬다). 이게 없으면 아래 0은 「링크를 못 찾는 조회」와 구분되지 않는다.
        expect(w.findAll('a[href="/study/books"]')).toHaveLength(2);
        expect(w.findAll('a[href="/study/history"]')).toHaveLength(0);
        expect(w.findAll('a[href="/history"]')).toHaveLength(0);
    });

    // 칩 기본 책 규칙과 같은 책 — 첫 책(7)이 아닌 최근 책(11)이라 「그냥 첫 책」과 갈린다.
    test('(b2) 대기 중 필기 책 = 칩 기본 책(최근 걸고 잰 책)이고, 편집기가 뜬다', async () => {
        dashboardPayload = { ...DASHBOARD, study: STUDY_IDLE_WITH_BOOKS };
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        await vi.waitFor(() => expect(w.find('[data-testid="notes-book"]').exists()).toBe(true));

        expect((w.find('[data-testid="notes-book"]').element as HTMLSelectElement).value).toBe('11');
        expect(w.find('[data-testid="recall-body"]').exists()).toBe(true);
        expect(w.find('[data-testid="recall-book"]').exists()).toBe(false);
        expect(agendaCalls()).toBe(0);
        // 양성 대조 — 같은 fetch 목이 필기 목록 왕복은 잡는다.
        expect(notesListCalls()).toEqual(['/api/study/notes?bookId=11']);
    });

    // 측정 중인 책이 곧 필기할 책이다 — 최근 책(11)도 첫 책(7)도 아닌 9.
    test('(b3) 측정 중 필기 책 = 지금 공부하는 책', async () => {
        dashboardPayload = {
            ...DASHBOARD,
            study: { ...STUDY_IDLE_WITH_BOOKS, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z', activeBook: STUDY_BOOKS[1] },
        };
        const w = await mountDashboard();
        await flushPromises();
        await vi.waitFor(() => expect(w.find('[data-testid="notes-book"]').exists()).toBe(true));

        expect((w.find('[data-testid="notes-book"]').element as HTMLSelectElement).value).toBe('9');
        expect(agendaCalls()).toBe(0);
    });

    test('(c) 저장값이 study면 마운트하자마자 필기 카드다 — 독서를 한 번 그렸다 넘어가지 않는다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        const w = await mountDashboard();

        expect(w.find('.dash-margin-card').exists()).toBe(false);
        await flushPromises();
        expect(w.find('.dash-recall-card').exists()).toBe(true);
        expect(agendaCalls()).toBe(0);
    });

    test('(d) 독서로 돌아오면 여백 카드가 복귀한다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(w.find('.dash-recall-card').exists()).toBe(true);

        await modeBtn(w, '독서').trigger('click');
        await flushPromises();
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');
        expect(w.find('.dash-recall-card').exists()).toBe(false);
    });

    // 옛 (e)는 agenda 실패였다 — 홈이 agenda를 안 부르게 되면서(2026-09-15) 홈 카드가 부르는 유일한 왕복인 필기 목록으로 옮겼다.
    test('(e) 필기 목록을 못 받으면 자리 문구로 알리되 모드를 되돌리지 않는다', async () => {
        notesOk = false;
        dashboardPayload = { ...DASHBOARD, study: STUDY_IDLE_WITH_BOOKS };
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(w.text()).toContain('불러오지 못했');
        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
    });

    test('(f) 독서 stop으로 히어로가 공부로 넘어가면 카드도 함께 넘어간다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        const w = await mountDashboard();
        await flushPromises();
        // 서버 진실이 이긴다 — 독서 측정 중엔 독서 화면이다.
        expect(w.find('.dash-margin-card .dash-pill').text()).toBe('여백');

        await btnWith(w, '측정 종료')!.trigger('click');
        await flushPromises();

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-recall-card .dash-pill').text()).toBe('필기');
        expect(w.find('.dash-margin-card').exists()).toBe(false);
    });
});

// 세로 바(SSR fragments/side-rails)의 모드(한쪽 메뉴만 보임)는 #side-rails[data-mode] 한 속성이다 — 홈에선 이 섬이 쓴다.
// 설계 claude-docs/plans/2026-09-15-web-side-rails.md §3-5 · §7 T-6.
describe('DashboardApp — 모드가 세로 바 모드(#side-rails[data-mode])를 끌고 간다', () => {
    const railMode = () => document.getElementById('side-rails')!.getAttribute('data-mode');
    beforeEach(() => { document.body.innerHTML = '<div id="side-rails" data-mode="reading"></div>'; });

    test('(g) 공부 토글 → study, 독서로 돌아오면 → reading', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();
        expect(railMode()).toBe('study');

        await modeBtn(w, '독서').trigger('click');
        await flushPromises();
        expect(railMode()).toBe('reading');
    });

    test('(h) 저장값 study로 마운트 → 마운트 직후 study', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        await mountDashboard();
        expect(railMode()).toBe('study');
    });

    test('(i) 서버 독서 진행 중 + 저장 study → reading(서버 진실이 이긴다)', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        document.getElementById('side-rails')!.setAttribute('data-mode', 'study'); // 인라인 부트가 저장값으로 흐린 상태
        dashboardPayload = { ...DASHBOARD, hasActiveSession: true, activeStartedAt: '2026-09-04T00:00:00Z' };
        await mountDashboard();
        await flushPromises();
        expect(railMode()).toBe('reading');
    });
});

// 공부 측정 중 홈 = 타이머와 필기가 한 장(.focus-stack.is-merged) + 독서등(body.study-lamp) — 설계 2026-09-15-study-focus-lamp §2-4·§2-5.
// 색·전환·레이아웃은 jsdom이 못 잰다(실 브라우저 원장 U-1~U-11). 여기선 「언제 켜고 끄는가」와 첫 페인트 힌트의 수명을 잰다.
//
// 계측기 메모
//  · 통과가 확정하는 것: 시작 → 합침·등·힌트, 종료 → 전부 해제 · 독서 모드엔 스택이 없다 · 응답 전엔 부트가 붙인 클래스를 그대로 둔다.
//  · 실패가 배제하는 것: 마운트 직후 기본값(IDLE_STUDY)으로 힌트를 지워 새로고침마다 낮→밤 깜빡임 · 종료 뒤 등이 눌어붙음.
describe('DashboardApp — 공부 집중(합침 + 독서등)', () => {
    const LAMP = 'study-lamp';
    const KEY = 'booktimer.studyLamp';
    beforeEach(() => {
        document.body.innerHTML = '<div id="side-rails" data-mode="reading"></div>';
        document.body.className = '';
    });
    afterEach(() => { document.body.className = ''; });

    test('(l1) 공부 대기: 스택은 있고 합침·등·힌트는 없다 → 시작하면 셋 다 켜지고 → 종료하면 셋 다 꺼진다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');
        await flushPromises();

        expect(w.find('.focus-stack.lamp-page').exists()).toBe(true);
        expect(w.find('.focus-stack').classes()).not.toContain('is-merged');
        expect(document.body.classList.contains(LAMP)).toBe(false);
        expect(localStorage.getItem(KEY)).toBeNull();

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').classes()).toContain('is-merged'));
        expect(document.body.classList.contains(LAMP)).toBe(true);
        expect(localStorage.getItem(KEY)).toBe('1');

        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').classes()).not.toContain('is-merged'));
        expect(document.body.classList.contains(LAMP)).toBe(false);
        expect(localStorage.getItem(KEY)).toBeNull();
    });

    test('(l2) 독서 모드엔 스택이 없다', async () => {
        const w = await mountDashboard();
        await flushPromises();
        expect(w.find('.focus-stack').exists()).toBe(false);
        expect(w.find('.dash-margin-card').exists()).toBe(true);   // 양성 대조 — 독서 화면을 실제로 그렸다
    });

    test('(l3) 응답 전엔 부트가 붙인 등을 건드리지 않고, 응답(대기)을 본 뒤에 끈다', async () => {
        let release: () => void = () => { };
        dashboardGate = new Promise<void>(r => { release = r; });
        localStorage.setItem('booktimer.timerMode', 'study');
        localStorage.setItem(KEY, '1');
        document.body.classList.add(LAMP);   // 인라인 부트가 첫 페인트 전에 붙인 상태

        const w = mount(DashboardApp, { attachTo: document.body });
        await flushPromises();
        expect(w.find('.status-line').text()).toContain('불러오는 중');   // 아직 응답 전이다
        expect(document.body.classList.contains(LAMP)).toBe(true);
        expect(localStorage.getItem(KEY)).toBe('1');

        release();
        await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
        await flushPromises();
        expect(document.body.classList.contains(LAMP)).toBe(false);
        expect(localStorage.getItem(KEY)).toBeNull();
    });

    test('(l4) 새로고침(측정 중 응답)이면 전환 없이 곧장 합쳐진 한 장이다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dashboardPayload = { ...DASHBOARD, study: STUDY_ACTIVE };
        const w = await mountDashboard();
        await flushPromises();

        expect(w.find('.focus-stack').classes()).toContain('is-merged');
        expect(document.body.classList.contains(LAMP)).toBe(true);
    });
});
