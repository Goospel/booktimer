// 어댑터가 Reanimated의 「시스템 줄이기」에 한 번 더 꺼지지 않는가(REQ-435) — transitionFor가 이미 줄이기 규칙을 적용했다.
// 시스템 줄이기가 켜진 채로 reanimated를 처음 불러야 한다(require 전에 둔다 — import는 끌어올려져 쓰지 않는다).
(global as unknown as { _REANIMATED_IS_REDUCED_MOTION: boolean })._REANIMATED_IS_REDUCED_MOTION = true;
const { makeMutable } = require('react-native-reanimated') as typeof import('react-native-reanimated');
const { animateTo } = require('./animate') as typeof import('./animate');

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

test('REQ-435 · 시스템 줄이기가 켜져도 opacity 150ms linear는 건너뛰지 않는다(48ms 뒤 0.32 — 건너뛰면 1)', () => {
  const sv = makeMutable(0);
  animateTo(sv, 1, { type: 'timing', duration: 150, easing: 'linear' });
  jest.advanceTimersByTime(48);
  expect(sv.value).toBeCloseTo(0.32, 2);
});
