// @vitest-environment jsdom
// studyPause — 공부 측정 일시정지 원장 · 단축키 판정(설계 2026-10-20-study-timer-keyboard-shortcut §6-1).
//  · 통과가 확정하는 것: 멈춤↔다시가 구간 하나를 닫는다 · 0ms 구간은 남지 않는다 · 다른 세션 원장은 버린다 ·
//    멈춘 채 종료 본문엔 지금까지가 닫혀 실리되 원장은 그대로 · 저장소가 던져도 죽지 않는다 · Alt+P만 단축키다.
//  · 실패가 배제하는 것: 서버 400을 부르는 from >= to 구간 · 종료 실패 뒤 멈춤이 풀림 · key로 판정(한글·π·Process에서 죽음) ·
//    AltGr(Ctrl+Alt)·Shift·Meta 조합 오발.
import { describe, test, expect, vi, afterEach } from 'vitest';
import {
    PAUSE_KEY, pauseFor, togglePause, pauseIntervalsAt, loadPause, savePause, isPauseKey, pauseKeyLabel,
} from '../src/dashboard/studyPause';
import { awaySeconds } from '../src/dashboard/studyAway';

const S = '2026-10-20T01:00:00.000Z';
const T0 = Date.parse('2026-10-20T01:10:00.000Z');
const iso = (ms: number) => new Date(ms).toISOString();

afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

describe('studyPause 원장', () => {
    test('REQ-01 · 멈췄다 다시 누르면 [멈춘 시각, 다시 누른 시각] 구간 하나가 쌓이고 멈춤이 풀린다', () => {
        const paused = togglePause(pauseFor(null, S), T0);
        expect(paused.pausedFrom).toBe(iso(T0));
        expect(paused.intervals).toEqual([]);
        const resumed = togglePause(paused, T0 + 120_000);
        expect(resumed.pausedFrom).toBeNull();
        expect(resumed.intervals).toEqual([{ from: iso(T0), to: iso(T0 + 120_000) }]);
        expect(awaySeconds(resumed)).toBe(120);
    });

    test('REQ-01 · 같은 밀리초에 멈췄다 다시 누르면 구간을 남기지 않는다(서버 400 from >= to 방지)', () => {
        const same = togglePause(togglePause(pauseFor(null, S), T0), T0);
        expect(same.pausedFrom).toBeNull();
        expect(same.intervals).toEqual([]);
        // 시계 역행도 버린다
        const back = togglePause(togglePause(pauseFor(null, S), T0), T0 - 5_000);
        expect(back.intervals).toEqual([]);
    });

    test('REQ-02 · 다른 측정 세션의 원장은 버리고 빈 원장을 준다', () => {
        const old = togglePause(togglePause(pauseFor(null, 'other'), T0), T0 + 1_000);
        expect(pauseFor(old, S)).toEqual({ startedAt: S, intervals: [], pausedFrom: null });
        expect(pauseFor(old, 'other')).toBe(old);   // 같은 세션이면 그대로(대조)
    });

    test('REQ-02 · 저장했다 읽으면 같은 원장, null이면 지운다', () => {
        const l = togglePause(pauseFor(null, S), T0);
        savePause(l);
        expect(loadPause()).toEqual(l);
        expect(localStorage.getItem(PAUSE_KEY)).not.toBeNull();
        savePause(null);
        expect(localStorage.getItem(PAUSE_KEY)).toBeNull();
        localStorage.setItem(PAUSE_KEY, '{"startedAt":1}');
        expect(loadPause()).toBeNull();   // 모양이 틀리면 버린다
        localStorage.setItem(PAUSE_KEY, JSON.stringify({ startedAt: S }));   // 같은 세션인데 intervals가 없다(리뷰 F7)
        expect(loadPause()).toBeNull();
    });

    test('REQ-02 · 저장소가 던져도 죽지 않는다 — 읽기 null, 쓰기 무시', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
        vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('blocked'); });
        expect(loadPause()).toBeNull();
        expect(() => savePause(pauseFor(null, S))).not.toThrow();
        expect(() => savePause(null)).not.toThrow();
    });

    test('REQ-03 · 멈춘 채면 종료 본문 구간에 지금까지가 닫혀 실리고, 원장은 그대로 멈춰 있다', () => {
        const done = togglePause(togglePause(pauseFor(null, S), T0), T0 + 60_000);
        const l = togglePause(done, T0 + 120_000);
        expect(pauseIntervalsAt(l, T0 + 300_000)).toEqual([
            { from: iso(T0), to: iso(T0 + 60_000) },
            { from: iso(T0 + 120_000), to: iso(T0 + 300_000) },
        ]);
        expect(l.pausedFrom).toBe(iso(T0 + 120_000));
        expect(l.intervals).toHaveLength(1);
        expect(pauseIntervalsAt(null, T0)).toEqual([]);
        expect(pauseIntervalsAt(l, T0 + 120_000)).toHaveLength(1);   // 0ms 열린 구간은 싣지 않는다
    });
});

const ev = (o: Partial<KeyboardEvent>) =>
    ({ code: '', altKey: false, ctrlKey: false, metaKey: false, shiftKey: false, ...o }) as KeyboardEvent;

describe('studyPause 키', () => {
    test.each([
        ['Alt+P', { code: 'KeyP', altKey: true }],
        ['한글 입력 중(key ㅔ)', { code: 'KeyP', altKey: true, key: 'ㅔ' }],
        ['맥 Option(key π)', { code: 'KeyP', altKey: true, key: 'π' }],
        ['조합 중(229·isComposing)', { code: 'KeyP', altKey: true, key: 'Process', keyCode: 229, isComposing: true }],
    ])('REQ-07 · Alt+P만 단축키다 — 참: %s', (_, o) => {
        expect(isPauseKey(ev(o as Partial<KeyboardEvent>))).toBe(true);
    });

    test.each([
        ['Ctrl+Alt+P(AltGr)', { code: 'KeyP', altKey: true, ctrlKey: true }],
        ['Alt+Shift+P', { code: 'KeyP', altKey: true, shiftKey: true }],
        ['Meta+Alt+P', { code: 'KeyP', altKey: true, metaKey: true }],
        ['P 단독', { code: 'KeyP' }],
        ['Alt+O', { code: 'KeyO', altKey: true }],
    ])('REQ-07 · Alt+P만 단축키다 — 거짓: %s', (_, o) => {
        expect(isPauseKey(ev(o))).toBe(false);
    });

    test.each([
        ['MacIntel', 'Option+P'], ['iPad', 'Option+P'], ['iPhone', 'Option+P'],
        ['Win32', 'Alt+P'], ['Linux x86_64', 'Alt+P'], ['', 'Alt+P'],
    ])('REQ-08 · 맥·아이패드 표기는 Option+P, 그 밖은 Alt+P — %j', (platform, label) => {
        expect(pauseKeyLabel(platform)).toBe(label);
    });
});
