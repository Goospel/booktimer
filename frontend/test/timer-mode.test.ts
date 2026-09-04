// timerMode — 순수 함수(node 환경). 이번 PR은 복귀 재조회 스로틀(shouldRefresh)만 다룬다.
// 모드 저장·effectiveMode는 토글 PR에서 같은 파일에 이어 붙는다.
import { describe, test, expect } from 'vitest';
import { shouldRefresh, REFRESH_THROTTLE_MS } from '../src/dashboard/timerMode';

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
