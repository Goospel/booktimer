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
import { mount, flushPromises } from '@vue/test-utils';
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
    nickname: '테스터', loginId: 'tester', ...READING_TIMER,
    graph: GRAPH,
    quotes: [], emailVerified: true, study: STUDY_IDLE,
};

// 케이스가 바꾸는 손잡이
let startStatus = 200;
let changeStatus = 200;
let stopStudyBody: Record<string, unknown> = { ...STUDY_IDLE };
let shelf = SHELF;
// 필드가 빠진 옛 응답을 돌려줄 문 하나(''=없음) — 정규화 계측기 (i1)~(i3)가 켠다.
let partialDoor = '';
/**
 * 옛 서버 응답 흉내 — todaySeconds·books·activeBook 등이 통째로 없고, **10분 전에 시작한 측정 중**이다.
 * 정규화하면 히어로 숫자가 0 + 600 = 10:00, 날것 res.json()이면 undefined + 600 = NaN → '00:00'.
 */
const PARTIAL_ACTIVE = () => ({ hasActiveSession: true, activeStartedAt: new Date(Date.now() - 600_000).toISOString() });
const req: { url: string; body: string }[] = [];

const ok = (json: unknown, status = 200) =>
    Promise.resolve({ ok: status < 400, status, statusText: 'x', json: async () => json });

function fetchImpl(url: string, init?: RequestInit) {
    const body = String(init?.body ?? '');
    if (url.includes('/api/study/start')) {
        req.push({ url, body });
        if (startStatus !== 200) return ok({}, startStatus);
        if (partialDoor === 'start') return ok(PARTIAL_ACTIVE());
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
        if (changeStatus !== 200) return ok({}, changeStatus);
        if (partialDoor === 'change') return ok(PARTIAL_ACTIVE());
        const id = (JSON.parse(body || '{}') as { bookId: number | null }).bookId;
        return ok({
            ...STUDY_IDLE, books: shelf, hasActiveSession: true, activeStartedAt: new Date().toISOString(),
            activeBook: shelf.find(b => b.id === id) ?? null,
        });
    }
    // 홈 여백 카드가 「지금 그 책」의 글을 부른다(2026-09-07) — 0건이어도 카드는 뜬다.
    if (url.includes('/api/stories/of/')) {
        return Promise.resolve({
            ok: true, status: 200,
            json: async () => ({ book: { id: 1, title: '지금 책', author: null, coverUrl: null },
                                 ownerNickname: '테스터', self: true, entries: [] }),
        });
    }
    if (url.includes('/api/study/history')) return ok({ graph: GRAPH, months: [] });
    // 홈 필기 카드(2026-09-15)가 필기할 책의 목록을 부른다 — (n1)·(n2)가 이 호출의 bookId로 필기 책을 잰다.
    if (url.includes('/api/study/notes?')) return ok({ notes: [] });
    if (url.includes('/api/dashboard')) {
        if (partialDoor === 'dashboard') return ok({ ...DASHBOARD, study: PARTIAL_ACTIVE() });
        return ok({ ...DASHBOARD, study: { ...STUDY_IDLE, books: shelf } });
    }
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
    changeStatus = 200;
    partialDoor = '';
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
    // 서버가 공부 측정 중을 실어 오면 이미 공부 모드이고 막대엔 토글이 없다(2026-09-15 결정 6) — 있을 때만 누른다.
    await w.findAll('.dash-mode-toggle button').find(b => b.text() === '공부')?.trigger('click');
    return w;
}
// 히어로 카드 안의 버튼만 — 측정 중엔 우측 패널 대신 머리 막대라(2026-09-15) 카드 루트로 범위를 잡는다(시트 버튼과 안 섞이게).
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('.dash-timer-hero button').find(b => b.text().includes(text));
const sheetRow = (w: ReturnType<typeof mount>, title: string) =>
    w.findAll('.book-sheet-book').find(b => b.text().includes(title))!;
const kv = (w: ReturnType<typeof mount>) => w.find('[data-testid="focus-book"] .dash-kv-v').text();

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

    test('(c) 「바꾸기」 시트에서 다른 책을 고르면 **책만 바뀐다** — 시작은 시작 버튼이 한다', async () => {
        const w = await mountStudy();
        await btnWith(w, '바꾸기')!.trigger('click');

        expect(w.find('.book-sheet-title').text()).toBe('공부할 책을 고르세요');
        await sheetRow(w, '형법').trigger('click');

        // 고르기는 시작이 아니다 — 칩만 바뀌고 문은 두드리지 않는다(요청 0건으로 잰다).
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        expect(sent('/api/study/start')).toHaveLength(0);
        expect(w.find('.dash-book-chip-title').text()).toBe('형법');
        expect(w.find('.dash-pill-pulse').exists()).toBe(false);

        // 양성 대조군 — 버튼은 고른 그 책으로 진짜 시작시킨다(「아무 데서도 시작 안 됨」 배제).
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));

        expect(JSON.parse(sent('/api/study/start')[0].body)).toEqual({ bookId: 6 });
        await vi.waitFor(() => expect(kv(w)).toBe('형법'));
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

