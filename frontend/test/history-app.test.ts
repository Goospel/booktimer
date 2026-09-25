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

// ── R2 PR-3 — 날짜를 펼치면 측정 한 건씩, 그 줄에서 책을 붙이거나 바꾼다 ──────────────────────
// 계측기 메모
//  · 통과가 확정하는 것: sessions가 있는 날만 펼침 버튼이 서고, 줄마다 시각(수동은 「직접 기록」)·시간(60초 미만은
//    「1분 미만」)·제목(없으면 「책 없음」)·버튼(책 없으면 「책 붙이기」, 있으면 「바꾸기」)을 그린다. 누른 줄의
//    **id로** POST /api/sessions/{id}/book에 X-CSRF-TOKEN을 달아 보내고 /api/history를 다시 받는다.
//  · 실패가 배제하는 것: 옛 응답(sessions 없음)에서 빈 펼침 · 수동 기록에 거짓 시각(00:00) · 줄 뒤섞임(다른 id로 전송) ·
//    수동 기록에 「책 없이 두기」(서버 409) · 실패를 삼키고 시트 닫기.
const S_BLANK = { id: 11, start: '07:40', end: '08:20', seconds: 2400, bookId: null, bookTitle: null, manual: false };
const S_SHORT = { id: 12, start: '21:00', end: '21:00', seconds: 30, bookId: 3, bookTitle: '클린 코드', manual: false };
const S_MANUAL = { id: 13, start: null, end: null, seconds: 1800, bookId: 3, bookTitle: '클린 코드', manual: true };

const SESSION_MONTHS = [
    {
        month: '2026-06',
        totalSeconds: 4230,
        days: [
            { date: '2026-06-20', totalSeconds: 4230, books: [{ title: '클린 코드', coverUrl: null, seconds: 1830 }],
              manuallyFilled: true, sessions: [S_BLANK, S_SHORT, S_MANUAL] },
            // 옛 응답 모양 — sessions 키가 없다(서버 배포 전 캐시·다른 경로). 펼침이 없어야 한다.
            { date: '2026-06-19', totalSeconds: 3600, books: [], manuallyFilled: false },
        ],
    },
];

async function expand(wrapper: ReturnType<typeof mount>) {
    await wrapper.find('.record-toggle').trigger('click');
    return wrapper.findAll('.record-session');
}

describe('MonthlyRecords — 측정 한 건 줄 (R2 PR-3)', () => {
    test('sessions 있는 날만 펼침 버튼(aria-expanded)이 서고, 없는 날은 옛 모양 그대로', () => {
        const w = mount(MonthlyRecords, { props: { months: SESSION_MONTHS } });
        const toggles = w.findAll('.record-toggle');
        expect(toggles).toHaveLength(1);
        // 클래스만 세면 없는 날도 <button>(눌러도 아무 일 없는 포커스 가능 버튼)으로 그려지는 회귀를 놓친다.
        expect(w.findAll('.record-head').map((h) => h.element.tagName)).toEqual(['BUTTON', 'DIV']);
        expect(toggles[0].attributes('aria-expanded')).toBe('false');
        expect(toggles[0].text()).toContain('2026-06-20');
        expect(w.text()).toContain('2026-06-19');           // 펼침이 없는 날도 그린다(양성)
        expect(w.findAll('.record-session')).toHaveLength(0); // 접힌 채로 시작
    });

    test('펼치면 줄마다 시각·시간·제목·버튼 — 서버가 준 순서 그대로', async () => {
        const w = mount(MonthlyRecords, { props: { months: SESSION_MONTHS } });
        const rows = await expand(w);
        expect(w.find('.record-toggle').attributes('aria-expanded')).toBe('true');
        expect(rows).toHaveLength(3);

        expect(rows[0].text()).toContain('07:40–08:20');
        expect(rows[0].text()).toContain('40분');
        expect(rows[0].text()).toContain('책 없음');
        expect(rows[0].find('button').text()).toBe('책 붙이기');

        expect(rows[1].text()).toContain('1분 미만');
        expect(rows[1].text()).toContain('클린 코드');
        expect(rows[1].find('button').text()).toBe('바꾸기');
    });

    test('수동 기록 줄은 시각 대신 「직접 기록」 — 서버 앵커 시각을 찍지 않는다', async () => {
        const w = mount(MonthlyRecords, { props: { months: SESSION_MONTHS } });
        const rows = await expand(w);
        expect(rows[2].text()).toContain('직접 기록');
        expect(rows[2].text()).toContain('30분');
        expect(rows[2].text()).not.toContain('null');
        expect(rows[2].text()).not.toContain('–');
    });

    test('줄 버튼은 **그 줄**을 assign으로 올린다', async () => {
        const w = mount(MonthlyRecords, { props: { months: SESSION_MONTHS } });
        const rows = await expand(w);
        await rows[1].find('button').trigger('click');
        await rows[0].find('button').trigger('click');
        expect(w.emitted('assign')).toEqual([[S_SHORT], [S_BLANK]]);
    });

    test('다시 누르면 접힌다', async () => {
        const w = mount(MonthlyRecords, { props: { months: SESSION_MONTHS } });
        await expand(w);
        await w.find('.record-toggle').trigger('click');
        expect(w.find('.record-toggle').attributes('aria-expanded')).toBe('false');
        expect(w.findAll('.record-session')).toHaveLength(0);
    });
});

