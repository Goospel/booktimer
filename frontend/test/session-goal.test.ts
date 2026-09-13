// sessionGoal.ts — 책별 「회당 시간」의 닿음 판정·분 입력 환산(설계 §4.5). 하루 목표 게이지(studyProgress)를 대체한다.
//
// 계측기 메모 — 값이 틀려도 화면은 그럴듯한 자리라 전부 「계산된 수」로 잰다.
//  · 통과가 확정하는 것: 회당 없음(null·0)은 스톱워치 · 경계(elapsed = goal)가 달성 쪽 · 남은/초과 초가 정확 ·
//    음수 경과는 0으로 눌림 · 분→초 방향 · 빈칸·0·음수는 「해제(null)」로 모인다.
//  · 실패가 배제하는 것: `>=`를 `>`로(경계 1초 늦은 달성) · 0을 「0초짜리 회당」으로 읽어 시작 즉시 달성 ·
//    분/초 뒤집힘 · 빈칸을 0초로 보내 서버 400.
import { describe, test, expect } from 'vitest';
import { sessionGoalView, minutesToSessionGoal } from '../src/dashboard/sessionGoal';

describe('sessionGoalView — 회당 시간 대비 이번 측정', () => {
    test('회당 시간이 없으면(null·undefined·0) 스톱워치다', () => {
        expect(sessionGoalView(null, 800)).toEqual({ kind: 'stopwatch' });
        expect(sessionGoalView(undefined, 800)).toEqual({ kind: 'stopwatch' });
        expect(sessionGoalView(0, 800)).toEqual({ kind: 'stopwatch' });
    });

    test('아직이면 남은 초를 센다 — 20분 회당, 800초 경과 → 400초 남음', () => {
        expect(sessionGoalView(1200, 800)).toEqual({ kind: 'countdown', goal: 1200, remaining: 400 });
    });

    test('정확히 닿은 순간이 달성이다(경계) — 초과 0', () => {
        expect(sessionGoalView(1200, 1200)).toEqual({ kind: 'reached', goal: 1200, overflow: 0 });
        // 양성 쌍: 1초 전은 아직이다 — 「언제나 달성」 구현과 구분된다.
        expect(sessionGoalView(1200, 1199)).toEqual({ kind: 'countdown', goal: 1200, remaining: 1 });
    });

    test('넘기면 초과분을 센다 — 측정은 계속된다', () => {
        expect(sessionGoalView(1200, 1500)).toEqual({ kind: 'reached', goal: 1200, overflow: 300 });
    });

    test('음수 경과(기기 시계가 서버보다 느림)는 0으로 눌린다', () => {
        expect(sessionGoalView(1200, -5)).toEqual({ kind: 'countdown', goal: 1200, remaining: 1200 });
    });
});

describe('minutesToSessionGoal — 분 입력 → 초 | null(해제)', () => {
    test('50분 = 3000초 (초→분이 아니다)', () => {
        expect(minutesToSessionGoal(50)).toBe(3000);
        expect(minutesToSessionGoal(1)).toBe(60);
    });

    test('빈칸·NaN·0·음수는 null — 「회당 시간 없음」이지 0초가 아니다(서버는 0을 400으로 막는다)', () => {
        expect(minutesToSessionGoal('')).toBeNull();
        expect(minutesToSessionGoal(NaN)).toBeNull();
        expect(minutesToSessionGoal('abc')).toBeNull();
        expect(minutesToSessionGoal(null)).toBeNull();
        expect(minutesToSessionGoal(undefined)).toBeNull();
        expect(minutesToSessionGoal(0)).toBeNull();
        expect(minutesToSessionGoal(-3)).toBeNull();
    });

    // ⚠️ UI 미도달 · 방어용: `<input step="1">`이라 브라우저에선 소수가 submit되지 않는다(stepMismatch).
    test('소수는 내림한 정수 분 — 7.9분 → 420초 (UI 미도달 · 방어용)', () => {
        expect(minutesToSessionGoal(7.9)).toBe(420);
    });
});
