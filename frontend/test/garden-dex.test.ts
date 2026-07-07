// @vitest-environment jsdom
// 서재 도감 풀 리디자인 (설계 §6.6, 잔여분 2번). GardenDex를:
//  ① 단일 체크박스 필터 → 상태 필터칩(전체/보유/미보유)
//  ② 텍스트 진척 → 시각 진행바(owned/total%)
//  ③ "🚧 베타 개발 중" 배너 제거
//  ④ 셀 클릭 → 캐릭터 상세시트(DexDetailSheet) 열림
// 백엔드 0 — AuthorCharacterDto 기존 필드만 사용. 순수 시각(스프라이트)은 실 브라우저 게이트.
import { describe, test, expect, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import GardenDex from '../src/garden/GardenDex.vue';

const CATALOG = {
    ownedAuthorCharacterCount: 2,
    totalAuthorCharacterCount: 4,
    authorCharacters: [
        { code: 'a', emoji: '📖', name: '한강', spriteId: null, owned: true, matchName: '한강', affection: 3, level: 2, title: '단짝' },
        { code: 'b', emoji: '🕯️', name: '헤세', spriteId: null, owned: true, matchName: '헤르만 헤세', affection: 0, level: 0, title: '' },
        { code: 'c', emoji: '🌊', name: '울프', spriteId: null, owned: false, matchName: '버지니아 울프', affection: 0, level: 0, title: '' },
        { code: 'd', emoji: '📕', name: '카뮈', spriteId: null, owned: false, matchName: '알베르 카뮈', affection: 0, level: 0, title: '' },
    ],
};

afterEach(() => { document.body.innerHTML = ''; });

function mountDex() {
    return mount(GardenDex, { props: { catalog: CATALOG }, attachTo: document.body });
}

describe('GardenDex — 서재 도감 풀 리디자인 (§6.6)', () => {
    test('상태 필터칩 3개(전체/보유/미보유)를 렌더한다', () => {
        const w = mountDex();
        const chips = w.findAll('.garden-tab');
        expect(chips.length).toBe(3);
        expect(chips.map(c => c.text())).toEqual(['전체', '보유', '미보유']);
    });

    test('기본은 전체 — 보유+미보유 셀 모두 렌더', () => {
        const w = mountDex();
        expect(w.findAll('.plant-cell').length).toBe(4);
    });

    test('"보유" 칩 → 미보유 셀은 사라지고 보유 셀만 남는다', async () => {
        const w = mountDex();
        await w.findAll('.garden-tab').find(c => c.text() === '보유')!.trigger('click');
        const cells = w.findAll('.plant-cell');
        expect(cells.length).toBe(2);
        expect(w.text()).toContain('한강');
        expect(w.text()).not.toContain('버지니아 울프'); // 미보유 잠금 라벨 부재
    });

    test('"미보유" 칩 → 보유 셀은 사라지고 미보유 셀만 남는다', async () => {
        const w = mountDex();
        await w.findAll('.garden-tab').find(c => c.text() === '미보유')!.trigger('click');
        expect(w.findAll('.plant-cell').length).toBe(2);
    });

    test('시각 진행바를 렌더하고 폭은 owned/total% (2/4 = 50%)', () => {
        const w = mountDex();
        const fill = w.find('.garden-meter-fill');
        expect(fill.exists()).toBe(true);
        expect(fill.attributes('style')).toContain('width: 50%');
    });

    test('"🚧 베타 개발 중" 배너를 더는 렌더하지 않는다', () => {
        const w = mountDex();
        expect(w.find('.garden-beta').exists()).toBe(false);
        expect(w.text()).not.toContain('개발 중');
    });

    test('셀 클릭 → 그 캐릭터 상세시트가 열린다', async () => {
        const w = mountDex();
        expect(w.find('.dex-detail-overlay').exists()).toBe(false);
        await w.findAll('.plant-cell')[0].trigger('click'); // 한강
        const sheet = w.find('.dex-detail-overlay');
        expect(sheet.exists()).toBe(true);
        expect(sheet.text()).toContain('한강');
    });

    test('상세시트 닫기 → 오버레이가 사라진다', async () => {
        const w = mountDex();
        await w.findAll('.plant-cell')[0].trigger('click');
        expect(w.find('.dex-detail-overlay').exists()).toBe(true);
        await w.find('.dex-detail-close').trigger('click');
        expect(w.find('.dex-detail-overlay').exists()).toBe(false);
    });
});
