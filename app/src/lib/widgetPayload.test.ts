import type { ReadingWidgetProps, WidgetMonth } from '../../widgets/ReadingWidget';
import type { ContributionDay } from '../api/types';
import { goalProgress, goalStatus, nextMidnightOf } from './goalProgress';
import { WIDGET_URL } from './widgetIntent';
import { signedOutWidget, widgetMonth, widgetPayload, type WidgetSource, widgetTimeline } from './widgetPayload';

// v1 REQ-34 · 범위 보충 REQ-106 — 위젯은 스스로 갱신하지 못해 앱이 값을 밀어 넣는다. 날짜를 항상 싣는다.
// 위젯 확장(2026-10-28) REQ-184 — 목표 · 연속 글은 홈 목표 줄과 같은 조각(goalParts)에서 온다. 「목표 채움」 글은 없다.
// 픽스처의 두 연속은 다른 값이다(goalStreak 5 · currentStreak 9) — 어느 쪽을 읽었는지 물을 수 있게.
const MIN = 60;
const now = new Date(2026, 9, 23, 21, 0);
const at = (h: number, m: number, s = 0) => new Date(2026, 9, 23, h, m, s).toISOString();
const idle = {
  hasActiveSession: false,
  activeStartedAt: null,
  activeBookTitle: null,
  todayReadSeconds: 32 * 60 + 59,
  todayGoalSeconds: 30 * 60,
  graph: { goalStreak: 5, currentStreak: 9 },
};
const start = { label: '읽기 시작', url: WIDGET_URL.start };
const reading = (startedAt: string, extra: Partial<WidgetSource> = {}) => ({ ...idle, hasActiveSession: true, activeStartedAt: startedAt, activeBookTitle: '데미안', ...extra });
const studying = { hasActiveSession: true, activeStartedAt: at(20, 40), activeBook: { title: '선형대수학' } };
const texts = (p: ReadingWidgetProps) => [p.idleTitle, p.idleNote, p.runNote].filter((t): t is string => t !== undefined);

test('REQ-34 · 측정 중이 아니어도 dateLabel이 있다', () => {
  // 끝난 몫 32분 ≥ 목표 30분 — 서버가 오늘을 이미 셌다(5일째 그대로)
  expect(widgetPayload(idle, now)).toEqual({ dateLabel: '10월 23일', todayMinutes: 32, mode: 'reading', idleTitle: '32분', idleNote: '목표 30분 · 5일째', action: start });
});

test('REQ-34 · 측정 중이면 activeStartedAt과 책 제목을 싣는다', () => {
  const started = '2026-10-23T11:30:00Z';
  const p = widgetPayload({ ...idle, hasActiveSession: true, activeStartedAt: started, activeBookTitle: '데미안' }, now);
  expect(p.dateLabel).toBe('10월 23일');
  expect(p.activeStartedAt).toBe(Date.parse(started));
  expect(p.bookTitle).toBe('데미안');
});

test('REQ-106 · 공부 측정 중이면 공부 모드와 그 시작 시각·책 제목을 싣는다', () => {
  const studyAt = '2026-10-23T11:40:00Z';
  const study = { hasActiveSession: true, activeStartedAt: studyAt, activeBook: { title: '혼자 공부하는 머신러닝+딥러닝' } };
  const p = widgetPayload({ ...idle, study }, now);
  expect(p.mode).toBe('study');
  expect(p.activeStartedAt).toBe(Date.parse(studyAt));
  expect(p.bookTitle).toBe('혼자 공부하는 머신러닝+딥러닝');
  expect(p.todayMinutes).toBe(32); // 대기 값은 독서 그대로
  // 책 없이 재는 공부 — 제목 키가 없다
  const bare = widgetPayload({ ...idle, study: { ...study, activeBook: null } }, now);
  expect(bare.mode).toBe('study');
  expect(bare).not.toHaveProperty('bookTitle');
  // 양성 대조 — 독서 측정 중이면 독서(공부가 끝난 상태)
  const r = widgetPayload({ ...idle, hasActiveSession: true, activeStartedAt: '2026-10-23T11:30:00Z', activeBookTitle: '데미안', study: { hasActiveSession: false, activeStartedAt: null, activeBook: null } }, now);
  expect(r.mode).toBe('reading');
  expect(r.bookTitle).toBe('데미안');
  // 둘 다 쉬면 독서 대기
  expect(widgetPayload({ ...idle, study: { hasActiveSession: false, activeStartedAt: null, activeBook: null } }, now)).toEqual({
    dateLabel: '10월 23일',
    todayMinutes: 32,
    mode: 'reading',
    idleTitle: '32분',
    idleNote: '목표 30분 · 5일째',
    action: start,
  });
});

test('REQ-184 · 측정 전 읽은 날 — 큰 글 「12분」 · 작은 글 「목표 30분 · 5일째」(연속은 goalStreak — 9가 아니다)', () => {
  const p = widgetPayload({ ...idle, todayReadSeconds: 12 * MIN }, now);
  expect(p.idleTitle).toBe('12분');
  expect(p.idleNote).toBe('목표 30분 · 5일째');
  expect(p).not.toHaveProperty('runNote');
});

test('REQ-184 · 안 읽은 날(59초) — 큰 글 「하루 목표 30분」 · 작은 글 없음 · 60초부터는 읽은 날 글(경계)', () => {
  const blank = widgetPayload({ ...idle, todayReadSeconds: 59 }, now);
  expect(blank.idleTitle).toBe('하루 목표 30분');
  expect(blank).not.toHaveProperty('idleNote');
  const one = widgetPayload({ ...idle, todayReadSeconds: 60 }, now);
  expect(one.idleTitle).toBe('1분');
  expect(one.idleNote).toBe('목표 30분 · 5일째');
});

