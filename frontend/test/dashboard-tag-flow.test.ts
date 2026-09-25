// @vitest-environment jsdom
// DashboardApp — 종료 후 태깅 플로우(발견 1). 책 없이 시작한 세션을 종료하면(stop 응답 untagged=true)
// 통합 책 시트(mode=tag)가 뜨고, 책을 고르면 POST /api/sessions/{sessionId}/tag-book 을 호출한다. 건너뛰면 미호출.
// 시트는 표지 목록을 /api/books 로 로드(재사용). 순수 시각은 실 브라우저 게이트 — 여기선 시트 등장·태깅 배선만.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: null, totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};

// 책 없이 측정 "중"으로 시작(activeBookTitle=null) → 종료 시 untagged 시트가 떠야 한다.
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester',
    remainingSeconds: 1200, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
    hasActiveSession: true, activeStartedAt: '2026-06-26T08:00:00Z', activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '읽는 책' }], finishedBooks: [], wantToReadBooks: [{ id: 3, title: '읽고싶은 책' }],
    recentBookId: null,
    graph: GRAPH,
    quotes: [], emailVerified: true,
};

// 태깅 시트가 /api/books 로 로드하는 책장(표지 목록). 태깅 대상 '읽고싶은 책'(id 3) 포함.
const SHELF = {
    searchEnabled: false,
    books: [
        { id: 1, title: '읽는 책', author: null, coverUrl: null, isbn13: 'i1', status: 'READING', statusLabel: '읽는 중' },
        { id: 3, title: '읽고싶은 책', author: null, coverUrl: null, isbn13: 'i3', status: 'WANT_TO_READ', statusLabel: '읽고 싶음' },
    ],
};

const STOP_UNTAGGED = {
    sessionId: 77, untagged: true,
    timer: {
        remainingSeconds: 0, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: true,
        hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
        readingBooks: [{ id: 1, title: '읽는 책' }], finishedBooks: [], recentBookId: null,
    },
    graph: GRAPH,
};

let tagCall: { url: string; body: unknown } | null = null;

function fetchImpl(url: string, opts?: { body?: string }) {
    if (url.includes('/tag-book')) {
        tagCall = { url, body: JSON.parse(opts!.body!) };
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ sessionId: 77, bookTitle: '읽고싶은 책' }) });
    }
    if (url.includes('/api/sessions/stop')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => STOP_UNTAGGED });
    }
    if (url.includes('/api/stories/feed')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ mine: null, groups: [] }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => SHELF });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...DASHBOARD }) });
}

beforeEach(() => { tagCall = null; vi.stubGlobal('fetch', vi.fn((u: string, o?: { body?: string }) => fetchImpl(u, o))); });
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

describe('DashboardApp — 종료 후 태깅 시트 (발견 1)', () => {
    test('책 없이 측정 종료 → 시트 등장, 책 선택 시 tag-book 호출 후 시트 닫힘', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        const stopBtn = wrapper.findAll('button').find(b => b.text().includes('측정 종료'))!;
        await stopBtn.trigger('click');

        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(true));
        await flushPromises();

        const bookBtn = wrapper.findAll('.book-sheet-book').find(b => b.text().includes('읽고싶은 책'))!;
        await bookBtn.trigger('click');

        await vi.waitFor(() => expect(tagCall).toBeTruthy());
        expect(tagCall!.url).toContain('/api/sessions/77/tag-book');
        expect(tagCall!.body).toEqual({ bookId: 3 });

        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(false));
    });

    test('건너뛰기 → 시트 닫힘, tag-book 미호출', async () => {
        const wrapper = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.dash-timer-hero').exists()).toBe(true));

        await wrapper.findAll('button').find(b => b.text().includes('측정 종료'))!.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(true));
        await flushPromises();

        await wrapper.findAll('button').find(b => b.text().includes('건너뛰기'))!.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.book-sheet-overlay').exists()).toBe(false));
        expect(tagCall).toBeNull();
    });
});

