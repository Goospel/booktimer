// @vitest-environment jsdom
// DashboardApp — 공부 하루 목표를 히어로에서 바로 고친다(설계 §2.3-ⓑ). 설정 페이지·미니앱 문은 쓰지 않는다.
//
// 계측기 메모
//  · 통과가 확정하는 것: 요청이 POST /api/study/goal로 나가고 body가 **초**(30분 → 1800)이며,
//    응답의 goalSeconds가 게이지로 돌아온다. 독서 모드에선 이 문·이 UI가 존재하지 않는다.
//  · 실패가 배제하는 것: 분을 그대로 보내기(30) · 독서 목표 문(/api/miniapp/goal)이나 SSR /settings로 새기 ·
//    실패(400)를 성공처럼 그리기 · 목표 UI가 독서 히어로로 새기.
//
// fetch 스텁은 URL별 명시 분기이고 **미지 URL은 throw**한다 — 폴백 관용구로 우연히 통과하지 않게.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-09-05', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const STUDY_IDLE = { hasActiveSession: false, activeStartedAt: null, todaySeconds: 900, goalSeconds: 0 };
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null,
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
    graph: GRAPH, garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
    quotes: [], emailVerified: true, study: STUDY_IDLE,
};

let goalOk = true;
// true면 goal 응답에서 todaySeconds까지 뺀다 — 정규화(studyStateOf) 계측기 (d)용.
let goalPartial = false;
const goalBodies: string[] = [];

function fetchImpl(url: string, init?: RequestInit) {
    if (url.includes('/api/study/goal')) {
        goalBodies.push(String(init?.body ?? ''));
        return Promise.resolve({
            ok: goalOk, status: goalOk ? 200 : 400, statusText: 'bad',
            json: async () => (goalPartial ? { goalSeconds: 1800 } : { ...STUDY_IDLE, goalSeconds: 1800 }),
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
    goalOk = true;
    goalPartial = false;
    goalBodies.length = 0;
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

describe('DashboardApp — 공부 하루 목표', () => {
    test('(a) 「하루 목표 정하기」 → 30분 저장이 초로 나가고, 응답이 게이지가 된다', async () => {
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        expect(w.find('.dash-progress-track').exists()).toBe(false);
        await btnWith(w, '하루 목표 정하기')!.trigger('click');
        await w.find('form.dash-goal-edit input').setValue(30);
        await w.find('form.dash-goal-edit').trigger('submit');
        await vi.waitFor(() => expect(goalBodies).toHaveLength(1));

        expect(JSON.parse(goalBodies[0])).toEqual({ dailyGoalSeconds: 1800 });
        expect(countOf('/api/study/goal')).toBe(1);
        // 독서 목표 문·SSR 설정 폼으로 새지 않는다.
        expect(countOf('/api/miniapp/goal')).toBe(0);
        expect(countOf('/settings')).toBe(0);

        await vi.waitFor(() => expect(w.find('.dash-progress-track').exists()).toBe(true));
        expect(w.find('.dash-progress-meta').text()).toContain('하루 목표 30분');
        // 폼을 닫는 것은 **응답을 본 뒤**다(카드가 emit 직후 스스로 닫지 않는다).
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    test('(b) 400이면 오류를 말하고 게이지는 생기지 않는다', async () => {
        goalOk = false;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        await btnWith(w, '하루 목표 정하기')!.trigger('click');
        await w.find('form.dash-goal-edit input').setValue(30);
        await w.find('form.dash-goal-edit').trigger('submit');
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));

        expect(w.find('.alert-error').text()).toContain('목표');
        expect(w.find('.dash-progress-track').exists()).toBe(false);
        // 실패했으니 폼은 열린 채 **사용자가 친 30이 남는다** — 닫으면 그 입력이 사라지고
        // 다시 열었을 때 옛 goalSeconds로 프리필된다(리뷰 반영 2026-09-05).
        expect(w.find('form.dash-goal-edit').exists()).toBe(true);
        expect((w.find('form.dash-goal-edit input').element as HTMLInputElement).value).toBe('30');
    });

    test('(d) todaySeconds가 빠진 goal 응답도 게이지가 0%로 선다 — 정규화(studyStateOf)', async () => {
        // 날것 res.json()을 대입하면 todaySeconds가 undefined라 pct가 NaN%가 되어 width가 사라진다.
        goalPartial = true;
        const w = await mountDashboard();
        await modeBtn(w, '공부').trigger('click');

        await btnWith(w, '하루 목표 정하기')!.trigger('click');
        await w.find('form.dash-goal-edit input').setValue(30);
        await w.find('form.dash-goal-edit').trigger('submit');
        await vi.waitFor(() => expect(w.find('.dash-progress-track').exists()).toBe(true));

        expect(w.find('.dash-progress-fill').attributes('style')).toContain('width: 0%');
        expect(w.find('.alert-error').exists()).toBe(false);
    });

    test('(c) 독서 모드엔 목표 편집 문이 없다 — 공부 문도 두드리지 않는다', async () => {
        const w = await mountDashboard();

        expect(w.findAll('.dash-goal-set, .dash-goal-change, .dash-goal-edit')).toHaveLength(0);
        expect(btnWith(w, '하루 목표 정하기')).toBeUndefined();
        expect(countOf('/api/study/goal')).toBe(0);
        // 양성 대조: 독서 히어로엔 (독서 원장의) 게이지가 이미 있다 — 「없음」이 렌더 실패가 아니다.
        expect(w.find('.dash-progress-track').exists()).toBe(true);
    });
});