test('REQ-184 · 채운 날 — 끝난 몫 40분이면 작은 글 「목표 30분 · 5일째」(서버가 이미 오늘을 셌다 · 「목표 채움」 글은 없다) · 진행 중 몫으로 처음 채우면 머리 「목표 30분 · 6일째」', () => {
  const done = widgetPayload({ ...idle, todayReadSeconds: 40 * MIN }, now);
  expect(done.idleTitle).toBe('40분');
  expect(done.idleNote).toBe('목표 30분 · 5일째');
  // 끝난 20분 + 20:45 시작 15분 = 35분 ≥ 30분 — 진행 중 몫으로 처음 채웠다
  const run = widgetPayload(reading(at(20, 45), { todayReadSeconds: 20 * MIN }), now);
  expect(run.runNote).toBe('목표 30분 · 6일째');
  for (const p of [done, run]) expect(texts(p).join('|')).not.toMatch(/채움/);
});

test('REQ-184 · 목표 없음 — 큰 글 「12분」 · 작은 글 「목표 없음」 · 안 읽은 날 큰 글 「하루 목표 없음」 · 연속 글 없음(goalStreak 4여도)', () => {
  const none = { ...idle, todayGoalSeconds: 0, graph: { goalStreak: 4, currentStreak: 9 } };
  const read = widgetPayload({ ...none, todayReadSeconds: 12 * MIN }, now);
  expect(read.idleTitle).toBe('12분');
  expect(read.idleNote).toBe('목표 없음');
  const blank = widgetPayload({ ...none, todayReadSeconds: 0 }, now);
  expect(blank.idleTitle).toBe('하루 목표 없음');
  expect(blank).not.toHaveProperty('idleNote');
  for (const p of [read, blank]) expect(texts(p).join('|')).not.toMatch(/일째/);
});

test('REQ-184 · 독서 측정 중 머리 — 「목표 30분 · 5일째」(첫 1분에도 · 「오늘 N분」 없음) · 목표 없음 「목표 없음」 · 공부는 runNote 없음', () => {
  const first = widgetPayload(reading(at(20, 59, 30), { todayReadSeconds: 0 }), now);
  expect(first.runNote).toBe('목표 30분 · 5일째');
  expect(first).not.toHaveProperty('idleTitle');
  expect(first).not.toHaveProperty('idleNote');
  expect(texts(first).join('|')).not.toMatch(/오늘/);
  const none = widgetPayload(reading(at(20, 50), { todayGoalSeconds: 0, todayReadSeconds: 0 }), now);
  expect(none.runNote).toBe('목표 없음');
  const study = widgetPayload({ ...idle, study: studying }, now);
  expect(study.mode).toBe('study');
  expect(texts(study)).toEqual([]); // 위젯이 「공부 중」을 그린다 — 공부엔 목표가 없다
});

// 이 테스트가 두 출처가 갈리는 고침을 단독으로 잡는다 — 홈 줄(goalStatus)과 위젯 글이 같은 조각이다.
test('REQ-184 · 위젯 글은 홈 목표 줄과 같은 조각이다 — 같은 progress로 goalStatus가 「오늘 {큰 글} · {작은 글}」(읽은 날 · 채운 날 · 목표 없음)이고 안 읽은 날엔 goalStatus = 큰 글', () => {
  const cases = [
    { todayReadSeconds: 12 * MIN, todayGoalSeconds: 30 * MIN },
    { todayReadSeconds: 40 * MIN, todayGoalSeconds: 30 * MIN },
    { todayReadSeconds: 12 * MIN, todayGoalSeconds: 0 },
    { todayReadSeconds: 59, todayGoalSeconds: 30 * MIN },
    { todayReadSeconds: 0, todayGoalSeconds: 0 },
  ];
  for (const c of cases) {
    const d = { ...idle, ...c };
    const progress = goalProgress({ todayRead: c.todayReadSeconds, goal: c.todayGoalSeconds, activeStartedAt: null, now: now.getTime(), nextMidnight: nextMidnightOf(now.getTime()), goalStreak: 5 });
    const home = goalStatus({ progress, goal: c.todayGoalSeconds, measuring: false });
    const p = widgetPayload(d, now);
    expect(home).toBe(p.idleNote === undefined ? p.idleTitle : `오늘 ${p.idleTitle} · ${p.idleNote}`);
  }
});

test('REQ-180 · 단추 — 측정 전 「읽기 시작」+start · 독서 측정 중 「측정 종료」+stop-reading · 공부 측정 중 「측정 종료」+stop-study', () => {
  expect(widgetPayload(idle, now).action).toEqual({ label: '읽기 시작', url: 'booktimer://widget/start' });
  expect(widgetPayload(reading(at(20, 50)), now).action).toEqual({ label: '측정 종료', url: 'booktimer://widget/stop-reading' });
  expect(widgetPayload({ ...idle, study: studying }, now).action).toEqual({ label: '측정 종료', url: 'booktimer://widget/stop-study' });
});

