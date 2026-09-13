// @vitest-environment jsdom
// 공부 기록 화면(/study/history) — /history의 조각(ContributionGraph·MonthlyRecords)을 import 재사용하되
// 문구·범례·빈 상태가 공부 원장의 것으로 바뀌는지 잰다. 음성 단언(독서 문구 부재)은 전부 양성과 쌍이다 —
// "없다"만 세면 컴포넌트가 통째로 안 그려져도 초록이 된다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyHistoryApp from '../src/study/StudyHistoryApp.vue';

// 서버 StudyHistoryService — 잔디 농도는 고정 절대 눈금(4h)이고 manual은 항상 false(공부엔 직접 채움이 없다).
const STUDY_GRAPH = {
    weeks: [
        [
            { date: null, totalSeconds: 0, level: 0, manual: false },
            { date: '2026-09-01', totalSeconds: 3600, level: 2, manual: false },
            { date: '2026-09-02', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-09-03', totalSeconds: 5400, level: 3, manual: false },
            { date: '2026-09-04', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-09-05', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-09-06', totalSeconds: 0, level: 0, manual: false },
        ],
    ],
    monthLabels: [{ weekIndex: 0, label: '9월' }],
    totalSeconds: 9000,
    activeDays: 2,
    currentStreak: 1,
};

// books 키가 아예 없다 — 공부 원장엔 책이 없다(MonthlyRecords가 그 부재에 안 죽는지가 이 픽스처의 몫).
const STUDY_MONTHS = [
    { month: '2026-09', totalSeconds: 5400, days: [{ date: '2026-09-03', totalSeconds: 5400 }] },
];

function stubFetch(body: unknown, ok = true) {
    const f = vi.fn().mockResolvedValue({ ok, json: async () => body });
    vi.stubGlobal('fetch', f);
    return f;
}

beforeEach(() => {
    stubFetch({ graph: STUDY_GRAPH, months: STUDY_MONTHS });
});

afterEach(() => {
    vi.unstubAllGlobals();
});

async function mountApp() {
    const wrapper = mount(StudyHistoryApp);
    await new Promise((r) => setTimeout(r, 0));
    await wrapper.vm.$nextTick();
    return wrapper;
}

describe('StudyHistoryApp', () => {
    test('공부 원장만 부른다 — /api/study/history 하나, 독서 /api/history는 안 부른다', async () => {
        await mountApp();
        const urls = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.map((c) => String(c[0]));
        expect(urls.some((u) => u.includes('/api/study/history'))).toBe(true);
        expect(urls.some((u) => u.includes('/api/history'))).toBe(false);
    });

    test('잔디 문구가 공부의 것이다 — 「공부 잔디」·「적게…많이」, 독서의 「직접 채움」 범례는 없다', async () => {
        const wrapper = await mountApp();
        const text = wrapper.text();

        expect(wrapper.findAll('h2').map((h) => h.text())).toContain('공부 잔디');
        expect(text).toContain('적게');
        expect(text).toContain('많이');
        expect(text).not.toContain('직접 채움');
        expect(text).not.toContain('목표 달성');
    });

    test('잔디 그리드 조각을 그대로 재사용한다 (level-N 셀)', async () => {
        const wrapper = await mountApp();
        const cells = wrapper.findAll('.grass-cell');
        expect(cells.length).toBeGreaterThan(0);
        expect(cells.some((c) => c.classes('level-2'))).toBe(true);
        expect(cells.some((c) => c.classes('manual'))).toBe(false);
    });

    test('공부 잉크 — 카드 두 장에 is-study가 붙는다 (토큰 스코프의 유일한 손잡이)', async () => {
        const wrapper = await mountApp();
        expect(wrapper.findAll('.card.is-study').length).toBe(2);
    });

    test('월별 목록 — books 없는 날도 그린다 (제목 줄은 생략)', async () => {
        const wrapper = await mountApp();
        expect(wrapper.text()).toContain('2026-09-03');
        expect(wrapper.findAll('.record-books').length).toBe(0);
    });

    test('연속 뱃지가 「연속 공부」다 (독서 문구가 아니다)', async () => {
        const wrapper = await mountApp();
        expect(wrapper.text()).toContain('연속 공부');
        expect(wrapper.text()).not.toContain('연속 독서');
        expect(wrapper.text()).toContain('일 공부');
    });

    test('빈 상태 문구가 공부의 것이다', async () => {
        stubFetch({ graph: STUDY_GRAPH, months: [] });
        const wrapper = await mountApp();
        expect(wrapper.text()).toContain('아직 공부 기록이 없어요');
        expect(wrapper.text()).not.toContain('아직 독서 기록이 없습니다');
    });

    test('하단 네비는 공부 세계 안에서만 돈다 — 홈 · 일정 · 공부 서재', async () => {
        const wrapper = await mountApp();
        expect(wrapper.findAll('.link-row a').map((a) => a.attributes('href')))
            .toEqual(['/', '/study', '/study/books']);
    });

    test('응답 실패 → 실패 문구', async () => {
        stubFetch({}, false);
        const wrapper = await mountApp();
        expect(wrapper.text()).toContain('데이터를 불러오지 못했습니다');
    });
});
