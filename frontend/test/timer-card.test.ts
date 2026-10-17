// @vitest-environment jsdom
// TimerCard 우패널 3상태(measuring/idle/achieved) 렌더 분기 테스트.
// 핵심 회귀 가드: 달성(measuring 아님 + isAchieved)해도 '측정 시작' 폼이 사라지지 않아야 한다
// — 디자인 변경 때 achieved 패널이 격려 메시지만 렌더해 시작 버튼이 증발한 회귀를 막는다.
// 패널 위치·색 등 순수 시각은 jsdom 무의미 → 실 브라우저 게이트. 여기선 어떤 컨트롤이 뜨는지만.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import TimerCard from '../src/dashboard/TimerCard.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const books: BookOption[] = [{ id: 1, title: '데미안' }];

// 달성 = 오늘 읽은 초가 목표에 닿음(todayReadSeconds ≥ todayGoalSeconds), 측정 안 함.
// 판정 출처가 부채(remainingSeconds)에서 읽은 초로 옮겨졌다 — 부채는 0에서 바닥을 쳐 초과분을 못 실었다.
function baseProps(over: Record<string, unknown> = {}) {
    return {
        remainingSeconds: 0,
        carriedDebtSeconds: 0,
        todayGoalSeconds: 3600,
        todayReadSeconds: 3600,
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
        const w = make(); // todayReadSeconds=goal → 달성, hasActiveSession=false
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
        const w = make({ remainingSeconds: 1800, todayReadSeconds: 1800 }); // 절반만 읽음 → 미달성
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
        const w = make({ remainingSeconds: 1800, todayReadSeconds: 1800, starting: true }); // idle
        const btn = w.findAll('button').find(b => b.text().includes('시작'));
        expect(btn).toBeTruthy();
        expect(btn!.attributes('disabled')).toBeDefined();
        expect(btn!.text()).toContain('시작하는 중');
    });

    test('starting=false(기본) → "측정 시작", 활성', () => {
        const w = make({ remainingSeconds: 1800, todayReadSeconds: 1800 });
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        expect(btn.attributes('disabled')).toBeUndefined();
    });

    test('달성 패널에서도 starting=true면 시작 버튼 비활성', () => {
        const w = make({ starting: true }); // todayReadSeconds=goal → achieved
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

// R2 — 측정 중 패널. 책 없이 재는 중이면 「지금 읽는 책」이 빈칸이고 누적이 「0시간 0분」이던 자리(P11),
// 그리고 측정 중에 책을 바꾸는 문이 웹에만 없던 자리(P4 — 공부 StudyTimerCard엔 있다).
describe('TimerCard 측정 중 — 책 없이 · 책 바꾸기 (R2)', () => {
    const measuring = { hasActiveSession: true, activeStartedAt: '2026-06-25T00:00:00Z' };

    test('책 없이 측정 중이면 「책 없이」라고 말하고 누적 줄은 없다', () => {
        const w = make({ ...measuring, activeBookTitle: null });
        expect(w.find('.dash-kv-v').text()).toBe('책 없이');
        expect(w.text()).not.toContain('이 책 누적 독서');
    });

    test('책이 있으면 제목과 누적 줄이 선다 (양성 대조)', () => {
        const w = make({ ...measuring, activeBookTitle: '데미안', activeBookTotalSeconds: 3900 });
        expect(w.find('.dash-kv-v').text()).toBe('데미안');
        expect(w.text()).toContain('1시간 5분');
    });

    test('[책 바꾸기] → changeBook을 올린다 — 시트를 여는 건 부모다', async () => {
        const w = make({ ...measuring, activeBookTitle: '데미안' });
        await w.findAll('button').find(b => b.text() === '책 바꾸기')!.trigger('click');
        expect(w.emitted('changeBook')).toHaveLength(1);
        expect(w.emitted('stop')).toBeUndefined();
    });

    test('changing=true면 [책 바꾸기]가 잠긴다(교체 왕복 중)', () => {
        const w = make({ ...measuring, activeBookTitle: '데미안', changing: true });
        expect(w.findAll('button').find(b => b.text() === '책 바꾸기')!.attributes('disabled')).toBeDefined();
    });
});

// PR-3 — 아치 표제창 + 오늘 표. 시계 길이 클래스 · 밀린 시간 안내 자리 · 측정 중 검정 알약.
describe('REQ-16 · 홈 아치 · 오늘 표', () => {
    const measuring = { hasActiveSession: true, activeStartedAt: '2026-06-25T00:00:00Z', activeBookTitle: '데미안' };

    test('시계는 5자면 is-long 없음, 1시간 넘은 8자면 is-long', () => {
        const short = make({ todayReadSeconds: 42 * 60 + 15, remainingSeconds: 3600 });
        expect(short.find('.dash-timer-num').text()).toBe('42:15');
        expect(short.find('.dash-timer-num').classes()).not.toContain('is-long');
        short.unmount(); wrapper = null;
        const long = make({ todayReadSeconds: 3700, todayGoalSeconds: 7200 });
        expect(long.find('.dash-timer-num').text()).toBe('01:01:40');
        expect(long.find('.dash-timer-num').classes()).toContain('is-long');
    });

    test('밀린 시간 안내는 오늘 표(오른쪽) 끝에 선다 — 아치 안엔 없다', () => {
        const w = make({ carriedDebtSeconds: 600, todayReadSeconds: 0 });
        expect(w.findAll('.dash-timer-right .dash-forgive-note')).toHaveLength(1);
        expect(w.findAll('.dash-timer-left .dash-forgive-note')).toHaveLength(0);
        expect(w.find('.dash-timer-right').element.lastElementChild?.classList.contains('dash-forgive-note')).toBe(true);
    });

    test('밀린 시간이 없으면 안내도 없다(양성 쌍)', () => {
        const w = make({ todayReadSeconds: 0 });
        expect(w.findAll('.dash-forgive-note')).toHaveLength(0);
    });

    test('REQ-22 · 홈 측정 중 검정 알약은 「측정 종료」 하나', () => {
        const w = make(measuring);
        const black = w.findAll('.btn-primary, .dash-btn-fill, .auth-btn-primary');
        expect(black.map((b) => b.text())).toEqual(['측정 종료']);
    });
});

// 미니앱과 같은 자리·같은 획(ui.tsx SPROUT_PATHS — editorialTheme.test.ts가 문자열을 대조한다). 꾸밈이라 화면 읽기에선 빠진다.
describe('REQ-18 · 목표 달성 표식은 선 새싹·잉크 도장 SVG다', () => {
    test('달성: 눈썹에 선 새싹 1(획 3) · 달성 칸에 도장 1(원 2 + 획 3) · 이모지 칸 0', () => {
        const w = make();
        const sprout = w.findAll('svg[data-sprout]');
        expect(sprout).toHaveLength(1);
        expect(sprout[0].attributes('aria-hidden')).toBe('true');
        expect(sprout[0].findAll('path')).toHaveLength(3);
        expect(w.find('.dash-pill').text()).toBe('오늘 목표 달성');

        const stamp = w.findAll('svg[data-goal-stamp]');
        expect(stamp).toHaveLength(1);
        expect(stamp[0].attributes('aria-hidden')).toBe('true');
        expect(stamp[0].findAll('circle')).toHaveLength(2);
        expect(stamp[0].findAll('path')).toHaveLength(3);
        expect(w.find('.dash-achieved-emoji').exists()).toBe(false);
    });

    test('미달성 대기엔 둘 다 없다(양성 쌍)', () => {
        const w = make({ todayReadSeconds: 0 });
        expect(w.find('.dash-state-panel').exists()).toBe(true);
        expect(w.findAll('svg[data-sprout]')).toHaveLength(0);
        expect(w.findAll('svg[data-goal-stamp]')).toHaveLength(0);
    });

    test('선 색은 토큰이다 — 프레젠테이션 속성엔 var()가 안 먹어 app.css가 칠한다', () => {
        const css = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css'), 'utf8')
            .replace(/\/\*[\s\S]*?\*\//g, '');
        expect(/\.dash-sprout\s*\{[^}]*stroke:\s*var\(--accent\)/.test(css)).toBe(true);
        expect(/\.dash-goal-stamp\s*\{[^}]*stroke:\s*var\(--text\)/.test(css)).toBe(true);
    });
});
