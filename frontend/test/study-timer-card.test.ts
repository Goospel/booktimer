// @vitest-environment jsdom
// StudyTimerCard — 공부 히어로(카운트업 + 세션 경과 + 시작/종료).
// 1차 범위는 미니앱 1차와 같은 선: 목표 게이지·책 선택·태깅은 없다(부재가 규칙 — 억제 코드 0줄).
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = ''; });

function mountCard(props: Record<string, unknown>) {
    return mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, ...props },
        attachTo: document.body,
    });
}

describe('StudyTimerCard — idle', () => {
    test('오늘 공부한 시간을 세고, 시작 버튼만 있다(책 칩·바꾸기·종료 없음)', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 3600 });

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-timer-num').text()).toBe('01:00:00');
        const texts = w.findAll('button').map(b => b.text());
        expect(texts.some(t => t.includes('공부 측정 시작'))).toBe(true);
        expect(texts.some(t => t.includes('측정 종료'))).toBe(false);
        expect(texts.some(t => t.includes('바꾸기'))).toBe(false);
        expect(w.find('.dash-book-chip').exists()).toBe(false);
    });

    test('시작 버튼 클릭 → start emit, starting이면 비활성 + "시작하는 중…"', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0 });
        await w.findAll('button').find(b => b.text().includes('공부 측정 시작'))!.trigger('click');
        expect(w.emitted('start')).toHaveLength(1);

        await w.setProps({ starting: true });
        const btn = w.findAll('button').find(b => b.text().includes('시작하는 중'))!;
        expect(btn.attributes('disabled')).toBeDefined();
    });
});

describe('StudyTimerCard — 측정 중', () => {
    test('세션 경과와 오늘 누적(완료 합 + 경과)을 동시에 보여주고, 종료를 emit한다', async () => {
        vi.useFakeTimers();
        const startedAt = new Date(Date.now() - 65_000).toISOString();
        const w = mountCard({ todaySeconds: 3600, hasActiveSession: true, activeStartedAt: startedAt });

        expect(w.find('.dash-session-time').text()).toBe('01:05');
        expect(w.find('.dash-timer-num').text()).toBe('01:01:05');

        await w.findAll('button').find(b => b.text().includes('측정 종료'))!.trigger('click');
        expect(w.emitted('stop')).toHaveLength(1);
    });
});

describe('StudyTimerCard — 표현 규칙', () => {
    test('기본 이모지를 쓰지 않는다(독서 카드의 🌿·🌱 문법을 물려받지 않는다)', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 120, hasActiveSession: true, activeStartedAt: new Date().toISOString() });
        expect(w.text()).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    });
});
