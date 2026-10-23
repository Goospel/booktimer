// 오늘 목표 진행(범위 보충 §3.4 · REQ-90). 끝난 몫은 서버(계정 타임존), 진행 중 몫은 기기 자정부터 센다.
// 서버 goalStreak은 끝난 몫만 본다 — 오늘을 진행 중 몫으로 처음 채우면 앱이 하나를 얹어 보인다.

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

/** 기기 시간대의 다음 자정(ms). */
export function nextMidnightOf(now: number): number {
  const d = new Date(now);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime();
}
