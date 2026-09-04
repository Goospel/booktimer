// @vitest-environment jsdom
// 공부 서재(/study/books) — 분류 축이 「회독 수」 하나인 서재. 독서 서재(BooksApp)와 파일을 나눈 이유는
// 상태 3종·공개여부·인기도·책방 링크가 여기 없어서다(미니앱 StudyLibrary와 같은 판단).
//
// 이 파일의 계측 규칙 둘:
//  ① fetch 스텁은 URL마다 명시 분기하고 미지 URL은 throw — 「폴백이 대신 답해서」 우연히 초록이 되는 창을 닫는다.
//  ② 음성 단언(「독서 문을 안 두드린다」·「담기 버튼이 없다」)은 전부 양성과 쌍이다 — 컴포넌트가 통째로
//     안 그려져도 초록이 되는 사각을 막는다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyBooksApp from '../src/study/StudyBooksApp.vue';

// 서버 StudyBookApiController.StudyShelfResponse 그대로.
// 민법총칙 = isbn 있음·2독·5400초 / 헌법 = isbn 없음(직접 추가분)·0독·0초 — 칩 규칙 두 갈래의 대조군.
const SHELF = {
    searchEnabled: true,
    books: [
        { id: 1, title: '민법총칙', author: 'A', coverUrl: null, isbn13: 'i1', readCount: 2, purchaseLink: null, totalSeconds: 5400 },
        { id: 2, title: '헌법', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
    ],
};

// 검색 응답의 owned는 「독서 책장」 기준이다 — 공부 화면은 이 값을 무시하고 내 공부 서재 isbn으로 다시 센다.
// 민법총칙: owned:false 인데 내 서재엔 있음 / 형법: owned:true 인데 내 서재엔 없음 → 쌍으로 뒤집혀 있다.
const SEARCH_RESULTS = [
    { title: '민법총칙', author: 'A', isbn13: 'i1', coverUrl: null, publisher: '박영사', purchaseLink: null, owned: false },
    { title: '형법', author: 'B', isbn13: 'i9', coverUrl: null, publisher: '법문사', purchaseLink: null, owned: true },
];

let shelf: typeof SHELF;
let shelfOk: boolean;
let calls: { url: string; body: unknown }[];

function fetchImpl(url: string, init?: RequestInit) {
    calls.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (url.endsWith('/api/study/books')) {
        if (init?.method === 'POST') return Promise.resolve({ ok: true, status: 200, json: async () => SHELF.books[0] });
        return Promise.resolve({ ok: shelfOk, status: shelfOk ? 200 : 500, text: async () => '', json: async () => shelf });
    }
    if (/\/api\/study\/books\/\d+\/read-count$/.test(url)) {
        return Promise.resolve({ ok: true, status: 200, json: async () => SHELF.books[0] });
    }
    if (/\/api\/study\/books\/\d+\/delete$/.test(url)) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ deleted: true }) });
    }
    if (url.startsWith('/api/books/search?')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ results: SEARCH_RESULTS }) });
    }
    throw new Error('unexpected fetch: ' + url);
}

const urls = () => calls.map((c) => c.url);
const countOf = (pred: (u: string) => boolean) => urls().filter(pred).length;
const bodyOf = (pred: (u: string) => boolean) => calls.find((c) => pred(c.url))?.body;

beforeEach(() => {
    shelf = SHELF;
    shelfOk = true;
    calls = [];
    vi.stubGlobal('fetch', vi.fn((u: string, i?: RequestInit) => fetchImpl(u, i)));
    vi.stubGlobal('confirm', vi.fn(() => true));
});
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

async function mountApp() {
    const wrapper = mount(StudyBooksApp);
    await new Promise((r) => setTimeout(r, 0));
    await wrapper.vm.$nextTick();
    return wrapper;
}

/** 텍스트를 눌러 비교 — 템플릿 줄바꿈·들여쓰기가 단언을 브리틀하게 만들지 않도록. */
const flat = (s: string) => s.replace(/\s+/g, ' ');

