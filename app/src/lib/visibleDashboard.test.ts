// 타이머 오프라인 T0(설계 2026-11-02 §6.4) — 기준 값(캐시 · 서버)을 보이는 순간의 날로 굴리고, 서버가 이미 닫았을 진행 중은 멈춘 것으로 본다.
import type { ContributionDay, Dashboard } from '../api/types';
import { dayKey, goalProgress, nextMidnightOf } from './goalProgress';
import { DISPLAY_CAP_MS } from './liveActivity';
import { type DoneMeasure, EMPTY_LEDGER, end, type Ledger } from './measureLedger';
import { goalNoticeStep } from './reminders';
import { type Base, cachedOf, effectiveDashboard, rollToDay } from './visibleDashboard';
import { nextDaySource, widgetTimeline } from './widgetPayload';

const MIN = 60;
const iso = (d: Date) => dayKey(d.getFullYear(), d.getMonth(), d.getDate());
/** 서버 잔디 — today까지 날짜, 그 뒤 칸은 date null(서버 꼴) */
function weeks(today: Date, levels: Record<string, number> = {}): ContributionDay[][] {
  const sunday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - today.getDay());
  return Array.from({ length: 3 }, (_, w) =>
    Array.from({ length: 7 }, (_, i) => {
      const d = new Date(sunday.getFullYear(), sunday.getMonth(), sunday.getDate() - 7 * w + i);
      const level = levels[iso(d)] ?? 0;
      return d > today ? { date: null, totalSeconds: 0, level: 0, manual: false } : { date: iso(d), totalSeconds: level * 600, level, manual: false };
    }),
  );
}
const book = (id: number, title: string) => ({ id, title, author: null, coverUrl: null, isbn13: null });
const studyBook = { id: 9, title: '선형대수학', author: null, coverUrl: null, isbn13: null, totalSeconds: 3600 };

function dash(at: Date, p: Partial<Dashboard> = {}): Dashboard {
  return {
    todayGoalSeconds: 30 * MIN,
    todayReadSeconds: 34 * MIN,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBook: null,
    readingBooks: [book(1, '데미안')],
    wantToReadBooks: [book(2, '수레바퀴 아래서')],
    recentBookId: 1,
    graph: { goalStreak: 5, weeks: weeks(at, { [iso(at)]: 4 }) },
    study: { hasActiveSession: false, activeStartedAt: null, todaySeconds: 25 * MIN, activeBook: null, recentBookId: 9, books: [studyBook] },
    ...p,
  } as Dashboard;
}
const base = (at: Date, p: Partial<Dashboard> = {}): Base => ({ d: dash(at, p), at: at.getTime(), sentAt: at.getTime() - 300, seq: 1 });

const oct30 = new Date(2026, 9, 30, 21, 0);
const oct31 = new Date(2026, 9, 31, 10, 0);

test('REQ-360 · 다른 날의 값이면 새 날로 굴린다 — 끝난 몫 0 · 연속 유예 · 상한 지난 진행 중은 멈춤 · 여러 날', () => {
  // 어제 채운 날(34분 ≥ 30분) — 오늘 아침엔 끝난 몫 0 · 연속 5 그대로(서버 유예와 같다)
  const b = base(oct30);
  const r = rollToDay(b, oct31.getTime());
  const n = nextDaySource(b.d, nextMidnightOf(b.at));
  expect(r.rolled).toBe(true);
  expect(r.d.todayReadSeconds).toBe(0);
  expect(r.d.todayReadSeconds).toBe(n.todayReadSeconds);
  expect(r.d.graph.goalStreak).toBe(5);
  expect(r.d.graph.goalStreak).toBe(n.graph?.goalStreak);
  expect(r.d.study.todaySeconds).toBe(0);
  expect(r.d.todayGoalSeconds).toBe(30 * MIN);

  // 어제 17:00에 켠 측정(상한 23:00 — 자정 전에 서버가 닫았다)은 굴린 날엔 멈춰 있다
  const ran = rollToDay(base(oct30, { hasActiveSession: true, activeStartedAt: new Date(2026, 9, 30, 17, 0).toISOString(), activeBookTitle: '데미안', activeBook: book(1, '데미안') }), oct31.getTime());
  expect(ran.d.hasActiveSession).toBe(false);
  expect(ran.d.activeStartedAt).toBeNull();
  expect(ran.d.activeBook).toBeNull();

  // 여러 날 — 10/28(미달 10분 · 연속 3)에서 10/31로: 미달 날을 넘었으니 연속 0 · 지난 날 칸 셋 + 오늘 빈 칸
  const oct28 = new Date(2026, 9, 28, 21, 0);
  const many = rollToDay(base(oct28, { todayReadSeconds: 10 * MIN, graph: { goalStreak: 3, weeks: weeks(oct28) } }), oct31.getTime());
  expect(many.rolled).toBe(true);
  expect(many.d.graph.goalStreak).toBe(0);
  expect(many.d.graph.weeks!.slice(-4).map((w) => w[0].date)).toEqual(['2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31']);
});

