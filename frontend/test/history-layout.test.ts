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

    // 절대값 — 상대값 테스트만으론 경계가 옛 880이어도 통과한다. 바 여백 248을 뺀 852가 2단 최소 폭.
    test('태블릿 가로(1024)는 stacked, 데스크톱 경계 1100부터 split', () => {
        expect(chooseLayout(1024)).toBe('stacked');
        expect(chooseLayout(1100)).toBe('split');
    });

    test('SPLIT_MIN_WIDTH는 양수 상수', () => {
        expect(SPLIT_MIN_WIDTH).toBeGreaterThan(0);
    });
});
