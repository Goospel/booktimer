// @vitest-environment jsdom
// BookPickForm — "책 없이 시작"(발견 1) 배선. 시작을 책 선택으로 가로막지 않는다:
// 책이 있어도 "책 없이 측정"을 고르면 start(null), 책이 0권이어도 바로 시작 버튼으로 start(null).
// 책이 있으면 기본은 recentBookId(이어 읽기)라 기존 "책 골라 시작"도 그대로 동작.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import BookPickForm from '../src/dashboard/BookPickForm.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const reading: BookOption[] = [{ id: 1, title: '데미안' }];

describe('BookPickForm — 책 없이 시작 (발견 1)', () => {
    test('기본(책 있음): recentBookId로 start 발생 — 이어 읽기 그대로', async () => {
        wrapper = mount(BookPickForm, {
            props: { readingBooks: reading, finishedBooks: [], recentBookId: 1 },
            attachTo: document.body,
        });
        const btn = wrapper.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(wrapper.emitted('start')![0]).toEqual([1]);
    });

    test('"책 없이 측정" 옵션을 고른 뒤 시작하면 start가 null로 발생', async () => {
        wrapper = mount(BookPickForm, {
            props: { readingBooks: reading, finishedBooks: [], recentBookId: 1 },
            attachTo: document.body,
        });
        expect(wrapper.text()).toContain('책 없이 측정');
        const nullOption = wrapper.findAll('option').find(o => o.text().includes('책 없이'))!;
        await nullOption.setSelected();
        const btn = wrapper.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(wrapper.emitted('start')![0]).toEqual([null]);
    });

    test('책 0권이어도 "책 없이 측정 시작" 버튼으로 start(null) — 시작을 막지 않음', async () => {
        wrapper = mount(BookPickForm, {
            props: { readingBooks: [], finishedBooks: [], recentBookId: null },
            attachTo: document.body,
        });
        const btn = wrapper.findAll('button').find(b => b.text().includes('책 없이'))!;
        expect(btn).toBeTruthy();
        await btn.trigger('click');
        expect(wrapper.emitted('start')![0]).toEqual([null]);
    });
});
