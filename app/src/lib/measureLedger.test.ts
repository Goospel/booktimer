// 타이머 오프라인 T2(설계 2026-11-02 §6.3) — 기기 원장의 순수 전이. 앱 측정은 끝날 때까지 기기에만 있다(Q1).
import { begin, capActive, dayParts, type DoneMeasure, drop, EMPTY_LEDGER, end, forgetSettled, isDirty, type Ledger, markFailed, pending, settle, unknownTo } from './measureLedger';

const H = 60 * 60 * 1000;
const T0 = Date.UTC(2026, 10, 3, 1, 0, 0); // 10:00 KST
const iso = (ms: number) => new Date(ms).toISOString();
const done = (startedAt: number, endedAt: number, p: Partial<DoneMeasure> = {}): DoneMeasure => ({
  mode: 'reading',
  startedAt: iso(startedAt),
  endedAt: iso(endedAt),
  bookId: 1,
  failedSince: null,
  settledAt: null,
  ...p,
});
const running = (startedAt: number, mode: 'reading' | 'study' = 'reading'): Ledger => ({ active: { mode, startedAt: iso(startedAt), bookId: 1, bookTitle: '데미안' }, done: [] });

test('REQ-357 · begin은 진행 중이 있으면 그대로 · 시작 시각은 밀리초 ISO', () => {
  const l = begin(EMPTY_LEDGER, { mode: 'reading', at: T0 + 123, bookId: 1, bookTitle: '데미안' });
  expect(l.active).toEqual({ mode: 'reading', startedAt: '2026-11-03T01:00:00.123Z', bookId: 1, bookTitle: '데미안' });
  expect(l.done).toEqual([]);
  // 진행 중이 있으면(다른 모드여도) 같은 객체
  expect(begin(l, { mode: 'study', at: T0 + 1000, bookId: null, bookTitle: null })).toBe(l);
});

test('REQ-357 · begin은 6시간 지난 진행 중을 먼저 끝낸 칸으로 옮기고 새 측정을 연다 — 1ms 전이면 그대로 둔다', () => {
  const old = running(T0);
  const at6 = begin(old, { mode: 'study', at: T0 + 6 * H, bookId: null, bookTitle: null });
  expect(at6.done).toEqual([done(T0, T0 + 6 * H)]);
  expect(at6.active).toMatchObject({ mode: 'study', startedAt: iso(T0 + 6 * H) });
  const before = begin(old, { mode: 'study', at: T0 + 6 * H - 1, bookId: null, bookTitle: null });
  expect(before).toBe(old);
});

test('REQ-357 · end — 1초 미만이면 칸 없이 진행 중만 비운다 · 시계가 뒤로 가도 칸이 없다 · 6시간을 넘긴 끝은 시작 + 6시간', () => {
  expect(end(running(T0), T0 + 999)).toEqual(EMPTY_LEDGER);
  expect(end(running(T0), T0 + 1000)).toEqual({ active: null, done: [done(T0, T0 + 1000)] });
  expect(end(running(T0), T0 - 60_000)).toEqual(EMPTY_LEDGER); // 시계가 뒤로
  expect(end(running(T0), T0 + 7 * H)).toEqual({ active: null, done: [done(T0, T0 + 6 * H)] });
  expect(end(EMPTY_LEDGER, T0)).toBe(EMPTY_LEDGER);
  // 공부 · 책 없이도 칸에 그대로 옮긴다
  const s: Ledger = { active: { mode: 'study', startedAt: iso(T0), bookId: null, bookTitle: null }, done: [] };
  expect(end(s, T0 + 5000).done).toEqual([done(T0, T0 + 5000, { mode: 'study', bookId: null })]);
});

test('REQ-359 · capActive — 6시간 정각이면 시작 + 6시간으로 끝내고 1ms 전이면 그대로', () => {
  const l = running(T0);
  expect(capActive(l, T0 + 6 * H - 1)).toBe(l);
  expect(capActive(l, T0 + 6 * H)).toEqual({ active: null, done: [done(T0, T0 + 6 * H)] });
  expect(capActive(EMPTY_LEDGER, T0)).toBe(EMPTY_LEDGER);
});

test('REQ-359 · settle · drop · markFailed는 (모드, 시작 시각)으로 칸을 찾고 pending은 받은 칸을 빼고 시작 시각 순이다', () => {
  const a = done(T0, T0 + H);
  const b = done(T0 + 2 * H, T0 + 3 * H, { mode: 'study' });
  const c = done(T0 - H, T0 - 1000);
  const l: Ledger = { active: null, done: [a, b, c] };
  expect(pending(l)).toEqual([c, a, b]);
  const s = settle(l, a, 5);
  expect(s.done[0].settledAt).toBe(5);
  expect(pending(s)).toEqual([c, b]);
  // 같은 시작 · 다른 모드는 다른 칸
  expect(settle(l, { ...a, mode: 'study' }, 5)).toBe(l);
  expect(drop(l, b).done).toEqual([a, c]);
  const f = markFailed(l, c, 7);
  expect(f.done[2].failedSince).toBe(7);
  expect(markFailed(f, c, 9)).toBe(f); // 처음 실패한 때를 지킨다
});

test('REQ-368 · 받은 칸은 그보다 늦게 보낸 대시보드에서 걷히고 그 전에(같은 순간 포함) 보낸 대시보드엔 남는다', () => {
  const sent = done(T0, T0 + H, { settledAt: 1000 });
  const waiting = done(T0 + 2 * H, T0 + 3 * H);
  const l: Ledger = { active: null, done: [sent, waiting] };
  expect(unknownTo(l, 1000)).toEqual([sent, waiting]); // 받은 순간에 보낸 요청은 모를 수 있다
  expect(unknownTo(l, 1001)).toEqual([waiting]);
  expect(forgetSettled(l, 1000)).toBe(l);
  expect(forgetSettled(l, 1001)).toEqual({ active: null, done: [waiting] });
});

test('REQ-364 · isDirty — 진행 중 · 보낼 칸이면 참, 받은 칸만이면 거짓', () => {
  expect(isDirty(running(T0))).toBe(true);
  expect(isDirty({ active: null, done: [done(T0, T0 + H)] })).toBe(true);
  expect(isDirty({ active: null, done: [done(T0, T0 + H, { settledAt: 1 })] })).toBe(false);
  expect(isDirty(EMPTY_LEDGER)).toBe(false);
  expect(isDirty(undefined)).toBe(false); // 가게가 닫혔으면
});

test('REQ-374 · dayParts — 그날 창과 겹친 초를 모드별로 더한다(자정 앞 몫 · 다른 날 0)', () => {
  const dayStart = new Date(2026, 10, 3).getTime();
  const dayEnd = new Date(2026, 10, 4).getTime();
  const parts = [
    done(dayEnd - 10 * 60_000, dayEnd + 20 * 60_000), // 자정을 걸친 독서 — 그날 몫 10분
    done(dayStart + H, dayStart + H + 90_500, { mode: 'study' }),
    done(dayEnd + H, dayEnd + 2 * H), // 다음 날
  ];
  expect(dayParts(parts, dayStart, dayEnd)).toEqual({ reading: 600, study: 90 });
  expect(dayParts(parts, dayEnd, dayEnd + 24 * H)).toEqual({ reading: 20 * 60 + 3600, study: 0 });
  expect(dayParts([], dayStart, dayEnd)).toEqual({ reading: 0, study: 0 });
});
