// timerMode — 순수 함수(node 환경). 복귀 재조회 스로틀(shouldRefresh) + 모드 저장·effectiveMode.
import { describe, test, expect } from 'vitest';
import { shouldRefresh, REFRESH_THROTTLE_MS, readMode, writeMode, effectiveMode, MODE_KEY } from '../src/dashboard/timerMode';

describe('shouldRefresh — 60초 스로틀', () => {
    test('스로틀 창 안(59.999초)이면 재조회하지 않는다', () => {
        expect(shouldRefresh(0, 59_999)).toBe(false);
    });

    test('경계(60초)에 닿으면 재조회한다', () => {
        expect(shouldRefresh(0, 60_000)).toBe(true);
        expect(REFRESH_THROTTLE_MS).toBe(60_000);
    });

    test('force면 스로틀 창 안이어도 재조회한다(409 → 강제 재조회 경로)', () => {
        expect(shouldRefresh(0, 1, true)).toBe(true);
    });
});

describe('effectiveMode — 서버 진실이 저장값을 이긴다', () => {
    test('독서 진행 중이면 저장값이 study여도 독서', () => {
        expect(effectiveMode(true, true, 'study')).toBe('reading');
        expect(effectiveMode(true, false, 'study')).toBe('reading');
    });

    test('공부 진행 중(독서는 아님)이면 저장값이 reading이어도 공부', () => {
        expect(effectiveMode(false, true, 'reading')).toBe('study');
    });

    test('둘 다 진행 중이 아니면 저장값을 따른다', () => {
        expect(effectiveMode(false, false, 'study')).toBe('study');
        expect(effectiveMode(false, false, 'reading')).toBe('reading');
    });
});

describe('readMode / writeMode — 미지값·접근 불가는 reading으로 떨어진다', () => {
    test('저장값이 study면 study', () => {
        expect(readMode({ getItem: () => 'study' })).toBe('study');
    });

    test('미지값·null·저장소 없음·접근 예외는 모두 reading', () => {
        expect(readMode({ getItem: () => 'garbage' })).toBe('reading');
        expect(readMode({ getItem: () => null })).toBe('reading');
        expect(readMode(null)).toBe('reading');
        expect(readMode({ getItem: () => { throw new Error('SecurityError'); } })).toBe('reading');
    });

    test('writeMode는 같은 키에 쓰고, 저장소가 throw해도 삼킨다(사파리 프라이빗)', () => {
        const saved: Record<string, string> = {};
        writeMode('study', { setItem: (k: string, v: string) => { saved[k] = v; } });
        expect(saved[MODE_KEY]).toBe('study');
        expect(() => writeMode('reading', { setItem: () => { throw new Error('QuotaExceeded'); } })).not.toThrow();
    });
});