// REQ-185 — 위젯은 스스로 값을 못 구한다. 측정 중 목표에 닿는 순간은 타임라인 둘째 항목이 앱을 안 열어도 바꾼다. 자정 항목(REQ-193)은 아래 절 — 여기 단언은 그날 항목만 본다.
test('REQ-185 · 독서 측정 중 · 목표 전이면 목표에 닿는 시각에 둘째 항목(21:00 · 20:50 시작 · 끝난 10분 · 목표 30분 → 21:10에 머리 「목표 30분 · 6일째」)', () => {
  const t = widgetTimeline(reading(at(20, 50), { todayReadSeconds: 10 * MIN }), now);
  expect(sameDay(t, now)).toHaveLength(2);
  expect(t[0].date).toEqual(now);
  expect(t[0].props.runNote).toBe('목표 30분 · 5일째');
  expect(t[1].date).toEqual(new Date(2026, 9, 23, 21, 10));
  expect(t[1].props.runNote).toBe('목표 30분 · 6일째');
  expect(t[1].props.action).toEqual({ label: '측정 종료', url: WIDGET_URL.stopReading }); // 측정은 이어진다
  expect(t[1].props.dateLabel).toBe('10월 23일');
});

test('REQ-185 · 공부 측정 · 이미 채움 · 목표 없음 · 자정 너머(23:50 시작 · 남은 30분) · 상한 너머 · 측정 전이면 그날 항목은 하나', () => {
  const late = new Date(2026, 9, 23, 23, 55);
  const sources: [WidgetSource, Date][] = [
    [{ ...idle, todayReadSeconds: 0, study: studying }, now], // 공부
    [reading(at(20, 50), { todayReadSeconds: 25 * MIN }), now], // 이미 채움(25 + 10)
    [reading(at(20, 50), { todayGoalSeconds: 0, todayReadSeconds: 0 }), now], // 목표 없음
    [reading(at(23, 50), { todayReadSeconds: 0 }), late], // 닿는 시각 00:20 — 다음 자정 너머
    [reading(at(14, 0), { todayGoalSeconds: 8 * 60 * MIN, todayReadSeconds: 0 }), new Date(2026, 9, 23, 15, 0)], // 시작 + 6시간 상한 너머
    [{ ...idle, todayReadSeconds: 0 }, now], // 측정 전
  ];
  for (const [d, n] of sources) {
    const t = widgetTimeline(d, n);
    expect(sameDay(t, n)).toHaveLength(1);
    expect(t[0]).toEqual({ date: n, props: widgetPayload(d, n) });
  }
});

// 업데이트 사이엔 옛 JS가 남긴 값과 새 그림이 엇갈린다 — 새 칸이 모두 선택이어야 옛 값이 여전히 위젯 값이다(타입 검사가 단언한다).
test('REQ-187 · 새 칸은 선택이다 — 옛 값 {dateLabel, todayMinutes, mode}도 ReadingWidgetProps다', () => {
  const old: ReadingWidgetProps = { dateLabel: '10월 23일', todayMinutes: 32, mode: 'reading' };
  expect(Object.keys(old)).toHaveLength(3);
});

// ── W③ 큰 위젯 이번 달 달력(REQ-189 · 190) — 일요일 시작 · 날짜로 찾는다 ──
// 서버 잔디는 weeks[0] = 최신 주(뒤집힘 — CLAUDE.md 「잔디 방향 규약」). 픽스처도 서버 순서 그대로 최신 주가 앞이고, 앞날은 날짜 null.
// 가짜 자료 = 피그마 시안(2026년 10월 · 오늘 28일): 꽉 찬 점 8일(2 · 9 · 10 · 23~27) · 빈 원 4일(5 · 13 · 22 · 오늘 28 — 12분).
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
function serverWeeks(today: Date, levels: Record<string, number>, n = 10): ContributionDay[][] {
  const sunday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - today.getDay());
  const out: ContributionDay[][] = [];
  for (let w = 0; w < n; w++) {
    const week: ContributionDay[] = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(sunday.getFullYear(), sunday.getMonth(), sunday.getDate() - 7 * w + i);
      const level = levels[iso(d)] ?? 0;
      week.push(d > today ? { date: null, totalSeconds: 0, level: 0, manual: false } : { date: iso(d), totalSeconds: level * 600, level, manual: false });
    }
    out.push(week);
  }
  return out;
}
const oct28 = new Date(2026, 9, 28, 21, 0);
const octLevels: Record<string, number> = {
  '2026-09-30': 4, // 다른 달 — 무시
  '2026-10-02': 4,
  '2026-10-05': 1,
  '2026-10-09': 4,
  '2026-10-10': 4,
  '2026-10-13': 3,
  '2026-10-22': 2,
  '2026-10-23': 4,
  '2026-10-24': 4,
  '2026-10-25': 4,
  '2026-10-26': 4,
  '2026-10-27': 4,
  '2026-10-28': 2, // 오늘 — 서버 칸이 아니라 진행 중 몫을 얹은 판정이 정한다
};
const octIdle = { ...idle, todayReadSeconds: 12 * MIN, graph: { goalStreak: 5, currentStreak: 9, weeks: serverWeeks(oct28, octLevels) } };
const cells = (m: Pick<WidgetMonth, 'rows'>) => m.rows.flat();
const marksOf = (m: Pick<WidgetMonth, 'rows'>) => Object.fromEntries(cells(m).filter((c) => c.day > 0).map((c) => [c.day, c.mark]));
const blanksBefore = (m: Pick<WidgetMonth, 'rows'>) => cells(m).findIndex((c) => c.day > 0);
const blank = { seconds: 0, filled: false };

