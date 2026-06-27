// 책BTI 재스킨 — 순수 표시 로직 단위 테스트(node 환경, 컴포넌트 mount 불필요).
// 시각 재스킨이라 새 도메인 로직은 적으나, 데이터 바인딩에 끼는 distinct 실패를 못박는다:
//  · joinLabels  — 장르/저자 라벨 구분자(' · ')·빈배열 회귀
//  · refreshState — 다시분석 버튼 3상태(refreshing 우선 → exhausted → ready)
//  · dotIndex    — 캐러셀 도트 활성 인덱스(0 나눗셈 가드·반올림·clamp)
import { describe, test, expect } from 'vitest';
import { joinLabels, refreshState, dotIndex } from '../src/personality/personalityView';

describe('joinLabels', () => {
    test('빈 배열 → 빈 문자열', () => {
        expect(joinLabels([])).toBe('');
    });
    test('단일 → 구분자 없음', () => {
        expect(joinLabels([{ label: '소설' }])).toBe('소설');
    });
    test('여러 개 → " · "로 연결(시안 구분자)', () => {
        expect(joinLabels([{ label: '소설' }, { label: '에세이' }, { label: '인문' }]))
            .toBe('소설 · 에세이 · 인문');
    });
});

describe('refreshState', () => {
    test('refreshing 중이면 remaining 무관하게 refreshing', () => {
        expect(refreshState(0, true)).toBe('refreshing');
        expect(refreshState(3, true)).toBe('refreshing');
    });
    test('remaining 0 & 비-refreshing → exhausted', () => {
        expect(refreshState(0, false)).toBe('exhausted');
    });
    test('remaining > 0 & 비-refreshing → ready', () => {
        expect(refreshState(2, false)).toBe('ready');
    });
});

describe('dotIndex', () => {
    test('count 0 → 0', () => {
        expect(dotIndex(100, 50, 0)).toBe(0);
    });
    test('step 0 → 0 (0 나눗셈 가드)', () => {
        expect(dotIndex(100, 0, 3)).toBe(0);
    });
    test('가장 가까운 칸으로 반올림', () => {
        expect(dotIndex(0, 100, 3)).toBe(0);
        expect(dotIndex(60, 100, 3)).toBe(1);   // round(0.6)=1
        expect(dotIndex(140, 100, 3)).toBe(1);  // round(1.4)=1
        expect(dotIndex(160, 100, 3)).toBe(2);  // round(1.6)=2
    });
    test('상한 clamp(끝까지 스크롤)', () => {
        expect(dotIndex(9999, 100, 3)).toBe(2);
    });
    test('하한 clamp(음수 scrollLeft)', () => {
        expect(dotIndex(-50, 100, 3)).toBe(0);
    });
});
