// motion.ts의 설정 객체를 reanimated에 거는 얇은 어댑터(계획 D15) — animate.test가 시스템 줄이기 아래 실행을 잰다, 기기 D1 · D2가 본다.
// reanimated를 부르는 곳은 이 파일과 부품의 Animated.* · useAnimatedStyle뿐이다. 움직이는 값은 transform · opacity만(REQ-434 — forbidden 가드).
import { AccessibilityInfo } from 'react-native';
import { Easing, ReduceMotion, type SharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { a11yFlag } from './a11yFlag';
import type { Transition } from './motion';

// 줄이기 규칙은 transitionFor가 이미 적용했다 — Reanimated 기본값(System)이 opacity 150ms까지 0ms로 끄지 않게 Never
export function animateTo(sv: SharedValue<number>, to: number, t: Transition): void {
  if (t.type === 'spring') sv.value = withSpring(to, { ...t.config, reduceMotion: ReduceMotion.Never });
  else if (t.type === 'timing') sv.value = withTiming(to, { duration: t.duration, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
  else sv.value = to;
}

/** 시스템 「동작 줄이기」(지금 값 — Reanimated useReducedMotion은 앱 시작 때 값에 머문다) — 켜지면 transitionFor가 transform은 바로 · opacity는 150ms linear로 바꾼다(REQ-435) */
export const useReduced = a11yFlag(() => AccessibilityInfo.isReduceMotionEnabled(), 'reduceMotionChanged');
