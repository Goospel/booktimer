// @vitest-environment jsdom
// DashboardApp — 공부 측정 시작·종료 단축키 Alt+P(설계 2026-10-20-study-timer-start-stop-shortcut §7 Task 1·4).
//
// 계측기 메모
//  · 존재가 아니라 **요청 URL과 본문의 bookId**, 그리고 편집기의 selection 위치를 잰다.
//  · 이 파일은 afterEach에서 전부 언마운트한다 — 남은 카드의 창 리스너가 다음 케이스의 키를 받으면 단언이 공허해진다.
//  · matchMedia를 참으로 심는다 — jsdom엔 없어서 finePointer()가 늘 거짓이면 캐럿 테스트가 공허하게 통과한다.
//  · jsdom은 글자 입력을 재현하지 못한다 — 「글자가 안 들어갔다」는 단언하지 않는다(U1·U2).
//  · 통과가 확정하는 것: 키가 버튼과 같은 문(/api/study/start·stop)을 같은 bookId로 두드린다 · 책 시트가 열리면 무시한다 ·
//    독서 모드에선 듣지 않는다 · 쓰던 자리(본문·제목)의 포커스·캐럿을 지킨다.
//  · 실패가 배제하는 것: 칩과 다른 책으로 시작 · 시트 뒤에서 오발 · 독서 화면으로 새는 리스너 · 키로 시작해도 캐럿이 글 끝으로 튐.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';

const GRAPH = {
    weeks: [[{ date: '2026-10-20', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const BOOK = (id: number, title: string) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0,
});
const SHELF = [BOOK(5, '헌법'), BOOK(6, '형법')];
const STUDY_IDLE = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 60, goalSeconds: 0,
    activeBook: null, recentBookId: 5, books: SHELF, untaggedSessionId: null,
};
const READING_TIMER = {
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
};

let shelf = SHELF;
let stopStudyBody: Record<string, unknown> = { ...STUDY_IDLE };
const req: { url: string; body: string }[] = [];
const ok = (json: unknown, status = 200) =>
    Promise.resolve({ ok: status < 400, status, statusText: 'x', json: async () => json });

function fetchImpl(url: string, init?: RequestInit) {
    const body = String(init?.body ?? '');
    if (url.includes('/api/study/start')) {
        req.push({ url, body });
        const id = (JSON.parse(body || '{}') as { bookId: number | null }).bookId;
        return ok({
            ...STUDY_IDLE, books: shelf, hasActiveSession: true, activeStartedAt: new Date().toISOString(),
            activeBook: shelf.find(b => b.id === id) ?? null,
        });
    }
    if (url.includes('/api/study/stop')) { req.push({ url, body }); return ok({ ...stopStudyBody, books: shelf }); }
    if (url.includes('/api/study/sessions/')) { req.push({ url, body }); return ok({ ...STUDY_IDLE, books: shelf }); }
    if (url.includes('/api/stories/of/')) {
        return ok({ book: { id: 1, title: '지금 책', author: null, coverUrl: null }, ownerNickname: '테스터', self: true, entries: [] });
    }
    if (url.includes('/api/study/history')) return ok({ graph: GRAPH, months: [] });
    if (url.includes('/api/study/notes?')) return ok({ notes: [] });
    // 필기 저장(POST)·이어 쓰기(PUT) — 이 파일의 케이스는 저장을 부르지 않지만, 불러도 죽지 않게 받는다.
    if (url.includes('/api/study/notes')) return ok({ id: 31, bookId: 5, title: '', body: '', revision: 0, updatedAt: '2026-10-20T00:00:00Z' });
    if (url.includes('/api/dashboard')) {
        return ok({ nickname: '테스터', loginId: 'tester', ...READING_TIMER, graph: GRAPH, quotes: [], emailVerified: true,
                    study: { ...STUDY_IDLE, books: shelf } });
    }
    // 독서 문 — 공부 키가 여기로 새면 카운트로 잡힌다.
    if (url.includes('/api/sessions/')) { req.push({ url, body }); return ok({ ...READING_TIMER, hasActiveSession: true, activeStartedAt: new Date().toISOString() }); }
    if (url.includes('/api/books')) return ok({ books: [], searchEnabled: false });
    throw new Error('unexpected fetch: ' + url);
}
const sent = (needle: string) => req.filter(r => r.url.includes(needle));

const mounted: { unmount: () => void }[] = [];
beforeEach(() => {
    shelf = SHELF;
    stopStudyBody = { ...STUDY_IDLE };
    req.length = 0;
    localStorage.clear();
    localStorage.setItem('booktimer.timerMode', 'study');
    vi.stubGlobal('fetch', vi.fn((u: string, i?: RequestInit) => fetchImpl(u, i)));
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (document as any).createRange = () => ({
        setStart: () => {}, setEnd: () => {}, commonAncestorContainer: document.body,
        getBoundingClientRect: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }),
        getClientRects: () => ({ length: 0, item: () => null, [Symbol.iterator]: function* () {} }),
    });
});
afterEach(() => {
    mounted.splice(0).forEach(w => w.unmount());
    vi.unstubAllGlobals();
    localStorage.clear();
    document.body.innerHTML = '';
    document.body.className = '';
});

