// 독서 알림 · 목표 달성 알림 계획(v1 REQ-33 · REQ-92 · REQ-153). 순수 함수 — 네이티브 import 0(예약은 lib/notify가 한다).
// 독서 알림은 고른 시각에 앞으로 30일 하루 한 통. 오늘 시각이 지났거나 · 오늘 목표를 채웠거나 · 독서를 재는 중이면 내일부터.
import type { Dashboard } from '../api/types';
import { goalNotice } from './goalNotice';
import { goalProgress, type GoalProgress, nextMidnightOf } from './goalProgress';

export const REMINDER_BODY = '오늘 읽을 시간이에요 — 목표를 채우면 연속이 이어져요';
/** 목표 없음(0)이면 목표 · 연속을 말하지 않는다(REQ-179) */
export const REMINDER_BODY_NO_GOAL = '오늘 읽을 시간이에요';
export const goalBody = (streak: number) => `오늘 목표를 채웠어요 · ${streak}일째`; // 흐름 안 달성 알림도 이 글을 쓴다

/**
 * 달성 순간 흐름 안 알림(REQ-178) — 홈 effect가 관측마다 부른다. 직전 관측이 미달(false)이고 이번이 채움이며 지금 목표 > 0일 때만 글.
 * 처음 읽은 값(null — 첫 값 · 낡은 날 다음 첫 값)은 알리지 않는다. 목표 0엔 달성이 없다(서버 GoalMetPushService · 미니앱 crossedGoal).
 * 목표 0의 채움(읽은 날)은 목표 G의 채움을 늘 품으므로, 목표를 0 → G로 바꾸는 것만으로는 미달 → 채움이 생기지 않는다.
 */
export function goalMetNotice(wasFilled: boolean | null, progress: GoalProgress, goal: number): string | null {
  return wasFilled === false && progress.filled && goal > 0 ? goalBody(progress.streak) : null;
}

export interface ReminderSetting {
  on: boolean;
  hour: number;
  minute: number;
}
/** 기본 꺼짐 · 처음 켜면 오후 8:00(미니앱 N3 서버 기본 20시와 같다) */
export const DEFAULT_REMINDER: ReminderSetting = { on: false, hour: 20, minute: 0 };
export const REMINDER_DAYS = 30;

/** 8:00 전 → 8:00 · 22:00 뒤 → 22:00 */
export function clampTime(t: { hour: number; minute: number }): { hour: number; minute: number } {
  const m = Math.min(22 * 60, Math.max(8 * 60, t.hour * 60 + t.minute));
  return { hour: Math.floor(m / 60), minute: m % 60 };
}

export interface Planned {
  id: string;
  at: number;
  body: string;
}

const pad = (n: number) => String(n).padStart(2, '0');

export function planNotifications(p: {
  setting: ReminderSetting;
  permitted: boolean;
  signedIn: boolean;
  now: number;
  dash: Pick<Dashboard, 'todayReadSeconds' | 'todayGoalSeconds' | 'hasActiveSession' | 'activeStartedAt' | 'graph'> | null;
}): Planned[] {
  if (!p.setting.on || !p.permitted || !p.signedIn) return [];
  const { hour, minute } = clampTime(p.setting);
  const today = new Date(p.now);
  const input = p.dash && {
    todayRead: p.dash.todayReadSeconds,
    goal: p.dash.todayGoalSeconds,
    activeStartedAt: p.dash.hasActiveSession && p.dash.activeStartedAt ? Date.parse(p.dash.activeStartedAt) : null,
    now: p.now,
    nextMidnight: nextMidnightOf(p.now),
    goalStreak: p.dash.graph.goalStreak ?? 0,
  };
  const todayAt = new Date(today.getFullYear(), today.getMonth(), today.getDate(), hour, minute).getTime();
  const skipToday = todayAt <= p.now || (input !== null && (goalProgress(input).filled || input.activeStartedAt !== null));
  // 글이 바뀌면 id도 바뀌어야 이미 걸린 30통을 바꿔 끼운다(scheduleDiff는 id만 본다 · ours()는 접두어 'reminder-'만 본다 — 꼬리표는 끝에).
  // 대시보드를 모르면(null) 목표 있는 글 — 다음 대시보드 읽기가 바꿔 끼운다.
  const noGoal = input !== null && input.goal === 0;

  const out: Planned[] = [];
  for (let i = skipToday ? 1 : 0; out.length < REMINDER_DAYS; i++) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() + i, hour, minute);
    const id = `reminder-${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(hour)}${pad(minute)}${noGoal ? '-nogoal' : ''}`;
    out.push({ id, at: d.getTime(), body: noGoal ? REMINDER_BODY_NO_GOAL : REMINDER_BODY });
  }
  const goal = input && goalNotice({ ...input, remindersOn: true, permitted: true });
  if (goal && input) out.push({ id: goal.id, at: goal.at, body: goalBody(input.goalStreak + 1) });
  return out;
}

const ours = (id: string) => id.startsWith('reminder-') || id.startsWith('goal-');

/** 우리 id만 본다 · cancel = 계획에 없는 우리 id · add = 아직 없는 독서 알림 + 목표 달성(늘 — 같은 id는 시스템이 바꿔 끼운다) */
export function scheduleDiff(scheduledIds: readonly string[], plan: readonly Planned[]): { cancel: string[]; add: Planned[] } {
  const want = new Set(plan.map((x) => x.id));
  const have = new Set(scheduledIds);
  return {
    cancel: scheduledIds.filter((id) => ours(id) && !want.has(id)),
    add: plan.filter((x) => x.id.startsWith('goal-') || !have.has(x.id)),
  };
}
