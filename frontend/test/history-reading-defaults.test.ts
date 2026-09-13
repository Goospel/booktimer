// @vitest-environment jsdom
// 독서 기본값 양성 대조군 — /history의 잔디·월별 목록은 prop 기본값(mode='reading' / emptyText)으로만
// 독서 문구를 얻는다. 그 기본값을 'study'로 뒤집으면 /history 잔디가 통째로 공부 문구가 되는데도
// 기존 546건이 전부 초록이었다: 어느 테스트도 history/ContributionGraph의 문구를 안 봤다
// (contribution-graph.test.ts는 대시보드 컴포넌트를 본다). 즉 「독서 테스트 무수정 green」이 확정한 것은
// 「픽스처가 mode를 모른다」뿐이고 그건 처음부터 참이라 아무것도 배제하지 않았다. 배제는 여기가 맡는다.
//
// 파일을 따로 둔 이유: U1의 계측기가 「기존 독서 테스트 13파일 diff 0줄」이라, 그중 하나인
// history-app.test.ts에 덧붙이면 그 계측기 자체가 흐려진다.
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ContributionGraph from '../src/history/ContributionGraph.vue';
import MonthlyRecords from '../src/history/MonthlyRecords.vue';

// currentStreak > 0 이어야 연속 뱃지(「N일 연속 독서」)가 렌더된다.
const READING_GRAPH = {
    weeks: [
        [
            { date: null, totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-15', totalSeconds: 3600, level: 2, manual: false },
            { date: '2026-06-16', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-17', totalSeconds: 1800, level: 1, manual: true },
            { date: '2026-06-18', totalSeconds: 7200, level: 4, manual: false },
            { date: '2026-06-19', totalSeconds: 0, level: 0, manual: false },
            { date: '2026-06-20', totalSeconds: 0, level: 0, manual: false },
        ],
    ],
    monthLabels: [{ weekIndex: 0, label: '6월' }],
    totalSeconds: 12600,
    activeDays: 3,
    currentStreak: 1,
};

describe('독서 기본값 양성 대조군', () => {
    test('/history 잔디는 독서 문구를 쓴다 — mode 기본값이 뒤집히면 여기서 죽는다', () => {
        const wrapper = mount(ContributionGraph, { props: { graph: READING_GRAPH } });   // mode 생략
        const text = wrapper.text();

        expect(wrapper.findAll('h2').map((h) => h.text())).toContain('독서 잔디');
        expect(text).toContain('일 연속 독서');
        expect(text).toContain('목표 미달');
        expect(text).toContain('목표 달성');
        expect(text).toContain('직접 채움');      // 독서 전용 스와치 — 공부 모드엔 없다
    });

    test('/history 월별 목록의 빈 상태는 독서 문구를 쓴다 — emptyText 기본값이 뒤집히면 여기서 죽는다', () => {
        const wrapper = mount(MonthlyRecords, { props: { months: [] } });                 // emptyText 생략

        expect(wrapper.text()).toContain('아직 독서 기록이 없습니다');
    });
});
