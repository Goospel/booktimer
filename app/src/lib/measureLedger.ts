// 기기 원장(타이머 오프라인 설계 2026-11-02 §6.3 — T2). 앱 측정은 끝날 때까지 기기에만 있고(Q1), 끝난 칸은 보내기 차례가
// 서버 마감 문으로 올린다. 순수 — 같으면 같은 객체를 돌려 다시 그리지 않는다. 칸은 (모드, 시작 시각)으로 찾는다.
import { SESSION_CAP_MS } from './goalProgress';

export type MeasureMode = 'reading' | 'study';
/** 이 기기가 재는 측정 — 서버는 모른다(Q1). startedAt = 밀리초 ISO */
export interface ActiveMeasure {
  mode: MeasureMode;
  startedAt: string;
  bookId: number | null;
  bookTitle: string | null;
}
/** 기기에서 끝난 측정. failedSince = 연결 말고 서버가 실패한 첫 때(REQ-359) · settledAt = 서버가 받은 때(REQ-368), null = 보낼 칸 */
export interface DoneMeasure {
  mode: MeasureMode;
  startedAt: string;
  endedAt: string;
  bookId: number | null;
  failedSince: number | null;
  settledAt: number | null;
}
export interface Ledger {
  active: ActiveMeasure | null;
  done: DoneMeasure[];
}

export const EMPTY_LEDGER: Ledger = { active: null, done: [] };
const iso = (ms: number) => new Date(ms).toISOString();
const same = (a: DoneMeasure, b: Pick<DoneMeasure, 'mode' | 'startedAt'>) => a.mode === b.mode && a.startedAt === b.startedAt;

/** 진행 중을 끝 = [시작, 시작 + 6시간] 안으로 자른 at에서 끝낸다. 1초 미만이면 끝난 칸을 남기지 않는다(서버도 1초 미만 조각은 적지 않는다) */
export function end(l: Ledger, at: number): Ledger {
  const a = l.active;
  if (!a) return l;
  const s = Date.parse(a.startedAt);
  const e = Math.min(Math.max(at, s), s + SESSION_CAP_MS);
  if (e - s < 1000) return { active: null, done: l.done };
  return { active: null, done: [...l.done, { mode: a.mode, startedAt: a.startedAt, endedAt: iso(e), bookId: a.bookId, failedSince: null, settledAt: null }] };
}

/** 6시간 이상 켜진 진행 중은 시작 + 6시간으로 끝낸다(서버 스윕의 기기판) */
export function capActive(l: Ledger, now: number): Ledger {
  return l.active && now - Date.parse(l.active.startedAt) >= SESSION_CAP_MS ? end(l, now) : l;
}

/** 새 측정을 연다 — 6시간 지난 진행 중은 먼저 끝낸다. 그래도 진행 중이 있으면 그대로 */
export function begin(l: Ledger, p: { mode: MeasureMode; at: number; bookId: number | null; bookTitle: string | null }): Ledger {
  const c = capActive(l, p.at);
  if (c.active) return l;
  return { ...c, active: { mode: p.mode, startedAt: iso(p.at), bookId: p.bookId, bookTitle: p.bookTitle } };
}

function patch(l: Ledger, x: DoneMeasure, f: (d: DoneMeasure) => DoneMeasure): Ledger {
  let changed = false;
  const done = l.done.map((d) => {
    if (!same(d, x)) return d;
    const n = f(d);
    changed ||= n !== d;
    return n;
  });
  return changed ? { ...l, done } : l;
}

export const settle = (l: Ledger, x: DoneMeasure, at: number) => patch(l, x, (d) => ({ ...d, settledAt: at }));
export const markFailed = (l: Ledger, x: DoneMeasure, at: number) => patch(l, x, (d) => (d.failedSince === null ? { ...d, failedSince: at } : d));
export function drop(l: Ledger, x: DoneMeasure): Ledger {
  const done = l.done.filter((d) => !same(d, x));
  return done.length === l.done.length ? l : { ...l, done };
}

/** 보낼 칸 — 시작 시각 순 */
export const pending = (l: Ledger) => l.done.filter((d) => d.settledAt === null).sort((a, b) => Date.parse(a.startedAt) - Date.parse(b.startedAt));

/** sentAt에 보낸 대시보드가 아직 모르는 끝난 몫 — 보낼 칸 + 그 뒤(같은 순간 포함)에 받은 칸 */
export const unknownTo = (l: Ledger, sentAt: number) => l.done.filter((d) => d.settledAt === null || d.settledAt >= sentAt);

/** sentAt보다 먼저 받은 칸을 걷는다 — 그 요청의 응답에 들어 있다 */
export function forgetSettled(l: Ledger, sentAt: number): Ledger {
  const done = l.done.filter((d) => d.settledAt === null || d.settledAt >= sentAt);
  return done.length === l.done.length ? l : { ...l, done };
}

/** 서버가 모르는 것이 있다 — 진행 중도 센다(Q1 — 로그아웃 확인 REQ-364) */
export const isDirty = (l: Ledger | undefined) => !!l && (l.active !== null || l.done.some((d) => d.settledAt === null));

/** 그날 창 [dayStart, dayEnd)과 겹친 초 — 모드별 */
export function dayParts(parts: DoneMeasure[], dayStart: number, dayEnd: number): { reading: number; study: number } {
  const ms = { reading: 0, study: 0 };
  for (const p of parts) ms[p.mode] += Math.max(0, Math.min(Date.parse(p.endedAt), dayEnd) - Math.max(Date.parse(p.startedAt), dayStart));
  return { reading: Math.floor(ms.reading / 1000), study: Math.floor(ms.study / 1000) };
}
