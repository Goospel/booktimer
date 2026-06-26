// @vitest-environment jsdom
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ContributionGraph from '../src/dashboard/ContributionGraph.vue';

const GRAPH = {
    weeks: [[{ date: '2026-06-25', totalSeconds: 600, level: 2, manual: false }]],
    monthLabels: [], totalSeconds: 600, activeDays: 1, currentStreak: 3,
    growthStageName: 'SPROUT', growthStageEmoji: '🌱', growthStageLabel: '새싹',
};

describe('ContributionGraph 독서 기록 진입점', () => {
    test('pill 라벨이 "독서 기록"이다', () => {
        const wrapper = mount(ContributionGraph, { props: { graph: GRAPH } });
        expect(wrapper.find('.dash-pill').text()).toBe('독서 기록');
    });
    test('"전체 기록 →" 링크가 /history로 간다', () => {
        const wrapper = mount(ContributionGraph, { props: { graph: GRAPH } });
        const link = wrapper.find('a[href="/history"]');
        expect(link.exists()).toBe(true);
        expect(link.text()).toContain('전체 기록');
    });
});
