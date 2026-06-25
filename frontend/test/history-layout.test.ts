// 기록 카드 stacked↔split 전환 임계폭 판단 — 순수함수 경계 테스트 (node env).
// 좁으면 'stacked'(pill 탭 + 한 패널), 임계폭 이상이면 'split'(2단 grid).
import { describe, test, expect } from 'vitest';
import { chooseLayout, SPLIT_MIN_WIDTH } from '../src/history/layout';

describe('chooseLayout', () => {
    test('임계폭 미만은 stacked', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH - 1)).toBe('stacked');
        expect(chooseLayout(460)).toBe('stacked');
        expect(chooseLayout(0)).toBe('stacked');
    });

    test('임계폭 정확히는 split (>=)', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH)).toBe('split');
    });

    test('임계폭 초과는 split', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH + 1)).toBe('split');
        expect(chooseLayout(1200)).toBe('split');
    });

    test('SPLIT_MIN_WIDTH는 양수 상수', () => {
        expect(SPLIT_MIN_WIDTH).toBeGreaterThan(0);
    });
});
