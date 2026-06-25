// 잔디 셀 다크 툴팁 텍스트 조립 — 순수함수 경계 테스트 (node env).
// 시안(HistoryPage.dc.html)의 hover 툴팁 규칙을 못 박는다:
//   level>0  → "{날짜} · {N}분"  (manual이면 " · 직접 채움" 덧붙임)
//   level==0 → "{날짜} · 기록 없음"
//   placeholder(date=null, future/선행) → 툴팁 없음(null)
import { describe, test, expect } from 'vitest';
import { cellTooltip } from '../src/history/grassTooltip';

describe('cellTooltip', () => {
    test('placeholder(date=null)는 툴팁이 없다(null)', () => {
        expect(cellTooltip({ date: null, totalSeconds: 0, level: 0, manual: false })).toBeNull();
    });

    test('level 0(기록 없는 날)은 "기록 없음"', () => {
        expect(cellTooltip({ date: '2026-06-16', totalSeconds: 0, level: 0, manual: false }))
            .toBe('2026-06-16 · 기록 없음');
    });

    test('level>0은 "날짜 · N분" — 분은 floor(초/60)', () => {
        expect(cellTooltip({ date: '2026-06-15', totalSeconds: 3600, level: 2, manual: false }))
            .toBe('2026-06-15 · 60분');
    });

    test('초가 분으로 안 나눠떨어지면 내림(floor)', () => {
        // 90초 → 1분, 3000초 → 50분
        expect(cellTooltip({ date: '2026-06-15', totalSeconds: 90, level: 1, manual: false }))
            .toBe('2026-06-15 · 1분');
        expect(cellTooltip({ date: '2026-06-15', totalSeconds: 3000, level: 1, manual: false }))
            .toBe('2026-06-15 · 50분');
    });

    test('manual이면 "· 직접 채움"을 덧붙인다', () => {
        expect(cellTooltip({ date: '2026-06-17', totalSeconds: 1800, level: 1, manual: true }))
            .toBe('2026-06-17 · 30분 · 직접 채움');
    });

    test('level 0 + manual: 직접 채웠지만 0분이면 "기록 없음"이 우선(분 표기 없음)', () => {
        // manual이라도 level 0이면 시간 표기 대신 "기록 없음"(시안: level>0 분기에서만 분/직접채움 노출)
        expect(cellTooltip({ date: '2026-06-18', totalSeconds: 0, level: 0, manual: true }))
            .toBe('2026-06-18 · 기록 없음');
    });
});
