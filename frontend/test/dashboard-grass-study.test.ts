// @vitest-environment jsdom
// 대시보드 잔디의 모드 전파 — 공부 모드 문구·링크·잉크 클래스, 그리고 **독서 기본값 양성 대조군**.
//
// 양성 대조군을 같은 파일에 둔 이유: PR-1에서 history/ContributionGraph의 mode 기본값을 'study'로
// 뒤집어도 546건이 전부 초록이었다 — 독서 문구를 보는 테스트가 0개였기 때문이다. 이번 PR이 같은 수법의
// prop을 **최다 사용 화면**(대시보드)에 넣으므로, 뒤집기를 죽이는 계측기를 처음부터 같이 세운다.
// (U1의 「기존 독서 13파일 diff 0줄」 계측기를 흐리지 않으려고 기존 contribution-graph.test.ts엔 안 덧붙인다.)
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ContributionGraph from '../src/dashboard/ContributionGraph.vue';

// 주 2개 — weeks[0]가 최신 주(왼쪽)라는 서버 규약을 밟는다. 한 주짜리 픽스처는 순서 회귀를 원리상 못 잡는다.
const GRAPH = {
    weeks: [
        [{ date: '2026-09-03', totalSeconds: 7200, level: 3, manual: false }],
        [{ date: '2026-08-27', totalSeconds: 1800, level: 1, manual: false }],
    ],
    monthLabels: [], totalSeconds: 9000, activeDays: 2, currentStreak: 3,
};

describe('대시보드 잔디 — 공부 모드', () => {
    const study = () => mount(ContributionGraph, { props: { graph: GRAPH, mode: 'study' as const } });

    test('pill·링크가 공부 기록을 가리킨다 (독서 링크는 사라진다)', () => {
        const w = study();
        expect(w.find('.dash-pill').text()).toBe('공부 기록');
        expect(w.find('a[href="/study/history"]').exists()).toBe(true);
        expect(w.find('a[href="/history"]').exists()).toBe(false);
    });

    test('루트 카드에 공부 잉크(is-study)가 붙는다', () => {
        expect(study().find('.dash-grass-card').classes()).toContain('is-study');
    });

    test('범례는 「적게…많이」이고 「직접 채움」 스와치가 없다 (공부엔 수동 기록이 없다)', () => {
        const w = study();
        const text = w.text();
        expect(text).toContain('적게');
        expect(text).toContain('많이');
        expect(text).not.toContain('목표 미달');
        expect(text).not.toContain('직접 채움');
        expect(w.findAll('.dash-grass-cell.manual')).toHaveLength(0);
    });

    test('연속·활동일 문구가 「공부」로 바뀐다', () => {
        const text = study().text();
        expect(text).toContain('3일 연속 공부');
        expect(text).toContain('2일 공부');
        expect(text).not.toContain('연속 독서');
    });

    test('셀 순서는 그대로 — 첫 셀이 최신 주(weeks[0], level 3 → s4)다', () => {
        expect(study().findAll('.dash-grass-grid .dash-grass-cell')[0].classes()).toContain('s4');
    });
});

describe('대시보드 잔디 — 독서 기본값 양성 대조군 (mode 기본값이 뒤집히면 여기서 죽는다)', () => {
    const reading = () => mount(ContributionGraph, { props: { graph: GRAPH } });   // mode 생략

    test('pill·링크가 독서 기록을 가리킨다', () => {
        const w = reading();
        expect(w.find('.dash-pill').text()).toBe('독서 기록');
        expect(w.find('a[href="/history"]').exists()).toBe(true);
        expect(w.find('a[href="/study/history"]').exists()).toBe(false);
    });

    test('루트 카드에 is-study가 없다', () => {
        expect(reading().find('.dash-grass-card').classes()).not.toContain('is-study');
    });

    test('독서 문구 전부 — 연속 독서·목표 미달/달성·직접 채움 스와치', () => {
        const w = reading();
        const text = w.text();
        expect(text).toContain('3일 연속 독서');
        expect(text).toContain('2일 독서');
        expect(text).toContain('목표 미달');
        expect(text).toContain('목표 달성');
        expect(text).toContain('직접 채움');
        expect(w.findAll('.dash-grass-cell.manual')).toHaveLength(1);   // 범례 스와치 1개
    });
});