test('REQ-360 · 굴리기는 읽는 책 목록 · 공부 서재를 지우지 않는다', () => {
  const b = base(oct30);
  const r = rollToDay(b, oct31.getTime());
  expect(r.d.readingBooks).toEqual(b.d.readingBooks);
  expect(r.d.wantToReadBooks).toEqual(b.d.wantToReadBooks);
  expect(r.d.recentBookId).toBe(1);
  expect(r.d.study.books).toEqual([studyBook]);
  expect(r.d.study.recentBookId).toBe(9);
});

test('REQ-360 · 굴린 값으로 만든 위젯 타임라인에도 자정 항목이 7개 있다', () => {
  const r = rollToDay(base(oct30), oct31.getTime());
  const t = widgetTimeline(r.d, oct31);
  expect(t.filter((e) => e.date.getHours() === 0 && e.date.getMinutes() === 0)).toHaveLength(7);
  expect(r.d.graph.weeks!.at(-1)).toEqual([{ date: '2026-10-31', totalSeconds: 0, level: 0, manual: false }]);
  // 음성 대조 — 오늘 빈 칸이 없으면 서버의 오늘(어제) ≠ 기기의 오늘이라 자정 항목 0개(가드 widgetPayload.ts)
  const bare = { ...r.d, graph: { ...r.d.graph, weeks: r.d.graph.weeks!.slice(0, -1) } };
  expect(widgetTimeline(bare, oct31)).toHaveLength(1);
});

test('REQ-360 · 같은 날이면 굴리지 않는다(rolled 거짓 · 같은 객체)', () => {
  const b = base(oct30);
  const r = rollToDay(b, new Date(2026, 9, 30, 23, 59, 59, 999).getTime());
  expect(r.rolled).toBe(false);
  expect(r.d).toBe(b.d);
});

test('REQ-375 · 시작 + 6시간 10분이 지난 서버 진행 중(독서 · 공부)은 멈춘 것으로 — 1ms 전이면 그대로', () => {
  const day = new Date(2026, 9, 30, 8, 0);
  const startedAt = new Date(2026, 9, 30, 8, 0).toISOString();
  const cap = Date.parse(startedAt) + DISPLAY_CAP_MS;

  const r = base(day, { hasActiveSession: true, activeStartedAt: startedAt, activeBookTitle: '데미안', activeBook: book(1, '데미안') });
  expect(rollToDay(r, cap - 1).d).toBe(r.d);
  const off = rollToDay(r, cap).d;
  expect(off).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null, todayReadSeconds: 34 * MIN });

  const s = base(day, { study: { ...dash(day).study, hasActiveSession: true, activeStartedAt: startedAt, activeBook: studyBook } });
  expect(rollToDay(s, cap - 1).d).toBe(s.d);
  const soff = rollToDay(s, cap).d;
  expect(soff.study).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBook: null, todaySeconds: 25 * MIN, books: [studyBook] });
});

// ── 타이머 오프라인 T2(§6.4) — 원장 얹기 · 날마다 접기 · 확인 안 된 서버 진행 중 ──────────────────────────
const at = (d: Date, h: number, m = 0) => new Date(d.getFullYear(), d.getMonth(), d.getDate(), h, m).getTime();
const part = (s: number, e: number, p: Partial<DoneMeasure> = {}): DoneMeasure => ({
  mode: 'reading',
  startedAt: new Date(s).toISOString(),
  endedAt: new Date(e).toISOString(),
  bookId: 1,
  failedSince: null,
  settledAt: null,
  ...p,
});
const ledger = (p: Partial<Ledger>): Ledger => ({ ...EMPTY_LEDGER, ...p });
const serverReading = { hasActiveSession: true, activeStartedAt: new Date(at(oct30, 20, 30)).toISOString(), activeBookTitle: '데미안', activeBook: book(1, '데미안') };
const serverStudy = (d: Date) => ({ study: { ...dash(d).study, hasActiveSession: true, activeStartedAt: new Date(at(oct30, 20, 30)).toISOString(), activeBook: studyBook } });

