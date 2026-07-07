// @vitest-environment jsdom
// 가입완료 환영 배너 (§6.4, 설계 잔여분 3번). 온보딩 완료 후 첫 대시보드 진입에서 1회 환영한다.
// 신호: 서버 플래시(RedirectAttributes) → 셸 data-just-onboarded → main.ts가 읽어 DashboardApp에 prop 주입.
// 순수 시각(배너 톤)은 실 브라우저 게이트.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import WelcomeBanner from '../src/dashboard/WelcomeBanner.vue';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
    growthStageName: 'SPROUT', growthStageEmoji: '🌱', growthStageLabel: '새싹',
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null,
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [], finishedBooks: [], wantToReadBooks: [], recentBookId: null,
    graph: GRAPH, garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
    quotes: [], emailVerified: true,
};
function fetchImpl(url: string) {
    if (url.includes('/api/dashboard')) return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
    if (url.includes('/api/stories/feed')) return Promise.resolve({ ok: true, status: 200, json: async () => ({ mine: null, groups: [] }) });
    return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
}
beforeEach(() => { vi.stubGlobal('fetch', vi.fn((u: string) => fetchImpl(u))); });
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

describe('WelcomeBanner (§6.4)', () => {
    test('닉네임을 넣어 환영 문구를 보여주고 ✕는 close를 emit한다', async () => {
        const w = mount(WelcomeBanner, { props: { nickname: '테스터' } });
        expect(w.text()).toContain('테스터');
        expect(w.text()).toContain('환영');
        await w.find('.welcome-banner-close').trigger('click');
        expect(w.emitted('close')).toBeTruthy();
    });
});

describe('DashboardApp — 가입완료 환영 배너 (§6.4)', () => {
    async function mountApp(props: Record<string, unknown> = {}) {
        const w = mount(DashboardApp, { props, attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
        await flushPromises();
        return w;
    }

    test('justOnboarded=true → 환영 배너를 보여준다', async () => {
        const w = await mountApp({ justOnboarded: true });
        expect(w.find('.welcome-banner').exists()).toBe(true);
        expect(w.find('.welcome-banner').text()).toContain('테스터'); // data.nickname
    });

    test('justOnboarded 미지정 → 환영 배너가 없다 (일반 로드)', async () => {
        const w = await mountApp();
        expect(w.find('.welcome-banner').exists()).toBe(false);
    });

    test('환영 배너 ✕ → 사라진다', async () => {
        const w = await mountApp({ justOnboarded: true });
        expect(w.find('.welcome-banner').exists()).toBe(true);
        await w.find('.welcome-banner-close').trigger('click');
        expect(w.find('.welcome-banner').exists()).toBe(false);
    });
});