// 리뷰 Minor-3 — 필기 책은 「측정 중인 책 → 칩 기본 책(고른 책 → 최근 책 → 첫 책)」이다. 시트에서 고른 책이
// 시작 뒤에도 남으면, 측정 중 다른 책으로 바꿔 필기하다가 종료하는 순간 필기가 **옛 고른 책**으로 튄다
// (편집기가 비워진다 — PR-2에서 카드가 갈라지는 순간 쓰던 글이 사라지는 장면). 고르기는 시작 전까지만 유효하다.
describe('DashboardApp — 측정 종료 뒤 필기 책이 제자리에 남는다', () => {
    const notesSelect = (w: ReturnType<typeof mount>) =>
        (w.find('[data-testid="notes-book"]').element as HTMLSelectElement).value;

    test('(n1) 형법을 골라 시작 → 헌법으로 교체 → 종료: 필기는 헌법 그대로, 목록 재조회 없음', async () => {
        const w = await mountStudy();
        await btnWith(w, '바꾸기')!.trigger('click');
        await sheetRow(w, '형법').trigger('click');
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('형법'));

        await btnWith(w, '책 바꾸기')!.trigger('click');
        await sheetRow(w, '헌법').trigger('click');   // 교체 응답: activeBook 헌법 · recentBookId 5(헌법)
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));
        await vi.waitFor(() => expect(notesSelect(w)).toBe('5'));
        // 교체에 따른 목록 조회는 flush 뒤에 나간다 — 그걸 기다린 다음에 세야 종료 뒤 증가분만 잡힌다.
        // (첫 번째 bookId=5는 마운트 때 최근 책으로 부른 것 — 교체분은 두 번째다.)
        await vi.waitFor(() => expect(countOf('/api/study/notes?bookId=5')).toBe(2));
        const listsBefore = countOf('/api/study/notes?');

        await btnWith(w, '측정 종료')!.trigger('click');   // 종료 응답: activeBook null · recentBookId 5
        await vi.waitFor(() => expect(w.find('.dash-pill-pulse').exists()).toBe(false));
        await new Promise(r => setTimeout(r, 20));

        expect(notesSelect(w)).toBe('5');
        expect(countOf('/api/study/notes?')).toBe(listsBefore);
        expect(w.find('.dash-book-chip-title').text()).toBe('헌법');   // 칩도 방금 잰 책
    });

    // 반대편 경계 — 책 없이 시작하면 시작 순간 칩이 recent(헌법)로 바뀌어 필기가 튄다. 그래서 이 경로는 비우지 않는다.
    test('(n2) 형법을 골라 두고 「책 없이 시작」 → 종료: 칩·필기 모두 형법 그대로', async () => {
        const w = await mountStudy();
        await btnWith(w, '바꾸기')!.trigger('click');
        await sheetRow(w, '형법').trigger('click');
        await vi.waitFor(() => expect(notesSelect(w)).toBe('6'));

        await btnWith(w, '책 없이 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('책 없이'));
        expect(notesSelect(w)).toBe('6');

        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.dash-book-chip-title').exists()).toBe(true));
        expect(w.find('.dash-book-chip-title').text()).toBe('형법');
        expect(notesSelect(w)).toBe('6');
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

    test('(d2) 교체가 404(다른 곳에서 지운 책)면 시트를 닫고 알린 뒤 화면을 다시 받는다 — start와 같은 규칙(E8)', async () => {
        changeStatus = 404;
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));

        await btnWith(w, '책 바꾸기')!.trigger('click');
        await sheetRow(w, '형법').trigger('click');
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));

        expect(w.find('.alert-error').text()).toContain('서재에 없어요');
        // 시트를 닫는 것이 계측기의 핵심 — 열린 채면 지워진 그 행이 목록에 남아 눌러도 계속 실패한다.
        expect(w.find('.book-sheet-overlay').exists()).toBe(false);
        expect(countOf('/api/dashboard')).toBe(2);   // 최초 + 재조회
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
        // 2026-09-07 홈 잔디 철거 — 모드 전환에도, stop 뒤에도 이 왕복은 더 이상 없다.
        // 이 단언이 여기 있는 이유: 이 파일이 실제로 공부 모드로 들어가 공부 stop까지 밟는 유일한 자리다.
        expect(countOf('/api/study/history')).toBe(0);
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

describe('DashboardApp — 정규화(studyStateOf)가 문마다 걸려 있다', () => {
    // 계측기: 측정 중이고 시작 시각이 과거인 응답에서 todaySeconds를 뺀다(PARTIAL_ACTIVE). 히어로 숫자는
    // todaySeconds + elapsed라, 정규화하면 0 + 600 = '10:00'이고 날것 res.json()이면 NaN → fmtMSS가 '00:00'.
    // (2026-09-13까지는 하루 목표 게이지가 관측기였다 — 게이지가 사라져 이 숫자로 옮겼다.)
    //
    // ⚠️ (i4) 태깅 자리는 잠그지 못한다 — 종료 후라 elapsed가 0이어서 NaN과 0이 둘 다 '00:00'이다.
    // 태깅 응답의 나머지 필드는 StudyTimerCard가 withDefaults로 스스로 메워 화면이 같다.
    const heroNum = (w: ReturnType<typeof mount>) => w.find('.dash-timer-num').text();
    const TEN_MIN = /^10:0\d$/;

    test('(i1) applyDashboard — /api/dashboard의 study가 필드 누락이어도 히어로가 10:00이다', async () => {
        partialDoor = 'dashboard';
        const w = await mountStudy();

        await vi.waitFor(() => expect(heroNum(w)).toMatch(TEN_MIN));
        expect(w.find('.alert-error').exists()).toBe(false);
    });

    test('(i2) start 응답이 필드 누락이어도 히어로가 10:00이다', async () => {
        partialDoor = 'start';
        const w = await mountStudy();
        expect(heroNum(w)).toBe('01:00');   // 양성 대조: 시작 전은 대시보드의 todaySeconds 60

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.dash-pill-pulse').exists()).toBe(true));

        await vi.waitFor(() => expect(heroNum(w)).toMatch(TEN_MIN));
        expect(w.find('.alert-error').exists()).toBe(false);
    });

    test('(i3) 교체 응답이 필드 누락이어도 히어로가 10:00이다', async () => {
        partialDoor = 'change';
        const w = await mountStudy();
        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(kv(w)).toBe('헌법'));

        await btnWith(w, '책 바꾸기')!.trigger('click');
        await sheetRow(w, '형법').trigger('click');
        await vi.waitFor(() => expect(sent('/api/study/active/book')).toHaveLength(1));

        // 새 activeStartedAt은 타이머의 다음 1초 틱에 반영된다 — 기본 대기(1초)와 경계가 겹쳐 넉넉히 준다.
        await vi.waitFor(() => expect(heroNum(w)).toMatch(TEN_MIN), { timeout: 3000 });
        expect(w.find('.alert-error').exists()).toBe(false);
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

// 전환 뒤 순서(설계 2026-09-15-study-focus-lamp §2-6·결정 10, 리뷰 Minor-2) — jsdom엔 startViewTransition·matchMedia가 없어
// 이 두 가지가 한 번도 실행되지 않았다. 가짜 전환의 finished를 붙잡아 「전환이 끝나기 전엔 캐럿도 시트도 없다」를 잰다.
//  · 통과가 확정하는 것: 캐럿(마우스 환경)과 태깅 시트가 **전환이 끝난 뒤에** 온다.
//  · 실패가 배제하는 것: 시트를 전환 전에 열기(스냅숏에 찍혀 뚝 나타남) · fine pointer 판별 반전 · 캐럿 호출 삭제.
describe('DashboardApp — 전환이 끝난 뒤에 캐럿·태깅 시트', () => {
    let release: () => void = () => { };
    beforeEach(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (document as any).createRange = () => ({
            setStart: () => {}, setEnd: () => {}, commonAncestorContainer: document.body,
            getBoundingClientRect: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }),
            getClientRects: () => ({ length: 0, item: () => null, [Symbol.iterator]: function* () {} }),
        });
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (document as any).startViewTransition = (cb: () => Promise<void>) => {
            const done = cb();
            const gate = new Promise<void>(r => { release = r; });
            return { finished: gate.then(() => done) };
        };
        vi.stubGlobal('matchMedia', () => ({ matches: true }));
    });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    afterEach(() => { delete (document as any).startViewTransition; document.body.className = ''; });

    test('(v1) 시작: 전환 중엔 편집기 포커스가 없고, 끝나면 필기 끝에 캐럿이 온다', async () => {
        const w = await mountStudy();
        await vi.waitFor(() => expect(w.find('.ProseMirror').exists()).toBe(true));

        await btnWith(w, '공부 측정 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').classes()).toContain('is-merged'));   // 콜백 안에서 상태는 이미 새 값
        await flushPromises();
        expect(document.activeElement).not.toBe(w.find('.ProseMirror').element);

        release();
        await vi.waitFor(() => expect(document.activeElement).toBe(w.find('.ProseMirror').element));
    });

    test('(v2) 책 없이 끝낸 종료: 전환 중엔 태깅 시트가 없고, 끝나면 뜬다', async () => {
        stopStudyBody = { ...STUDY_IDLE, books: SHELF, untaggedSessionId: 42 };
        const w = await mountStudy();
        await btnWith(w, '책 없이 시작')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').classes()).toContain('is-merged'));
        release();
        await flushPromises();

        await btnWith(w, '측정 종료')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').classes()).not.toContain('is-merged'));
        await flushPromises();
        expect(w.find('.book-sheet-overlay').exists()).toBe(false);

        release();
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(true));
    });
});
