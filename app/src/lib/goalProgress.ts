// 오늘 목표 진행(범위 보충 §3.4 · REQ-90). 끝난 몫은 서버(계정 타임존), 진행 중 몫은 기기 자정부터 센다.
// 서버 goalStreak은 끝난 몫만 본다 — 오늘을 진행 중 몫으로 처음 채우면 앱이 하나를 얹어 보인다.
import { durationLabel } from './format';
import type { ContributionDay, Dashboard } from '../api/types';

export const SESSION_CAP_MS = 6 * 60 * 60 * 1000; // 서버 6시간 상한과 같다
const DAY_MS = 24 * 60 * 60 * 1000;

export interface GoalInput {
  /** 오늘 끝난 몫(초) — 대시보드 todayReadSeconds */
  todayRead: number;
  /** 오늘 목표(초) — 0이면 목표 없음(읽은 날이 채운 날) */
  goal: number;
  /** 진행 중 측정의 시작(ms) — 없으면 null */
  activeStartedAt: number | null;
  now: number;
  /** 기기 시간대의 다음 자정(ms) */
  nextMidnight: number;
  /** 서버가 끝난 몫으로 센 목표 연속 */
  goalStreak: number;
}

export interface GoalProgress {
  todaySeconds: number;
  filled: boolean;
  /** 보일 연속 */
  streak: number;
  /** 지금 측정을 이어 가면 목표에 닿는 시각(ms) — 목표 > 0 · 미달 · 지금 뒤 · 다음 자정 전 · 시작 + 6시간 안일 때만 */
  reachAt: number | null;
}

export const isFilled = (seconds: number, goal: number) => seconds > 0 && seconds >= goal;

export function goalProgress(p: GoalInput): GoalProgress {
  // ponytail: 오늘 0시 = 다음 자정 − 24시간. 서머타임 날엔 1시간 어긋난다(한국은 없음) — 필요하면 todayStart를 입력으로 받는다.
  const todayStart = p.nextMidnight - DAY_MS;
  let running = 0;
  let countFrom = 0;
  let capAt = 0;
  if (p.activeStartedAt !== null) {
    countFrom = Math.max(p.activeStartedAt, todayStart);
    capAt = p.activeStartedAt + SESSION_CAP_MS;
    running = Math.max(0, Math.floor((Math.min(p.now, capAt) - countFrom) / 1000));
  }
  const todaySeconds = p.todayRead + running;
  const filled = isFilled(todaySeconds, p.goal);
  const streak = filled && !isFilled(p.todayRead, p.goal) ? p.goalStreak + 1 : p.goalStreak;

  let reachAt: number | null = null;
  if (p.activeStartedAt !== null && p.goal > 0 && !filled) {
    const at = countFrom + (p.goal - p.todayRead) * 1000;
    if (at > p.now && at < p.nextMidnight && at <= capAt) reachAt = at;
  }
  return { todaySeconds, filled, streak, reachAt };
}

const GOAL_CHOICES_MIN = [10, 20, 30, 45, 60, 90, 120];
/** 목표 0의 이름 — 시트 줄과 홈 줄이 같은 말을 쓴다(REQ-175 · 177) */
const NO_GOAL = '목표 없음';

/** 하루 목표 시트 줄(REQ-175) — 맨 위 「목표 없음」(0 = 서버의 「목표 없음」), 그 아래 10분~2시간. 지금 값인 줄만 고른 줄 — 분 줄 꼬리는 「지금 목표」, 「목표 없음」 줄은 꼬리 없이 굵은 글씨 · 「선택됨」만. */
export function goalChoices(current: number): { seconds: number; title: string; selected: boolean; meta?: string }[] {
  return [0, ...GOAL_CHOICES_MIN.map((m) => m * 60)].map((seconds) => {
    const selected = seconds === current;
    return { seconds, title: seconds === 0 ? NO_GOAL : durationLabel(seconds), selected, meta: selected && seconds > 0 ? '지금 목표' : undefined };
  });
}