async function mountHome() {
    const w = mount(DashboardApp, { attachTo: document.body });
    mounted.push(w);
    await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
    return w;
}
async function mountStudy() {
    const w = await mountHome();
    await vi.waitFor(() => expect(w.find('.ProseMirror').exists()).toBe(true));
    return w;
}
const altP = (target: EventTarget = window) => {
    const e = new KeyboardEvent('keydown', { code: 'KeyP', key: 'p', altKey: true, bubbles: true, cancelable: true });
    target.dispatchEvent(e);
    return e;
};
const merged = (w: ReturnType<typeof mount>) => w.find('.focus-stack').classes().includes('is-merged');
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('.dash-timer-hero button').find(b => b.text().includes(text));
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const editorOf = (w: ReturnType<typeof mount>) => (w.find('.ProseMirror').element as any).editor;

describe('DashboardApp — Alt+P로 공부 측정 시작·종료', () => {
    test.each([
        ['칩의 책(최근 책 헌법)', SHELF, 5],
        ['서재가 빔', [], null],
    ])('REQ-03 · 대기 중 Alt+P → /api/study/start 본문이 칩의 책 id다(서재가 비면 null): %s', async (_, books, id) => {
        shelf = books as typeof SHELF;
        const w = await mountHome();
        altP();
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));
        expect(JSON.parse(sent('/api/study/start')[0].body)).toEqual({ bookId: id });
        expect(sent('/api/sessions/')).toHaveLength(0);
        await vi.waitFor(() => expect(merged(w)).toBe(true));
    });

    test('REQ-04 · 책 없이 잰 측정을 Alt+P로 끝내면 /api/study/stop 뒤 태깅 시트가 뜬다(버튼과 같다)', async () => {
        stopStudyBody = { ...STUDY_IDLE, untaggedSessionId: 42 };
        const w = await mountHome();
        await btnWith(w, '책 없이 시작')!.trigger('click');
        await vi.waitFor(() => expect(merged(w)).toBe(true));

        altP();
        await vi.waitFor(() => expect(sent('/api/study/stop')).toHaveLength(1));
        await vi.waitFor(() => expect(w.find('.book-sheet-title').exists()).toBe(true));
        expect(w.find('.book-sheet-title').text()).toBe('무슨 책을 공부하셨나요?');
        expect(merged(w)).toBe(false);
    });

    test('REQ-05 · 태깅 시트가 열려 있으면 Alt+P가 아무것도 안 하고, 닫으면 칩의 책으로 시작한다(양성 대조)', async () => {
        stopStudyBody = { ...STUDY_IDLE, untaggedSessionId: 42 };
        const w = await mountHome();
        await btnWith(w, '책 없이 시작')!.trigger('click');
        await vi.waitFor(() => expect(merged(w)).toBe(true));
        altP();
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(true));

        const before = req.length;
        expect(altP().defaultPrevented).toBe(true);   // 조합은 삼킨다
        await flushPromises();
        expect(req.length).toBe(before);

        await w.find('.book-sheet-overlay').trigger('click');
        await vi.waitFor(() => expect(w.find('.book-sheet-overlay').exists()).toBe(false));
        altP();
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(2));
        expect(JSON.parse(sent('/api/study/start')[1].body)).toEqual({ bookId: 5 });
    });

    test('REQ-05 · 독서 모드에선 Alt+P를 듣지 않는다 — 삼키지도 요청하지도 않는다', async () => {
        localStorage.removeItem('booktimer.timerMode');
        const w = await mountHome();
        expect(w.find('.focus-stack').exists()).toBe(false);   // 독서 화면이다
        expect(altP().defaultPrevented).toBe(false);
        await flushPromises();
        expect(req).toHaveLength(0);

        // 양성 대조 — 공부로 바꾸면 같은 키가 시작한다
        await w.findAll('.dash-mode-toggle button').find(b => b.text() === '공부')!.trigger('click');
        await vi.waitFor(() => expect(w.find('.focus-stack').exists()).toBe(true));
        altP();
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));
    });
});

