// @vitest-environment jsdom
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import QuickNav from '../src/dashboard/QuickNav.vue';

describe('QuickNav', () => {
    test('독서 기록(/history) 타일이 더 이상 없다 — 잔디 박스로 흡수 (회귀 가드)', () => {
        const wrapper = mount(QuickNav, { props: { loginId: 'tester' } });
        expect(wrapper.find('a[href="/history"]').exists()).toBe(false);
    });
    test('내 책장·내 책방·책BTI 타일은 남아 있다', () => {
        const wrapper = mount(QuickNav, { props: { loginId: 'tester' } });
        expect(wrapper.find('a[href="/books"]').exists()).toBe(true);
        expect(wrapper.find('a[href="/u/tester"]').exists()).toBe(true);
        expect(wrapper.find('a[href="/personality"]').exists()).toBe(true);
    });
});
