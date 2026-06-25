// @vitest-environment jsdom
// DashHeader 사용자 메뉴(아바타 드롭다운) 동작 테스트 — 열림/닫힘 상태기계와 항목 일원화 확인.
// 메뉴 위치·그림자 등 순수 시각은 jsdom 무의미 → 실 브라우저 게이트(N-083). 여기선 동작·접근성·항목만.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import DashHeader from '../src/dashboard/DashHeader.vue';

let wrapper: VueWrapper | null = null;
function make() {
    wrapper = mount(DashHeader, { props: { loginId: 'testid' }, attachTo: document.body });
    return wrapper;
}
afterEach(() => { wrapper?.unmount(); wrapper = null; });

describe('DashHeader 사용자 메뉴', () => {
    test('초기엔 메뉴가 닫혀 있고 aria-expanded=false', () => {
        const w = make();
        expect(w.find('[role="menu"]').exists()).toBe(false);
        expect(w.find('.dash-header-avatar').attributes('aria-expanded')).toBe('false');
    });

    test('아바타 클릭 → 메뉴 열림 + 설정·문의·로그아웃 일원화', async () => {
        const w = make();
        await w.find('.dash-header-avatar').trigger('click');

        expect(w.find('[role="menu"]').exists()).toBe(true);
        expect(w.find('.dash-header-avatar').attributes('aria-expanded')).toBe('true');

        // 설정·문의는 링크
        const hrefs = w.findAll('a[role="menuitem"]').map(a => a.attributes('href'));
        expect(hrefs).toContain('/settings');
        expect(hrefs).toContain('/feedback');

        // 로그아웃은 POST /logout form + CSRF
        const form = w.find('form[action="/logout"]');
        expect(form.exists()).toBe(true);
        expect(form.attributes('method')).toBe('post');
        expect(form.find('input[name="_csrf"]').exists()).toBe(true);
        expect(form.find('button[type="submit"]').text()).toContain('로그아웃');
    });

    test('다시 클릭하면 닫힘(토글)', async () => {
        const w = make();
        const avatar = w.find('.dash-header-avatar');
        await avatar.trigger('click');
        expect(w.find('[role="menu"]').exists()).toBe(true);
        await avatar.trigger('click');
        expect(w.find('[role="menu"]').exists()).toBe(false);
    });

    test('Escape 키 → 메뉴 닫힘', async () => {
        const w = make();
        await w.find('.dash-header-avatar').trigger('click');
        expect(w.find('[role="menu"]').exists()).toBe(true);

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        await w.vm.$nextTick();
        expect(w.find('[role="menu"]').exists()).toBe(false);
    });

    test('메뉴 바깥 클릭 → 닫힘', async () => {
        const w = make();
        await w.find('.dash-header-avatar').trigger('click');
        expect(w.find('[role="menu"]').exists()).toBe(true);

        document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        await w.vm.$nextTick();
        expect(w.find('[role="menu"]').exists()).toBe(false);
    });

    test('메뉴 내부 클릭은 닫지 않음(설정 링크 클릭 등)', async () => {
        const w = make();
        await w.find('.dash-header-avatar').trigger('click');
        const settings = w.findAll('a[role="menuitem"]').find(a => a.attributes('href') === '/settings');
        // 내부 클릭이 바깥클릭 핸들러로 새어 닫지 않아야 함(이동은 jsdom에서 막음)
        await settings!.trigger('click');
        expect(w.find('[role="menu"]').exists()).toBe(true);
    });
});