// ── R2 PR-2 ──────────────────────────────────────────────────────────────────────────────
// 측정 중 책 바꾸기(P4) · 태깅 실패를 시트 안에서(P7) · 태깅 뒤 재조회(P10).
// 서버를 **상태를 가진 가짜**로 둔다 — 「교체 뒤 종료하면 칩이 새 책」은 start·active/book·stop이
// 같은 recentBookId를 이어 받아야만 잴 수 있다. 미지 URL은 throw(우연 통과 금지).
type Opt = { id: number; title: string };
const BOOK1: Opt = { id: 1, title: '읽는 책' };
const BOOK2: Opt = { id: 2, title: '둘째 책' };
const WANT3: Opt = { id: 3, title: '읽고싶은 책' };
const R2_SHELF = {
    searchEnabled: false,
    books: [
        { id: 1, title: '읽는 책', author: null, coverUrl: null, isbn13: 'i1', status: 'READING', statusLabel: '읽는 중' },
        { id: 2, title: '둘째 책', author: null, coverUrl: null, isbn13: 'i2', status: 'READING', statusLabel: '읽는 중' },
        { id: 3, title: '읽고싶은 책', author: null, coverUrl: null, isbn13: 'i3', status: 'WANT_TO_READ', statusLabel: '읽고 싶음' },
    ],
};

const srv = {
    active: false, activeBook: null as Opt | null, recent: null as number | null,
    reading: [BOOK1, BOOK2] as Opt[], want: [WANT3] as Opt[],
    changeStatus: 200, tagStatus: 200, untagged: true,
};
const calls: { url: string; body: unknown }[] = [];
function timer() {
    return {
        remainingSeconds: 1200, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
        hasActiveSession: srv.active, activeStartedAt: srv.active ? '2026-06-26T08:00:00Z' : null,
        activeBookTitle: srv.activeBook?.title ?? null, activeBookTotalSeconds: srv.activeBook ? 600 : 0,
        activeBook: srv.activeBook,
        readingBooks: srv.reading, finishedBooks: [], recentBookId: srv.recent, wantToReadBooks: srv.want,
    };
}
/** 읽고싶음 책을 걸면 읽는 중으로 옮긴다 — 서버 changeActiveBook·tagBook과 같은 전환. */
function adopt(b: Opt | null) {
    if (b && srv.want.some(x => x.id === b.id)) { srv.want = srv.want.filter(x => x.id !== b.id); srv.reading = [b, ...srv.reading]; }
}
const byId = (id: number | null) => [BOOK1, BOOK2, WANT3].find(b => b.id === id) ?? null;
const res = (json: unknown, status = 200) => Promise.resolve({ ok: status < 400, status, json: async () => json });
function statefulFetch(url: string, opts?: { body?: string }) {
    const body = opts?.body ? JSON.parse(opts.body) : undefined;
    calls.push({ url, body });
    if (url.includes('/api/sessions/start')) {
        srv.active = true; srv.activeBook = byId(body.bookId); adopt(srv.activeBook);
        if (srv.activeBook) srv.recent = srv.activeBook.id;
        return res(timer());
    }
    if (url.includes('/api/sessions/active/book')) {
        if (srv.changeStatus !== 200) return res({}, srv.changeStatus);
        srv.activeBook = byId(body.bookId); adopt(srv.activeBook);
        if (srv.activeBook) srv.recent = srv.activeBook.id;
        return res(timer());
    }
    if (url.includes('/api/sessions/stop')) {
        const untagged = srv.activeBook === null && srv.untagged;
        srv.active = false; srv.activeBook = null;
        return res({ sessionId: 77, untagged, timer: timer(), graph: GRAPH });
    }
    if (url.includes('/tag-book')) {
        if (srv.tagStatus !== 200) return res({}, srv.tagStatus);
        const b = byId(body.bookId); adopt(b); srv.recent = b!.id;
        return res({ sessionId: 77, bookTitle: b!.title });
    }
    if (url.includes('/api/stories/')) return res({ book: null, ownerNickname: '테스터', self: true, entries: [] });
    if (url.includes('/api/books')) return res(R2_SHELF);
    if (url.includes('/api/dashboard')) return res({ ...DASHBOARD, ...timer() });
    throw new Error('unexpected fetch: ' + url);
}
const sentTo = (needle: string) => calls.filter(c => c.url.includes(needle));

