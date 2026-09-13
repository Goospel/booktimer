import { formatDuration } from './format';
import { combineWheel } from './wheelTime';

/**
 * 공부 책별 「회당 시간」 — 한 번 앉을 때 공부할 시간(1분~6시간). 서버는 책에 값만 들고
 * (`StudyBookRow.sessionGoalSeconds`), 「닿았나」는 여기 순수 함수가 매초 다시 판정한다(세션 스냅샷 없음 —
 * 책의 <b>현재 값</b>이 기준이라 측정 중 값을 바꾸면 곧바로 새 값으로 그린다).
 */

/** 휠 시간 열의 상한 — 서버 상한(6시간 = 세션 cap)과 같다. */
export const SESSION_GOAL_MAX_HOURS = 6;

/** 회당 시간이 없는 책의 휠 첫 칸(30분, 사용자 확정). */
export const DEFAULT_SESSION_GOAL = 1_800;

export type SessionGoalView =
  | { kind: 'stopwatch' }
  | { kind: 'countdown'; goal: number; remaining: number }
  | { kind: 'reached'; goal: number; overflow: number };

/** 「닿음」 판정 — 회당 시간이 없거나 0 이하면 제한 없는 스톱워치. 음수 경과(시계 어긋남)는 0으로 누른다. */
export function sessionGoalView(goal: number | null | undefined, elapsed: number): SessionGoalView {
  if (goal == null || goal <= 0) return { kind: 'stopwatch' };
  const done = Math.max(0, elapsed);
  return done >= goal
    ? { kind: 'reached', goal, overflow: done - goal }
    : { kind: 'countdown', goal, remaining: goal - done };
}

/** 휠 판정 — 휠은 분 단위라 빈칸·소수·자투리 초가 원리상 없고, 남는 경계는 0분과 6시간 초과 둘뿐이다. */
export function sessionGoalWheelState(
  hours: number,
  minutes: number,
): { seconds: number; valid: boolean; note: string | null } {
  const seconds = combineWheel(hours, minutes);
  if (seconds <= 0) return { seconds, valid: false, note: '1분 이상으로 골라 주세요' };
  if (seconds > SESSION_GOAL_MAX_HOURS * 3600) return { seconds, valid: false, note: '최대 6시간까지 잴 수 있어요' };
  return { seconds, valid: true, note: null };
}

/**
 * 손잡이 문구 — 값이 있으면 바꾸는 말, 없으면 정하러 가는 말. 대기 중엔 값을 함께 말하고, 측정 중엔
 * 바로 위 줄(「회당 50분 · 38분 남음」)이 값을 이미 말하므로 되풀이하지 않는다.
 */
export function sessionGoalHandleLabel(v: number | null | undefined, measuring = false): string {
  if (v == null || v <= 0) return '회당 시간 정하기';
  return measuring ? '회당 시간 바꾸기' : `회당 ${formatDuration(v)} · 바꾸기`;
}

/** 손잡이가 여는 책 — 측정 중이면 재고 있는 책(책 없이 재면 없음), 대기 중이면 캐러셀에서 고른 책. */
export function sessionGoalSheetTarget<T>(measuring: boolean, activeBook: T | null, selectedBook: T | null): T | null {
  return measuring ? activeBook : selectedBook;
}

/** 달성 햅틱 — 「안 닿음 → 닿음」으로 바뀌는 순간만. 이전 값이 없는 첫 렌더(`null`)에서 이미 닿아 있으면 울리지 않는다. */
export function shouldHaptic(prevReached: boolean | null, nextReached: boolean): boolean {
  return prevReached === false && nextReached;
}

/**
 * 측정 중 한 줄 — 스톱워치면 없다. 닿은 직후 1분 동안은 「달성」만 말한다(「0초 더 공부했어요」는 할 말이 아니다).
 */
export function studySessionLine(view: SessionGoalView): string | null {
  switch (view.kind) {
    case 'stopwatch':
      return null;
    case 'countdown':
      return `회당 ${formatDuration(view.goal)} · ${formatDuration(view.remaining)} 남음`;
    case 'reached':
      return view.overflow >= 60
        ? `회당 ${formatDuration(view.goal)} 달성 · ${formatDuration(view.overflow)} 더 공부했어요`
        : `회당 ${formatDuration(view.goal)} 달성`;
  }
}
