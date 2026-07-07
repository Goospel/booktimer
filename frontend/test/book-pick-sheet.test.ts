// @vitest-environment jsdom
// BookPickSheet — 통합 책 시트(발견 1, §6.5). 시작 전('start')엔 측정할 책을 고르고,
// 종료 후('tag')엔 방금 세션에 책을 붙인다. 표지·상태·검색담기는 /api/books·/api/books/search 재사용.
// 순수 시각(슬라이드·표지 이미지)은 실 브라우저 게이트. 여기선 목록·필터·이벤트·검색담기 배선만.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import BookPickSheet from '../src/dashboard/BookPickSheet.vue';
import type { BookOption } from '../src/dashboard/types';

const reading: BookOption[] = [{ id: 1, title: '읽는 책' }];
const finished: BookOption[] = [{ id: 2, title: '완독 책' }];
const wantToRead: BookOption[] = [{ id: 3, title: '읽고싶은 책' }];

const SHELF = {
    searchEnabled: true,
    books: [
        { id: 1, title: '읽는 책', author: '저자A', coverUrl: null, isbn13: 'i1', status: 'READING', statusLabel: '읽는 중' },
        { id: 2, title: '완독 책', author: '저자B', coverUrl: null, isbn13: 'i2', status: 'FINISHED', statusLabel: '완독' },
        { id: 3, title: '읽고싶은 책', author: '저자C', coverUrl: null, isbn13: 'i3', status: 'WANT_TO_READ', statusLabel: '읽고 싶음' },
    ],
};
const SEARCH = {
    results: [
        { title: '새로운 책', author: '신저자', isbn13: 'n9', coverUrl: null, publisher: '출판사', purchaseLink: null, category: null, pubDate: null, owned: false },
    ],
};

let addBody: Record<string, unknown> | null = null;
function fetchImpl(url: string, opts?: { method?: string; body?: string }) {
    if (url.includes('/api/books/search')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => SEARCH });
    }
    if (url.includes('/api/books') && opts?.method === 'POST') {
        addBody = JSON.parse(opts.body!);
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ id: 99, title: '새로운 책', author: '신저자', coverUrl: null, isbn13: 'n9', status: 'WANT_TO_READ', statusLabel: '읽고 싶음' }) });
    }
    if (url.includes('/api/books')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => SHELF });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
}

let wrapper: VueWrapper | null = null;
beforeEach(() => { addBody = null; vi.stubGlobal('fetch', vi.fn((u: string, o?: { method?: string; body?: string }) => fetchImpl(u, o))); });
afterEach(() => { wrapper?.unmount(); wrapper = null; vi.unstubAllGlobals(); document.body.innerHTML = ''; });

function make(over: Record<string, unknown> = {}) {
    wrapper = mount(BookPickSheet, {
        props: { mode: 'start', readingBooks: reading, finishedBooks: finished, wantToReadBooks: wantToRead, ...over },
        attachTo: document.body,
    });
    return wrapper;
}

describe('BookPickSheet — 통합 책 시트 (발견 1, §6.5)', () => {
    test('start 모드: 책장(표지 목록) 로드 후 렌더, 고르면 pick(bookId) 발생', async () => {
        const w = make();
        await flushPromises();
        expect(w.text()).toContain('읽는 책');
        expect(w.text()).toContain('완독 책');
        const btn = w.findAll('.book-sheet-book').find(b => b.text().includes('완독 책'))!;
        await btn.trigger('click');
        expect(w.emitted('pick')![0]).toEqual([2]);
    });

    test('start 모드: "책 없이 측정하기" → bookless 발생', async () => {
        const w = make();
        await flushPromises();
        const btn = w.findAll('button').find(b => b.text().includes('책 없이'))!;
        await btn.trigger('click');
        expect(w.emitted('bookless')).toBeTruthy();
    });

    test('tag 모드: 제목이 태깅용이고 하단은 건너뛰기(skip)', async () => {
        const w = make({ mode: 'tag' });
        await flushPromises();
        expect(w.text()).toContain('무슨 책');
        const skip = w.findAll('button').find(b => b.text().includes('건너뛰기'))!;
        await skip.trigger('click');
        expect(w.emitted('skip')).toBeTruthy();
    });

    test('필터칩: "완독"을 고르면 완독 책만 남는다', async () => {
        const w = make();
        await flushPromises();
        const chip = w.findAll('.book-sheet-chip').find(c => c.text().includes('완독'))!;
        await chip.trigger('click');
        const rows = w.findAll('.book-sheet-book').map(b => b.text());
        expect(rows.some(t => t.includes('완독 책'))).toBe(true);
        expect(rows.some(t => t.includes('읽는 책'))).toBe(false);
    });

    test('overlay 클릭 → close 발생', async () => {
        const w = make();
        await flushPromises();
        await w.find('.book-sheet-overlay').trigger('click');
        expect(w.emitted('close')).toBeTruthy();
    });

    test('검색해서 담기: "담기" → POST /api/books, added 발생, owned 전환', async () => {
        const w = make();
        await flushPromises();
        await w.find('.book-sheet-search input').setValue('새로운');
        await w.find('.book-sheet-search').trigger('submit');
        await flushPromises();
        expect(w.text()).toContain('새로운 책');
        const addBtn = w.findAll('button').find(b => b.text().includes('담기'))!;
        await addBtn.trigger('click');
        await flushPromises();
        expect(addBody).toEqual(expect.objectContaining({ title: '새로운 책', status: 'WANT_TO_READ' }));
        expect(w.emitted('added')![0][0]).toEqual(expect.objectContaining({ id: 99 }));
        // 담은 뒤 그 검색행은 '이미 책장에 있음'으로 전환(담기 버튼 사라짐)
        expect(w.findAll('button').some(b => b.text().includes('담기'))).toBe(false);
    });

    test('로드 실패 시 폴백: props 목록을 제목만으로 렌더한다', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 500, json: async () => ({}) })));
        const w = make();
        await flushPromises();
        expect(w.text()).toContain('읽는 책');
        const btn = w.findAll('.book-sheet-book').find(b => b.text().includes('읽고싶은 책'))!;
        await btn.trigger('click');
        expect(w.emitted('pick')![0]).toEqual([3]);
    });
});
