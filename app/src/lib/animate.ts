// motion.ts의 설정 객체를 reanimated에 거는 얇은 어댑터(계획 D15) — 단위 테스트 없음, 기기 D1 · D2가 본다.
// reanimated를 부르는 곳은 이 파일과 부품의 Animated.* · useAnimatedStyle뿐이다. 움직이는 값은 transform · opacity만(REQ-434 — forbidden 가드).
import { Easing, type SharedValue, useReducedMotion, withSpring, withTiming } from 'react-native-reanimated';

import type { Transition } from './motion';

export function animateTo(sv: SharedValue<number>, to: number, t: Transition): void {
  if (t.type === 'spring') sv.value = withSpring(to, t.config);
  else if (t.type === 'timing') sv.value = withTiming(to, { duration: t.duration, easing: Easing.linear });
  else sv.value = to;
}

/** 시스템 「동작 줄이기」 — 켜지면 transitionFor가 transform은 바로 · opacity는 150ms linear로 바꾼다(REQ-435) */
export const useReduced = (): boolean => useReducedMotion();
