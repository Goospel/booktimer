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

    test('응답 실패 → 실패 문구', async () => {
        stubFetch({}, false);
        const wrapper = await mountApp();
        expect(wrapper.text()).toContain('데이터를 불러오지 못했습니다');
    });
});

// ── R2 PR-3 — 공부 기록도 측정 한 건씩 펼쳐 책을 붙인다 ──────────────────────────────────────
// 공부 시트(StudyBookSheet)는 fetch를 안 하는 계약이라 공부 서재(/api/study/books)는 이 화면이 마운트 때 받아 둔다.
describe('StudyHistoryApp — 기록에서 책 붙이기 (R2 PR-3)', () => {
    // 계측기 메모 — 독서 HistoryApp과 거의 복사본이라 한쪽만 고치다 생기는 회귀를 여기서 따로 잡는다.
    //  · 통과가 확정하는 것: 누른 줄의 id로 X-CSRF-TOKEN을 달아 POST, 떼기는 {bookId:null}, 실패면 시트가 남고 재조회 없음,
    //    책 있는 줄은 「다른 책으로 바꿀까요?」+그 책에 aria-current, 서재 로드 실패면 빈 서재라고 단언하지 않는다.
    //  · 실패가 배제하는 것: 떼기 본문 {}(서버 400) · CSRF 누락(조용한 403) · 실패를 삼키고 닫기 · currentBookId 끊김.
    const ROW = { id: 21, start: '09:00', end: '09:50', seconds: 3000, bookId: null, bookTitle: null, manual: false };
    const ROW_BOOKED = { id: 22, start: '10:00', end: '10:30', seconds: 1800, bookId: 7, bookTitle: '헌법', manual: false };
    const MONTHS = [{ month: '2026-09', totalSeconds: 4800, days: [{ date: '2026-09-03', totalSeconds: 4800, sessions: [ROW, ROW_BOOKED] }] }];
    const SHELF = { searchEnabled: false, books: [
        { id: 7, title: '헌법', author: null, coverUrl: null, isbn13: null, readCount: 1, purchaseLink: null, totalSeconds: 0 },
    ] };
    let posts: { url: string; body: unknown; headers: Record<string, string> }[] = [];
    let shelfOk = true;
    let postOk = true;
    let postHold: Promise<void> | null = null; // 있으면 POST 응답을 그때까지 붙잡는다(경주 재현용)

    beforeEach(() => {
        posts = [];
        shelfOk = true;
        postOk = true;
        postHold = null;
        vi.stubGlobal('fetch', vi.fn((url: string, opts?: { method?: string; body?: string; headers?: Record<string, string> }) => {
            if (opts?.method === 'POST') {
                posts.push({ url, body: JSON.parse(opts.body ?? 'null'), headers: opts.headers ?? {} });
                const ok = postOk;
                return (postHold ?? Promise.resolve()).then(() => ({ ok, status: ok ? 200 : 409, json: async () => ({}) }));
            }
            if (url.includes('/api/study/books')) {
                return Promise.resolve({ ok: shelfOk, status: shelfOk ? 200 : 500, json: async () => SHELF, text: async () => '' });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => ({ graph: STUDY_GRAPH, months: MONTHS }) });
        }));
        const meta = document.createElement('meta');
        meta.name = '_csrf';
        meta.content = 'tok-study';
        document.head.appendChild(meta);
    });
    afterEach(() => { document.head.querySelectorAll('meta[name="_csrf"]').forEach((m) => m.remove()); });

    const calls = (needle: string) => (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls
        .filter((c) => String(c[0]) === needle && !(c[1] as { method?: string } | undefined)?.method).length;

    async function openSheet(rowIndex = 0) {
        const w = await mountApp();
        await vi.waitFor(() => expect(w.find('.record-toggle').exists()).toBe(true));
        await w.find('.record-toggle').trigger('click');
        await w.findAll('.record-session button')[rowIndex].trigger('click');
        return w;
    }

    test('마운트 때 공부 서재(/api/study/books)를 한 번 받는다', async () => {
        await mountApp();
        await vi.waitFor(() => expect(calls('/api/study/books')).toBe(1));
    });

    test('[책 붙이기] → 공부 시트(assign) → POST /api/study/sessions/{id}/book(+CSRF) → /api/study/history 재조회', async () => {
        const w = await openSheet();
        expect(w.find('.book-sheet-panel.is-study').exists()).toBe(true);
        expect(w.find('.book-sheet-title').text()).toBe('이 측정은 무슨 책이었나요?');
        expect(calls('/api/study/history')).toBe(1);

        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(calls('/api/study/history')).toBe(2));
        expect(posts).toEqual([{ url: '/api/study/sessions/21/book', body: { bookId: 7 },
            headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'tok-study' }) }]);
        await vi.waitFor(() => expect(w.find('.book-sheet-panel').exists()).toBe(false));
    });

    test('책 있는 줄 [바꾸기] → 「다른 책으로 바꿀까요?」·그 책 aria-current → 「책 없이 두기」는 {bookId: null}', async () => {
        const w = await openSheet(1);
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        expect(w.find('.book-sheet-book').attributes('aria-current')).toBe('true');
        const cta = w.find('.book-sheet-cta');
        expect(cta.text()).toBe('책 없이 두기');
        await cta.trigger('click');
        await vi.waitFor(() => expect(posts).toHaveLength(1));
        expect(posts[0].url).toBe('/api/study/sessions/22/book');
        expect(posts[0].body).toEqual({ bookId: null });
    });

    test('POST 실패면 시트가 열린 채 시트 안에서 말하고, 재조회하지 않는다', async () => {
        postOk = false;
        const w = await openSheet();
        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-error').exists()).toBe(true));
        expect(w.find('.book-sheet-error').text()).toBe('책을 붙이지 못했어요');
        expect(w.find('.book-sheet-panel').exists()).toBe(true);
        expect(calls('/api/study/history')).toBe(1);
    });

    // F3 — 보낸 뒤 시트를 닫고 다른 줄을 열면, 먼저 보낸 요청의 결과가 새 시트에 붙으면 안 된다.
    async function raceToSecondRow(ok: boolean) {
        postOk = ok;
        let release!: () => void;
        postHold = new Promise<void>((r) => { release = r; });
        const w = await openSheet(0);
        await w.find('.book-sheet-book').trigger('click');
        await vi.waitFor(() => expect(posts).toHaveLength(1));
        await w.find('.book-sheet-close').trigger('click');
        await w.findAll('.record-session button')[1].trigger('click');
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        release();
        return w;
    }

    test('먼저 보낸 요청이 실패해도 새로 연 다른 줄의 시트엔 오류를 붙이지 않는다', async () => {
        const w = await raceToSecondRow(false);
        await new Promise((r) => setTimeout(r, 0));
        await w.vm.$nextTick();
        expect(w.find('.book-sheet-panel').exists()).toBe(true);
        expect(w.find('.book-sheet-error').exists()).toBe(false);
    });

    test('먼저 보낸 요청이 성공하면 기록은 재조회하되, 새로 연 시트는 닫지 않는다', async () => {
        const w = await raceToSecondRow(true);
        await vi.waitFor(() => expect(calls('/api/study/history')).toBe(2));
        await w.vm.$nextTick();
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
    });

    test('공부 서재를 못 받았으면 시트 안에서 「책 목록을 불러오지 못했어요」 — 서재가 비었다고 단언하지 않는다', async () => {
        shelfOk = false;
        const w = await openSheet();
        await vi.waitFor(() => expect(w.find('.book-sheet-error').exists()).toBe(true));
        expect(w.find('.book-sheet-error').text()).toBe('책 목록을 불러오지 못했어요');
        expect(w.find('.book-sheet-empty').exists()).toBe(false);
        expect(w.find('.book-sheet-cta').exists()).toBe(true); // 시트는 그대로 섰다(양성)
    });
});
