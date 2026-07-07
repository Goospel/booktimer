// @vitest-environment jsdom
// DexCell — 도감 셀. 리디자인(§6.6)으로 셀을 클릭 가능한 버튼으로 승격해
// 클릭 시 상세시트를 열도록 select 이벤트를 emit한다(키보드 접근성 = 네이티브 button).
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DexCell from '../src/garden/DexCell.vue';

const baseProps = {
    isOwned: true,
    name: '한강',
    emoji: '📖',
    spriteId: null as string | null,
    lockedLabel: '한강',
    cellTitle: '한강',
};

describe('DexCell — 클릭 가능한 셀 (§6.6)', () => {
    test('셀은 네이티브 button 이다 (키보드·포커스 접근성)', () => {
        const w = mount(DexCell, { props: baseProps });
        expect(w.find('button.plant-cell').exists()).toBe(true);
    });

    test('클릭하면 select 이벤트를 emit한다', async () => {
        const w = mount(DexCell, { props: baseProps });
        await w.find('.plant-cell').trigger('click');
        expect(w.emitted('select')).toBeTruthy();
    });
});
