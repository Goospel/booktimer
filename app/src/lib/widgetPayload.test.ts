import type { ReadingWidgetProps } from '../../widgets/ReadingWidget';
import { goalProgress, goalStatus, nextMidnightOf } from './goalProgress';
import { WIDGET_URL } from './widgetIntent';
import { widgetPayload, type WidgetSource, widgetTimeline } from './widgetPayload';

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

// REQ-185 — 위젯은 스스로 값을 못 구한다. 측정 중 목표에 닿는 순간은 타임라인 둘째 항목이 앱을 안 열어도 바꾼다. 자정 항목은 없다(v1 §4.10).
test('REQ-185 · 독서 측정 중 · 목표 전이면 목표에 닿는 시각에 둘째 항목(21:00 · 20:50 시작 · 끝난 10분 · 목표 30분 → 21:10에 머리 「목표 30분 · 6일째」)', () => {
  const t = widgetTimeline(reading(at(20, 50), { todayReadSeconds: 10 * MIN }), now);
  expect(t).toHaveLength(2);
  expect(t[0].date).toEqual(now);
  expect(t[0].props.runNote).toBe('목표 30분 · 5일째');
  expect(t[1].date).toEqual(new Date(2026, 9, 23, 21, 10));
  expect(t[1].props.runNote).toBe('목표 30분 · 6일째');
  expect(t[1].props.action).toEqual({ label: '측정 종료', url: WIDGET_URL.stopReading }); // 측정은 이어진다
  expect(t[1].props.dateLabel).toBe('10월 23일');
});

test('REQ-185 · 공부 측정 · 이미 채움 · 목표 없음 · 자정 너머(23:50 시작 · 남은 30분) · 상한 너머 · 측정 전이면 한 항목', () => {
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
    expect(t).toHaveLength(1);
    expect(t[0]).toEqual({ date: n, props: widgetPayload(d, n) });
  }
});

// 업데이트 사이엔 옛 JS가 남긴 값과 새 그림이 엇갈린다 — 새 칸이 모두 선택이어야 옛 값이 여전히 위젯 값이다(타입 검사가 단언한다).
test('REQ-187 · 새 칸은 선택이다 — 옛 값 {dateLabel, todayMinutes, mode}도 ReadingWidgetProps다', () => {
  const old: ReadingWidgetProps = { dateLabel: '10월 23일', todayMinutes: 32, mode: 'reading' };
  expect(Object.keys(old)).toHaveLength(3);
});
