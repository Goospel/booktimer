// @vitest-environment jsdom
// 칸 시계 — 명조 Noto Serif KR엔 고정폭 숫자가 없어(tabular-nums가 안 먹는다) 매초 바뀌는 시계가 좌우로 흔들린다.
// 미니앱 Home.tsx HeroClock과 같은 처방: 숫자 한 자씩 1ch 칸, 읽기 이름은 그릇이 시계 글자 그대로 든다.
// 칸 폭이 실제로 같은지는 jsdom이 레이아웃을 안 하므로 화면에서 잰다(원장 U-3) — 여기선 구조와 배선만.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ClockDigits from '../src/shared/ClockDigits.vue';
import TimerCard from '../src/dashboard/TimerCard.vue';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';
import { fmtMSS } from '../src/dashboard/timerProgress';

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = ''; });

const HERE = dirname(fileURLToPath(import.meta.url));
const APP_CSS = join(HERE, '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css');

describe('ClockDigits', () => {
    test('REQ-05 · 시계 숫자는 한 자씩 1ch 칸에 선다', () => {
        const w = mount(ClockDigits, { props: { text: '01:25:00' } });
        const root = w.find('.clock-digits');
        expect(root.attributes('role')).toBe('timer');
        expect(root.attributes('aria-label')).toBe('01:25:00');
        expect(root.text()).toBe('01:25:00');
        const cells = root.findAll('span');
        expect(cells).toHaveLength(8);
        expect(cells.every((c) => c.attributes('aria-hidden') === 'true')).toBe(true);
        expect(root.findAll('.clock-d')).toHaveLength(6);
        // 콜론 칸은 클래스 없이 제 폭(비례) — 숫자만 칸에 세운다.
        expect(cells.filter((c) => c.text() === ':').every((c) => !c.classes().length)).toBe(true);

        const css = readFileSync(APP_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
        const rule = /(^|\})\s*\.clock-d\s*\{([^}]*)\}/.exec(css);
        expect(rule, 'app.css에 .clock-d 규칙이 없다').not.toBeNull();
        expect(rule![2]).toMatch(/width\s*:\s*1ch/);
        expect(rule![2]).toMatch(/display\s*:\s*inline-block/);
    });

    test('엣지 — 5자 · 숫자 없는 글자 · 0', () => {
        expect(mount(ClockDigits, { props: { text: '42:15' } }).findAll('.clock-d')).toHaveLength(4);
        const done = mount(ClockDigits, { props: { text: '목표 달성' } });
        expect(done.findAll('.clock-d')).toHaveLength(0);
        expect(done.text()).toBe('목표 달성');
        expect(mount(ClockDigits, { props: { text: fmtMSS(Number.NaN) } }).text()).toBe('00:00');
    });
});

/** 보이는 시계 자리들의 읽기 이름 — `[role="timer"]` 그릇마다. */
const timers = (w: { findAll: (s: string) => { attributes: (n: string) => string | undefined }[] }) =>
    w.findAll('[role="timer"]').map((t) => t.attributes('aria-label'));

const STUDY_BOOK = (id: number, title: string, sessionGoalSeconds: number | null = null) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0,
    sessionGoalSeconds,
});

function readingProps(over: Record<string, unknown> = {}) {
    return {
        remainingSeconds: 3000, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 600,
        carryover: false, streak: 0, hasActiveSession: false, activeStartedAt: null, activeBookTitle: null,
        activeBookTotalSeconds: 0, readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [],
        recentBookId: 1, ...over,
    };
}

describe('REQ-05 · 홈에서 매초 바뀌는 8자리가 칸 시계를 쓴다', () => {
    test('독서 대기 — 오늘 시계 · 목표까지', () => {
        vi.useFakeTimers();
        const w = mount(TimerCard, { props: readingProps() as never, attachTo: document.body });
        expect(timers(w)).toEqual(['10:00', '목표까지 50:00']);
    });

    test('독서 측정 중 — 오늘 시계 · 목표까지 · 이번 측정', () => {
        vi.useFakeTimers();
        const started = new Date(Date.now() - 125_000).toISOString();
        const w = mount(TimerCard, {
            props: readingProps({ hasActiveSession: true, activeStartedAt: started, activeBookTitle: '데미안' }) as never,
            attachTo: document.body,
        });
        expect(timers(w)).toEqual(['12:05', '목표까지 47:55', '02:05']);
    });

    test('공부 대기 — 오늘 시계', () => {
        vi.useFakeTimers();
        const w = mount(StudyTimerCard, {
            props: { todaySeconds: 3600, hasActiveSession: false, activeStartedAt: null }, attachTo: document.body,
        });
        expect(timers(w)).toEqual(['01:00:00']);
    });

    test('공부 측정 중(회당 시간 남음) — 오늘 · 이번 측정 · 남은 시간', () => {
        vi.useFakeTimers();
        const w = mount(StudyTimerCard, {
            props: {
                todaySeconds: 600, hasActiveSession: true, activeStartedAt: new Date(Date.now() - 30 * 60_000).toISOString(),
                books: [STUDY_BOOK(5, '헌법', 3000)], activeBook: STUDY_BOOK(5, '헌법', 3000),
            },
            attachTo: document.body,
        });
        expect(timers(w)).toEqual(['40:00', '30:00', '20:00']);
    });

    test('공부 측정 중(회당 시간 넘김) — 넘긴 시간은 「+」까지 한 시계', () => {
        vi.useFakeTimers();
        const w = mount(StudyTimerCard, {
            props: {
                todaySeconds: 0, hasActiveSession: true, activeStartedAt: new Date(Date.now() - 60 * 60_000).toISOString(),
                books: [STUDY_BOOK(5, '헌법', 3000)], activeBook: STUDY_BOOK(5, '헌법', 3000),
            },
            attachTo: document.body,
        });
        expect(timers(w)).toEqual(['01:00:00', '01:00:00', '+10:00']);
    });
});
