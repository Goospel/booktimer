// @vitest-environment jsdom
// DashboardApp 동작 테스트 — 측정 종료 시 잔디(contribution graph)가 새로고침 없이 즉시 갱신되는지.
// 핵심 회귀 가드: stop 응답에 동봉된 graph로 ContributionGraph(연속일·셀)가 reactive하게 다시 그려져야 한다.
// (종료 응답이 타이머만 주던 시절엔 잔디가 stale로 남아 페이지 새로고침을 해야 색이 진해졌다.)
// 순수 시각(색/위치)은 jsdom 무의미 → 실 브라우저 게이트. 여기선 데이터 갱신 배선만.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH_INITIAL = {
    weeks: [[
        { date: null, totalSeconds: 0, level: 0, manual: false },
        { date: '2026-06-25', totalSeconds: 0, level: 0, manual: false },
    ]],
    monthLabels: [{ weekIndex: 0, label: '6월' }],
    totalSeconds: 0,
    activeDays: 0,
    currentStreak: 1,
    growthStageName: 'SPROUT',
    growthStageEmoji: '🌱',
    growthStageLabel: '새싹',
};

// 측정 종료 후 — 방금 확정된 세션이 반영돼 연속일·활동일·총시간이 올라간 잔디.
const GRAPH_AFTER_STOP = { ...GRAPH_INITIAL, currentStreak: 5, activeDays: 1, totalSeconds: 1800 };

// 초기 /api/dashboard — 측정 "중" 상태로 시작(종료 버튼이 떠야 하므로 hasActiveSession=true).
const DASHBOARD_RESPONSE = {
    nickname: '테스터',
    loginId: 'tester',
    remainingSeconds: 1200,
    carriedDebtSeconds: 0,
    todayGoalSeconds: 3600,
    carryover: true,
    hasActiveSession: true,
    activeStartedAt: '2026-06-26T08:00:00Z',
    activeBookTitle: '데미안',
    activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }],
    finishedBooks: [],
    recentBookId: 1,
    graph: GRAPH_INITIAL,
    garden: {
        ownedAuthorCharacterCount: 0,
        totalAuthorCharacterCount: 0,
        ownedCharacters: [],
        ownedBuildingCount: 0,
        totalBuildingCount: 0,
    },
    quotes: [],
    emailVerified: true,
};

// POST /api/sessions/stop — 중첩 구조 { timer, graph }. 타이머는 종료 반영, graph는 갱신된 잔디.
const STOP_RESPONSE = {
    timer: {
        remainingSeconds: 0,
        carriedDebtSeconds: 0,
        todayGoalSeconds: 3600,
        carryover: true,
        hasActiveSession: false,
        activeStartedAt: null,
        activeBookTitle: null,
        activeBookTotalSeconds: 0,
        readingBooks: [{ id: 1, title: '데미안' }],
        finishedBooks: [],
        recentBookId: 1,
    },
    graph: GRAPH_AFTER_STOP,
};

function fetchImpl(url: string) {
    if (url.includes('/api/sessions/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => STOP_RESPONSE });
    }
    // 기본: /api/dashboard
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD_RESPONSE }) });
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url: string) => fetchImpl(url)));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('DashboardApp', () => {
    test('마운트 시 /api/dashboard 를 호출한다', async () => {
        mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
        expect(url).toContain('/api/dashboard');
    });

    test('측정 종료 → 잔디(연속일)가 stop 응답으로 즉시 갱신된다 (새로고침 불필요)', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });

        // 초기 로드 — 연속일 칩이 1
        await vi.waitFor(() => expect(wrapper.find('.dash-streak-chip').exists()).toBe(true));
        expect(wrapper.find('.dash-streak-chip strong').text()).toBe('1');

        // 측정 종료 버튼 클릭
        const stopBtn = wrapper.findAll('button').find(b => b.text().includes('측정 종료'));
        expect(stopBtn).toBeTruthy();
        await stopBtn!.trigger('click');

        // stop 호출 확인
        await vi.waitFor(() => {
            const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls.map(c => c[0] as string);
            expect(calls.some(u => u.includes('/api/sessions/stop'))).toBe(true);
        });

        // 잔디가 새 graph로 갱신 — 연속일 5로 (현재는 graph 미갱신이라 RED)
        await vi.waitFor(() => expect(wrapper.find('.dash-streak-chip strong').text()).toBe('5'));
    });
});
