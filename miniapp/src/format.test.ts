import { describe, expect, it } from 'vitest';

import { elapsedSeconds, formatClock, formatDuration } from './format';

describe('formatDuration', () => {
  it('시·분을 함께 쓰되 0분이면 시간만 쓴다', () => {
    expect(formatDuration(4800)).toBe('1시간 20분');
    expect(formatDuration(7200)).toBe('2시간');
  });

  it('1시간 미만은 분, 1분 미만은 초', () => {
    expect(formatDuration(2700)).toBe('45분');
    expect(formatDuration(30)).toBe('30초');
    expect(formatDuration(0)).toBe('0초');
  });

  it('음수(밀린 시간)는 절댓값으로 표시한다 — 부호는 호출부 문구가 맡는다', () => {
    expect(formatDuration(-4800)).toBe('1시간 20분');
  });
});

describe('formatClock', () => {
  it('MM:SS로 센다 — 매초 자리가 바뀌어야 카운트업이 움직여 보인다', () => {
    expect(formatClock(0)).toBe('00:00');
    expect(formatClock(65)).toBe('01:05');
    expect(formatClock(3599)).toBe('59:59');
  });

  it('1시간부터는 HH:MM:SS로 넘어간다', () => {
    expect(formatClock(3600)).toBe('01:00:00');
    expect(formatClock(4805)).toBe('01:20:05');
  });

  it('음수·NaN은 00:00 — 계산이 어긋나도 화면에 "-1:-5"가 뜨지 않는다', () => {
    expect(formatClock(-30)).toBe('00:00');
    expect(formatClock(NaN)).toBe('00:00');
  });
});

describe('elapsedSeconds', () => {
  it('시작 시각부터의 경과 초를 센다', () => {
    expect(elapsedSeconds('2026-08-06T00:00:00Z', Date.parse('2026-08-06T00:01:30Z'))).toBe(90);
  });

  it('시계 오차로 시작이 미래여도 음수를 내지 않는다', () => {
    expect(elapsedSeconds('2026-08-06T00:05:00Z', Date.parse('2026-08-06T00:00:00Z'))).toBe(0);
  });
});