async function mountR2(over: Partial<typeof srv> = {}) {
    Object.assign(srv, {
        active: true, activeBook: null, recent: null, reading: [BOOK1, BOOK2], want: [WANT3],
        changeStatus: 200, tagStatus: 200, untagged: true,
    }, over);
    calls.length = 0;
    vi.stubGlobal('fetch', vi.fn((u: string, o?: { body?: string }) => statefulFetch(u, o)));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
    const w = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
    return w;
}
type W = ReturnType<typeof mount>;
const panelBtn = (w: W, text: string) => w.findAll('.dash-state-panel button').find(b => b.text() === text);
const row = (w: W, title: string) => w.findAll('.book-sheet-book').find(b => b.text().includes(title))!;
async function openSheetFrom(w: W, label: string) {
    await panelBtn(w, label)!.trigger('click');
    await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(true));
    await flushPromises();
}

describe('DashboardApp — 측정 중 책 바꾸기 (R2 P4)', () => {
    test('[책 바꾸기] → 교체 시트 → 고른 책 id로 POST active/book, 패널이 새 책을 말한다', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1 });
        await openSheetFrom(w, '책 바꾸기');
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        // 지금 그 책엔 aria-current — 서버 TimerState.activeBook이 좌표다
        expect(row(w, '읽는 책').attributes('aria-current')).toBe('true');

        await row(w, '읽고싶은 책').trigger('click');
        await vi.waitFor(() => expect(sentTo('/api/sessions/active/book')).toHaveLength(1));
        expect(sentTo('/api/sessions/active/book')[0].body).toEqual({ bookId: 3 });
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        expect(w.find('.dash-kv-v').text()).toBe('읽고싶은 책');
        expect(sentTo('/tag-book')).toHaveLength(0);
    });

    test('교체 시트 CTA 「책 없이 읽기」 → {bookId: null}, 새 측정을 시작하지 않는다', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1 });
        await openSheetFrom(w, '책 바꾸기');
        await w.find('.book-sheet-cta').trigger('click');
        await vi.waitFor(() => expect(sentTo('/api/sessions/active/book')).toHaveLength(1));
        expect(sentTo('/api/sessions/active/book')[0].body).toEqual({ bookId: null });
        expect(sentTo('/api/sessions/start')).toHaveLength(0);
        await vi.waitFor(() => expect(w.find('.dash-kv-v').text()).toBe('책 없이'));
    });

    test('409(다른 곳에서 이미 끝남)면 시트를 닫고 알린 뒤 화면을 다시 받는다', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1, changeStatus: 409 });
        await openSheetFrom(w, '책 바꾸기');
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        expect(w.find('.alert-error').text()).toContain('진행 중인 측정이 없어요');
        await vi.waitFor(() => expect(sentTo('/api/dashboard')).toHaveLength(2));
    });

    // 404 = 다른 곳에서 지운 책. 시트를 열어 두면 지워진 행이 남아 눌러도 같은 오류만 반복된다 — 닫고 재조회가 목록을 고친다.
    test('404(그 책이 서재에 없음)면 시트를 닫고 알린 뒤 화면을 다시 받는다', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1, changeStatus: 404 });
        await openSheetFrom(w, '책 바꾸기');
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        expect(w.find('.alert-error').text()).toContain('그 책이 서재에 없어요');
        await vi.waitFor(() => expect(sentTo('/api/dashboard')).toHaveLength(2));
    });

    test('교체 왕복 중엔 시트 행이 잠긴다(이중 제출 방지)', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1 });
        await openSheetFrom(w, '책 바꾸기');
        let release!: () => void;
        const gate = new Promise<void>(r => { release = r; });
        vi.stubGlobal('fetch', vi.fn(async (u: string, o?: { body?: string }) => {
            if (u.includes('/api/sessions/active/book')) await gate;
            return statefulFetch(u, o);
        }));
        await row(w, '둘째 책').trigger('click');
        await flushPromises();
        expect(row(w, '읽고싶은 책').attributes('disabled')).toBeDefined();
        release();
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
    });

    test('409·404 밖의 실패(500)면 시트를 연 채 시트 안에서 말한다', async () => {
        const w = await mountR2({ activeBook: BOOK1, recent: 1, changeStatus: 500 });
        await openSheetFrom(w, '책 바꾸기');
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-panel .book-sheet-error').exists()).toBe(true));
        expect(w.find('.book-sheet-error').text()).toBe('책을 바꾸지 못했어요');
    });

    // 시트에서 고른 책(pickedBook)은 「시작 전 고르기」다. 측정 중에 책을 바꿨는데 그 값이 남아 있으면
    // 종료하는 순간 칩이 옛 고른 책으로 튄다 — 서버 recent(새 책)를 따라야 한다(공부 Minor-3과 같은 규칙).
    test('고른 책으로 시작 → 측정 중 교체 → 종료: 칩은 교체한 책', async () => {
        const w = await mountR2({ active: false, recent: 1 });
        await openSheetFrom(w, '바꾸기');
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.dash-book-chip-title').text()).toBe('둘째 책'));

        await panelBtn(w, '측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(panelBtn(w, '책 바꾸기')).toBeTruthy());
        expect(sentTo('/api/sessions/start')[0].body).toEqual({ bookId: 2 });

        await openSheetFrom(w, '책 바꾸기');
        await row(w, '읽는 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));

        await panelBtn(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.dash-book-chip-title').exists()).toBe(true));
        expect(w.find('.dash-book-chip-title').text()).toBe('읽는 책');
    });
});

describe('DashboardApp — 종료 후 태깅 실패·성공 (R2 P7·P10)', () => {
    async function stopToTagSheet(w: W) {
        await openSheetFrom(w, '측정 종료');
    }

    // 이 시트는 그 세션을 붙일 지금의 유일한 진입점이다 — 실패에 닫으면 미태깅으로 굳는다.
    test('tag-book이 실패하면 시트를 연 채 시트 안에서 말한다(딤 뒤 페이지 알림이 아니라)', async () => {
        const w = await mountR2({ tagStatus: 404 });
        await stopToTagSheet(w);
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-panel .book-sheet-error').exists()).toBe(true));
        expect(w.find('.book-sheet-error').text()).toBe('책을 연결하지 못했어요');
        expect(w.find('.book-sheet-overlay').exists()).toBe(true);
        expect(w.find('.alert-error').exists()).toBe(false);
    });

    test('실패 뒤 시트를 닫았다 다시 열면 옛 오류가 남아 있지 않다', async () => {
        const w = await mountR2({ tagStatus: 404 });
        await stopToTagSheet(w);
        await row(w, '둘째 책').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-error').exists()).toBe(true));
        await w.find('.book-sheet-close').trigger('click');
        await openSheetFrom(w, '바꾸기');
        expect(w.find('.book-sheet-error').exists()).toBe(false);
    });

    // tag-book 응답엔 상태가 없다 — 읽고싶음 책을 붙이면 서버에선 읽는 중이 됐는데 화면 목록은 옛것이다.
    test('tag-book 성공 뒤 /api/dashboard를 다시 받는다 — 붙인 읽고싶음 책이 칩으로 온다', async () => {
        const w = await mountR2({ reading: [], recent: null });
        await stopToTagSheet(w);
        expect(sentTo('/api/dashboard')).toHaveLength(1);
        await row(w, '읽고싶은 책').trigger('click');
        await vi.waitFor(() => expect(sentTo('/api/dashboard')).toHaveLength(2));
        await vi.waitFor(() => expect(w.find('.dash-book-chip-title').text()).toBe('읽고싶은 책'));
    });
});
