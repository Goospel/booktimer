// @vitest-environment jsdom
// TagBookSheet — 종료 후 "무슨 책이었나요?" 태깅 시트(발견 1).
// 읽는중+완독+읽고싶음 셋 중 고르면 tag(bookId), 건너뛰면 skip(세션은 책 없이 유지).
// 순수 시각(슬라이드·위치)은 jsdom 무의미 → 실 브라우저 게이트. 여기선 렌더 목록·이벤트 배선만.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import TagBookSheet from '../src/dashboard/TagBookSheet.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const reading: BookOption[] = [{ id: 1, title: '읽는 책' }];
const finished: BookOption[] = [{ id: 2, title: '완독 책' }];
const wantToRead: BookOption[] = [{ id: 3, title: '읽고싶은 책' }];

function make(over: Record<string, unknown> = {}) {
    wrapper = mount(TagBookSheet, {
        props: { readingBooks: reading, finishedBooks: finished, wantToReadBooks: wantToRead, ...over },
        attachTo: document.body,
    });
    return wrapper;
}

describe('TagBookSheet — 종료 후 태깅 (발견 1)', () => {
    test('세 목록(읽는중·완독·읽고싶음)의 책을 모두 렌더한다', () => {
        const w = make();
        expect(w.text()).toContain('읽는 책');
        expect(w.text()).toContain('완독 책');
        expect(w.text()).toContain('읽고싶은 책');
    });

    test('책을 고르면 tag 이벤트가 그 bookId로 발생', async () => {
        const w = make();
        const bookBtn = w.findAll('button').find(b => b.text().includes('읽고싶은 책'))!;
        await bookBtn.trigger('click');
        expect(w.emitted('tag')![0]).toEqual([3]);
    });

    test('건너뛰기를 누르면 skip 이벤트가 발생(세션은 책 없이 유지)', async () => {
        const w = make();
        const skipBtn = w.findAll('button').find(b => b.text().includes('건너뛰기'))!;
        await skipBtn.trigger('click');
        expect(w.emitted('skip')).toBeTruthy();
    });

    test('책이 하나도 없어도 건너뛰기로 닫을 수 있다', () => {
        const w = make({ readingBooks: [], finishedBooks: [], wantToReadBooks: [] });
        expect(w.findAll('button').some(b => b.text().includes('건너뛰기'))).toBe(true);
    });
});