test('REQ-189 · 달력 줄 — 2026년 10월은 앞 빈칸 4 · 31일 · 5줄, 8월은 앞 빈칸 6 · 6줄, 2028년 2월은 29일(윤년) · 모든 줄이 7칸', () => {
  const months: [Date, number, number, number][] = [
    [oct28, 4, 31, 5],
    [new Date(2026, 7, 31, 12), 6, 31, 6],
    [new Date(2028, 1, 15, 12), 2, 29, 5],
    [new Date(2026, 1, 15, 12), 0, 28, 4], // 1일이 일요일인 28일 달 — 가장 적은 4줄(레이아웃 최소 높이)
  ];
  for (const [n, before, days, rows] of months) {
    const m = widgetMonth([], n, blank);
    expect(blanksBefore(m)).toBe(before);
    expect(cells(m).filter((c) => c.day > 0).map((c) => c.day)).toEqual(Array.from({ length: days }, (_, i) => i + 1));
    expect(m.rows).toHaveLength(rows);
    for (const row of m.rows) expect(row).toHaveLength(7);
    // 빈칸은 날 0 · 표식 0 — 위젯이 숫자 없는 자리로 그린다
    for (const c of cells(m).filter((c) => c.day === 0)) expect(c).toEqual({ day: 0, mark: 0, today: false });
  }
});

test('REQ-189 · 표식 — 4단계 = 2 · 1~3단계 = 1 · 0 = 0 · 앞날 = 0 · 다른 달(9월 30일 4단계) 무시 · 날짜 없는 칸 무시', () => {
  const m = widgetPayload(octIdle, oct28).month!;
  const marks = marksOf(m);
  for (const d of [2, 9, 10, 23, 24, 25, 26, 27]) expect(marks[d]).toBe(2);
  for (const d of [5, 13, 22, 28]) expect(marks[d]).toBe(1);
  expect(Object.entries(marks).filter(([, k]) => k > 0)).toHaveLength(12);
  expect(marks[1]).toBe(0); // 9월 30일이 앞 칸에 섞이지 않는다
  // 앞날 — 기기 날짜가 서버보다 늦어 앞날 칸에 날짜 · 단계가 와도 표식은 없다
  const ahead = serverWeeks(new Date(2026, 9, 30), { ...octLevels, '2026-10-29': 4, '2026-10-30': 4 });
  expect(marksOf(widgetMonth(ahead, oct28, blank))[29]).toBe(0);
});

test('REQ-189 · 날짜로 찾는다 — weeks 순서를 뒤집어도(가장 옛 주가 앞) 같은 달력이다', () => {
  const weeks = octIdle.graph.weeks;
  const flipped = [...weeks].reverse().map((w) => [...w].reverse());
  expect(widgetMonth(flipped, oct28, { seconds: 12 * MIN, filled: false })).toEqual(widgetMonth(weeks, oct28, { seconds: 12 * MIN, filled: false }));
  // 양성 대조 — 단계를 하나 바꾸면 달력이 달라진다(같은 결과가 우연이 아니다)
  const changed = serverWeeks(oct28, { ...octLevels, '2026-10-02': 1 });
  expect(marksOf(widgetMonth(changed, oct28, blank))[2]).toBe(1);
});

test('REQ-189 · 오늘 칸은 진행 중 몫 — 서버 칸이 0단계여도 진행 중으로 채우면 2 · 읽기만 했으면 1 · 0초면 0 · 오늘만 today', () => {
  const weeks = serverWeeks(oct28, { ...octLevels, '2026-10-28': 0 });
  // 끝난 20분 + 20:45 시작 15분 = 35분 ≥ 30분
  const run = widgetPayload({ ...octIdle, todayReadSeconds: 20 * MIN, hasActiveSession: true, activeStartedAt: new Date(2026, 9, 28, 20, 45).toISOString(), activeBookTitle: '데미안', graph: { goalStreak: 5, weeks } }, oct28);
  expect(marksOf(run.month!)[28]).toBe(2);
  expect(marksOf(widgetPayload({ ...octIdle, graph: { goalStreak: 5, weeks } }, oct28).month!)[28]).toBe(1);
  // 서버 칸이 2단계라도 오늘 0초면(다른 기기 시간대 등) 진행 판정을 따른다
  expect(marksOf(widgetPayload({ ...octIdle, todayReadSeconds: 0 }, oct28).month!)[28]).toBe(0);
  const todays = cells(widgetPayload(octIdle, oct28).month!).filter((c) => c.today);
  expect(todays.map((c) => c.day)).toEqual([28]);
  // 목표 없음(0)이면 읽은 날 = 서버 4단계와 같은 뜻 — 오늘 12분도 꽉 찬 점이고 머리 연속은 없다(설계 §4.2 · §8-7)
  const noGoal = widgetPayload({ ...octIdle, todayGoalSeconds: 0, graph: { goalStreak: 5, weeks: octIdle.graph.weeks } }, oct28).month!;
  expect(marksOf(noGoal)[28]).toBe(2);
  expect(noGoal).not.toHaveProperty('streak');
});

test('REQ-189 · 달이 바뀐 날 — 11월 1일(일요일)엔 11월 한 장 · 앞 빈칸 0 · 10월 칸은 안 보인다', () => {
  const nov1 = new Date(2026, 10, 1, 0, 30);
  const weeks = serverWeeks(nov1, octLevels);
  const m = widgetPayload({ ...idle, todayReadSeconds: 0, graph: { goalStreak: 0, weeks } }, nov1).month!;
  expect(m.title).toBe('11월');
  expect(blanksBefore(m)).toBe(0);
  expect(m.rows).toHaveLength(5);
  expect(Object.values(marksOf(m)).filter((k) => k > 0)).toEqual([]);
  expect(m.a11y).toBe('11월, 목표 채운 날 0일, 읽은 날 0일');
});