/**
 * 홈 목표 줄 글(REQ-176 · 177). 안 읽은 날(측정 전 · 분 아래를 버려 「0분」이 찍힐 초)이면 「오늘 0분」도, 오늘 걸린 연속도 쓰지 않는다
 * — 0/목표 꼴과 이어 가야 할 연속이 점수로 읽힌다. 목표 없음이면 연속을 보이지 않는다(점수를 고르지 않은 사람).
 * 단서는 어느 상태든 「바꾸기」라 화면이 붙인다. 글은 goalParts 조각을 잇기만 한다 — 위젯이 같은 조각을 자리별로 쓴다(REQ-184).
 */
export function goalStatus(p: { progress: GoalProgress; goal: number; measuring: boolean }): string {
  const s = goalParts(p);
  return [s.today && `오늘 ${s.today}`, s.goal, s.streak].filter(Boolean).join(' · ');
}

/** 보이는 대시보드의 오늘 목표 진행 — 홈 · 서재가 같은 입력(REQ-90). 진행 중 몫은 독서 측정 중일 때만 */
export function progressOf(d: Dashboard, now: number): GoalProgress {
  const startedAt = d.hasActiveSession && d.activeStartedAt ? Date.parse(d.activeStartedAt) : null;
  return goalProgress({ todayRead: d.todayReadSeconds, goal: d.todayGoalSeconds, activeStartedAt: startedAt, now, nextMidnight: nextMidnightOf(now), goalStreak: d.graph.goalStreak ?? 0 });
}

/** 홈 박스 작은 글(REQ-270 · F1 §4.1) — 측정 전 = today · goal 조각(연속 빼고 — 결정 다), 측정 중 = today 조각만(목표를 붙이면 두 줄이 되어 박스가 커진다 — F1 §7-13) */
export function boxGoalText(p: { progress: GoalProgress; goal: number; measuring: boolean }): string {
  const s = goalParts(p);
  return p.measuring ? `오늘 ${s.today}` : [s.today && `오늘 ${s.today}`, s.goal].filter(Boolean).join(' · ');
}

/** 박스 작은 글을 「 · 」 조각으로 — 화면이 조각마다 따로 그려 줄 넘김 상자에 담는다. iOS는 한 글자 안에선 숫자 · 한글 사이도 끊는다(2026-11-13 기기: 「목표 1시간 / 30분」 → 줄바꿈 없는 칸으로도 「목표 1 / 시간 30분」) */
export function textChunks(text: string): string[] {
  const parts = text.split(' · ');
  return parts.map((s, i) => (i < parts.length - 1 ? `${s} ·` : s));
}

/** 서재 맨 위 목표 줄(REQ-271 · F1 §4.2-2) — 「하루 목표 30분 · 5일째」. goalParts.goal은 안 읽은 날 이미 「하루 …」라 쓰지 않는다(「하루 하루」) · 연속은 goalParts가 줄 때만(읽은 날 · 측정 중 · 목표 > 0 · 연속 > 0) */
export function libraryGoalLine(p: { progress: GoalProgress; goal: number; measuring: boolean }): string {
  const streak = goalParts(p).streak;
  return `하루 ${p.goal > 0 ? `목표 ${durationLabel(p.goal)}` : NO_GOAL}${streak ? ` · ${streak}` : ''}`;
}

export interface GoalParts {
  today?: string;
  goal: string;
  streak?: string;
}

/** 홈 목표 줄의 조각(REQ-176 · 177 · 184) — 홈은 잇고 위젯은 나눠 쓴다. 안 읽은 날(측정 전 · 1분 미만)엔 goal 하나(「하루 목표 30분」). */
export function goalParts(p: { progress: GoalProgress; goal: number; measuring: boolean }): GoalParts {
  const target = p.goal > 0 ? `목표 ${durationLabel(p.goal)}` : NO_GOAL;
  if (!p.measuring && p.progress.todaySeconds < 60) return { goal: `하루 ${target}` };
  const streak = p.goal > 0 && p.progress.streak > 0 ? `${p.progress.streak}일째` : undefined;
  return { today: durationLabel(p.progress.todaySeconds), goal: target, streak };
}

