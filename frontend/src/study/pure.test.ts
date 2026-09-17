import { describe, expect, it } from 'vitest';

import {
    aiStatusLine,
    calendarCells,
    cellLabel,
    cellMarks,
    cycleCheck,
    dayTitle,
    errorMessage,
    monthTitle,
    nextDay,
    notesBookParam,
    planSummary,
    planWeeks,
    prevDay,
    recallScopePrefill,
    recallSubjectPrefill,
    readCountLabel,
    recallDateParam,
    sameTitleExists,
    studyOwned,
    studyView,
    validatePlanForm,
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

describe('studyView', () => {
    it('/study는 달력, /study/history는 기록, /study/books는 서재 — 같은 셸이 경로로 화면을 고른다', () => {
        expect(studyView('/study')).toBe('calendar');
        expect(studyView('/study/history')).toBe('history');
        expect(studyView('/study/books')).toBe('books');
    });

    it('컨텍스트 패스 배포에서도 끝자락으로 고른다', () => {
        expect(studyView('/ctx/study/history')).toBe('history');
        expect(studyView('/ctx/study/books')).toBe('books');
        expect(studyView('/ctx/study')).toBe('calendar');
    });

    it('/study/recall은 백지노트 화면 — 바 「백지노트」가 여는 곳', () => {
        expect(studyView('/study/recall')).toBe('recall');
    });

    it('/study/notes는 필기 화면 — 바 「필기」가 여는 곳(컨텍스트 패스 포함)', () => {
        expect(studyView('/study/notes')).toBe('notes');
        expect(studyView('/ctx/study/notes')).toBe('notes');
    });
});

describe('notesBookParam', () => {
    const shelf = [{ id: 7 }, { id: 9 }];

    // 첫 책(7)이 아닌 id로 잰다 — 첫 책이면 「param이 닿았다」와 「폴백」이 같은 값이다.
    it('?bookId=가 서재에 있으면 그 id', () => {
        expect(notesBookParam('?bookId=9', shelf)).toBe(9);
    });

    it('서재에 없는 id는 첫 책으로 — 남의 책 id로 목록을 부르지 않는다', () => {
        expect(notesBookParam('?bookId=42', shelf)).toBe(7);
    });

    it('숫자가 아니거나 없으면 첫 책', () => {
        expect(notesBookParam('?bookId=abc', shelf)).toBe(7);
        expect(notesBookParam('', shelf)).toBe(7);
    });

    it('서재가 비었으면 null', () => {
        expect(notesBookParam('?bookId=9', [])).toBeNull();
    });
});

describe('recallDateParam', () => {
    // 형식이 틀린 값을 그대로 넘기면 fetchAgenda('2026-9')가 400이 된다 — 틀리면 null(=오늘)로 떨어뜨린다.
    it('?date=YYYY-MM-DD면 그 날짜', () => {
        expect(recallDateParam('?date=2026-09-12')).toBe('2026-09-12');
    });

    it('다른 쿼리가 붙어 있어도 date만 읽는다', () => {
        expect(recallDateParam('?date=2026-09-12&x=1')).toBe('2026-09-12');
    });

    it('0 채움이 없는 날짜·빈 값·쿼리 없음은 null', () => {
        expect(recallDateParam('?date=2026-9-1')).toBeNull();
        expect(recallDateParam('?date=')).toBeNull();
        expect(recallDateParam('')).toBeNull();
    });

    // 형식은 맞는데 달력에 없는 날 — 2월 30일은 머리 「2월 30일」+ 저장 400, 13월은 agenda 실패로 화면 전체가 막힌다.
    it('달력에 없는 날(2026-02-30 · 2026-13-01)은 null', () => {
        expect(recallDateParam('?date=2026-02-30')).toBeNull();
        expect(recallDateParam('?date=2026-13-01')).toBeNull();
    });

    it('윤년 2월 29일은 있는 날이다', () => {
        expect(recallDateParam('?date=2028-02-29')).toBe('2028-02-29');
    });
});

describe('studyOwned', () => {
    // 검색 응답의 owned는 「독서 책장」 기준이라 공부 화면에선 쓰지 않는다 — 내 공부 서재 isbn 집합으로 다시 센다.
    it('내 공부 서재 isbn 집합에 있으면 true', () => {
        expect(studyOwned(new Set(['i1']), 'i1')).toBe(true);
    });

    it('isbn이 없는 책은 「없다」 — 열쇠가 없으니 매칭할 수 없다', () => {
        expect(studyOwned(new Set(['i1']), null)).toBe(false);
    });

    it('집합 밖 isbn은 false', () => {
        expect(studyOwned(new Set<string>(), 'i1')).toBe(false);
    });
});

describe('readCountLabel', () => {
    it('0독도 그린다 — 「아직 안 돌았다」는 빈칸이 아니라 정보다', () => {
        expect(readCountLabel(0)).toBe('0독');
        expect(readCountLabel(3)).toBe('3독');
    });
});

describe('errorMessage', () => {
    it('400은 서버가 준 한국어 문구를 그대로 쓴다 — 사용자가 고칠 수 있는 유일한 실패다', () => {
        expect(errorMessage(400, '할 일을 입력해 주세요')).toBe('할 일을 입력해 주세요');
    });

    it('400인데 본문이 비면 고정 문구로 떨어진다', () => {
        expect(errorMessage(400, '   ')).toBe('일정을 추가하지 못했어요.');
    });

    it('404는 본문을 믿지 않는다 — GlobalExceptionHandler가 error.html을 통째로 준다', () => {
        expect(errorMessage(404, '<!DOCTYPE html><html><body>오류</body></html>'))
            .toBe('책을 찾을 수 없어요');
    });

    it('500도 본문을 버리고 고정 문구를 쓴다 — HTML이 상태줄에 찍히면 안 된다', () => {
        expect(errorMessage(500, '<!DOCTYPE html><html>...</html>')).toBe('일정을 추가하지 못했어요.');
    });

    it('409도 본문을 믿는다 — 신청 API가 「이미 신청했거나 승인된 상태예요」를 본문으로 준다', () => {
        expect(errorMessage(409, '이미 신청했거나 승인된 상태예요')).toBe('이미 신청했거나 승인된 상태예요');
    });

    it('403은 본문을 버리고 부른 쪽이 준 폴백을 쓴다 — CSRF 만료 응답이 상태줄에 찍히면 안 된다', () => {
        expect(errorMessage(403, '<!DOCTYPE html><html><body>Forbidden</body></html>', 'AI 기능을 신청하지 못했어요.'))
            .toBe('AI 기능을 신청하지 못했어요.');
    });

    it('폴백을 주면 500·빈 400에도 그 문구가 나온다 — 「일정」 문구가 엉뚱한 화면에 새지 않는다', () => {
        expect(errorMessage(500, '<html>...</html>', 'AI 기능을 신청하지 못했어요.')).toBe('AI 기능을 신청하지 못했어요.');
        expect(errorMessage(400, '  ', 'AI 기능을 신청하지 못했어요.')).toBe('AI 기능을 신청하지 못했어요.');
    });

    it('429·503도 본문을 믿는다 — 상한·AI 장애 사유는 사용자가 행동을 바꿀 수 있는 안내다', () => {
        expect(errorMessage(429, '오늘 몫을 다 썼어요 — 한국 시간 오전 9시에 다시 채워져요', 'x'))
            .toBe('오늘 몫을 다 썼어요 — 한국 시간 오전 9시에 다시 채워져요');
        expect(errorMessage(503, 'AI 응답을 받지 못했어요 — 글은 저장돼 있어요', 'x'))
            .toBe('AI 응답을 받지 못했어요 — 글은 저장돼 있어요');
    });

    it('403 본문이 한국어 사유면 그걸 쓴다 — 백지복습 문은 사유를 평문으로 준다', () => {
        expect(errorMessage(403, 'AI 기능은 승인 후 쓸 수 있어요', 'x')).toBe('AI 기능은 승인 후 쓸 수 있어요');
    });

    it('413도 본문을 믿는다 — 「사진은 3MB 이하로」는 사용자가 바로 고칠 수 있는 안내다', () => {
        expect(errorMessage(413, '사진은 3MB 이하로 올려 주세요', '사진을 읽지 못했어요.'))
            .toBe('사진은 3MB 이하로 올려 주세요');
    });

    it('HTML은 상태와 무관하게 버린다 — 서버 자체가 낸 503·429도 error.html일 수 있다', () => {
        expect(errorMessage(503, '<!DOCTYPE html><html>...</html>', 'AI 분석을 받지 못했어요.'))
            .toBe('AI 분석을 받지 못했어요.');
        expect(errorMessage(429, '<html>Too Many</html>', 'AI 분석을 받지 못했어요.'))
            .toBe('AI 분석을 받지 못했어요.');
    });
});

describe('recall 프리필', () => {
    const items = [
        { id: 1, date: '2026-09-03', bookId: null, subject: '정보처리기사', task: '3장 함수 p.45-70' },
        { id: 2, date: '2026-09-03', bookId: null, subject: '영어', task: '단어 200개' },
    ];

    it('범위는 그날 할 일들을 줄바꿈으로 잇는다 — 오늘 하기로 한 것이 곧 오늘의 범위다', () => {
        expect(recallScopePrefill(items)).toBe('3장 함수 p.45-70\n단어 200개');
    });

    it('과목은 첫 일정의 과목 — 일정이 없으면 빈 문자열이라 사용자가 직접 쓴다', () => {
        expect(recallSubjectPrefill(items)).toBe('정보처리기사');
        expect(recallSubjectPrefill([])).toBe('');
        expect(recallScopePrefill([])).toBe('');
    });
});

describe('aiStatusLine', () => {
    it('NONE: 승인제임을 알리고 신청 버튼을 준다', () => {
        expect(aiStatusLine('NONE', false, null)).toEqual({
            text: 'AI 분석·일정 기능은 승인제예요.',
            button: 'AI 기능 신청',
        });
    });

    it('키가 켜져 있어도 미승인이면 승인 얘기를 먼저 한다 — 「꺼져 있어요」는 틀린 안내다', () => {
        expect(aiStatusLine('NONE', true, null).button).toBe('AI 기능 신청');
        expect(aiStatusLine('PENDING', true, null).text).toBe('승인 대기 중이에요.');
        expect(aiStatusLine('REJECTED', true, null).button).toBe('다시 신청');
    });

    it('PENDING: 신청한 날을 함께 보여 주고 버튼은 없앤다 — 두 번 눌러도 소용없다', () => {
        expect(aiStatusLine('PENDING', false, '2026-09-03T04:05:06Z')).toEqual({
            text: '승인 대기 중 — 9월 3일 신청',
            button: null,
        });
    });

    it('PENDING인데 시각을 모르면 날짜 없이 말한다(과거 데이터·null 방어)', () => {
        expect(aiStatusLine('PENDING', false, null)).toEqual({
            text: '승인 대기 중이에요.',
            button: null,
        });
    });

    it('REJECTED: 다시 신청할 수 있다 — 막다른 길로 두지 않는다', () => {
        expect(aiStatusLine('REJECTED', false, '2026-09-03T04:05:06Z')).toEqual({
            text: '승인되지 않았어요.',
            button: '다시 신청',
        });
    });

    it('APPROVED인데 AI가 꺼져 있으면 저장만 된다고 말한다(키가 아직 없는 판)', () => {
        expect(aiStatusLine('APPROVED', false, '2026-09-03T04:05:06Z')).toEqual({
            text: 'AI 기능이 꺼져 있어 저장만 됩니다.',
            button: null,
        });
    });

    it('APPROVED이고 AI가 켜져 있으면 상태 줄이 사라진다 — 그 자리는 AI 버튼 몫이다', () => {
        expect(aiStatusLine('APPROVED', true, '2026-09-03T04:05:06Z')).toEqual({
            text: '',
            button: null,
        });
    });
});

describe('validatePlanForm', () => {
    const TODAY = '2026-09-03';
    const ok = { subject: '정보보안기사', scope: '1장 접근통제', examDate: '2026-12-03', dailyMinutes: 120, daysPerWeek: 5 };

    it('제대로 채운 폼은 통과한다', () => {
        expect(validatePlanForm(ok, TODAY)).toBeNull();
    });

    it('과목이 비면 막는다', () => {
        expect(validatePlanForm({ ...ok, subject: '   ' }, TODAY)).toBe('주제를 입력해 주세요.');
    });

    it('시험일을 안 고르면 막는다', () => {
        expect(validatePlanForm({ ...ok, examDate: '' }, TODAY)).toBe('시험일을 골라 주세요.');
    });

    it('시험일이 오늘이거나 지났으면 막는다 — 짤 일정이 없다', () => {
        expect(validatePlanForm({ ...ok, examDate: TODAY }, TODAY)).toBe('시험일은 내일 이후로 정해 주세요.');
        expect(validatePlanForm({ ...ok, examDate: '2026-09-02' }, TODAY)).toBe('시험일은 내일 이후로 정해 주세요.');
    });

    it('시험일이 1년을 넘으면 막는다 — 경계(365일)는 통과', () => {
        // 주 1일로 재는 것은 항목 수 상한(아래)과 겹치지 않게 하려는 것이다 — 여기서 재는 것은 기간이다.
        expect(validatePlanForm({ ...ok, examDate: '2027-09-03', daysPerWeek: 1 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, examDate: '2027-09-04', daysPerWeek: 1 }, TODAY))
            .toBe('시험일은 1년 안으로 정해 주세요.');
    });

    it('하루 공부 시간은 10~600분이다 — 경계는 통과', () => {
        expect(validatePlanForm({ ...ok, dailyMinutes: 10 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, dailyMinutes: 600 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, dailyMinutes: 9 }, TODAY)).toBe('하루 공부 시간은 10분에서 600분 사이로 적어 주세요.');
        expect(validatePlanForm({ ...ok, dailyMinutes: 601 }, TODAY)).toBe('하루 공부 시간은 10분에서 600분 사이로 적어 주세요.');
    });

    it('주 공부일수는 1~7일이다 — 경계는 통과', () => {
        expect(validatePlanForm({ ...ok, daysPerWeek: 1 }, TODAY)).toBeNull();
        // 주 7일은 짧은 기간으로 잰다 — 긴 기간이면 항목 수 상한이 먼저 걸려 이 경계를 못 본다.
        expect(validatePlanForm({ ...ok, examDate: '2026-10-03', daysPerWeek: 7 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, daysPerWeek: 0 }, TODAY)).toBe('주 공부일수는 1일에서 7일 사이로 정해 주세요.');
        expect(validatePlanForm({ ...ok, daysPerWeek: 8 }, TODAY)).toBe('주 공부일수는 1일에서 7일 사이로 정해 주세요.');
    });

    it('범위는 4000자까지다 — 경계는 통과', () => {
        expect(validatePlanForm({ ...ok, scope: '가'.repeat(4000) }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, scope: '가'.repeat(4001) }, TODAY)).toBe('범위는 4000자까지 적을 수 있어요.');
    });

    it('예상 항목 수가 90개를 넘으면 막는다 — 90은 통과, 91은 거부(주 7일 기준)', () => {
        expect(validatePlanForm({ ...ok, examDate: '2026-12-02', daysPerWeek: 7 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, examDate: '2026-12-03', daysPerWeek: 7 }, TODAY))
            .toBe('기간이 길어 한 번에 만들기 어려워요. 시험일을 앞당기거나 주 공부일수를 줄여 주세요.');
    });

    it('기간이 아니라 항목 수로 막는다 — 1년짜리라도 주 1일이면 통과한다', () => {
        expect(validatePlanForm({ ...ok, examDate: '2027-09-03', daysPerWeek: 1 }, TODAY)).toBeNull();
        expect(validatePlanForm({ ...ok, examDate: '2027-01-03', daysPerWeek: 6 }, TODAY))
            .toBe('기간이 길어 한 번에 만들기 어려워요. 시험일을 앞당기거나 주 공부일수를 줄여 주세요.');
    });

    it('서버 오늘을 아직 모르면 막지 않는다 — 화면이 먼저 지레 잠기지 않게', () => {
        expect(validatePlanForm(ok, '')).toBeNull();
    });
});

