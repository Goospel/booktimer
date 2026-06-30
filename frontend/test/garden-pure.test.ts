// pure.ts를 직접 ESM import — 돌봄 뷰(PortraitVillage)가 쓰는 순수함수만 남음.
// (CoC 가로 무대 은퇴로 좌표·줌·격자·아이소·wanderStep·walkPose·idlePose·AMBIENT_DECOR
//  테스트는 모두 삭제됐다 — 해당 순수함수가 pure.ts에서 사라졌기 때문.)
import { describe, test, expect } from 'vitest';
import {
    clampNorm,
    pickTodayAuthor, affectionProgress, AFFECTION_THRESHOLDS,
} from '../src/garden/pure';

describe('garden pure.ts (돌봄 뷰 순수함수)', () => {

    describe('clampNorm', () => {
        test('음수 → 0', () => expect(clampNorm(-0.5)).toBeCloseTo(0));
        test('1 초과 → 1', () => expect(clampNorm(1.5)).toBeCloseTo(1));
        test('범위 내 통과', () => expect(clampNorm(0.42)).toBeCloseTo(0.42));
        test('경계 0', () => expect(clampNorm(0)).toBeCloseTo(0));
        test('경계 1', () => expect(clampNorm(1)).toBeCloseTo(1));
    });

    // ── PortraitVillage(다마고치 돌봄 뷰) 순수 함수 ──

    describe('pickTodayAuthor (오늘의 작가 선정 — 정 레벨 최저, 동률 첫)', () => {
        test('빈 목록 → null', () => {
            expect(pickTodayAuthor([])).toBeNull();
        });
        test('단일 → 그 작가', () => {
            const a = { code: 'A', level: 2 };
            expect(pickTodayAuthor([a])).toBe(a);
        });
        test('다수 → 정 레벨 최저(키울 여지 큰 순)', () => {
            const a = { code: 'A', level: 3 };
            const b = { code: 'B', level: 1 };
            const c = { code: 'C', level: 5 };
            expect(pickTodayAuthor([a, b, c])).toBe(b);
        });
        test('정 레벨 동률 → 첫(입력 순서 보존)', () => {
            const a = { code: 'A', level: 1 };
            const b = { code: 'B', level: 1 };
            expect(pickTodayAuthor([a, b])).toBe(a);
        });
        test('전원 동률 → 첫', () => {
            const a = { code: 'A', level: 0 };
            const b = { code: 'B', level: 0 };
            const c = { code: 'C', level: 0 };
            expect(pickTodayAuthor([a, b, c])).toBe(a);
        });
    });

    describe('affectionProgress (정 미터 — feedCount → 다음 임계 진행도)', () => {
        test('임계 테이블 = [1,3,7,15,30] (AffectionLevel 복제)', () => {
            expect(AFFECTION_THRESHOLDS).toEqual([1, 3, 7, 15, 30]);
        });
        test('0 → Lv0, 0/1, progress 0', () => {
            const p = affectionProgress(0);
            expect(p.level).toBe(0);
            expect(p.prevThreshold).toBe(0);
            expect(p.nextThreshold).toBe(1);
            expect(p.progress).toBeCloseTo(0);
            expect(p.isMax).toBe(false);
        });
        test('1 → Lv1 진입, progress 0', () => {
            const p = affectionProgress(1);
            expect(p.level).toBe(1);
            expect(p.prevThreshold).toBe(1);
            expect(p.nextThreshold).toBe(3);
            expect(p.progress).toBeCloseTo(0);
        });
        test('2 → Lv1 중간, progress 0.5', () => {
            expect(affectionProgress(2).progress).toBeCloseTo(0.5);
        });
        test('3 → Lv2 진입, progress 0', () => {
            const p = affectionProgress(3);
            expect(p.level).toBe(2);
            expect(p.prevThreshold).toBe(3);
            expect(p.nextThreshold).toBe(7);
            expect(p.progress).toBeCloseTo(0);
        });
        test('6 → Lv2, progress 0.75', () => {
            expect(affectionProgress(6).progress).toBeCloseTo(0.75);
        });
        test('7 → Lv3 진입', () => {
            const p = affectionProgress(7);
            expect(p.level).toBe(3);
            expect(p.nextThreshold).toBe(15);
        });
        test('14 → Lv3, progress 7/8', () => {
            expect(affectionProgress(14).progress).toBeCloseTo(7 / 8);
        });
        test('15 → Lv4 진입', () => {
            const p = affectionProgress(15);
            expect(p.level).toBe(4);
            expect(p.nextThreshold).toBe(30);
        });
        test('29 → Lv4, progress 14/15', () => {
            expect(affectionProgress(29).progress).toBeCloseTo(14 / 15);
        });
        test('30 → Lv5 만렙, isMax, progress 1, next null', () => {
            const p = affectionProgress(30);
            expect(p.level).toBe(5);
            expect(p.isMax).toBe(true);
            expect(p.progress).toBeCloseTo(1);
            expect(p.nextThreshold).toBeNull();
        });
        test('만렙 초과(100)도 progress 1·isMax', () => {
            expect(affectionProgress(100).progress).toBeCloseTo(1);
            expect(affectionProgress(100).isMax).toBe(true);
        });
        test('음수 방어 → Lv0, progress 0', () => {
            const p = affectionProgress(-5);
            expect(p.level).toBe(0);
            expect(p.progress).toBeCloseTo(0);
        });
    });
});
