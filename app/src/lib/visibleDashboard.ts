// 보이는 대시보드(타이머 오프라인 설계 2026-11-02 §6.4) — 화면 · 위젯 · Live Activity · 알림이 이 값 하나를 쓴다. 순수.
// 기준 값(마지막으로 받은 대시보드 — 캐시일 수 있다)을 보이는 순간의 날로 굴리고, 서버가 이미 닫았을 진행 중은 멈춘 것으로 본다.
import type { Dashboard } from '../api/types';
import { dayKey, nextMidnightOf } from './goalProgress';
import { DISPLAY_CAP_MS } from './liveActivity';
import { nextDaySource } from './widgetPayload';

/** d = 받은 대시보드 · at = 판정한 순간(REQ-349) · sentAt = 요청을 보낸 때 · seq = 이 실행의 요청 순번(디스크 값은 0) */
export interface Base {
  d: Dashboard;
  at: number;
  sentAt: number;
  seq: number;
}

/** now의 날까지 굴린다(위젯 자정 규칙 nextDaySource — REQ-360). 굴렸으면 오늘 빈 잔디 칸을 붙인다. 상한 지난 서버 진행 중은 멈춘 것으로(REQ-375). */
export function rollToDay(base: Base, now: number): { d: Dashboard; rolled: boolean } {
  const todayEnd = nextMidnightOf(now);
  let d = base.d;
  let rolled = false;
  for (let dayEnd = nextMidnightOf(base.at); dayEnd < todayEnd; dayEnd = nextMidnightOf(dayEnd)) {
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

const stale = (startedAt: string | null, now: number) => startedAt !== null && Date.parse(startedAt) + DISPLAY_CAP_MS <= now;

/** REQ-375 — 서버가 이미 닫았을 측정(시작 + 6시간 10분)은 멈춘 것으로. 그 몫은 다음 읽기가 채운다. */
function stopStale(d: Dashboard, now: number): Dashboard {
  let v = d;
  if (v.hasActiveSession && stale(v.activeStartedAt, now)) v = { ...v, hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBook: null };
  if (v.study.hasActiveSession && stale(v.study.activeStartedAt, now)) v = { ...v, study: { ...v.study, hasActiveSession: false, activeStartedAt: null, activeBook: null } };
  return v;
}
