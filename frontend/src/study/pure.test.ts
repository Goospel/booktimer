import { describe, expect, it } from 'vitest';

import {
    calendarCells,
    cellLabel,
    cellMarks,
    cycleCheck,
    monthTitle,
    nextDay,
    planSummary,
    prevDay,
    studyNavLinks,
} from './pure';

describe('calendarCells', () => {
    it('앞쪽 요일 오프셋만큼 빈 칸을 채운다 (2026-09-01은 화요일 → 앞에 2칸)', () => {
        const cells = calendarCells(2026, 9);
        expect(cells.slice(0, 3)).toEqual([null, null, '2026-09-01']);
    });

    it('말일을 하드코딩하지 않는다 — 윤년 2월은 29일까지', () => {
        expect(calendarCells(2024, 2).filter(Boolean)).toHaveLength(29);
        expect(calendarCells(2026, 2).filter(Boolean)).toHaveLength(28);
    });

    it('마지막 칸이 그 달 말일이다', () => {
        const cells = calendarCells(2026, 9);
        expect(cells[cells.length - 1]).toBe('2026-09-30');
    });
});

describe('cycleCheck', () => {
    it('무기록 → 지킴 → 못 지킴 → 무기록으로 세 번에 제자리', () => {
        expect(cycleCheck(null)).toBe(true);
        expect(cycleCheck(true)).toBe(false);
        expect(cycleCheck(false)).toBe(null);
    });
});

describe('monthTitle', () => {
    it('0 채움 없이 읽는 말로 쓴다', () => {
        expect(monthTitle(2026, 9)).toBe('2026년 9월');
        expect(monthTitle(2026, 12)).toBe('2026년 12월');
    });
});

describe('prevDay / nextDay', () => {
    it('달 경계를 넘는다', () => {
        expect(prevDay('2026-09-01')).toBe('2026-08-31');
        expect(nextDay('2026-08-31')).toBe('2026-09-01');
    });

    it('윤년 2월 말을 넘는다', () => {
        expect(nextDay('2024-02-28')).toBe('2024-02-29');
        expect(nextDay('2024-02-29')).toBe('2024-03-01');
    });
});

describe('cellMarks', () => {
    const recalls = [
        { date: '2026-09-10', analyzed: true, hasQuestions: true },
        { date: '2026-09-12', analyzed: false, hasQuestions: false },
    ];

    it('그날 복습이 있으면 「복습」 마크', () => {
        expect(cellMarks('2026-09-10', recalls).recall).toBe(true);
        expect(cellMarks('2026-09-11', recalls).recall).toBe(false);
    });

    it('전날 복습에 문제가 있으면 그날에 「문제」 마크 — 오늘 풀 몫이다', () => {
        expect(cellMarks('2026-09-11', recalls).questions).toBe(true);
        expect(cellMarks('2026-09-10', recalls).questions).toBe(false);
    });

    it('전날 복습이 있어도 문제가 없으면 「문제」 마크는 없다', () => {
        expect(cellMarks('2026-09-13', recalls).questions).toBe(false);
    });

    it('달 경계를 넘어 전달 말일의 문제도 잡는다', () => {
        const crossing = [{ date: '2026-08-31', analyzed: true, hasQuestions: true }];
        expect(cellMarks('2026-09-01', crossing).questions).toBe(true);
    });
});

describe('cellLabel', () => {
    it('숫자만 남지 않게 상태·일정 수까지 읽어 준다', () => {
        expect(cellLabel('2026-09-02', null, 0)).toBe('2일');
        expect(cellLabel('2026-09-02', true, 0)).toBe('2일, 지킴');
        expect(cellLabel('2026-09-12', false, 2)).toBe('12일, 못 지킴, 일정 2개');
    });
});

describe('planSummary', () => {
    const items = (tasks: string[]) =>
        tasks.map((task, i) => ({ id: i + 1, date: '2026-09-10', bookId: null, subject: '과목', task }));

    it('일정이 없으면 빈 문자열', () => {
        expect(planSummary([])).toBe('');
    });

    it('1개면 그 할 일 그대로', () => {
        expect(planSummary(items(['1장 p.1-20']))).toBe('1장 p.1-20');
    });

    it('2개 이상이면 첫 할 일 + 나머지 개수', () => {
        expect(planSummary(items(['1장 p.1-20', '2장 p.21-40']))).toBe('1장 p.1-20 +1');
        expect(planSummary(items(['가', '나', '다']))).toBe('가 +2');
    });
});

describe('studyNavLinks', () => {
    it('홈과 내 책장으로 돌아가는 길을 준다', () => {
        expect(studyNavLinks().map((l) => l.href)).toEqual(['/', '/books']);
    });
});
