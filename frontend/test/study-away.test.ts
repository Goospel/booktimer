// @vitest-environment jsdom
// studyAway — 공부 측정 중 탭을 떠난 구간 원장(설계 2026-09-28-web-study-away-time §3 PR-2).
//  · 통과가 확정하는 것: 30초 경계(29초 폐기·31초 채택) · 측정 세션이 바뀌면 옛 구간을 버린다 ·
//    되돌리기가 그 구간만 뺀다 · pagehide가 대기 구간만 버린다 · localStorage가 던져도 죽지 않는다.
//  · 실패가 배제하는 것: 유예 반전(짧은 이탈 차감) · 다른 세션에 옛 구간이 붙기 · 되돌리기가 전부 지우기.
import { describe, test, expect, vi, afterEach } from 'vitest';
import {
    AWAY_KEY, ledgerFor, markHidden, markVisible, clearPending, undo, awaySeconds, loadLedger, saveLedger,
    AWAY_PREF_KEY, loadAwayOn, saveAwayOn, canonicalIso,
} from '../src/dashboard/studyAway';

// 측정 세션 식별자 정규화 — 시작 응답(100ns 9자리)과 재조회(µs 6자리)가 같은 순간을 다른 문자열로 준다(실측).
describe('studyAway canonicalIso', () => {
    test('정밀도만 다른 두 표기는 같은 정규 표기(밀리초)가 된다', () => {
        expect(canonicalIso('2026-09-29T07:01:52.657934400Z')).toBe('2026-09-29T07:01:52.657Z');
        expect(canonicalIso('2026-09-29T07:01:52.657934Z')).toBe('2026-09-29T07:01:52.657Z');
    });
    test('다른 순간은 다른 값으로 남는다(대조군)', () => {
        expect(canonicalIso('2026-09-29T07:01:52.658Z')).not.toBe(canonicalIso('2026-09-29T07:01:52.657934Z'));
    });
    test('읽을 수 없는 값은 던지지 않고 그대로 둔다', () => {
        expect(canonicalIso('garbage')).toBe('garbage');
    });
});

const S = '2026-09-28T01:00:00.000Z';
const T0 = Date.parse('2026-09-28T01:10:00.000Z');
const empty = () => ledgerFor(null, S);

afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

describe('studyAway 순수 함수', () => {
    test('29초 이탈은 버린다 — closed null, 차감 0, 대기도 비운다', () => {
        const { ledger, closed } = markVisible(markHidden(empty(), T0), T0 + 29_000);
        expect(closed).toBeNull();
        expect(ledger.intervals).toEqual([]);
        expect(ledger.pendingFrom).toBeNull();
        expect(awaySeconds(ledger)).toBe(0);
    });

    test('31초 이탈은 떠난 순간부터 전부 멈춘 것으로 친다', () => {
        const { ledger, closed } = markVisible(markHidden(empty(), T0), T0 + 31_000);
        expect(closed).toEqual({ from: new Date(T0).toISOString(), to: new Date(T0 + 31_000).toISOString() });
        expect(ledger.intervals).toEqual([closed]);
        expect(awaySeconds(ledger)).toBe(31);
    });

    test('이미 떠나 있으면 hidden이 또 와도 처음 떠난 시각을 지킨다', () => {
        const l = markHidden(markHidden(empty(), T0), T0 + 10_000);
        expect(l.pendingFrom).toBe(new Date(T0).toISOString());
    });

    test('대기 구간 없이 visible이면 아무 일도 없다', () => {
        const { ledger, closed } = markVisible(empty(), T0);
        expect(closed).toBeNull();
        expect(ledger).toEqual(empty());
    });

    test('다른 startedAt의 원장은 빈 원장으로 갈아끼운다, 같으면 그대로', () => {
        const old = markVisible(markHidden(empty(), T0), T0 + 60_000).ledger;
        expect(ledgerFor(old, '2026-09-28T02:00:00.000Z'))
            .toEqual({ startedAt: '2026-09-28T02:00:00.000Z', intervals: [], pendingFrom: null });
        expect(ledgerFor(old, S)).toBe(old);
    });

    test('undo는 그 구간만 뺀다 — 여러 번 이탈은 누적된다', () => {
        let l = markVisible(markHidden(empty(), T0), T0 + 60_000).ledger;
        const r = markVisible(markHidden(l, T0 + 120_000), T0 + 420_000);
        l = r.ledger;
        expect(awaySeconds(l)).toBe(60 + 300);
        const after = undo(l, r.closed!);
        expect(awaySeconds(after)).toBe(60);
        expect(after.intervals).toHaveLength(1);
    });

    test('clearPending은 대기 구간만 버리고 닫힌 구간은 남긴다', () => {
        const closed = markVisible(markHidden(empty(), T0), T0 + 60_000).ledger;
        const l = clearPending(markHidden(closed, T0 + 100_000));
        expect(l.pendingFrom).toBeNull();
        expect(awaySeconds(l)).toBe(60);
        // 그 뒤 visible이 와도 차감은 늘지 않는다(같은 탭 이동은 이탈이 아니다).
        expect(markVisible(l, T0 + 500_000).closed).toBeNull();
    });
});

describe('studyAway 저장소', () => {
    test('저장한 원장을 그대로 읽고, null 저장은 키를 지운다', () => {
        const l = markHidden(empty(), T0);
        saveLedger(l);
        expect(loadLedger()).toEqual(l);
        saveLedger(null);
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
        expect(loadLedger()).toBeNull();
    });

    test('깨진 값·모양이 다른 값은 null', () => {
        localStorage.setItem(AWAY_KEY, '{not json');
        expect(loadLedger()).toBeNull();
        localStorage.setItem(AWAY_KEY, JSON.stringify({ startedAt: S }));
        expect(loadLedger()).toBeNull();
    });

    test('localStorage가 던져도 읽기는 null, 쓰기는 조용히 넘어간다', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied'); });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota'); });
        vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('denied'); });
        expect(loadLedger()).toBeNull();
        expect(() => saveLedger(empty())).not.toThrow();
        expect(() => saveLedger(null)).not.toThrow();
    });
});

// 기기별 선택(설계 §9) — 'on'만 켬, 기본 꺼짐. 끌 때도 'off'를 적는다(고른 적 없음과 구분).
describe('studyAway 기기별 선택', () => {
    test('REQ-03 · 기록이 없으면 꺼짐', () => {
        expect(loadAwayOn()).toBe(false);
    });

    test("REQ-02 · 켜면 'on', 끄면 'off'로 적고 그대로 읽는다", () => {
        saveAwayOn(true);
        expect(localStorage.getItem(AWAY_PREF_KEY)).toBe('on');
        expect(loadAwayOn()).toBe(true);
        saveAwayOn(false);
        expect(localStorage.getItem(AWAY_PREF_KEY)).toBe('off');
        expect(loadAwayOn()).toBe(false);
    });

    test.each(['true', '1', ''])("REQ-03 · 모르는 값(%j)은 꺼짐", v => {
        localStorage.setItem(AWAY_PREF_KEY, v);
        expect(loadAwayOn()).toBe(false);
    });

    test('REQ-02 · 저장소가 던져도 읽기는 꺼짐, 쓰기는 조용히 넘어간다', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied'); });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota'); });
        expect(loadAwayOn()).toBe(false);
        expect(() => saveAwayOn(true)).not.toThrow();
    });
});
