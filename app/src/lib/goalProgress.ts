// 오늘 목표 진행(범위 보충 §3.4 · REQ-90). 끝난 몫은 서버(계정 타임존), 진행 중 몫은 기기 자정부터 센다.
// 서버 goalStreak은 끝난 몫만 본다 — 오늘을 진행 중 몫으로 처음 채우면 앱이 하나를 얹어 보인다.
import { durationLabel } from './format';

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

const isFilled = (seconds: number, goal: number) => seconds > 0 && seconds >= goal;

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
 * 단서는 어느 상태든 「바꾸기」라 화면이 붙인다.
 */
export function goalStatus(p: { progress: GoalProgress; goal: number; measuring: boolean }): string {
  const target = p.goal > 0 ? `목표 ${durationLabel(p.goal)}` : NO_GOAL;
  if (!p.measuring && p.progress.todaySeconds < 60) return `하루 ${target}`;
  const parts = [`오늘 ${durationLabel(p.progress.todaySeconds)}`, target];
  if (p.goal > 0 && p.progress.streak > 0) parts.push(`${p.progress.streak}일째`);
  return parts.join(' · ');
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
