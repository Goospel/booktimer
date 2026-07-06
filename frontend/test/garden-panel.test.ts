// @vitest-environment jsdom
// GardenPanel 자동 스크롤 제거 (발견 5) — 2.5초 setInterval 자동 이동을 없애고
// 사용자가 직접 넘기는 스크롤로 바꾼다. 마운트 시 타이머를 걸지 않아야 한다.
// overflow-x 전환(hidden→auto)은 순수 CSS라 jsdom 무의미 → 실 브라우저 게이트.
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import GardenPanel from '../src/dashboard/GardenPanel.vue';

const CATALOG = {
    ownedAuthorCharacterCount: 2,
    totalAuthorCharacterCount: 20,
    ownedCharacters: [
        { code: 'a', emoji: '📖', name: '카뮈', spriteId: '' },
        { code: 'b', emoji: '🕯️', name: '헤세', spriteId: '' },
    ],
};

afterEach(() => { vi.restoreAllMocks(); document.body.innerHTML = ''; });

describe('GardenPanel — 자동 스크롤 제거 (발견 5)', () => {
    test('마운트 시 자동 스크롤 타이머(setInterval)를 걸지 않는다 — 사용자가 직접 넘긴다', () => {
        const spy = vi.spyOn(globalThis, 'setInterval');
        mount(GardenPanel, { props: { garden: CATALOG }, attachTo: document.body });
        expect(spy).not.toHaveBeenCalled();
    });

    test('무대(stage)는 @scroll로 중앙 작가 이름을 갱신하는 스크롤 컨테이너로 유지된다', () => {
        const w = mount(GardenPanel, { props: { garden: CATALOG }, attachTo: document.body });
        expect(w.find('.dash-garden-stage').exists()).toBe(true);
        expect(w.find('.dash-garden-focus-name').exists()).toBe(true);
    });
});
