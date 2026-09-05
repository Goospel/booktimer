// studyProgress.ts를 직접 ESM import — 함수 누락·시그니처 불일치면 import 실패로 즉시 RED.
// 공부 게이지는 이월·부채가 없다(독서 computeProgress와 분리된 이유) — 오늘 잰 시간과 목표만 본다.
//
// 계측기 메모: 값이 틀려도 화면은 그럴듯한 자리라, 단언은 전부 「계산된 수」로 잡는다.
//  - 통과가 확정하는 것: 인자 순서(목표, 오늘)·백분율 절사·상한 100·목표 0의 「달성 아님」·분→초 환산 방향.
//  - 실패가 배제하는 것: 인자 뒤바뀜(50% → 100%), 반올림 오용, goal 0을 100%로 치는 독서 관례 답습,
//    분/초 환산 뒤집힘(30분 → 0.5초).
import { describe, test, expect } from 'vitest';
import { studyProgress, minutesToGoalSeconds } from '../src/dashboard/studyProgress';

describe('studyProgress — 목표 대비 오늘', () => {
    test('절반 잼 — 남은 시간·퍼센트·문자열이 한꺼번에 맞는다', () => {
        expect(studyProgress(3600, 1800)).toEqual({
            remaining: 1800, pct: 50, pctStr: '50%', achieved: false, overflow: 0,
        });
    });

    test('정확히 달성 — remaining 0, achieved, overflow 0', () => {
        expect(studyProgress(3600, 3600)).toEqual({
            remaining: 0, pct: 100, pctStr: '100%', achieved: true, overflow: 0,
        });
    });

    test('초과 — pct는 100에서 멈추고 초과분만 따로 센다', () => {
        expect(studyProgress(3600, 5400)).toEqual({
            remaining: 0, pct: 100, pctStr: '100%', achieved: true, overflow: 1800,
        });
    });

    test('목표 0(=목표 없음)은 달성이 아니다 — 독서 computeProgress의 100%/achieved 관례를 따르지 않는다', () => {
        expect(studyProgress(0, 1800)).toEqual({
            remaining: 0, pct: 0, pctStr: '0%', achieved: false, overflow: 0,
        });
        expect(studyProgress(-60, 1800).achieved).toBe(false);
    });

    test('음수 오늘값은 0으로 눌린다', () => {
        expect(studyProgress(3600, -5)).toEqual({
            remaining: 3600, pct: 0, pctStr: '0%', achieved: false, overflow: 0,
        });
    });

    test('절사(내림)이지 반올림이 아니다 — 99.9%를 100%로 올려 달성처럼 보이게 하지 않는다', () => {
        // 3599/3600 = 99.97% → 반올림이면 100%(게이지 가득 = 거짓 달성 인상)
        expect(studyProgress(3600, 3599).pct).toBe(99);
        expect(studyProgress(3600, 3599).achieved).toBe(false);
    });
});

describe('minutesToGoalSeconds — 분 입력 → 초', () => {
    test('30분 = 1800초 (초→분이 아니다)', () => {
        expect(minutesToGoalSeconds(30)).toBe(1800);
        expect(minutesToGoalSeconds(1)).toBe(60);
    });

    test('빈칸·NaN·음수는 0 — 서버 400(음수)을 문 앞에서 막는다', () => {
        expect(minutesToGoalSeconds('abc')).toBe(0);
        expect(minutesToGoalSeconds('')).toBe(0);
        expect(minutesToGoalSeconds(null)).toBe(0);
        expect(minutesToGoalSeconds(undefined)).toBe(0);
        expect(minutesToGoalSeconds(-3)).toBe(0);
    });

    // ⚠️ **UI 미도달 · 방어용**: 이 경로는 브라우저에서 열리지 않는다 — `<input step="1">`이라
    // 7.5는 stepMismatch로 submit 자체가 안 나가고(요청 0건), 크롬이 「가장 근접한 유효 값 2개는
    // 7 및 8입니다」를 띄운다. 정수 분만 받는 것이 의도다(조용히 7로 내리지 않는다).
    // 그래도 남기는 이유는 프로그램 호출(다른 화면·테스트)이 소수를 넣어도 서버 계약이 깨지지 않는다는
    // 것을 잰다는 것 — 「브라우저에서 소수를 넣을 수 있다」의 증거가 아니다.
    test('소수는 내림한 정수 분 — 7.9분 → 7분 → 420초 (UI 미도달 · 방어용)', () => {
        expect(minutesToGoalSeconds(7.9)).toBe(420);
    });
});
