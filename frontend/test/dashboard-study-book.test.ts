// @vitest-environment jsdom
// DashboardApp — 공부 세션에 책 걸기(시작 시 선택 · 종료 후 태깅 · 측정 중 교체).
//
// 계측기 메모 — 「어느 세션에 어느 책을 거는가」는 값이 틀려도 화면이 그럴듯한 자리라,
// 존재·개수가 아니라 **요청 URL의 세션 id와 body의 bookId**를 잰다.
//  · 통과가 확정하는 것: 고른 책의 id가 그 문의 body로 나간다 · 태깅이 stop이 알려준 세션 id로 간다 ·
//    응답 StudyState가 화면(kv·칩)으로 돌아온다 · 공부 문과 독서 문이 섞이지 않는다 ·
//    필드가 빠진 옛 응답에서도 안 죽는다(studyStateOf).
//  · 실패가 배제하는 것: bookId 누락·인덱스 전송 · 독서 문(/api/sessions/*)으로 새기 ·
//    태깅 세션 id를 지어내기 · untaggedSessionId undefined를 「있음」으로 읽기 · 서재 0권에 시트 띄우기.
//
// fetch 스텁은 URL별 명시 분기이고 **미지 URL은 throw**한다(우연 통과 금지).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-09-05', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const BOOK = (id: number, title: string) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0,
});
const HEONBEOP = BOOK(5, '헌법');
const HYEONGBEOP = BOOK(6, '형법');
const SHELF = [HEONBEOP, HYEONGBEOP];
const STUDY_IDLE = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 60, goalSeconds: 0,
    activeBook: null, recentBookId: 5, books: SHELF, untaggedSessionId: null,
};
const READING_TIMER = {
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
};
const DASHBOARD = {
    nickname: '테스터', loginId: 'tester', profileCharacterCode: null, ...READING_TIMER,
    graph: GRAPH, garden: { ownedAuthorCharacterCount: 0, totalAuthorCharacterCount: 0, ownedCharacters: [] },
    quotes: [], emailVerified: true, study: STUDY_IDLE,
};

// 케이스가 바꾸는 손잡이
let startStatus = 200;
let stopStudyBody: Record<string, unknown> = { ...STUDY_IDLE };
let shelf = SHELF;
const req: { url: string; body: string }[] = [];

const ok = (json: unknown, status = 200) =>
    Promise.resolve({ ok: status < 400, status, statusText: 'x', json: async () => json });

function fetchImpl(url: string, init?: RequestInit) {
    const body = String(init?.body ?? '');
    if (url.includes('/api/study/start')) {
        req.push({ url, body });
        if (startStatus !== 200) return ok({}, startStatus);
        const id = (JSON.parse(body || '{}') as { bookId: number | null }).bookId;
        return ok({
            ...STUDY_IDLE, books: shelf, hasActiveSession: true, activeStartedAt: new Date().toISOString(),
            activeBook: shelf.find(b => b.id === id) ?? null,
        });
    }
    if (url.includes('/api/study/stop')) {
        req.push({ url, body });
        return ok(stopStudyBody);
    }
    if (url.includes('/api/study/sessions/')) {
        req.push({ url, body });
        const id = (JSON.parse(body || '{}') as { bookId: number }).bookId;
        return ok({ ...STUDY_IDLE, books: shelf, recentBookId: id });
    }
    if (url.includes('/api/study/active/book')) {
        req.push({ url, body });
        const id = (JSON.parse(body || '{}') as { bookId: number | null }).bookId;
        return ok({
            ...STUDY_IDLE, books: shelf, hasActiveSession: true, activeStartedAt: new Date().toISOString(),
            activeBook: shelf.find(b => b.id === id) ?? null,
        });
    }
    if (url.includes('/api/study/history')) return ok({ graph: GRAPH, months: [] });
    if (url.includes('/api/dashboard')) return ok({ ...DASHBOARD, study: { ...STUDY_IDLE, books: shelf } });
    // 독서 문 — (h)의 대조군. 공부 흐름에서 한 번이라도 여기로 새면 카운트로 잡힌다.
    if (url.includes('/api/sessions/start')) { req.push({ url, body }); return ok({ ...READING_TIMER, hasActiveSession: true, activeStartedAt: new Date().toISOString() }); }
    if (url.includes('/api/sessions/stop')) { req.push({ url, body }); return ok({ sessionId: 7, untagged: true, timer: READING_TIMER, graph: GRAPH }); }
    if (url.includes('/api/sessions/')) { req.push({ url, body }); return ok({ sessionId: 7, bookTitle: '데미안' }); }
    if (url.includes('/api/books')) return ok({ books: [], searchEnabled: false });
    throw new Error('unexpected fetch: ' + url);
}