test('REQ-378 · 확인 안 된 기준 값의 서버 진행 중(독서 · 공부)은 멈춘 것으로 보인다 — 확인된 값이면 그대로 · 기기 원장의 진행 중은 확인과 무관하게 보인다', () => {
  const now = at(oct30, 21, 1);
  const r = base(oct30, serverReading);
  expect(effectiveDashboard(r, EMPTY_LEDGER, now, true).d).toBe(r.d); // 원장이 비고 확인된 값이면 T0 그대로(같은 객체)
  expect(effectiveDashboard(r, EMPTY_LEDGER, now, false).d).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null, todayReadSeconds: 34 * MIN });
  const s = base(oct30, serverStudy(oct30));
  expect(effectiveDashboard(s, EMPTY_LEDGER, now, true).d.study.hasActiveSession).toBe(true);
  expect(effectiveDashboard(s, EMPTY_LEDGER, now, false).d.study).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBook: null, todaySeconds: 25 * MIN });
  // 기기 원장의 독서 진행 중 — 확인 안 된 값이어도 보인다
  const mine = ledger({ active: { mode: 'reading', startedAt: new Date(at(oct30, 20, 50)).toISOString(), bookId: 1, bookTitle: '데미안' } });
  expect(effectiveDashboard(r, mine, now, false).d).toMatchObject({ hasActiveSession: true, activeStartedAt: mine.active!.startedAt, activeBookTitle: '데미안' });
});

test('REQ-374 · 기기 독서 측정이 그 모드를 차지하고 서버의 공부 진행 중은 멈춘 것으로 — 책은 서재에서 찾는다', () => {
  const now = at(oct30, 21, 1);
  const b = base(oct30, serverStudy(oct30));
  const startedAt = new Date(at(oct30, 20, 50)).toISOString();
  const d = effectiveDashboard(b, ledger({ active: { mode: 'reading', startedAt, bookId: 2, bookTitle: '수레바퀴 아래서' } }), now, true).d;
  expect(d).toMatchObject({ hasActiveSession: true, activeStartedAt: startedAt, activeBookTitle: '수레바퀴 아래서', activeBook: book(2, '수레바퀴 아래서') }); // 읽고싶음 책도 찾는다
  expect(d.study).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBook: null, books: [studyBook] });
  // 서재에 없는 책(지워진 책 — 캐시가 낡음)이면 책 칸은 비고 제목은 원장 값
  const gone = effectiveDashboard(b, ledger({ active: { mode: 'reading', startedAt, bookId: 99, bookTitle: '지운 책' } }), now, true).d;
  expect(gone).toMatchObject({ activeBook: null, activeBookTitle: '지운 책' });
});

test('REQ-374 · 기기 공부 측정 — 공부 서재 책(없으면 원장 제목으로 만든 책) · 독서는 멈춘 것으로', () => {
  const now = at(oct30, 21, 1);
  const b = base(oct30, serverReading);
  const startedAt = new Date(at(oct30, 20, 50)).toISOString();
  const study = (bookId: number | null, bookTitle: string | null) => effectiveDashboard(b, ledger({ active: { mode: 'study', startedAt, bookId, bookTitle } }), now, true).d;
  const d = study(9, '선형대수학');
  expect(d.study).toMatchObject({ hasActiveSession: true, activeStartedAt: startedAt, activeBook: studyBook });
  expect(d).toMatchObject({ hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null });
  expect(study(5, '해석학').study.activeBook).toEqual({ id: 5, title: '해석학', author: null, coverUrl: null, isbn13: null, totalSeconds: 0 });
  expect(study(null, null).study.activeBook).toBeNull();
});

