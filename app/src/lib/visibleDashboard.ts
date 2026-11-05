// 보이는 대시보드(타이머 오프라인 설계 2026-11-02 §6.4) — 화면 · 위젯 · Live Activity · 알림이 이 값 하나를 쓴다. 순수.
// 기준 값(마지막으로 받은 대시보드 — 캐시일 수 있다)을 보이는 순간의 날로 굴리고, 서버가 이미 닫았을 진행 중은 멈춘 것으로 본다.
import type { Dashboard } from '../api/types';
import { dayKey, isFilled, nextMidnightOf } from './goalProgress';
import { DISPLAY_CAP_MS } from './liveActivity';
import { type ActiveMeasure, capActive, dayParts, type Ledger, unknownTo } from './measureLedger';
import { nextDaySource } from './widgetPayload';

const DAY_MS = 24 * 60 * 60 * 1000;
type Extra = (dayStart: number, dayEnd: number) => { reading: number; study: number };

/** d = 받은 대시보드 · at = 판정한 순간(REQ-349) · sentAt = 요청을 보낸 때 · seq = 이 실행의 요청 순번(디스크 값은 0) */
export interface Base {
  d: Dashboard;
  at: number;
  sentAt: number;
  seq: number;
}

/** 디스크 값(이 실행에서 아직 새로 읽지 않음)이거나 기준 값이 없다 — 목표 달성 띠의 「처음 값」이 아니다(REQ-371) */
export const cachedOf = (base: Base | null) => base === null || base.seq === 0;

/**
 * now의 날까지 굴린다(위젯 자정 규칙 nextDaySource — REQ-360). 굴렸으면 오늘 빈 잔디 칸을 붙인다. 상한 지난 서버 진행 중은 멈춘 것으로(REQ-375).
 * extra(T2)는 날마다 굴리기 전에 그날 몫으로 더한다 — 어제 기기에만 있던 몫이 어제 목표를 채웠으면 오늘 연속이 이어진다(REQ-374).
 */
export function rollToDay(base: Base, now: number, extra?: Extra): { d: Dashboard; rolled: boolean } {
  const todayEnd = nextMidnightOf(now);
  let d = base.d;
  let rolled = false;
  for (let dayEnd = nextMidnightOf(base.at); ; dayEnd = nextMidnightOf(dayEnd)) {
    if (extra) d = addDay(d, extra(dayEnd - DAY_MS, dayEnd));
    if (dayEnd >= todayEnd) break;
    const n = nextDaySource(d, dayEnd);
    // 읽는 책 목록 · 공부 서재 · 목표 등 나머지는 남긴다
    d = { ...d, ...n, activeBook: n.hasActiveSession ? d.activeBook : null, graph: { ...d.graph, ...n.graph }, study: { ...d.study, ...(n.study as Partial<Dashboard['study']>), todaySeconds: 0 } };
    rolled = true;
  }
  if (rolled) {
    // 서버의 오늘(잔디 마지막 날짜)이 기기의 오늘이 되게 — 없으면 위젯 타임라인 가드가 자정 항목을 다 뺀다(widgetPayload.ts)
    const t = new Date(now);
    const today = { date: dayKey(t.getFullYear(), t.getMonth(), t.getDate()), totalSeconds: 0, level: 0, manual: false };
    d = { ...d, graph: { ...d.graph, weeks: d.graph.weeks && [...d.graph.weeks, [today]] } };
  }
  return { d: stopStale(d, now), rolled };
}

/**
 * T2 — 화면 · 위젯 · LA · 알림이 쓰는 값. 확인 안 된 기준 값이면 서버 진행 중을 멈춘 것으로 보고(REQ-378), 기기 원장을 얹은 뒤(REQ-374)
 * 기준 값이 모르는 끝난 몫을 날마다 접으며 굴린다. confirmed = confirmedOf(가게 상태).
 */
export function effectiveDashboard(base: Base, ledger: Ledger, now: number, confirmed: boolean): { d: Dashboard; rolled: boolean } {
  const l = capActive(ledger, now); // 6시간 넘은 기기 진행 중은 보내기 전에도 끝난 몫
  const d = overlay(confirmed ? base.d : stopServer(base.d, () => true), l.active);
  const parts = unknownTo(l, base.sentAt);
  return rollToDay(d === base.d ? base : { ...base, d }, now, parts.length > 0 ? (s, e) => dayParts(parts, s, e) : undefined);
}

/** 기기 진행 중이 그 모드를 차지하고 다른 모드는 멈춘 것으로 — 이 기기 화면 · 잠금화면은 「한 번에 하나」다(위젯 · LA는 독서를 먼저 고른다) */
function overlay(d: Dashboard, a: ActiveMeasure | null): Dashboard {
  if (!a) return d;
  if (a.mode === 'reading') {
    const activeBook = [...d.readingBooks, ...d.wantToReadBooks].find((b) => b.id === a.bookId) ?? null;
    return { ...d, hasActiveSession: true, activeStartedAt: a.startedAt, activeBookTitle: a.bookTitle, activeBook, study: { ...d.study, hasActiveSession: false, activeStartedAt: null, activeBook: null } };
  }
  const activeBook = a.bookId === null ? null : (d.study.books.find((b) => b.id === a.bookId) ?? { id: a.bookId, title: a.bookTitle ?? '', author: null, coverUrl: null, isbn13: null, totalSeconds: 0 });
  return { ...d, hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null, study: { ...d.study, hasActiveSession: true, activeStartedAt: a.startedAt, activeBook } };
}

/** 그날 몫 더하기 — 독서 몫으로 목표를 처음 채우면 연속 +1(goalProgress의 진행 중 몫 규칙과 같다 · isFilled) */
function addDay(d: Dashboard, add: { reading: number; study: number }): Dashboard {
  if (add.reading === 0 && add.study === 0) return d;
  const todayReadSeconds = d.todayReadSeconds + add.reading;
  const first = isFilled(todayReadSeconds, d.todayGoalSeconds) && !isFilled(d.todayReadSeconds, d.todayGoalSeconds);
  return {
    ...d,
    todayReadSeconds,
    graph: first ? { ...d.graph, goalStreak: (d.graph.goalStreak ?? 0) + 1 } : d.graph,
    study: add.study === 0 ? d.study : { ...d.study, todaySeconds: d.study.todaySeconds + add.study },
  };
}

const stale = (startedAt: string | null, now: number) => startedAt !== null && Date.parse(startedAt) + DISPLAY_CAP_MS <= now;

/** 서버 진행 중(독서 · 공부) 중 off가 참인 것을 멈춘 것으로. 그 몫은 더하지 않는다(다음 읽기가 채운다). */
function stopServer(d: Dashboard, off: (startedAt: string | null) => boolean): Dashboard {
  let v = d;
  if (v.hasActiveSession && off(v.activeStartedAt)) v = { ...v, hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null };
  if (v.study.hasActiveSession && off(v.study.activeStartedAt)) v = { ...v, study: { ...v.study, hasActiveSession: false, activeStartedAt: null, activeBook: null } };
  return v;
}

/** REQ-375 — 서버가 이미 닫았을 측정(시작 + 6시간 10분)은 멈춘 것으로. 기기 진행 중은 capActive로 6시간 안이라 걸리지 않는다. */
const stopStale = (d: Dashboard, now: number) => stopServer(d, (s) => stale(s, now));
