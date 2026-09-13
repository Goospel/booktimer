// @vitest-environment jsdom
// DashboardApp 동작 테스트 — 측정 종료 시 stop 응답의 graph가 새로고침 없이 화면에 닿는지.
// 핵심 회귀 가드: 종료 응답에 동봉된 graph로 연속일이 reactive하게 다시 그려져야 한다.
// (종료 응답이 타이머만 주던 시절엔 stale로 남아 페이지 새로고침을 해야 값이 올라갔다.)
// 2026-09-07에 홈 잔디를 걷으면서 이 가드의 관측 지점이 잔디 칸 → 여백 카드의 연속일 칩으로 옮겼다 —
// graph를 화면까지 나르는 배선은 그대로라 회귀 가드는 살아 있다.
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
    quotes: [],
    emailVerified: true,
};

// 여백 카드가 「지금 그 책」의 글을 부른다 — 0건이어도 카드는 뜬다.
const MARGIN_RESPONSE = {
    book: { id: 1, title: '데미안', author: '헤세', coverUrl: null },
    ownerNickname: '테스터',
    self: true,
    entries: [],
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
    if (url.includes('/api/stories/of/')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => MARGIN_RESPONSE });
    }
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

    test('측정 종료 → 연속일이 stop 응답으로 즉시 갱신된다 (새로고침 불필요)', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });

        // 초기 로드 — 연속일 칩이 1
        await vi.waitFor(() => expect(wrapper.find('.dash-margin-card .dash-streak-chip').exists()).toBe(true));
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

    // 발견 2: 상단 4겹 정리 — 렌더 순서를 헤더 → 타이머 → (잔디 자리) → 바로가기 순으로.
    // 2026-09-07: 그 자리를 여백 카드가 잇는다. 자리(순서)는 규칙이고 내용물만 바뀌었다.
    test('렌더 순서: 타이머 → 여백 → 바로가기 (발견 2)', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));
        const html = wrapper.html();
        const pos = (s: string) => html.indexOf(s);
        expect(pos('dash-timer-hero')).toBeLessThan(pos('dash-margin-card'));
        expect(pos('dash-margin-card')).toBeLessThan(pos('dash-grid-2col'));
    });

    // 걷어낸 것을 「없다」로만 재면 컴포넌트를 통째로 안 그려도 통과한다 — 그래서 그 자리에 무엇이
    // 섰는지를 같은 테스트에서 함께 단언한다(여백 카드 = 양성 대조군).
    test('홈에 잔디가 없다 — 대신 여백 카드가 그 자리에 선다', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-margin-card').exists()).toBe(true));
        expect(wrapper.find('.dash-grass-grid').exists()).toBe(false);
        expect(wrapper.find('.dash-grass-card').exists()).toBe(false);
    });
});
