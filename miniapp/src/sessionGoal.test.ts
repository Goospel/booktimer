import { describe, expect, it } from 'vitest';

import {
  DEFAULT_SESSION_GOAL,
  SESSION_GOAL_MAX_HOURS,
  sessionGoalHandleLabel,
  sessionGoalSheetTarget,
  sessionGoalView,
  shouldHaptic,
  sessionGoalWheelState,
  studySessionLine,
} from './sessionGoal';

/**
 * 책별 「회당 시간」 — 「닿음」 판정과 문구를 순수 함수로 꺼냈다. 홈은 매초 `now`를 올리고 이 함수들을
 * 다시 부를 뿐이라(타이머 코드 신설 0), 화면이 옳은지는 여기서 거의 다 정해진다(하니스는 effect가 안 돈다, T-149).
 */
describe('sessionGoalView — 닿음 판정', () => {
  it('회당 시간이 없으면(null·undefined·0 이하) 스톱워치다 — 0을 「즉시 달성」으로 읽지 않는다', () => {
    expect(sessionGoalView(null, 100)).toEqual({ kind: 'stopwatch' });
    expect(sessionGoalView(undefined, 100)).toEqual({ kind: 'stopwatch' });
    expect(sessionGoalView(0, 100)).toEqual({ kind: 'stopwatch' });
    expect(sessionGoalView(-60, 100)).toEqual({ kind: 'stopwatch' });
  });

  it('덜 쟀으면 남은 시간이 온다', () => {
    expect(sessionGoalView(1_200, 800)).toEqual({ kind: 'countdown', goal: 1_200, remaining: 400 });
  });

  it('정확히 닿은 순간이 달성이다 — 경계는 「이상」이지 「초과」가 아니다', () => {
    expect(sessionGoalView(1_200, 1_200)).toEqual({ kind: 'reached', goal: 1_200, overflow: 0 });
    // 한 초 전은 아직이다 — 위 경계가 한 칸 밀리면 둘 중 하나가 깨진다.
    expect(sessionGoalView(1_200, 1_199)).toEqual({ kind: 'countdown', goal: 1_200, remaining: 1 });
  });

  it('넘기면 초과분이 온다 — 측정은 계속되고 초과도 기록이다', () => {
    expect(sessionGoalView(1_200, 1_500)).toEqual({ kind: 'reached', goal: 1_200, overflow: 300 });
  });

  it('음수 경과(기기 시계가 서버보다 늦음)는 0으로 누른다 — 남은 시간이 회당 시간보다 커지지 않는다', () => {
    expect(sessionGoalView(1_200, -5)).toEqual({ kind: 'countdown', goal: 1_200, remaining: 1_200 });
  });
});

describe('sessionGoalWheelState — 휠 판정(1분~6시간)', () => {
  it('0시간 0분은 저장할 수 없다 — 이유를 한 줄로 말한다', () => {
    expect(sessionGoalWheelState(0, 0)).toEqual({ seconds: 0, valid: false, note: '1분 이상으로 골라 주세요' });
  });

  it('6시간에 분이 붙으면 저장할 수 없다 — 서버 상한(6시간)을 넘는다', () => {
    expect(sessionGoalWheelState(6, 1)).toEqual({ seconds: 21_660, valid: false, note: '최대 6시간까지 잴 수 있어요' });
  });

  it('경계 안쪽은 저장할 수 있다 — 1분·6시간 정각·50분', () => {
    expect(sessionGoalWheelState(0, 1)).toEqual({ seconds: 60, valid: true, note: null });
    expect(sessionGoalWheelState(6, 0)).toEqual({ seconds: 21_600, valid: true, note: null });
    expect(sessionGoalWheelState(0, 50)).toEqual({ seconds: 3_000, valid: true, note: null });
  });

  it('휠 상한은 6시간이고, 첫 칸 기본값은 30분이다(사용자 확정)', () => {
    expect(SESSION_GOAL_MAX_HOURS).toBe(6);
    expect(DEFAULT_SESSION_GOAL).toBe(1_800);
  });
});

