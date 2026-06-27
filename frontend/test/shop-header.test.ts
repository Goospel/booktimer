// @vitest-environment jsdom
// ShopHeader — 케밥 드롭다운(차단·신고) 단위 테스트
// 핵심 불변식:
//   1. other(self=false): 헤더 우상단 케밥 버튼 → 열면 [차단][신고] → emit 확인
//   2. self(self=true): 케밥·팔로우·차단·신고 전부 없음(누수 가드)
//   3. 메뉴 닫힘 상태에서 차단이 직접 헤더에 노출되지 않는다
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ShopHeader from '../src/profile/ShopHeader.vue';

interface TagChip { label: string; clickable: boolean }

function baseProps(override: Record<string, unknown> = {}) {
    return {
        nickname: '주인',
        loginId: 'owner',
        personalityTags: [] as TagChip[],
        followerCount: 3,
        followingCount: 2,
        self: false,
        following: false,
        ...override,
    };
}

describe('ShopHeader — other(self=false) 케밥 드롭다운', () => {
    test('케밥 버튼이 존재하고 초기엔 메뉴 목록이 닫혀 있다', () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        expect(wrapper.find('.shop-menu-btn').exists()).toBe(true);
        expect(wrapper.find('.shop-menu-list').exists()).toBe(false);
    });

    test('메뉴 닫힘 상태에서 헤더 shop-actions에 차단 버튼이 직접 없다', () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        const actions = wrapper.find('.shop-actions');
        if (actions.exists()) {
            const blockBtn = actions.findAll('button').find(b => b.text().includes('차단'));
            expect(blockBtn).toBeUndefined();
        }
    });

    test('케밥 클릭 → 메뉴 열림, 차단·신고 항목 노출', async () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        await wrapper.find('.shop-menu-btn').trigger('click');
        expect(wrapper.find('.shop-menu-list').exists()).toBe(true);
        expect(wrapper.find('.shop-menu-list').text()).toContain('차단');
        expect(wrapper.find('.shop-menu-list').text()).toContain('신고');
    });

    test('메뉴 내 차단 클릭 → doBlock emit', async () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        await wrapper.find('.shop-menu-btn').trigger('click');
        const blockItem = wrapper.findAll('.shop-menu-item').find(item => item.text().includes('차단'));
        await blockItem!.trigger('click');
        expect(wrapper.emitted('doBlock')).toBeTruthy();
    });

    test('메뉴 내 신고 클릭 → openReport emit', async () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        await wrapper.find('.shop-menu-btn').trigger('click');
        const reportItem = wrapper.findAll('.shop-menu-item').find(item => item.text().includes('신고'));
        await reportItem!.trigger('click');
        expect(wrapper.emitted('openReport')).toBeTruthy();
    });
});

describe('ShopHeader — self(self=true) 누수 가드', () => {
    test('self=true: 케밥·팔로우·차단·신고 전부 없음', () => {
        const wrapper = mount(ShopHeader, { props: baseProps({ self: true }) });
        expect(wrapper.find('.shop-menu-btn').exists()).toBe(false);
        expect(wrapper.find('.shop-menu-list').exists()).toBe(false);
        expect(wrapper.find('.shop-actions').exists()).toBe(false);
        const text = wrapper.text();
        expect(text).not.toContain('팔로우');
        expect(text).not.toContain('차단');
        expect(text).not.toContain('신고');
    });
});

describe('ShopHeader — 제목 닉네임만 표시', () => {
    test('제목에 닉네임이 보인다', () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        expect(wrapper.find('.shop-id-title').text()).toContain('주인');
    });

    test('제목이 닉네임만이고 "님의 책방"을 포함하지 않는다', () => {
        const wrapper = mount(ShopHeader, { props: baseProps() });
        const titleText = wrapper.find('.shop-id-title').text();
        expect(titleText).toBe('주인');
        expect(titleText).not.toContain('책방');
    });
});
