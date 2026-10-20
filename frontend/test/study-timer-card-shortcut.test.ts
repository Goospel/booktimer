// @vitest-environment jsdom
// StudyTimerCard — 측정 시작·종료 단축키 Alt+P(설계 2026-10-20-study-timer-start-stop-shortcut §7 Task 0·1).
//
// 계측기 메모
//  · 이 파일은 afterEach에서 카드를 전부 언마운트한다 — study-timer-card.test.ts는 언마운트하지 않아 앞 카드의 창 리스너가
//    남고, 그러면 「조합을 삼킨다(defaultPrevented)」 단언이 남의 리스너로 공허하게 통과한다.
//  · jsdom은 글자 입력을 재현하지 못한다 — 「본문에 π·ㅔ가 안 들어갔다」는 여기서 단언하지 않는다(U1·U2).
import { describe, test, expect, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';

const mounted: { unmount: () => void }[] = [];
afterEach(() => { mounted.splice(0).forEach(w => w.unmount()); document.body.innerHTML = ''; });

function mountCard(props: Record<string, unknown>) {
    const w = mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, ...props },
        attachTo: document.body,
    });
    mounted.push(w);
    return w;
}
const iso = (ms: number) => new Date(ms).toISOString();

describe('StudyTimerCard — 측정 시작·종료 단축키', () => {
    test('REQ-01 · 측정 중 막대엔 「일시정지」·「다시 시작」이 없고 중단 수단은 「측정 종료」 하나다', () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        const labels = w.findAll('.focus-actions button').map(b => b.text());
        expect(labels.some(t => t.includes('측정 종료'))).toBe(true);   // 양성 대조 — 막대를 실제로 읽고 있다
        expect(labels.some(t => t.includes('일시정지') || t.includes('다시 시작'))).toBe(false);
        expect(w.find('[data-testid="focus-pause"]').exists()).toBe(false);
    });
});
