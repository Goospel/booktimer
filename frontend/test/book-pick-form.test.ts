// @vitest-environment jsdom
// BookPickForm — 표지 칩 + '바꾸기' + '책 없이'(발견 1, §6.5). 드롭다운을 걷어내고
// 기본 책(최근 읽은 책=이어 읽기)을 칩으로 보여준다. '측정 시작'은 그 책으로 1탭 시작,
// '바꾸기'는 책 고르기 시트를 열고(openSheet), '책 없이 시작'은 start(null). 시작을 막지 않는다.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import BookPickForm from '../src/dashboard/BookPickForm.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const reading: BookOption[] = [{ id: 1, title: '데미안' }];

function make(over: Record<string, unknown> = {}) {
    wrapper = mount(BookPickForm, {
        props: { readingBooks: reading, finishedBooks: [], wantToReadBooks: [], recentBookId: 1, ...over },
        attachTo: document.body,
    });
    return wrapper;
}

describe('BookPickForm — 표지 칩 + 바꾸기 + 책 없이 (발견 1)', () => {
    test('기본(책 있음): 칩에 기본 책 제목이 보이고, 측정 시작 → recentBookId로 start', async () => {
        const w = make();
        expect(w.text()).toContain('데미안');
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([1]);
    });

    test('recentBookId가 없으면 첫 책이 기본 — 측정 시작이 그 책으로 start', async () => {
        const w = make({ recentBookId: null, readingBooks: [{ id: 5, title: '토지' }] });
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([5]);
    });

    test('바꾸기 → openSheet 발생(책 고르기 시트 열기)', async () => {
        const w = make();
        const btn = w.findAll('button').find(b => b.text().includes('바꾸기'))!;
        await btn.trigger('click');
        expect(w.emitted('openSheet')).toBeTruthy();
    });

    test('책 없이 시작 → start(null)', async () => {
        const w = make();
        const btn = w.findAll('button').find(b => b.text().includes('책 없이'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([null]);
    });

    test('책 0권: "책 없이 측정 시작" → start(null) — 시작을 막지 않음', async () => {
        const w = make({ readingBooks: [], finishedBooks: [], wantToReadBooks: [], recentBookId: null });
        const btn = w.findAll('button').find(b => b.text().includes('책 없이'))!;
        expect(btn).toBeTruthy();
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([null]);
    });

    test('책 0권: "책 고르기" → openSheet(검색·담기 시트)', async () => {
        const w = make({ readingBooks: [], finishedBooks: [], wantToReadBooks: [], recentBookId: null });
        const btn = w.findAll('button').find(b => b.text().includes('고르기'))!;
        await btn.trigger('click');
        expect(w.emitted('openSheet')).toBeTruthy();
    });
});