describe('HistoryApp — 기록에서 책 붙이기·바꾸기 (R2 PR-3)', () => {
    const SHELF_BOOKS = {
        searchEnabled: false,
        books: [{ id: 5, title: '데미안', author: null, coverUrl: null, isbn13: null, status: 'READING', statusLabel: '읽는 중' }],
    };
    let posts: { url: string; body: unknown; headers: Record<string, string> }[] = [];
    let postOk = true;
    let postHold: Promise<void> | null = null; // 있으면 POST 응답을 그때까지 붙잡는다(경주 재현용)

    function route(url: string, opts?: { method?: string; body?: string; headers?: Record<string, string> }) {
        if (opts?.method === 'POST') {
            posts.push({ url, body: JSON.parse(opts.body ?? 'null'), headers: opts.headers ?? {} });
            const ok = postOk;
            return (postHold ?? Promise.resolve()).then(() => ({ ok, status: ok ? 200 : 409, json: async () => ({}) }));
        }
        if (url.includes('/api/books')) return Promise.resolve({ ok: true, status: 200, json: async () => SHELF_BOOKS });
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...MOCK_RESPONSE, months: SESSION_MONTHS }) });
    }
    const historyCalls = () => (fetch as ReturnType<typeof vi.fn>).mock.calls.filter((c) => String(c[0]) === '/api/history').length;

    beforeEach(() => {
        posts = [];
        postOk = true;
        postHold = null;
        vi.stubGlobal('fetch', vi.fn(route));
        const meta = document.createElement('meta');
        meta.name = '_csrf';
        meta.content = 'tok-123';
        document.head.appendChild(meta);
    });
    afterEach(() => { document.head.querySelectorAll('meta[name="_csrf"]').forEach((m) => m.remove()); });

    async function openSheetFor(rowIndex: number) {
        const w = mount(HistoryApp, { attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.record-toggle').exists()).toBe(true));
        const rows = await expand(w);
        await rows[rowIndex].find('button').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-book').exists()).toBe(true));
        return w;
    }

    test('[책 붙이기] → assign 시트 → 고른 책 id를 그 측정에 POST(+CSRF) → /api/history 재조회 → 시트 닫힘', async () => {
        const w = await openSheetFor(0);
        expect(w.find('.book-sheet-title').text()).toBe('이 측정은 무슨 책이었나요?');
        expect(historyCalls()).toBe(1);

        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(historyCalls()).toBe(2));
        expect(posts).toEqual([{ url: '/api/sessions/11/book', body: { bookId: 5 }, headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'tok-123' }) }]);
        await vi.waitFor(() => expect(w.find('.book-sheet-panel').exists()).toBe(false));
    });

    test('실측 줄 [바꾸기] 시트의 「책 없이 두기」 → {bookId: null}', async () => {
        const w = await openSheetFor(1);
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        const cta = w.find('.book-sheet-cta');
        expect(cta.text()).toBe('책 없이 두기');
        await cta.trigger('click');
        await vi.waitFor(() => expect(posts).toHaveLength(1));
        expect(posts[0].url).toBe('/api/sessions/12/book');
        expect(posts[0].body).toEqual({ bookId: null });
    });

    test('수동 기록 줄의 시트엔 「책 없이 두기」가 없다(수동 기록은 책 필수)', async () => {
        const w = await openSheetFor(2);
        expect(w.find('.book-sheet-book').exists()).toBe(true); // 시트는 떴다(양성)
        expect(w.find('.book-sheet-cta').exists()).toBe(false);
    });

    test('POST 실패면 시트가 열린 채 시트 안에서 말하고, 재조회하지 않는다', async () => {
        postOk = false;
        const w = await openSheetFor(0);
        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-error').exists()).toBe(true));
        expect(w.find('.book-sheet-error').text()).toBe('책을 붙이지 못했어요');
        expect(w.find('.book-sheet-panel').exists()).toBe(true);
        expect(historyCalls()).toBe(1);
    });

    // F3 — 보낸 뒤 시트를 닫고 다른 줄을 열면, 먼저 보낸 요청의 결과가 새 시트에 붙으면 안 된다.
    test('먼저 보낸 요청이 실패해도 새로 연 다른 줄의 시트엔 오류를 붙이지 않고 닫지도 않는다', async () => {
        postOk = false;
        let release!: () => void;
        postHold = new Promise<void>((r) => { release = r; });
        const w = await openSheetFor(0);
        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(posts).toHaveLength(1));
        await w.find('.book-sheet-close').trigger('click');
        await w.findAll('.record-session')[1].find('button').trigger('click');
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        release();
        await new Promise((r) => setTimeout(r, 0));
        await w.vm.$nextTick();
        expect(w.find('.book-sheet-panel').exists()).toBe(true);
        expect(w.find('.book-sheet-error').exists()).toBe(false);
    });
});
