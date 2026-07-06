// @vitest-environment jsdom
// 온보딩 하루 목표 프리셋 칩 [20][30][60][직접] (발견 10).
// 칩 선택 시 목표(분)를 emit하고, '직접'은 커스텀 입력 모드(null emit)로 넘긴다.
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import GoalPresets from '../src/onboarding/GoalPresets.vue';

describe('GoalPresets — 프리셋 칩 (발견 10)', () => {
    test('프리셋 3개(20/30/60)와 직접 칩을 렌더한다', () => {
        const w = mount(GoalPresets, { props: { initial: 30 } });
        const chips = w.findAll('.onb-preset-chip');
        expect(chips).toHaveLength(4);
        expect(w.text()).toContain('20분');
        expect(w.text()).toContain('30분');
        expect(w.text()).toContain('60분');
        expect(w.text()).toContain('직접');
    });

    test('초기값이 프리셋과 같으면 그 칩이 활성(aria-pressed)이다', () => {
        const w = mount(GoalPresets, { props: { initial: 30 } });
        const active = w.findAll('.onb-preset-chip').filter(c => c.attributes('aria-pressed') === 'true');
        expect(active).toHaveLength(1);
        expect(active[0].text()).toBe('30분');
    });

    test('초기값이 프리셋에 없으면 직접 칩이 활성이다', () => {
        const w = mount(GoalPresets, { props: { initial: 45 } });
        const active = w.findAll('.onb-preset-chip').filter(c => c.attributes('aria-pressed') === 'true');
        expect(active).toHaveLength(1);
        expect(active[0].text()).toBe('직접');
    });

    test('프리셋 칩 클릭 시 해당 분을 emit한다', async () => {
        const w = mount(GoalPresets, { props: { initial: 30 } });
        await w.findAll('.onb-preset-chip')[2].trigger('click'); // 60분
        expect(w.emitted('pick')).toBeTruthy();
        expect(w.emitted('pick')!.at(-1)).toEqual([60]);
    });

    test('직접 칩 클릭 시 null(커스텀 모드)을 emit한다', async () => {
        const w = mount(GoalPresets, { props: { initial: 30 } });
        await w.findAll('.onb-preset-chip')[3].trigger('click'); // 직접
        expect(w.emitted('pick')!.at(-1)).toEqual([null]);
    });
});