test('REQ-185 · 목표에 닿는 시각 항목에서는 오늘 칸이 2다 · 머리 연속 「6일째」', () => {
  const d = { ...octIdle, todayReadSeconds: 10 * MIN, hasActiveSession: true, activeStartedAt: new Date(2026, 9, 28, 20, 50).toISOString(), activeBookTitle: '데미안' };
  const t = widgetTimeline(d, oct28);
  expect(sameDay(t, oct28)).toHaveLength(2);
  expect(marksOf(t[0].props.month!)[28]).toBe(1);
  expect(t[0].props.month!.streak).toBe('5일째');
  expect(marksOf(t[1].props.month!)[28]).toBe(2);
  expect(t[1].props.month!.streak).toBe('6일째');
  expect(t[1].props.month!.a11y).toBe('10월, 목표 채운 날 9일, 읽은 날 12일');
});

// 둘째 줄 원문 = 설계 §4.4a 표 큰 위젯 열(홈 압박 PR 화해 — 「목표 채움」 없음). 글은 goalParts 조각 — 홈 목표 줄과 같은 출처다.
test('REQ-190 · 둘째 줄 — 측정 전 · 채운 날 · 안 읽은 날 · 목표 없음 · 독서 측정 중(목표에 닿은 뒤도) · 책 없이 · 공부 — §4.4a 원문', () => {
  const line = (d: WidgetSource, n = oct28) => widgetPayload(d, n).month!.line;
  const run = { ...octIdle, todayReadSeconds: 10 * MIN, hasActiveSession: true, activeStartedAt: new Date(2026, 9, 28, 20, 50).toISOString(), activeBookTitle: '데미안' };
  expect(line(octIdle)).toBe('10월 28일 · 오늘 12분 · 목표 30분');
  expect(line({ ...octIdle, todayReadSeconds: 40 * MIN })).toBe('10월 28일 · 오늘 40분 · 목표 30분');
  expect(line({ ...octIdle, todayReadSeconds: 59 })).toBe('10월 28일 · 하루 목표 30분');
  expect(line({ ...octIdle, todayGoalSeconds: 0 })).toBe('10월 28일 · 오늘 12분 · 목표 없음');
  expect(line({ ...octIdle, todayGoalSeconds: 0, todayReadSeconds: 0 })).toBe('10월 28일 · 하루 목표 없음');
  expect(line(run)).toBe('읽는 중 · 목표 30분 · 『데미안』');
  expect(widgetTimeline(run, oct28)[1].props.month!.line).toBe('읽는 중 · 목표 30분 · 『데미안』');
  expect(line({ ...run, todayGoalSeconds: 0 })).toBe('읽는 중 · 목표 없음 · 『데미안』');
  expect(line({ ...run, activeBookTitle: null })).toBe('읽는 중 · 목표 30분');
  expect(line({ ...octIdle, study: studying })).toBe('공부 중 · 『선형대수학』');
  // 같은 출처 — 측정 전 둘째 줄은 「날짜 · 홈 목표 줄(연속 뺌)」이다
  const progress = goalProgress({ todayRead: 12 * MIN, goal: 30 * MIN, activeStartedAt: null, now: oct28.getTime(), nextMidnight: nextMidnightOf(oct28.getTime()), goalStreak: 5 });
  expect(line(octIdle)).toBe(`10월 28일 · ${goalStatus({ progress, goal: 30 * MIN, measuring: false }).replace(' · 5일째', '')}`);
});

test('REQ-190 · 머리 「10월」 · 연속 「5일째」(goalStreak — 9가 아니다) · VoiceOver 한 문장 「10월, 목표 채운 날 8일, 읽은 날 12일」(읽은 날 = 빈 원 + 꽉 찬 점)', () => {
  const m = widgetPayload(octIdle, oct28).month!;
  expect(m.title).toBe('10월');
  expect(m.streak).toBe('5일째');
  expect(m.a11y).toBe('10월, 목표 채운 날 8일, 읽은 날 12일');
});

test('REQ-190 · 안 읽은 날 · 목표 없음이면 머리 연속 없음(REQ-184 규칙) · weeks가 없으면(옛 서버) 달력 없음', () => {
  expect(widgetPayload({ ...octIdle, todayReadSeconds: 59 }, oct28).month).not.toHaveProperty('streak');
  expect(widgetPayload({ ...octIdle, todayGoalSeconds: 0, graph: { ...octIdle.graph, goalStreak: 4 } }, oct28).month).not.toHaveProperty('streak');
  expect(widgetPayload(idle, now)).not.toHaveProperty('month'); // 큰 위젯도 작은 배치(REQ-187)
});

test('REQ-186 · 로그아웃 값에는 달력이 없다', () => {
  expect(signedOutWidget(oct28)).not.toHaveProperty('month');
  expect(widgetPayload(octIdle, oct28)).toHaveProperty('month'); // 양성 대조
});

