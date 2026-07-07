// @vitest-environment jsdom
// TimerCard 우패널 3상태(measuring/idle/achieved) 렌더 분기 테스트.
// 핵심 회귀 가드: 달성(measuring 아님 + isAchieved)해도 '측정 시작' 폼이 사라지지 않아야 한다
// — 디자인 변경 때 achieved 패널이 격려 메시지만 렌더해 시작 버튼이 증발한 회귀를 막는다.
// 패널 위치·색 등 순수 시각은 jsdom 무의미 → 실 브라우저 게이트. 여기선 어떤 컨트롤이 뜨는지만.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import TimerCard from '../src/dashboard/TimerCard.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const books: BookOption[] = [{ id: 1, title: '데미안' }];

// 달성 = remainingSeconds 0(오늘 목표를 다 채워 남은 시간 0), 측정 안 함
function baseProps(over: Record<string, unknown> = {}) {
    return {
        remainingSeconds: 0,
        carriedDebtSeconds: 0,
        todayGoalSeconds: 3600,
        carryover: false,
        streak: 3,
        hasActiveSession: false,
        activeStartedAt: null,
        activeBookTitle: null,
        activeBookTotalSeconds: 0,
        readingBooks: books,
        finishedBooks: [] as BookOption[],
        wantToReadBooks: [] as BookOption[],
        recentBookId: 1,
        ...over,
    };
}
function make(over: Record<string, unknown> = {}) {
    wrapper = mount(TimerCard, { props: baseProps(over) as never, attachTo: document.body });
    return wrapper;
}

describe('TimerCard 달성 상태', () => {
    test('미측정 + 달성 → 격려 메시지와 측정 시작 폼이 함께 보인다', () => {
        const w = make(); // remainingSeconds=0 → 달성, hasActiveSession=false
        // 격려 메시지 유지
        expect(w.text()).toContain('오늘도 약속을 지켰어요');
        // 측정 시작 버튼이 사라지지 않아야 함(회귀 가드)
        const startBtn = w.findAll('button').find(b => b.text().includes('측정 시작'));
        expect(startBtn).toBeTruthy();
        // 드롭다운 → 표지 칩으로 교체(§6.5). 달성 패널에도 책 칩이 함께 보인다.
        expect(w.find('.dash-book-chip').exists()).toBe(true);
    });

    test('달성 상태에서 측정 시작 → start 이벤트가 선택한 책 id로 발생', async () => {
        const w = make();
        const startBtn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await startBtn.trigger('click');
        expect(w.emitted('start')).toBeTruthy();
        expect(w.emitted('start')![0]).toEqual([1]);
    });
});

describe('TimerCard 회귀 가드', () => {
    test('미측정 + 미달성(idle) → 측정 시작 폼만, 격려 없음', () => {
        const w = make({ remainingSeconds: 1800 }); // 절반 남음 → 미달성
        expect(w.findAll('button').some(b => b.text().includes('측정 시작'))).toBe(true);
        expect(w.text()).not.toContain('오늘도 약속을 지켰어요');
    });

    test('측정 중(measuring) → 종료 버튼만, 시작 폼 없음', () => {
        const w = make({ hasActiveSession: true, activeStartedAt: '2026-06-25T00:00:00Z', activeBookTitle: '데미안' });
        expect(w.findAll('button').some(b => b.text().includes('측정 종료'))).toBe(true);
        expect(w.findAll('button').some(b => b.text().includes('측정 시작'))).toBe(false);
    });
});

// 시작/종료 진행 중 피드백 — 서버 왕복 동안 버튼이 멈춰 보이지 않게 "진행 중"을 표시하고
// 중복 클릭(409)을 막는다. 패널 크로스페이드는 순수 시각이라 jsdom 무의미 → 실 브라우저 게이트.
describe('TimerCard 진행 중 피드백', () => {
    test('starting=true → 시작 버튼 비활성 + "시작하는 중…"', () => {
        const w = make({ remainingSeconds: 1800, starting: true }); // idle
        const btn = w.findAll('button').find(b => b.text().includes('시작'));
        expect(btn).toBeTruthy();
        expect(btn!.attributes('disabled')).toBeDefined();
        expect(btn!.text()).toContain('시작하는 중');
    });

    test('starting=false(기본) → "측정 시작", 활성', () => {
        const w = make({ remainingSeconds: 1800 });
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        expect(btn.attributes('disabled')).toBeUndefined();
    });

    test('달성 패널에서도 starting=true면 시작 버튼 비활성', () => {
        const w = make({ starting: true }); // remainingSeconds=0 → achieved
        const btn = w.findAll('button').find(b => b.text().includes('시작'))!;
        expect(btn.attributes('disabled')).toBeDefined();
    });

    test('stopping=true(measuring) → 종료 버튼 비활성 + "종료하는 중…"', () => {
        const w = make({ hasActiveSession: true, activeStartedAt: '2026-06-25T00:00:00Z', activeBookTitle: '데미안', stopping: true });
        const btn = w.findAll('button').find(b => b.text().includes('종료'))!;
        expect(btn.attributes('disabled')).toBeDefined();
        expect(btn.text()).toContain('종료하는 중');
    });
});
