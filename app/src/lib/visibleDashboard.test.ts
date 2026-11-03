// 타이머 오프라인 T0(설계 2026-11-02 §6.4) — 기준 값(캐시 · 서버)을 보이는 순간의 날로 굴리고, 서버가 이미 닫았을 진행 중은 멈춘 것으로 본다.
import type { ContributionDay, Dashboard } from '../api/types';
import { dayKey, nextMidnightOf } from './goalProgress';
import { DISPLAY_CAP_MS } from './liveActivity';
import { type Base, rollToDay } from './visibleDashboard';
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