// ── 자정 항목(REQ-193~195 · 2026-10-31) — 앱을 안 열어도 자정에 새 날로. 새 날 값 = 「그 자정에 앱을 열었다면」의 대시보드 ──
// 두 출처를 다르게 둔다: 서버 잔디의 전날 칸 단계 ≠ 앱이 센 전날 마지막 상태 · goalStreak(유예 연속) ≠ 새 날 연속 · currentStreak 9는 섞어 둔다.
// 잔디가 있는 픽스처는 now와 같은 날까지 짓는다(서버의 오늘 = 기기의 오늘 — 가드 T11).
type Timeline = ReturnType<typeof widgetTimeline>;
const sameDay = (t: Timeline, n: Date) => t.filter((e) => e.date.getTime() < nextMidnightOf(n.getTime()));
const entry = (t: Timeline, when: Date) => {
  const e = t.find((x) => x.date.getTime() === when.getTime());
  expect(e).toBeDefined(); // Red: 자정 항목이 없다
  return e!.props;
};
const oct30 = new Date(2026, 9, 30, 21, 0);
// 사용자 사례(10/31 10:14 TF#2) — 서버 잔디 30일 칸은 0단계로 둔다(앱이 센 34분과 갈리게)
const oct30Idle = { ...idle, todayReadSeconds: 34 * MIN, todayGoalSeconds: 60 * MIN, graph: { goalStreak: 1, currentStreak: 9, weeks: serverWeeks(oct30, { '2026-10-29': 4 }) } };
const late = new Date(2026, 9, 23, 23, 55);
const oct24 = (h = 0, m = 0, s = 0) => new Date(2026, 9, 24, h, m, s);
const stopReading = { label: '측정 종료', url: WIDGET_URL.stopReading };

test('REQ-193 · 측정 전이면 지금 항목 뒤에 다음 자정부터 7번의 자정 항목 — 10/30 21:00 → 10/31 00:00 … 11/6 00:00', () => {
  const t = widgetTimeline(oct30Idle, oct30);
  expect(t.map((e) => e.date)).toEqual([oct30, ...Array.from({ length: 7 }, (_, i) => new Date(2026, 9, 31 + i))]);
});

test('REQ-193 · 지금이 자정 정각이면 첫 자정 항목은 다음 날 · 자정 1ms 전이면 1ms 뒤 — 같은 시각 항목이 두 번 없다', () => {
  const nov1 = new Date(2026, 10, 1);
  expect(widgetTimeline(idle, nov1).map((e) => e.date)).toEqual(Array.from({ length: 8 }, (_, i) => new Date(2026, 10, 1 + i)));
  const t = widgetTimeline(idle, new Date(2026, 9, 31, 23, 59, 59, 999));
  expect(entry(t, nov1).dateLabel).toBe('11월 1일');
  expect(t[0].props.dateLabel).toBe('10월 31일');
});

test('REQ-194 · 새 날 작은 위젯 — 「10월 31일」 · 큰 글 「하루 목표 1시간」 · 작은 글 없음 · [읽기 시작] · 옛 배치 분 0(끝난 34분은 전날 몫)', () => {
  const t = widgetTimeline(oct30Idle, oct30);
  // 양성 대조 — 픽스처가 사용자 그림(「10월 30일 · 오늘 34분 · 목표 1시간 · 1일째」)을 낸다
  expect(t[0].props).toMatchObject({ dateLabel: '10월 30일', idleTitle: '34분', idleNote: '목표 1시간 · 1일째' });
  const p = entry(t, new Date(2026, 9, 31));
  expect(p).toMatchObject({ dateLabel: '10월 31일', todayMinutes: 0, mode: 'reading', idleTitle: '하루 목표 1시간', action: start });
  expect(p).not.toHaveProperty('idleNote');
  expect(p).not.toHaveProperty('activeStartedAt');
});

test('REQ-194 · 새 날 큰 위젯 — 전날 칸은 앱이 센 마지막 상태(34분/1시간 → 빈 원 · 서버 칸 0단계여도 · 30초여도 빈 원) · 그 앞 날은 서버 잔디 · 오늘 칸 = 새 날 · 머리 연속 없음 · 둘째 줄 「10월 31일 · 하루 목표 1시간」', () => {
  const t = widgetTimeline(oct30Idle, oct30);
  // 양성 대조 — 읽은 날의 오늘 칸은 30일 · 머리 「1일째」
  expect(cells(t[0].props.month!).filter((c) => c.today).map((c) => c.day)).toEqual([30]);
  expect(t[0].props.month!.streak).toBe('1일째');
  const m = entry(t, new Date(2026, 9, 31)).month!;
  expect(marksOf(m)).toMatchObject({ 29: 2, 30: 1, 31: 0 });
  expect(cells(m).filter((c) => c.today).map((c) => c.day)).toEqual([31]);
  expect(m).not.toHaveProperty('streak');
  expect(m.line).toBe('10월 31일 · 하루 목표 1시간');
  // 1분 미만 전날(30초) — 서버 1단계와 같은 규칙(> 0)으로 빈 원 · 새 날 글은 안 읽은 날
  const p = entry(widgetTimeline({ ...oct30Idle, todayReadSeconds: 30 }, oct30), new Date(2026, 9, 31));
  expect(marksOf(p.month!)[30]).toBe(1);
  expect(p.idleTitle).toBe('하루 목표 1시간');
});