describe('DashboardApp — Alt+P는 쓰던 자리를 지킨다', () => {
    test('REQ-06 · 필기 본문 가운데서 Alt+P로 시작하고 끝내도 캐럿이 그 자리에 있다', async () => {
        const w = await mountStudy();
        const pm = w.find('.ProseMirror').element as HTMLElement;
        const editor = editorOf(w);
        editor.commands.setContent('<p>가나다라</p><p>마바사</p>', { emitUpdate: false });
        editor.commands.setTextSelection(3);
        editor.view.focus();
        expect(document.activeElement).toBe(pm);
        expect(editor.state.selection.from).toBe(3);

        altP(pm);
        await vi.waitFor(() => expect(merged(w)).toBe(true));
        await vi.waitFor(() => expect(w.find('.dash-pill-pulse').exists()).toBe(true));
        await flushPromises();
        expect(document.activeElement).toBe(pm);
        expect(editor.state.selection.from).toBe(3);

        altP(pm);
        await vi.waitFor(() => expect(merged(w)).toBe(false));
        await flushPromises();
        expect(sent('/api/study/stop')).toHaveLength(1);
        expect(document.activeElement).toBe(pm);
        expect(editor.state.selection.from).toBe(3);
    });

    test('REQ-06 · 제목 칸에서 Alt+P로 시작하면 포커스가 제목 칸에 남는다', async () => {
        const w = await mountStudy();
        const title = w.find('[data-testid="notes-title"]').element as HTMLInputElement;
        await vi.waitFor(() => expect(title.disabled).toBe(false));
        title.focus();
        expect(document.activeElement).toBe(title);

        altP(title);
        await vi.waitFor(() => expect(sent('/api/study/start')).toHaveLength(1));
        await vi.waitFor(() => expect(merged(w)).toBe(true));
        await flushPromises();
        expect(document.activeElement).toBe(title);
    });

    test('REQ-06 · 포커스가 없을 때 Alt+P로 시작하면 버튼처럼 필기 끝에 캐럿이 온다(양성 대조)', async () => {
        const w = await mountStudy();
        const pm = w.find('.ProseMirror').element as HTMLElement;
        const editor = editorOf(w);
        editor.commands.setContent('<p>가나다라</p><p>마바사</p>', { emitUpdate: false });
        editor.commands.setTextSelection(3);
        (document.activeElement as HTMLElement | null)?.blur();
        expect(document.activeElement).not.toBe(pm);

        altP();
        await vi.waitFor(() => expect(document.activeElement).toBe(pm));
        expect(editor.state.selection.from).toBe(editor.state.doc.content.size - 1);
    });
});