const urls = () => (fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls.map(c => String(c[0]));
const countOf = (needle: string) => urls().filter(u => u.includes(needle)).length;
const sent = (needle: string) => req.filter(r => r.url.includes(needle));

beforeEach(() => {
    startStatus = 200;
    stopStudyBody = { ...STUDY_IDLE };
    shelf = SHELF;
    req.length = 0;
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn((u: string, i?: RequestInit) => fetchImpl(u, i)));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); document.body.innerHTML = ''; });

async function mountStudy() {
    const w = mount(DashboardApp, { attachTo: document.body });
    await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
    await w.findAll('.dash-mode-toggle button').find(b => b.text() === '공부')!.trigger('click');
    return w;
}
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('.dash-state-panel button').find(b => b.text().includes(text));
const sheetRow = (w: ReturnType<typeof mount>, title: string) =>
    w.findAll('.book-sheet-book').find(b => b.text().includes(title))!;
const kv = (w: ReturnType<typeof mount>) => w.find('.dash-kv-v').text();

describe('DashboardApp — 공부 시작 시 책 선택', () => {
    test('(a) 기본 칩(헌법)으로 시작 → start body가 그 id, 독서 문은 0건', async () => {
        const w = await mountStudy();
        expect(w.find('.dash-book-chip-title').text()).toBe('헌법');

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));

        expect(JSON.parse(sent('/api/study/start')[0].body)).toEqual({ bookId: 5 });
        expect(countOf('/api/sessions/start')).toBe(0);
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));
    });

    test('(b) 「책 없이 시작」 → bookId null', async () => {
        const w = await mountStudy();
        await btnWith(w, '책 없이 시작')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));

        expect(JSON.parse(sent('/api/study/start')[0].body)).toEqual({ bookId: null });
        await vi.waitFor(() => expect(kv(w)).toBe('책 없이'));
    });

    test('(c) 「바꾸기」 시트에서 다른 책을 고르면 그 id로 시작하고 시트가 닫힌다', async () => {
        const w = await mountStudy();
        await btnWith(w, '바꾸기')!.trigger('click');

        expect(w.find('.book-sheet-title').text()).toBe('공부할 책을 고르세요');
        await sheetRow(w, '형법').trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));

        expect(JSON.parse(sent('/api/study/start')[0].body)).toEqual({ bookId: 6 });
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        expect(kv(w)).toBe('형법');
    });

    test('(g) 시작이 404(다른 곳에서 지운 책)면 알리고 화면을 다시 받는다', async () => {
        startStatus = 404;
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));

        expect(w.find('.alert-error').text()).toContain('서재에 없어요');
        expect(countOf('/api/dashboard')).toBe(2);   // 최초 + 재조회
    });
});

describe('DashboardApp — 측정 중 책 교체', () => {
    test('(d) 지금 책엔 aria-current, 다른 책으로 바꾸면 시간이 통째로 옮겨간다 → 「책 없이」까지', async () => {
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));

        await btnWith(w, '책 바꾸기')!.trigger('click');
        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        expect(sheetRow(w, '헌법').attributes('aria-current')).toBe('true');
        expect(sheetRow(w, '형법').attributes('aria-current')).toBeUndefined();

        await sheetRow(w, '형법').trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/active/book')).toHaveLength(1));
        expect(JSON.parse(sent('/api/study/active/book')[0].body)).toEqual({ bookId: 6 });
        await vi.waitFor(() => expect(kv(w)).toBe('형법'));

        // 「책 없이 공부하기」 — 시트 하단 CTA가 null을 보낸다(교체 취소가 아니다).
        await btnWith(w, '책 바꾸기')!.trigger('click');
        expect(w.find('.book-sheet-cta').text()).toBe('책 없이 공부하기');
        await w.find('.book-sheet-cta').trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/active/book')).toHaveLength(2));

        expect(JSON.parse(sent('/api/study/active/book')[1].body)).toEqual({ bookId: null });
        await vi.waitFor(() => expect(kv(w)).toBe('책 없이'));
    });
});

