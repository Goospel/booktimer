import { describe, expect, it } from 'vitest';

import { combineWheel, wheelIndices } from './wheelTime';

/**
 * 휠 변환의 공용 모듈 — 독서 목표 화면(`Goal.tsx`)과 공부 「회당 시간」 시트가 같은 변환을 쓴다.
 *
 * <p>옛 결과(상한 12시간)는 `goal.test.tsx`가 `Goal.tsx` 재export 경로로 계속 잰다. 여기서는
 * <b>상한 파라미터</b>가 실제로 먹는지와, 기본값이 독서 그대로인지를 <b>같은 입력의 양성 쌍</b>으로 잰다.
 */
describe('wheelIndices — 상한 파라미터', () => {
  it('상한 6이면 6시간 0분은 그대로 6시간 0분이다 — 회당 시간의 최댓값이 휠에 있다', () => {
    expect(wheelIndices(21_600, 6)).toEqual({ hours: 6, minutes: 0 });
  });

  it('상한 6을 넘는 값은 6시간 59분으로 붙인다 — 휠에 없는 칸(7시간)을 가리키지 않게', () => {
    expect(wheelIndices(25_200, 6)).toEqual({ hours: 6, minutes: 59 });
  });

  it('기본 상한은 12다 — 같은 7시간이 독서 휠에선 붙지 않고 그대로 선다(양성 쌍)', () => {
    expect(wheelIndices(25_200)).toEqual({ hours: 7, minutes: 0 });
    expect(wheelIndices(46_800)).toEqual({ hours: 12, minutes: 59 });
  });
});

describe('combineWheel — 공용 모듈에서도 같은 셈', () => {
  it('시·분을 초로 합친다', () => {
    expect(combineWheel(0, 30)).toBe(1_800);
    expect(combineWheel(6, 0)).toBe(21_600);
  });
});
