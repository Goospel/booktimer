// @vitest-environment jsdom
// DexDetailSheet — 도감 캐릭터 상세 바텀시트 (§6.6). 백엔드 0: AuthorCharacterDto 필드만 사용.
//  · 보유: 이름 + Lv·칭호 + 정(affection) 진행바(pure.ts affectionProgress 재사용)
//  · 미보유: 해금 힌트("『작가』…완독하면") + 진행바 없음
//  · 닫기 버튼 → close emit
import { describe, test, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import DexDetailSheet from '../src/garden/DexDetailSheet.vue';

const OWNED = { code: 'a', emoji: '📖', name: '한강', spriteId: null, owned: true, matchName: '한강', affection: 3, level: 2, title: '단짝' };
const LOCKED = { code: 'c', emoji: '🌊', name: '울프', spriteId: null, owned: false, matchName: '버지니아 울프', affection: 0, level: 0, title: '' };

describe('DexDetailSheet — 캐릭터 상세 (§6.6)', () => {
    test('보유 캐릭터: 이름·레벨·정 진행바를 보여준다', () => {
        const w = mount(DexDetailSheet, { props: { character: OWNED } });
        expect(w.text()).toContain('한강');
        expect(w.text()).toContain('Lv2');
        expect(w.find('.garden-meter-fill').exists()).toBe(true);
    });

    test('미보유 캐릭터: 해금 힌트를 보여주고 진행바는 없다', () => {
        const w = mount(DexDetailSheet, { props: { character: LOCKED } });
        expect(w.text()).toContain('완독');       // "…완독하면 …찾아와요" 해금 힌트
        expect(w.text()).toContain('버지니아 울프'); // 매치 작가명 노출
        expect(w.find('.garden-meter-fill').exists()).toBe(false);
    });

    test('닫기 버튼 → close 이벤트를 emit한다', async () => {
        const w = mount(DexDetailSheet, { props: { character: OWNED } });
        await w.find('.dex-detail-close').trigger('click');
        expect(w.emitted('close')).toBeTruthy();
    });

    // ESC는 위 레이어(상세시트)만 닫아야 한다 — 상위 도감 오버레이(VillageApp document ESC 리스너)까지
    // 함께 닫히지 않도록 전파를 막는다(중첩 모달 한 레벨 닫기).
    test('ESC → close emit하되 상위 document로 전파되지 않는다', () => {
        const parentSaw = vi.fn();
        document.addEventListener('keydown', parentSaw);
        const w = mount(DexDetailSheet, { props: { character: OWNED }, attachTo: document.body });
        const panel = w.find('.dex-detail-panel').element as HTMLElement;
        panel.focus();
        panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        expect(w.emitted('close')).toBeTruthy();
        expect(parentSaw).not.toHaveBeenCalled();
        document.removeEventListener('keydown', parentSaw);
        w.unmount();
    });
});