test('REQ-374 · 기준 값이 모르는 끝난 측정의 그날 몫을 더한다(자정 앞 몫 · 다른 날 0)', () => {
  const b = base(oct30); // 21:00에 받은 값 — 독서 34분 · 공부 25분
  const l = ledger({
    done: [
      part(at(oct30, 21, 10), at(oct30, 21, 20)), // 오늘 10분
      part(at(oct30, 21, 30), at(oct30, 21, 35), { mode: 'study' }), // 공부 5분
      part(at(oct30, 8), at(oct30, 8, 30), { settledAt: b.sentAt - 1 }), // 기준 값보다 먼저 받은 칸 — 이미 들어 있다
      part(at(oct30, 1) - 24 * 3600_000, at(oct30, 2) - 24 * 3600_000), // 기준 날보다 이전 날 — 얹지 않는다(비목표 6)
    ],
  });
  const d = effectiveDashboard(b, l, at(oct30, 22), true).d;
  expect(d.todayReadSeconds).toBe(44 * MIN);
  expect(d.study.todaySeconds).toBe(30 * MIN);
  // 자정을 걸친 기기 측정 — 다음 날엔 자정부터의 몫만
  const night = ledger({ done: [part(at(oct30, 23, 50), at(oct31, 0, 10))] });
  expect(effectiveDashboard(b, night, at(oct31, 9), true).d.todayReadSeconds).toBe(10 * MIN);
});

test('REQ-374 · 더한 몫이 목표를 처음 채우면 연속 +1 · 서버가 이미 채웠으면 그대로 · 목표 0이면 읽은 날이 채운 날(goalProgress와 같은 규칙)', () => {
  const now = at(oct30, 22);
  const tenMin = ledger({ done: [part(at(oct30, 21, 10), at(oct30, 21, 20))] });
  // 25분(미달) + 10분 → 채움 · 연속 5 → 6
  expect(effectiveDashboard(base(oct30, { todayReadSeconds: 25 * MIN }), tenMin, now, true).d.graph.goalStreak).toBe(6);
  // 34분(이미 채움) + 10분 → 그대로 5
  expect(effectiveDashboard(base(oct30), tenMin, now, true).d.graph.goalStreak).toBe(5);
  // 5분 + 10분 = 15분 < 30분 → 그대로
  expect(effectiveDashboard(base(oct30, { todayReadSeconds: 5 * MIN }), tenMin, now, true).d.graph.goalStreak).toBe(5);
  // 목표 0 — 서버도 읽은 날을 채운 날로 센다(ContributionGraphBuilder.levelFor). 안 읽은 날 + 기기 몫 → +1, 이미 읽은 날이면 그대로
  expect(effectiveDashboard(base(oct30, { todayGoalSeconds: 0, todayReadSeconds: 0 }), tenMin, now, true).d.graph.goalStreak).toBe(6);
  expect(effectiveDashboard(base(oct30, { todayGoalSeconds: 0, todayReadSeconds: 60 }), tenMin, now, true).d.graph.goalStreak).toBe(5);
  // 공부 몫은 목표 · 연속과 무관하다
  const study = ledger({ done: [part(at(oct30, 21, 10), at(oct30, 21, 20), { mode: 'study' })] });
  expect(effectiveDashboard(base(oct30, { todayReadSeconds: 25 * MIN }), study, now, true).d.graph.goalStreak).toBe(5);
});

test('REQ-374 · 어제 기기에만 있던 몫이 어제 목표를 채우면 오늘 연속이 이어진다(날마다 접기)', () => {
  const b = base(oct30, { todayReadSeconds: 25 * MIN }); // 어제 21:00 — 미달 · 연속 5
  const l = ledger({ done: [part(at(oct30, 21, 10), at(oct30, 21, 20))] }); // 퇴근 지하철 10분 → 어제 채움
  const r = effectiveDashboard(b, l, oct31.getTime(), true);
  expect(r.rolled).toBe(true);
  expect(r.d.todayReadSeconds).toBe(0);
  expect(r.d.graph.goalStreak).toBe(6);
  // 음성 대조 — 접지 않고 굴리면 어제가 미달이라 연속이 끊긴다
  expect(rollToDay(b, oct31.getTime()).d.graph.goalStreak).toBe(0);
});