/** 대시보드를 읽은 뒤 자정을 넘겼는가 — 끝난 몫 · 연속이 전날 값이라 다시 읽어야 한다. */
export function isStaleDay(loadedMidnight: number, now: number): boolean {
  return nextMidnightOf(now) !== loadedMidnight;
}

/** 기기 시간대의 다음 자정(ms). */
export function nextMidnightOf(now: number): number {
  const d = new Date(now);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime();
}

/** 측정 전 자정 다시 읽기까지(ms) — 읽은 날의 자정 +1초(REQ-349). 그 자정이 이미 지났으면 1초 뒤 — 자정 직전에 받은 값의 effect가 자정 뒤에 돌아도 다음 자정으로 밀리지 않게. */
export function midnightRereadDelay(loadedMidnight: number, now: number): number {
  return Math.max(0, loadedMidnight - now) + 1000;
}

/** 날짜 열쇠 YYYY-MM-DD(달은 0부터 — Date와 같다). 서버 잔디 날짜와 같은 꼴이라 글자 순서가 날짜 순서다(widgetPayload에서 옮김). */
export const dayKey = (y: number, m: number, d: number) => `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

/** 서버의 오늘(계정 시간대) — 잔디에서 날짜가 있는 가장 늦은 칸(오늘 뒤 칸은 날짜 null). 잔디가 없으면(옛 서버) undefined. 잔디를 만든 순간의 날이다 — 끝난 몫의 날은 보증하지 않는다(서버가 시계를 따로 읽는다). */
export function serverToday(weeks: ContributionDay[][] | undefined): string | undefined {
  return weeks?.flat().reduce((m, c) => (c.date && c.date > m ? c.date : m), '') || undefined;
}

/** 다시 읽기 간격 · 창(REQ-346 · 347 — 조절 상수). 창은 「자정 뒤 몇 초 안에 보낸 요청을 일시 어긋남으로 의심하나」다 — 그 뒤의 어긋남은 해외 · 크게 틀린 시계라 다시 읽어도 안 바뀐다. */
export const REREAD_MS = 1000;
const REREAD_WINDOW_MS = 10 * 1000;

/**
 * 이 대시보드 응답을 버리고 다시 읽어야 하나(REQ-346 · 347). 받은 순간 서버의 오늘이 기기의 오늘보다 이르고,
 * 요청을 그날 0시 전에 보냈거나(자정을 걸친 늦은 응답) 0시 뒤 10초 안에 보냈을 때(기기 시계가 서버보다 앞섬)만.
 */
export function shouldReread(server: string | undefined, sentAt: number, gotAt: number): boolean {
  const g = new Date(gotAt);
  const dayStart = new Date(g.getFullYear(), g.getMonth(), g.getDate()).getTime();
  return server !== undefined && server < dayKey(g.getFullYear(), g.getMonth(), g.getDate()) && sentAt < dayStart + REREAD_WINDOW_MS;
}

/**
 * 오늘의 대시보드와 그것을 판정한 순간(REQ-346 · 349 — 앱의 대시보드 읽기는 늘 이것으로 · REQ-348). 서버가 아직 전날로 센 응답은 쓰지 않고
 * 1초 뒤 한 번 다시 읽는다 — 쓰면 홈 · 위젯 · 알림이 「새 날짜 + 전날 값」이 되고 자정 전에 써 둔 위젯 자정 항목(REQ-193)까지 한 항목으로 덮인다.
 * 다시 읽은 응답은 판정 없이 쓴다(REQ-347 — 요청 많아야 2번). 쓰는 곳은 at을 그 값의 시각으로 쓴다(now · loadedMidnight · syncSurfaces · syncReminders).
 */
export async function todayDashboard(get: () => Promise<Dashboard>): Promise<{ d: Dashboard; at: number }> {
  const sentAt = Date.now();
  const first = await get();
  const at = Date.now();
  if (!shouldReread(serverToday(first.graph?.weeks), sentAt, at)) return { d: first, at };
  await new Promise((r) => setTimeout(r, REREAD_MS));
  const d = await get();
  return { d, at: Date.now() };
}