describe('DashboardApp — 종료 후 태깅', () => {
    test('(e) untaggedSessionId가 오면 그 세션 id로 태깅한다 — 독서 태깅 문은 0건', async () => {
        stopStudyBody = { ...STUDY_IDLE, untaggedSessionId: 42 };
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));
        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-title').exists()).toBe(true));

        expect(w.find('.book-sheet-title').text()).toBe('무슨 책을 공부하셨나요?');
        await sheetRow(w, '헌법').trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/sessions/')).toHaveLength(1));

        expect(sent('/api/study/sessions/')[0].url).toContain('/api/study/sessions/42/tag-book');
        expect(JSON.parse(sent('/api/study/sessions/')[0].body)).toEqual({ bookId: 5 });
        expect(countOf('/api/sessions/42/tag-book')).toBe(0);
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        // stop 뒤 잔디 재조회는 그대로(모드 전환 1 + stop 1).
        expect(countOf('/api/study/history')).toBe(2);
    });

    test('(e2) 태깅 시트의 「건너뛰기」는 닫기만 한다 — 아무 문도 두드리지 않는다', async () => {
        stopStudyBody = { ...STUDY_IDLE, untaggedSessionId: 42 };
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));
        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-title').exists()).toBe(true));

        await w.find('.book-sheet-cta').trigger('click');
        await w.vm.$nextTick();

        expect(w.find('.book-sheet-overlay').exists()).toBe(false);
        expect(sent('/api/study/sessions/')).toHaveLength(0);
        expect(sent('/api/study/active/book')).toHaveLength(0);
    });

    test('(f) 책을 걸고 잰 세션(untaggedSessionId null)엔 시트가 뜨지 않는다', async () => {
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));
        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/stop')).toHaveLength(1));
        await w.vm.$nextTick();

        expect(w.find('.book-sheet-overlay').exists()).toBe(false);
    });

    test('(f2) 서재가 0권이면 태깅 시트를 띄우지 않는다(고를 게 없다 — E10)', async () => {
        shelf = [];
        stopStudyBody = { ...STUDY_IDLE, books: [], recentBookId: null, untaggedSessionId: 42 };
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));
        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/stop')).toHaveLength(1));
        await w.vm.$nextTick();

        expect(w.find('.book-sheet-overlay').exists()).toBe(false);
        // 양성 대조: 같은 stop이 서재 2권일 땐 (e)에서 시트를 띄웠다.
        expect(w.find('a[href="/study/books"]').exists()).toBe(true);
    });

    test('(f3) 새 필드가 통째로 없는 옛 응답에도 안 죽는다(studyStateOf) — 시트 없음, 오류 없음', async () => {
        // 정규화가 없으면 undefined !== null이 참이라 시트를 띄우려다 s.books.length에서 던지고,
        // 그 예외를 catch가 「네트워크 오류」로 삼킨다 — 시트도 안 뜨고 화면도 그럴듯해서
        // 「시트 없음」만 재면 돌연변이가 살아남는다(실측: M8이 통과했다). 그래서 alert이 계측기다.
        stopStudyBody = { hasActiveSession: false, activeStartedAt: null, todaySeconds: 120, goalSeconds: 0 };
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));
        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/stop')).toHaveLength(1));
        await w.vm.$nextTick();

        expect(w.find('.alert-error').exists()).toBe(false);
        expect(w.find('.book-sheet-overlay').exists()).toBe(false);
        expect(w.find('.dash-timer-num').text()).toBe('02:00');
        expect(w.find('a[href="/study/books"]').exists()).toBe(true);
    });
});

describe('DashboardApp — 독서 대조군', () => {
    test('(h) 독서 stop의 태깅 시트는 독서 문구 — 공부 시트가 아니다(E21)', async () => {
        const w = mount(DashboardApp, { attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));

        await w.findAll('.dash-state-panel button').find(b => b.text().includes('측정 시작'))!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/sessions/start')).toHaveLength(1));
        await w.findAll('.dash-state-panel button').find(b => b.text().includes('측정 종료'))!.trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-title').exists()).toBe(true));

        expect(w.findAll('.book-sheet-title')).toHaveLength(1);
        expect(w.find('.book-sheet-title').text()).toBe('무슨 책을 읽으셨나요?');
        expect(w.text()).not.toContain('공부하셨나요');
        expect(countOf('/api/study/')).toBe(0);
    });
});
