// @vitest-environment jsdom
// StudyTimerCard — 일시정지(설계 2026-10-20-study-timer-keyboard-shortcut §6-3·§7 Task 2).
//
// 계측기 메모
//  · 이 파일은 afterEach에서 카드를 전부 언마운트한다 — 기존 study-timer-card.test.ts는 언마운트하지 않아 앞 카드의 창 리스너가
//    남고, 그러면 「조합을 삼킨다(defaultPrevented)」 단언이 남의 리스너로 공허하게 통과한다.
//  · jsdom은 글자 입력을 재현하지 못한다(합성 keydown은 글자를 넣지 않는다) — 「본문에 π·ㅔ가 안 들어갔다」는 여기서 단언하지 않는다(U1~U4).
//  · 통과가 확정하는 것: 멈춘 동안 잰 시간이 서 있고 이어진다 · 버튼·알약 문구 · 창 캡처 리스너가 조합을 삼키고 상태 조건을 지킨다 · 언마운트하면 떨어진다.
//  · 실패가 배제하는 것: 열린 구간을 무시(시계가 계속 감) · 대기·왕복 중 오발 · 키 반복 연타 · 독서 모드로 새는 리스너.
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';

const mounted: { unmount: () => void }[] = [];
afterEach(() => { mounted.splice(0).forEach(w => w.unmount()); vi.useRealTimers(); document.body.innerHTML = ''; });

function mountCard(props: Record<string, unknown>) {
    const w = mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, ...props },
        attachTo: document.body,
    });
    mounted.push(w);
    return w;
}
const iso = (ms: number) => new Date(ms).toISOString();
const altP = (o: KeyboardEventInit = {}) => {
    const e = new KeyboardEvent('keydown', { code: 'KeyP', key: 'p', altKey: true, bubbles: true, cancelable: true, ...o });
    window.dispatchEvent(e);
    return e;
};
const pauseBtn = (w: ReturnType<typeof mountCard>) => w.find('[data-testid="focus-pause"]');
const pill = (w: ReturnType<typeof mountCard>) => w.find('[data-testid="focus-state"]');
const session = (w: ReturnType<typeof mountCard>) => w.find('[data-testid="focus-session"] .dash-kv-v').text();
const BOOK = { id: 5, title: '헌법', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0, sessionGoalSeconds: 900 };

describe('StudyTimerCard — 일시정지', () => {
    test('REQ-04 · 멈춘 동안 오늘·이번 측정·회당 남은 시간이 멈춘 순간 값에 서 있고, 다시 시작하면 그 값에서 이어진다', async () => {
        vi.useFakeTimers();
        const now = Date.now();
        const w = mountCard({
            hasActiveSession: true, activeStartedAt: iso(now - 600_000), activeBook: BOOK, books: [BOOK],
            pausedFrom: iso(now - 300_000), pausedSeconds: 0,
        });
        expect(session(w)).toBe('05:00');
        expect(w.find('[data-testid="focus-today"]').text()).toBe('05:00');
        expect(w.find('.dash-session-line .dash-kv-v').text()).toBe('10:00');

        await vi.advanceTimersByTimeAsync(60_000);
        expect(session(w)).toBe('05:00');
        expect(w.find('.dash-session-line .dash-kv-v').text()).toBe('10:00');

        await w.setProps({ pausedFrom: null, pausedSeconds: 360 });
        expect(session(w)).toBe('05:00');
        await vi.advanceTimersByTimeAsync(1_000);
        expect(session(w)).toBe('05:01');
    });

    test('REQ-05 · 일시정지 버튼은 togglePause를 내고, 멈춘 동안엔 「다시 시작」·정적인 「일시정지」 알약(맥박 점 없음)이다', async () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        expect(pauseBtn(w).text()).toContain('일시정지');
        expect(pill(w).text()).toBe('측정 중');
        expect(pill(w).find('.dash-pulse-dot').exists()).toBe(true);
        await pauseBtn(w).trigger('click');
        expect(w.emitted('togglePause')).toHaveLength(1);

        await w.setProps({ pausedFrom: iso(Date.now()) });
        expect(pauseBtn(w).text()).toContain('다시 시작');
        expect(pill(w).text()).toBe('일시정지');
        expect(pill(w).find('.dash-pulse-dot').exists()).toBe(false);
        expect(pill(w).classes()).not.toContain('dash-pill-pulse');
        // 「측정 종료」 바로 앞에 선다
        const actions = w.findAll('.focus-actions button');
        expect(actions[actions.length - 2].attributes('data-testid')).toBe('focus-pause');
    });

    test('REQ-05 · 종료 왕복 중엔 일시정지 버튼이 잠긴다', async () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        expect(pauseBtn(w).attributes('disabled')).toBeUndefined();
        await w.setProps({ stopping: true });
        expect(pauseBtn(w).attributes('disabled')).toBeDefined();
    });

    test('REQ-07 · 측정 중 Alt+P는 togglePause를 내고 조합을 삼킨다 — 키 반복·시작/종료 왕복 중엔 내지 않는다', async () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        const e = altP();
        expect(e.defaultPrevented).toBe(true);
        expect(w.emitted('togglePause')).toHaveLength(1);

        expect(altP({ repeat: true }).defaultPrevented).toBe(true);
        expect(w.emitted('togglePause')).toHaveLength(1);

        await w.setProps({ stopping: true });
        altP();
        await w.setProps({ stopping: false, starting: true });
        altP();
        expect(w.emitted('togglePause')).toHaveLength(1);

        await w.setProps({ starting: false });
        altP({ key: 'Process', keyCode: 229, isComposing: true } as KeyboardEventInit);   // 한글 조합 중 모양
        expect(w.emitted('togglePause')).toHaveLength(2);

        // 다른 조합은 건드리지 않는다(대조)
        const other = altP({ code: 'KeyO', key: 'o' });
        expect(other.defaultPrevented).toBe(false);
        expect(w.emitted('togglePause')).toHaveLength(2);
    });

    test('REQ-07 · 대기 중 Alt+P는 아무것도 내지 않지만 조합은 삼킨다(맥 π 입력 방지)', () => {
        const w = mountCard({});
        expect(altP().defaultPrevented).toBe(true);
        expect(w.emitted('togglePause')).toBeUndefined();
    });

    test('REQ-07 · 언마운트하면 더는 듣지 않는다(독서 모드로 바뀐 뒤 새는 리스너 방지)', () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        expect(altP().defaultPrevented).toBe(true);   // 양성 대조
        w.unmount(); mounted.splice(mounted.indexOf(w), 1);
        expect(altP().defaultPrevented).toBe(false);
    });

    test('REQ-08 · 버튼에 aria-keyshortcuts="Alt+P"·보이는 힌트 Alt+P(aria-hidden)·이모지 0, 알약은 role=status이고 멈춰도 같은 요소다', async () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        expect(pauseBtn(w).attributes('aria-keyshortcuts')).toBe('Alt+P');
        const kbd = pauseBtn(w).find('.focus-kbd');
        expect(kbd.text()).toBe('Alt+P');   // jsdom navigator.platform = ''
        expect(kbd.attributes('aria-hidden')).toBe('true');
        expect(w.find('.focus-bar').text()).not.toMatch(/\p{Extended_Pictographic}/u);
        const before = pill(w);
        expect(before.attributes('role')).toBe('status');
        await w.setProps({ pausedFrom: iso(Date.now()) });
        expect(pill(w).element).toBe(before.element);
    });
});
