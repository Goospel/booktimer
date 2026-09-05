// StudyState 정규화 — 서버 응답·옛 픽스처를 8필드로 채우는 단 하나의 문(설계 §2.4-정규화).
//
// 계측기 메모
//  · 통과가 확정하는 것: 필드가 빠진 응답에서도 books가 **배열**이라 books.map이 죽지 않는다 ·
//    넘어온 값이 기본값을 이긴다(spread 순서가 { ...IDLE_STUDY, ...s }이다).
//  · 실패가 배제하는 것: spread 순서 뒤집힘({ ...s, ...IDLE_STUDY } → 서버 값이 매번 지워진다) ·
//    IDLE_STUDY가 4필드에 머물러 books/activeBook/recentBookId/untaggedSessionId가 undefined로 남기.
import { describe, test, expect } from 'vitest';
import { IDLE_STUDY, studyStateOf } from '../src/dashboard/types';

const ROW = {
    id: 5, title: '헌법', author: '홍길동', coverUrl: null, isbn13: null,
    readCount: 2, purchaseLink: null, totalSeconds: 120,
};

describe('studyStateOf', () => {
    test('없는 응답(undefined·null)은 IDLE_STUDY 8필드 그대로', () => {
        expect(studyStateOf(undefined)).toEqual(IDLE_STUDY);
        expect(studyStateOf(null)).toEqual(IDLE_STUDY);
        // 기본값이 「빈 배열」이라야 books.map이 산다 — undefined면 옛 픽스처에서 죽는다.
        expect(studyStateOf(undefined).books).toEqual([]);
    });

    test('옛 서버·옛 픽스처(4필드)도 8필드로 채워진다 — 새 필드는 부재의 기본값', () => {
        const s = studyStateOf({ hasActiveSession: true, activeStartedAt: '2026-09-05T00:00:00Z', todaySeconds: 60, goalSeconds: 3600 });

        expect(s.hasActiveSession).toBe(true);
        expect(s.todaySeconds).toBe(60);
        expect(s.books).toEqual([]);
        expect(s.activeBook).toBeNull();
        expect(s.recentBookId).toBeNull();
        expect(s.untaggedSessionId).toBeNull();
    });

    test('넘어온 값이 기본값을 이긴다 — 8필드 전부(spread 순서 양성 대조군)', () => {
        const s = studyStateOf({
            hasActiveSession: true, activeStartedAt: '2026-09-05T01:00:00Z', todaySeconds: 900, goalSeconds: 1800,
            activeBook: ROW, recentBookId: 5, books: [ROW], untaggedSessionId: 42,
        });

        expect(s.books).toEqual([ROW]);
        expect(s.activeBook).toEqual(ROW);
        expect(s.recentBookId).toBe(5);
        expect(s.untaggedSessionId).toBe(42);
        expect(s.goalSeconds).toBe(1800);
    });
});