test('REQ-374 · 6시간 넘게 켜진 기기 측정은 보내기 전에도 끝난 몫으로 보인다', () => {
  const b = base(oct30);
  const l = ledger({ active: { mode: 'reading', startedAt: new Date(at(oct30, 14, 30)).toISOString(), bookId: 1, bookTitle: '데미안' } });
  const d = effectiveDashboard(b, l, at(oct30, 21, 0), true).d; // 6시간 30분째
  expect(d.hasActiveSession).toBe(false);
  expect(d.todayReadSeconds).toBe(34 * MIN + 6 * 3600);
  // 1분 전(5시간 59분)엔 아직 진행 중 — 끝난 몫에 없다
  const run = effectiveDashboard(b, l, at(oct30, 20, 29), true).d;
  expect(run).toMatchObject({ hasActiveSession: true, todayReadSeconds: 34 * MIN });
});

test('REQ-368 · 받은 뒤 다음 읽기가 실패해도 오늘 몫이 줄지 않는다(받은 칸이 그 전 기준 값에 더해진다)', () => {
  const b = base(oct30); // sentAt = 21:00 − 300ms · 34분
  const settled = ledger({ done: [part(at(oct30, 21, 10), at(oct30, 21, 20), { settledAt: at(oct30, 21, 21) })] });
  const now = at(oct30, 21, 30);
  expect(effectiveDashboard(b, settled, now, true).d.todayReadSeconds).toBe(44 * MIN); // 서버가 받았지만 기준 값은 모른다
  // 받은 뒤에 보낸 요청의 응답(그 몫이 들어 있다)이 기준 값이면 한 번만
  const later: Base = { d: dash(oct30, { todayReadSeconds: 44 * MIN }), at: at(oct30, 21, 22), sentAt: at(oct30, 21, 21) + 1, seq: 2 };
  expect(effectiveDashboard(later, settled, now, true).d.todayReadSeconds).toBe(44 * MIN);
});

// 목표 달성 띠(REQ-371)는 원장을 얹은 보이는 값으로도 지금 규칙 그대로다 — 홈과 같은 계산(index.tsx)으로 관측을 이어 본다
function observe(b: Base, l: Ledger, now: number, prev: boolean | null) {
  const { d, rolled } = effectiveDashboard(b, l, now, true);
  const progress = goalProgress({
    todayRead: d.todayReadSeconds,
    goal: d.todayGoalSeconds,
    activeStartedAt: d.hasActiveSession && d.activeStartedAt ? Date.parse(d.activeStartedAt) : null,
    now,
    nextMidnight: nextMidnightOf(now),
    goalStreak: d.graph.goalStreak ?? 0,
  });
  return goalNoticeStep(prev, { rolled, cached: cachedOf(b), progress, goal: d.todayGoalSeconds });
}

test('REQ-371 · (T2) 원장 얹기 — 새 값이면 측정 중 채우는 순간 한 번 · 오프라인 정지로 끝난 몫이 되어도 다시 뜨지 않는다 · 디스크 값이면 뜨지 않는다', () => {
  const start = at(oct30, 21, 0);
  const running = ledger({ active: { mode: 'reading', startedAt: new Date(start).toISOString(), bookId: 1, bookTitle: '데미안' } });
  const stopped = end(running, start + 7 * 60_000); // 정지(오프라인 — 원장의 끝난 칸이 같은 몫을 더한다)
  for (const seq of [1, 0]) {
    const b: Base = { ...base(oct30, { todayReadSeconds: 25 * MIN }), seq };
    let prev: boolean | null = null;
    const seen: (string | null)[] = [];
    const steps: [Ledger, number][] = [
      [running, start + 60_000], // 26분 — 미달
      [running, start + 6 * 60_000], // 31분 — 채우는 순간
      [stopped, start + 7 * 60_000 + 1],
      [stopped, start + 9 * 60_000],
    ];
    for (const [l, now] of steps) {
      const step = observe(b, l, now, prev);
      seen.push(step.message);
      prev = step.prev;
    }
    // 새 값(seq 1)은 채우는 순간 한 번(연속 5 → 6) · 디스크 값(seq 0)은 띠 없음(REQ-371 — 캐시 → 새 값으로 띠가 뜨지 않게)
    expect(seen).toEqual(seq === 1 ? [null, '오늘 목표를 채웠어요 · 6일째', null, null] : [null, null, null, null]);
  }
  expect(cachedOf(null)).toBe(true);
  expect(cachedOf({ ...base(oct30), seq: 0 })).toBe(true);
  expect(cachedOf(base(oct30))).toBe(false);
});