test('REQ-194 · 달이 바뀐 자정 — 11/1 항목은 11월 한 장(앞 빈칸 0 · 5줄 · 1일만 굵게 · 표식 0) · 12/31에 읽으면 1/1 항목은 1월 6줄', () => {
  const nov = entry(widgetTimeline(oct30Idle, oct30), new Date(2026, 10, 1)).month!;
  expect(nov.title).toBe('11월');
  expect(blanksBefore(nov)).toBe(0);
  expect(nov.rows).toHaveLength(5);
  expect(cells(nov).filter((c) => c.today).map((c) => c.day)).toEqual([1]);
  expect(Object.values(marksOf(nov)).filter((k) => k > 0)).toEqual([]);
  // 안 읽은 11/1(덧붙인 0초 칸) — 11/2 항목에서 표식 없음(REQ-194 「0 없음」)
  expect(marksOf(entry(widgetTimeline(oct30Idle, oct30), new Date(2026, 10, 2)).month!)[1]).toBe(0);
  const dec31 = new Date(2026, 11, 31, 21, 0);
  const jan = entry(widgetTimeline({ ...idle, graph: { ...idle.graph, weeks: serverWeeks(dec31, {}) } }, dec31), new Date(2027, 0, 1)).month!;
  expect(jan.title).toBe('1월');
  expect(jan.rows).toHaveLength(6);
});

test('REQ-194 · 목표 없음이면 새 날 「하루 목표 없음」 · 전날 12분은 꽉 찬 점(목표 0 = 읽은 날이 채운 날) · 잔디 · 연속 칸 없는 옛 값에도 자정 항목(달력 없이)', () => {
  // 서버 30일 칸은 0단계(oct30Idle) — 꽉 찬 점은 앱이 센 전날 마지막 상태에서만 나온다
  const none = { ...oct30Idle, todayReadSeconds: 12 * MIN, todayGoalSeconds: 0, graph: { ...oct30Idle.graph, goalStreak: 4 } };
  const p = entry(widgetTimeline(none, oct30), new Date(2026, 9, 31));
  expect(p.idleTitle).toBe('하루 목표 없음');
  expect(marksOf(p.month!)[30]).toBe(2);
  expect(p.month).not.toHaveProperty('streak');
  const old = { hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, todayReadSeconds: 34 * MIN, todayGoalSeconds: 60 * MIN };
  const t = widgetTimeline(old, oct30);
  expect(t).toHaveLength(8);
  const q = entry(t, new Date(2026, 9, 31));
  expect(q.idleTitle).toBe('하루 목표 1시간');
  expect(q).not.toHaveProperty('month');
});

// 손 유도(서버 규칙 → 기대값). 서버 연속은 「오늘을 채웠으면 오늘부터, 아니면 어제부터」 거꾸로 센다(ContributionGraphBuilder currentGoalStreak 유예).
// 채움 = levelFor 4(정확히 100%도 4). 날별 초 = 끝난 몫 + 진행 중 세션의 오늘보다 이른 날 몫(withActiveBeforeToday). 새 날 0시의 「오늘」은 0초라 커서는 늘 전날이다.
test('REQ-194 · 새 날 연속 — 전날을 끝난 몫으로 채웠으면 그대로(40분 · goalStreak 6 → 「6일째」) · 진행 중 몫으로 처음 채웠으면 +1(18:30 시작 · goalStreak 2 → 「3일째」) · 못 채웠으면 0(23:50 시작 10분 · 유예 goalStreak 5 → 연속 없음) — 자정을 넘겨 재는 새 날 머리(00:00:01)로 본다', () => {
  const head = (d: WidgetSource) => entry(widgetTimeline(d, late), oct24(0, 0, 1)).runNote;
  // ① 10/23 서버 커서 = 10/23(40분 ≥ 30분)이라 6에 10/23이 들어 있다. 10/24 0시 커서 10/23 = 40 + 10(23:50~24:00) = 50분 → 채움 → 6
  expect(head(reading(at(23, 50), { todayReadSeconds: 40 * MIN, graph: { ...idle.graph, goalStreak: 6 } }))).toBe('목표 30분 · 6일째');
  // ② 10/23 서버 = 0 → 유예로 10/22부터 2. 10/24 0시 커서 10/23 = 0 + 5시간 30분(18:30~24:00 · 상한 00:30 전이라 진행 중) → 채움 → 3
  expect(head(reading(at(18, 30), { todayReadSeconds: 0, graph: { ...idle.graph, goalStreak: 2 } }))).toBe('목표 30분 · 3일째');
  // ③ 10/24 0시 커서 10/23 = 0 + 10분 < 30분 → 못 채움 → 0 → 머리 「목표 30분」(연속 글 없음)
  expect(head(reading(at(23, 50), { todayReadSeconds: 0, graph: { ...idle.graph, goalStreak: 5 } }))).toBe('목표 30분');
});