describe('sessionGoalHandleLabel — 대기 손잡이 문구', () => {
  it('회당 시간이 있으면 값과 동작을 함께 말한다', () => {
    expect(sessionGoalHandleLabel(3_000)).toBe('회당 50분 · 바꾸기');
    expect(sessionGoalHandleLabel(5_400)).toBe('회당 1시간 30분 · 바꾸기');
  });

  it('없으면 정하러 가는 말이다', () => {
    expect(sessionGoalHandleLabel(null)).toBe('회당 시간 정하기');
    expect(sessionGoalHandleLabel(undefined)).toBe('회당 시간 정하기');
    expect(sessionGoalHandleLabel(0)).toBe('회당 시간 정하기');
  });

  /** 측정 중엔 바로 위 줄(「회당 50분 · 38분 남음」)이 값을 이미 말한다 — 버튼이 값을 되풀이하면 두 번 보인다. */
  it('측정 중 손잡이는 값을 되풀이하지 않는다 — 있으면 「회당 시간 바꾸기」, 없으면 그대로 「회당 시간 정하기」', () => {
    expect(sessionGoalHandleLabel(3_000, true)).toBe('회당 시간 바꾸기');
    expect(sessionGoalHandleLabel(null, true)).toBe('회당 시간 정하기');
    // 대기 손잡이는 값을 말한다(양성 쌍).
    expect(sessionGoalHandleLabel(3_000, false)).toBe('회당 50분 · 바꾸기');
  });
});

describe('sessionGoalSheetTarget — 손잡이가 여는 책', () => {
  const a = { id: 1 };
  const b = { id: 2 };

  it('측정 중이면 재고 있는 책이다 — 캐러셀 선택이 남아 있어도', () => {
    expect(sessionGoalSheetTarget(true, a, b)).toBe(a);
  });

  it('측정 중인데 책 없이 재면 대상이 없다 — 캐러셀 선택으로 떨어지지 않는다', () => {
    expect(sessionGoalSheetTarget(true, null, b)).toBeNull();
  });

  it('대기 중이면 캐러셀에서 고른 책이다 — 「책 없이」면 없다', () => {
    expect(sessionGoalSheetTarget(false, a, b)).toBe(b);
    expect(sessionGoalSheetTarget(false, null, null)).toBeNull();
  });
});

describe('shouldHaptic — 달성 햅틱을 울릴 순간', () => {
  it('「안 닿음 → 닿음」으로 바뀌는 순간만 울린다', () => {
    expect(shouldHaptic(false, true)).toBe(true);
  });

  it('첫 렌더(이전 값 없음)에서 이미 닿아 있으면 울리지 않는다 — 앱을 다시 열 때마다 진동하면 소음이다', () => {
    expect(shouldHaptic(null, true)).toBe(false);
  });

  it('닿은 채로 매초 다시 그려도 다시 울리지 않는다', () => {
    expect(shouldHaptic(true, true)).toBe(false);
  });

  it('안 닿았으면 울리지 않는다', () => {
    expect(shouldHaptic(false, false)).toBe(false);
    expect(shouldHaptic(true, false)).toBe(false);
  });
});

describe('studySessionLine — 측정 중 한 줄', () => {
  it('스톱워치면 줄이 없다', () => {
    expect(studySessionLine({ kind: 'stopwatch' })).toBeNull();
  });

  it('닿기 전엔 회당 시간과 남은 시간을 말한다', () => {
    expect(studySessionLine({ kind: 'countdown', goal: 3_000, remaining: 2_280 })).toBe('회당 50분 · 38분 남음');
    // 1분 미만은 초로 — 「0분 남음」은 거짓말이다.
    expect(studySessionLine({ kind: 'countdown', goal: 60, remaining: 45 })).toBe('회당 1분 · 45초 남음');
  });

  it('닿으면 달성과 더 한 시간을 말한다', () => {
    expect(studySessionLine({ kind: 'reached', goal: 3_000, overflow: 300 })).toBe('회당 50분 달성 · 5분 더 공부했어요');
  });

  it('닿은 직후 1분 동안은 달성만 말한다 — 「0초 더 공부했어요」는 할 말이 아니다', () => {
    expect(studySessionLine({ kind: 'reached', goal: 60, overflow: 0 })).toBe('회당 1분 달성');
    expect(studySessionLine({ kind: 'reached', goal: 60, overflow: 59 })).toBe('회당 1분 달성');
    expect(studySessionLine({ kind: 'reached', goal: 60, overflow: 60 })).toBe('회당 1분 달성 · 1분 더 공부했어요');
  });
});