describe('공부 서재 — 목록', () => {
    test('(a) 공부 문만 두드린다 — /api/study/books 1건, 독서 책장·대시보드 0건', async () => {
        await mountApp();
        expect(countOf((u) => u.endsWith('/api/study/books'))).toBe(1);
        expect(countOf((u) => u === '/api/books')).toBe(0);
        expect(countOf((u) => u.includes('/api/dashboard'))).toBe(0);
    });

    test('(b) 회독 칩은 언제나, 누적 시간 칩은 0초면 안 그린다 (부재는 부재로 둔다)', async () => {
        const w = await mountApp();
        const rows = w.findAll('.shelf-list .book-row');
        expect(rows).toHaveLength(2);
        expect(flat(rows[0].text())).toContain('민법총칙');
        expect(flat(rows[0].text())).toContain('2독');
        expect(flat(rows[0].text())).toContain('1시간 30분 공부');
        expect(flat(rows[1].text())).toContain('헌법');
        expect(flat(rows[1].text())).toContain('0독');          // 0독은 「상태」라 그린다
        expect(rows[1].find('.book-time').exists()).toBe(false); // 0초는 「부재」라 안 그린다
    });

    test('(c) 회독은 절대값으로 보내고, 뮤테이션 뒤 목록을 다시 받는다', async () => {
        const w = await mountApp();
        const rows = w.findAll('.shelf-list .book-row');
        // 양성·음성 쌍: 0독 행의 「−」는 잠기고(서버 400을 문 앞에서 막는다), 2독 행의 「−」는 열려 있다
        expect(rows[1].find('.study-count-minus').attributes('disabled')).toBeDefined();
        expect(rows[0].find('.study-count-minus').attributes('disabled')).toBeUndefined();

        await rows[0].find('.study-count-plus').trigger('click');
        await new Promise((r) => setTimeout(r, 0));
        expect(bodyOf((u) => u.includes('/read-count'))).toEqual({ readCount: 3 }); // 2 + 1, 절대값
        expect(countOf((u) => u.endsWith('/api/study/books') && !u.includes('read-count'))).toBe(2); // 마운트 + 재조회
    });

    test('(f) 삭제는 confirm을 통과해야만 나간다', async () => {
        const w = await mountApp();
        await w.findAll('.shelf-list .book-row')[0].find('.book-actions .btn-danger').trigger('click');
        await new Promise((r) => setTimeout(r, 0));
        expect(countOf((u) => u.includes('/delete'))).toBe(1);

        vi.stubGlobal('confirm', vi.fn(() => false));
        const w2 = await mountApp();
        calls.length = 0;
        await w2.findAll('.shelf-list .book-row')[0].find('.book-actions .btn-danger').trigger('click');
        await new Promise((r) => setTimeout(r, 0));
        expect(countOf((u) => u.includes('/delete'))).toBe(0);
    });

    test('(i) 목록을 못 받으면 실패 문구와 다시 시도 버튼', async () => {
        shelfOk = false;
        const w = await mountApp();
        expect(w.text()).toContain('공부 서재를 불러오지 못했어요');
        expect(w.find('.link-btn').exists()).toBe(true);
    });
});

describe('공부 서재 — 담기', () => {
    test('(d) owned는 독서 책장 기준이라 무시하고 내 공부 서재 isbn으로 판정한다', async () => {
        const w = await mountApp();
        await w.find('.book-search-form input').setValue('민법');
        await w.find('.book-search-form').trigger('submit');
        await new Promise((r) => setTimeout(r, 0));
        expect(countOf((u) => u.startsWith('/api/books/search?'))).toBe(1);
        expect(urls().find((u) => u.startsWith('/api/books/search?'))).toContain('type=TITLE');

        const found = w.findAll('.search-scroll .book-row');
        expect(found).toHaveLength(2);
        // 민법총칙: 응답 owned:false 인데 내 서재(i1)에 있다 → 배지, 담기 없음
        expect(found[0].find('.shelf-owned-badge').exists()).toBe(true);
        expect(found[0].find('.book-actions .btn-primary').exists()).toBe(false);
        // 형법: 응답 owned:true 인데 내 서재엔 없다 → 담기 있음, 배지 없음
        expect(found[1].find('.book-actions .btn-primary').exists()).toBe(true);
        expect(found[1].find('.shelf-owned-badge').exists()).toBe(false);
    });

    test('(e) 담기 본문엔 status가 없다 — 독서 /api/books 계약이 아니다', async () => {
        const w = await mountApp();
        await w.find('.book-search-form input').setValue('형법');
        await w.find('.book-search-form').trigger('submit');
        await new Promise((r) => setTimeout(r, 0));
        await w.findAll('.search-scroll .book-row')[1].find('.book-actions .btn-primary').trigger('click');
        await new Promise((r) => setTimeout(r, 0));

        const body = calls.find((c) => c.url.endsWith('/api/study/books') && c.body)?.body as Record<string, unknown>;
        expect(body).toMatchObject({ title: '형법', isbn13: 'i9', publisher: '법문사' });
        expect(Object.keys(body)).not.toContain('status');
    });

    test('(g) 검색이 꺼진 날엔 직접 추가가 열려 있고, isbn 없이 담는다', async () => {
        shelf = { ...SHELF, searchEnabled: false };
        const w = await mountApp();
        expect(w.find('.book-search-form').exists()).toBe(false);
        expect(w.find('.book-manual-form').exists()).toBe(true);
        expect(w.find('details.manual-add').attributes('open')).toBeDefined();

        await w.find('.book-manual-form input[placeholder="제목"]').setValue('형법');
        await w.find('.book-manual-form').trigger('submit');
        await new Promise((r) => setTimeout(r, 0));
        expect(calls.find((c) => c.url.endsWith('/api/study/books') && c.body)?.body)
            .toEqual({ title: '형법', author: null, isbn13: null, coverUrl: null, publisher: null, purchaseLink: null });
    });
});

describe('공부 서재 — 셸', () => {
    test('(h) 하단 네비는 공부 세계 안에서만 돈다 (자기 자신·독서 서재로 나가지 않는다)', async () => {
        const w = await mountApp();
        expect(w.findAll('.link-row a').map((a) => a.attributes('href'))).toEqual(['/', '/study', '/study/history']);
    });

    test('(h) 카드에 공부 잉크(is-study)가 붙고, 화면에 이모지가 없다', async () => {
        const w = await mountApp();
        expect(w.findAll('.card.is-study')).toHaveLength(2);
        expect(w.text()).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    });
});