test('REQ-195 · 자정을 넘기는 독서 — 새 날 항목은 00:00:01(정각 항목 없음) · 측정 중(같은 시작 · [측정 종료]) · 오늘 칸 빈 원(목표 없음이면 꽉 찬 점) · 새 날 목표에 닿는 시각 항목(23:50 시작 → 00:30 「목표 30분 · 1일째」) · 전날 닿는 시각이 자정이면 전날 항목 없음(23:40 시작 · 끝난 10분 · goalStreak 3 → 00:00:01 「4일째」 · 00:30 「5일째」) · 그다음 자정부터 측정 전', () => {
  const d = reading(at(23, 50), { todayReadSeconds: 0, graph: { ...idle.graph, weeks: serverWeeks(late, {}) } });
  const t = widgetTimeline(d, late);
  expect(t.find((e) => e.date.getTime() === oct24().getTime())).toBeUndefined();
  const p = entry(t, oct24(0, 0, 1));
  expect(p).toMatchObject({ activeStartedAt: Date.parse(at(23, 50)), action: stopReading });
  expect(marksOf(p.month!)[24]).toBe(1);
  expect(marksOf(entry(widgetTimeline({ ...d, todayGoalSeconds: 0 }, late), oct24(0, 0, 1)).month!)[24]).toBe(2);
  // ④ 10/24 = 진행 중 30분(00:00~) → 오늘 채움 · 10/23은 못 채움이라 거기서 끊긴다 → 1
  expect(entry(t, oct24(0, 30)).runNote).toBe('목표 30분 · 1일째');
  expect(sameDay(t, late)).toHaveLength(1); // 닿는 시각 00:20 — 다음 자정 너머
  expect(entry(t, new Date(2026, 9, 25))).toMatchObject({ dateLabel: '10월 25일', idleTitle: '하루 목표 30분', action: start });
  // ⑤ 10/23 서버 = 10분 → 유예로 10/22부터 3. 10/24 0시 커서 10/23 = 10 + 20(23:40~24:00) = 30분 = 100% → 4단계 → 4 · 00:30에 10/24 = 30분 → +1 → 5
  const e = widgetTimeline(reading(at(23, 40), { todayReadSeconds: 10 * MIN, graph: { ...idle.graph, goalStreak: 3 } }), late);
  expect(sameDay(e, late)).toHaveLength(1); // 닿는 시각 = 자정 — 그날 항목 없음
  expect(entry(e, oct24(0, 0, 1)).runNote).toBe('목표 30분 · 4일째');
  expect(entry(e, oct24(0, 30)).runNote).toBe('목표 30분 · 5일째');
});

test('REQ-195 · 상한 — 자정 뒤 상한(18:30 시작)은 새 날 00:30에 닿아 새 날 칸 꽉 찬 점 · 10/25부터 측정 전 · 상한 = 자정(18:00 시작)이면 새 날 00:00은 측정 전(서버가 자정에 닫는다) · 같은 날 상한(15:00 시작 · 목표 8시간)이면 새 날은 측정 전이고 전날 칸 = 6시간(빈 원 · 서버 칸 0단계여도)', () => {
  const n = new Date(2026, 9, 23, 23, 0);
  const t = widgetTimeline(reading(at(18, 30), { todayReadSeconds: 0, graph: { ...idle.graph, goalStreak: 2, weeks: serverWeeks(n, { '2026-10-22': 4, '2026-10-23': 1 }) } }), n);
  expect(marksOf(entry(t, oct24(0, 0, 1)).month!)).toMatchObject({ 22: 2, 23: 2, 24: 1 });
  expect(marksOf(entry(t, oct24(0, 30)).month!)[24]).toBe(2);
  const oct25 = entry(t, new Date(2026, 9, 25));
  expect(oct25).not.toHaveProperty('activeStartedAt');
  expect(marksOf(oct25.month!)).toMatchObject({ 23: 2, 24: 2 });
  // 상한 = 자정 — 새 날 몫 0(서버가 자정에 닫는다) · 전날 = 6시간(꽉 찬 점 · 서버 칸 1단계여도)
  const edge = entry(widgetTimeline(reading(at(18, 0), { todayReadSeconds: 0, graph: { ...idle.graph, goalStreak: 2, weeks: serverWeeks(n, { '2026-10-23': 1 }) } }), n), oct24());
  expect(edge).toMatchObject({ idleTitle: '하루 목표 30분', action: start });
  expect(edge).not.toHaveProperty('activeStartedAt');
  expect(marksOf(edge.month!)[23]).toBe(2);
  // 같은 날 상한 — 15:00 + 6시간 = 21:00
  const n2 = new Date(2026, 9, 23, 20, 0);
  const same = entry(widgetTimeline(reading(at(15, 0), { todayGoalSeconds: 8 * 60 * MIN, todayReadSeconds: 0, graph: { ...idle.graph, weeks: serverWeeks(n2, {}) } }), n2), oct24());
  expect(same.idleTitle).toBe('하루 목표 8시간');
  expect(marksOf(same.month!)[23]).toBe(1);
});

test('REQ-195 · 공부가 자정을 넘기면(23:30 시작) 새 날 00:00도 「공부 중」(1초 뒤로 옮기지 않는다 — 독서 달력과 무관) · [측정 종료] 공부 · 그다음 자정부터 독서 측정 전', () => {
  const t = widgetTimeline({ ...idle, todayReadSeconds: 0, study: { ...studying, activeStartedAt: at(23, 30) } }, late);
  expect(entry(t, oct24())).toMatchObject({ mode: 'study', activeStartedAt: Date.parse(at(23, 30)), action: { label: '측정 종료', url: WIDGET_URL.stopStudy } });
  expect(entry(t, new Date(2026, 9, 25))).toMatchObject({ mode: 'reading', idleTitle: '하루 목표 30분', action: start });
});

test('REQ-193 · 서버의 오늘(잔디에서 날짜가 있는 가장 늦은 칸)이 기기의 오늘과 다르면 자정 항목 없이 그날 항목만 — 서버가 하루 앞(10/31) · 하루 뒤(10/29)', () => {
  expect(widgetTimeline(oct30Idle, oct30)).toHaveLength(8); // 양성 대조 — 같은 날이면 자정 항목이 있다
  for (const serverDay of [new Date(2026, 9, 31), new Date(2026, 9, 29)]) {
    const d = { ...oct30Idle, graph: { ...oct30Idle.graph, weeks: serverWeeks(serverDay, { '2026-10-29': 4 }) } };
    expect(widgetTimeline(d, oct30).map((e) => e.date)).toEqual([oct30]);
  }
});