describe('planWeeks', () => {
    it('주(월~일) 단위로 묶고 1주차부터 번호를 매긴다', () => {
        // 2026-09-03(목)·09-04(금)은 같은 주, 09-07(월)은 다음 주다.
        const weeks = planWeeks([
            { date: '2026-09-03', task: '1장' },
            { date: '2026-09-04', task: '2장' },
            { date: '2026-09-07', task: '3장' },
        ]);

        expect(weeks.map((w) => w.label)).toEqual(['1주차', '2주차']);
        expect(weeks[0].days.map((d) => d.date)).toEqual(['2026-09-03', '2026-09-04']);
        expect(weeks[1].days.map((d) => d.date)).toEqual(['2026-09-07']);
    });

    it('주 경계는 월요일이다 — 일요일과 그 다음 월요일은 다른 주다(서버 sanitizePlan과 같은 규칙)', () => {
        const weeks = planWeeks([
            { date: '2026-09-13', task: '일요일' },
            { date: '2026-09-14', task: '월요일' },
        ]);

        expect(weeks).toHaveLength(2);
    });

    it('빈 목록은 빈 결과다', () => {
        expect(planWeeks([])).toEqual([]);
    });
});

// 하루 패널(/study)과 홈 백지복습 카드가 같은 문형을 쓰는지 — 두 화면이 갈리면 같은 날이 다르게 읽힌다.
describe('dayTitle', () => {
    it('요일까지 붙인다 — 2026-09-07은 월요일이다', () => {
        expect(dayTitle('2026-09-07')).toBe('9월 7일 (월)');
    });

    it('0 채움을 벗긴다 — 09월이 아니라 9월, 06일이 아니라 6일', () => {
        expect(dayTitle('2026-09-06')).toBe('9월 6일 (일)');
    });
});

/*
 * 같은 제목 재등록 경고 — 서버는 막지 않는다(「isbn 없는 책은 여러 권 허용」이 기존 규약이고 테스트로
 * 박혀 있다). 다만 몇 달 뒤 잊고 다시 담으면 누적 시간이 두 행으로 갈리고 <b>합칠 기능이 없어 영구 분열</b>
 * 이라, 화면이 한 번 묻는다. 「뉴런 수학」/「수학 뉴런」은 못 잡는다 — 정확 일치가 의도한 단순화다.
 */
describe('sameTitleExists', () => {
    it('빈 서재에는 같은 제목이 없다', () => {
        expect(sameTitleExists([], '수학 뉴런')).toBe(false);
    });

    it('대소문자와 앞뒤 공백은 무시한다 — 같은 책을 다르게 적은 것뿐이다', () => {
        const books = [{ title: 'Math Neuron' }];

        expect(sameTitleExists(books, '  math neuron ')).toBe(true);
    });

    it('다른 제목은 경고하지 않는다', () => {
        expect(sameTitleExists([{ title: '수학 뉴런' }], '영어 뉴런')).toBe(false);
    });
});
