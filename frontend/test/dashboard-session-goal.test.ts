// @vitest-environment jsdom
// DashboardApp — 공부 책별 「회당 시간」을 히어로에서 바로 정한다(2026-09-13 컨셉 전환, 하루 목표 대체).
//
// 계측기 메모
//  · 통과가 확정하는 것: 요청이 POST /api/study/books/{칩 책 id}/session-goal로 나가고 body 키가
//    sessionGoalSeconds, 값이 **초**(45분 → 2700)이며, 응답 StudyState의 새 값이 손잡이로 돌아온다.
//    서버가 아직 보내는 하루 목표(goalSeconds)는 읽지도 부르지도 않는다. 독서 모드엔 이 문·이 UI가 없다.
//  · 실패가 배제하는 것: 분 그대로 보내기(45) · 옛 body 키(dailyGoalSeconds)·옛 문(/api/study/goal)으로 새기 ·
//    id 누락(/books/undefined/) · 실패(400)를 성공처럼 닫기 · 회당 UI가 독서 히어로로 새기.
//
// fetch 스텁은 URL별 명시 분기이고 **미지 URL은 throw**한다 — 폴백 관용구로 우연히 통과하지 않게.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-09-05', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const BOOK = (id: number, title: string, sessionGoalSeconds: number | null) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0,
    sessionGoalSeconds,
});
// goalSeconds 3600 — 서버는 PR-5 전까지 하루 목표를 계속 보낸다. 화면이 그걸 안 그린다는 것을 함께 잰다.
const STUDY_IDLE = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 900, goalSeconds: 3600,
    activeBook: null, recentBookId: 5, books: [BOOK(5, '헌법', 3000)], untaggedSessionId: null,
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester',
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
    graph: GRAPH,
    quotes: [], emailVerified: true, study: STUDY_IDLE,
};

let goalStatus = 200;
const goalReqs: { url: string; body: string }[] = [];

function fetchImpl(url: string, init?: RequestInit) {
    if (url.includes('/session-goal')) {
        goalReqs.push({ url, body: String(init?.body ?? '') });
        const seconds = (JSON.parse(String(init?.body ?? '{}')) as { sessionGoalSeconds: number | null }).sessionGoalSeconds;
        return Promise.resolve({
            ok: goalStatus < 400, status: goalStatus, statusText: 'x',
            json: async () => ({ ...STUDY_IDLE, books: [BOOK(5, '헌법', seconds)] }),
        });
    }
    // 홈 여백 카드가 「지금 그 책」의 글을 부른다(2026-09-07) — 0건이어도 카드는 뜬다.
    if (url.includes('/api/stories/of/')) {
        return Promise.resolve({
            ok: true, status: 200,
            json: async () => ({ book: { id: 1, title: '지금 책', author: null, coverUrl: null },
                                 ownerNickname: '테스터', self: true, entries: [] }),
        });
    }
    if (url.includes('/api/study/history')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ graph: GRAPH, months: [] }) });
    }
    if (url.includes('/api/dashboard')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
    }
    throw new Error('unexpected fetch: ' + url);
}
const urls = () => (fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls.map(c => String(c[0]));
const countOf = (needle: string) => urls().filter(u => u.includes(needle)).length;

beforeEach(() => {
    goalStatus = 200;
    goalReqs.length = 0;
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn((u: string, i?: RequestInit) => fetchImpl(u, i)));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); document.body.innerHTML = ''; });

async function mountDashboard() {
    const w = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
    return w;
}
const modeBtn = (w: ReturnType<typeof mount>, label: string) =>
    w.findAll('.dash-mode-toggle button').find(b => b.text() === label)!;
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('button').find(b => b.text().includes(text));

async function saveMinutes(w: ReturnType<typeof mount>, minutes: number) {
    await btnWith(w, '변경')!.trigger('click');
    await w.find('form.dash-goal-edit input').setValue(minutes);
    await w.find('form.dash-goal-edit').trigger('submit');
}

describe('DashboardApp — 공부 회당 시간', () => {
    test('(a) 「변경」 → 45분 저장이 칩 책 문으로 초가 되어 나가고, 응답이 손잡이로 돌아온다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        // 서버가 하루 목표 3600을 보내도 공부 히어로엔 게이지·「하루 목표」가 없다.
        expect(w.find('.dash-progress-track').exists()).toBe(false);
        expect(w.text()).not.toContain('하루 목표');
        expect(w.find('.dash-session-goal').text()).toContain('회당 50분');

        await saveMinutes(w, 45);
        await vi.waitFor(() => expect(goalReqs).toHaveLength(1));

        expect(goalReqs[0].url).toBe('/api/study/books/5/session-goal');
        expect(JSON.parse(goalReqs[0].body)).toEqual({ sessionGoalSeconds: 2700 });
        expect(countOf('/api/study/goal')).toBe(0);

        await vi.waitFor(() => expect(w.find('.dash-session-goal').text()).toContain('회당 45분'));
        // 폼을 닫는 것은 **응답을 본 뒤**다(카드가 emit 직후 스스로 닫지 않는다).
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    test('(b) 400이면 오류를 말하고, 폼은 사용자가 친 값과 함께 열려 있다', async () => {
        goalStatus = 400;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        await saveMinutes(w, 45);
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));

        expect(w.find('.alert-error').text()).toContain('회당 시간');
        expect(w.find('form.dash-goal-edit').exists()).toBe(true);
        expect((w.find('form.dash-goal-edit input').element as HTMLInputElement).value).toBe('45');
        // 실패한 값이 손잡이에 반영되지 않았다(서버 진실 = 50분 그대로).
        expect(w.find('.dash-session-goal').text()).toContain('회당 50분');
    });

    test('(c) 독서 모드엔 회당 시간 문·UI가 없다 — 공부 문도 두드리지 않는다', async () => {
        const w = await mountDashboard();

        expect(w.findAll('.dash-session-goal, .dash-goal-change, .dash-goal-edit')).toHaveLength(0);
        expect(w.text()).not.toContain('회당');
        expect(countOf('/session-goal')).toBe(0);
        // 양성 대조: 독서 히어로엔 (독서 원장의) 게이지가 이미 있다 — 「없음」이 렌더 실패가 아니다.
        expect(w.find('.dash-progress-track').exists()).toBe(true);
    });
});
