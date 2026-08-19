// @vitest-environment jsdom
// 책 시트의 「이미 있는 책」 배지 — 책장 화면(BooksApp)과 **같은 문구·같은 칩**이어야 한다.
// 두 곳이 같은 검색 API(/api/books/search)의 같은 `owned` 플래그를 그리므로, 표기가 갈리면
// 사용자는 같은 사실을 두 이름으로 읽는다. 어휘가 「서재」가 아니라 「책장」인 이유는 BooksApp.test.ts 참고.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import BookPickSheet from './BookPickSheet.vue';

function okJson(body: object) {
    return { ok: true, json: async () => body } as Response;
}

const searchRow = (title: string, owned: boolean) => ({
    title, author: '지은이', isbn13: title, coverUrl: null,
    publisher: null, purchaseLink: null, category: null, pubDate: null, owned,
});

/** 시트를 열고 검색 결과를 띄운다 — 담긴 책 한 권 + 아직 없는 책 한 권. */
async function mountAndSearch(): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(okJson({ books: [], searchEnabled: true }));
    const wrapper = mount(BookPickSheet, {
        attachTo: document.body,
        props: { mode: 'start', readingBooks: [], finishedBooks: [], wantToReadBooks: [] },
    });
    await vi.waitFor(() => expect(wrapper.find('.book-sheet-search').exists()).toBe(true));

    vi.mocked(fetch).mockResolvedValueOnce(okJson({
        results: [searchRow('미움받을 용기', true), searchRow('역행자', false)],
    }));
    await wrapper.find('.book-sheet-search input').setValue('책');
    await wrapper.find('.book-sheet-search').trigger('submit');
    await vi.waitFor(() => expect(wrapper.findAll('.book-sheet-result').length).toBe(2));
    return wrapper;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('책 시트 검색 결과 — 이미 책장에 있는 책', () => {
    test('배지는 「책장에 있어요」 — 기본 이모지를 쓰지 않는다', async () => {
        const wrapper = await mountAndSearch();

        // 정확 일치라 `📚`가 남아 있으면 깨진다. 책장 화면과 같은 문구인지도 이 값이 못 박는다.
        expect(wrapper.find('.book-sheet-owned').text()).toBe('책장에 있어요');
    });

    test('배지 대신 담을 길을 잃지 않는다 — 아직 없는 책엔 「담기」가 그대로다', async () => {
        const rows = (await mountAndSearch()).findAll('.book-sheet-result');

        expect(rows[0].find('.book-sheet-owned').exists()).toBe(true);
        expect(rows[0].find('.book-sheet-add-btn').exists()).toBe(false);
        expect(rows[1].find('.book-sheet-owned').exists()).toBe(false);
        expect(rows[1].find('.book-sheet-add-btn').exists()).toBe(true);
    });
});
