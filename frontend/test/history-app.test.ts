// @vitest-environment jsdom
// HistoryApp 동작 위주 테스트 — fetch mock으로 API 호출 검증.
// 브리틀한 정확 색상/문자열 단언은 피함; 동작·클래스 존재·상태를 확인한다.
// CSS .tab-panel 가시성(라디오 :checked ~ .panel CSS 셀렉터)은 실 브라우저 게이트로 별도 검증(N-083/T-053).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import HistoryApp from '../src/history/HistoryApp.vue';
import MonthlyRecords from '../src/history/MonthlyRecords.vue';

const MOCK_GRAPH = {
    weeks: [
        [
            { date: null, totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-15', totalSeconds: 3600, level: 2, manual: false },
            { date: '2026-06-16', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-17', totalSeconds: 1800, level: 1, manual: true },
            { date: '2026-06-18', totalSeconds: 7200, level: 4, manual: false },
            { date: '2026-06-19', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-20', totalSeconds: 0, level: 0, manual: false },
        ],
    ],
    monthLabels: [{ weekIndex: 0, label: '6월' }],
    totalSeconds: 12600,
    activeDays: 3,
    currentStreak: 1,
};

const MOCK_MONTHS = [
    {
        month: '2026-06',
        totalSeconds: 7200,
        days: [
            { date: '2026-06-20', totalSeconds: 3600, books: [{ title: '클린 코드', coverUrl: null, seconds: 3600 }], manuallyFilled: false },
            { date: '2026-06-19', totalSeconds: 3600, books: [], manuallyFilled: false },
        ],
    },
    {
        month: '2026-05',
        totalSeconds: 3600,
        days: [
            { date: '2026-05-31', totalSeconds: 3600, books: [{ title: '리팩터링', coverUrl: null, seconds: 3600 }], manuallyFilled: false },
        ],
    },
];

const MOCK_RESPONSE = {
    nickname: '테스터',
    months: MOCK_MONTHS,
    graph: MOCK_GRAPH,
    weeklyShortfall: [
        { date: '2026-06-18', debtSeconds: 3000 },
    ],
};

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ ...MOCK_RESPONSE }),
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    document.body.className = '';            // 컴포넌트가 토글한 history-wide 잔재 제거
    window.innerWidth = 1024;                // jsdom 기본값 복구(테스트 간 폭 누수 방지)
});

describe('HistoryApp', () => {
    test('마운트 시 /api/history 를 호출한다', async () => {
        mount(HistoryApp, { attachTo: document.body });
        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
        expect(url).toContain('/api/history');
    });

    test('잔디 셀 렌더: .grass-cell 클래스와 level-N/empty/manual 클래스가 함께 생성된다', async () => {
        const wrapper = mount(HistoryApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.grass-cell').exists()).toBe(true));

        // placeholder → empty
        const emptyCell = wrapper.findAll('.grass-cell').find(c => c.classes('empty'));
        expect(emptyCell).toBeDefined();

        // level-2 셀 존재
        const level2 = wrapper.findAll('.grass-cell').find(c => c.classes('level-2'));
        expect(level2).toBeDefined();

        // manual 셀 존재 (level-1 manual)
        const manualCell = wrapper.findAll('.grass-cell').find(c => c.classes('manual'));
        expect(manualCell).toBeDefined();
        expect(manualCell?.classes()).toContain('level-1');
    });
});

describe('MonthlyRecords 월 네비 경계', () => {
    test('초기 monthIndex = 0 (최신 달)', () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        // 최신달 레이블이 보인다
        expect(wrapper.text()).toContain('2026년 6월');
    });

    // 서버가 제목 목록 대신 책 목록(제목·표지·초)을 보낸다 — 웹은 제목만 쓰므로 그 꺼내는 길이 끊기면 조용히 빈칸이 된다.
    test('일자 행에 그날 읽은 책 제목을 적는다', () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        expect(wrapper.text()).toContain('클린 코드');
    });

    test('prev(◀) 클릭 → 과거 달로 이동 (monthIndex++)', async () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        const prev = wrapper.find('.month-nav-prev');
        await prev.trigger('click');
        expect(wrapper.text()).toContain('2026년 5월');
    });

    test('next(▶) 클릭 → 최신 달로 돌아옴 (monthIndex--)', async () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        const prev = wrapper.find('.month-nav-prev');
        const next = wrapper.find('.month-nav-next');
        await prev.trigger('click');           // 5월로
        await next.trigger('click');           // 6월로
        expect(wrapper.text()).toContain('2026년 6월');
    });

    test('최신 달(index=0)에서 next(▶)는 disabled', () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        const next = wrapper.find('.month-nav-next');
        expect((next.element as HTMLButtonElement).disabled).toBe(true);
    });

    test('가장 오래된 달에서 prev(◀)는 disabled', async () => {
        const wrapper = mount(MonthlyRecords, { props: { months: MOCK_MONTHS } });
        const prev = wrapper.find('.month-nav-prev');
        await prev.trigger('click'); // 5월(마지막)로
        expect((prev.element as HTMLButtonElement).disabled).toBe(true);
    });

    test('단 1개 달이면 prev·next 모두 disabled', () => {
        const wrapper = mount(MonthlyRecords, { props: { months: [MOCK_MONTHS[0]] } });
        const prev = wrapper.find('.month-nav-prev');
        const next = wrapper.find('.month-nav-next');
        expect((prev.element as HTMLButtonElement).disabled).toBe(true);
        expect((next.element as HTMLButtonElement).disabled).toBe(true);
    });

    test('좁은 폭(stacked): pill 탭 2개 + 클릭 시 active 전환', async () => {
        window.innerWidth = 500;                              // < SPLIT_MIN_WIDTH → stacked
        const wrapper = mount(HistoryApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.hist-tab').exists()).toBe(true));

        const tabs = wrapper.findAll('.hist-tab');
        expect(tabs).toHaveLength(2);
        // split 컨테이너는 없어야(stacked)
        expect(wrapper.find('.hist-split').exists()).toBe(false);

        // 초기: 첫 탭(일자별) active
        expect(tabs[0].classes()).toContain('active');
        expect(tabs[1].classes()).not.toContain('active');

        // 둘째 탭(빠뜨린날) 클릭 → active 이동
        await tabs[1].trigger('click');
        expect(tabs[0].classes()).not.toContain('active');
        expect(tabs[1].classes()).toContain('active');
    });

    test('넓은 폭(split): 탭 없이 2단(일자별/빠뜨린날) 나란히', async () => {
        window.innerWidth = 1200;                             // >= SPLIT_MIN_WIDTH → split
        const wrapper = mount(HistoryApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.hist-split').exists()).toBe(true));

        // split이면 pill 탭이 없다
        expect(wrapper.find('.hist-tab').exists()).toBe(false);
        // 두 패널(일자별 MonthlyRecords·빠뜨린날) 동시 렌더
        expect(wrapper.findAll('.hist-pane')).toHaveLength(2);
        // body에 history-wide(컨테이너 확장 트리거) 부착
        expect(document.body.classList.contains('history-wide')).toBe(true);
    });
});
