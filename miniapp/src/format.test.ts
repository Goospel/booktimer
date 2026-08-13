import { describe, expect, it } from 'vitest';

import { elapsedSeconds, formatClock, formatDuration, relativeTime } from './format';

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

/**
 * 피드 상대 시간 — 자정 경계가 아니라 24시간 단위 근사다(피드 톤). "어제"만 예외적으로 하루 구간을
 * 통째로 차지하고, 2주가 넘으면 상대 표기가 오히려 안 읽혀 절대 날짜로 넘어간다.
 */
describe('relativeTime', () => {
  const base = Date.parse('2026-08-14T12:00:00Z');
  const HOUR = 3_600_000;
  const DAY = 24 * HOUR;
  const ago = (ms: number) => new Date(base - ms).toISOString();

  it('1분 미만은 "방금 전" — 초 단위는 피드에서 잡음이다', () => {
    expect(relativeTime(ago(30_000), base)).toBe('방금 전');
  });

  it('1시간 미만은 분, 하루 미만은 시간', () => {
    expect(relativeTime(ago(5 * 60_000), base)).toBe('5분 전');
    expect(relativeTime(ago(3 * HOUR), base)).toBe('3시간 전');
  });

  it('24~47시간은 "어제" — 하루 구간을 통째로 차지한다', () => {
    expect(relativeTime(ago(DAY), base)).toBe('어제');
    expect(relativeTime(ago(47 * HOUR), base)).toBe('어제');
  });

  it('이틀부터는 N일 전 — 13일까지', () => {
    expect(relativeTime(ago(2 * DAY), base)).toBe('2일 전');
    expect(relativeTime(ago(3 * DAY), base)).toBe('3일 전');
    expect(relativeTime(ago(13 * DAY), base)).toBe('13일 전');
  });

  it('14일부터는 절대 날짜 — "20일 전"은 이미 안 읽힌다', () => {
    expect(relativeTime(ago(14 * DAY), base)).toBe('7월 31일');
    expect(relativeTime(ago(20 * DAY), base)).toBe('7월 25일');
  });

  it('미래 시각(시계 어긋남)은 "방금 전"으로 클램프 — "-3분 전"이 뜨지 않는다', () => {
    expect(relativeTime(ago(-60_000), base)).toBe('방금 전');
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
