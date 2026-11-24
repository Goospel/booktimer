// 움직임 값(언어 1.8) — 설정 객체만 돌려준다(순수 · reanimated import 0). reanimated를 부르는 곳은 lib/animate.ts 하나(계획 D15).
// 앱 스프링 = withSpring({ mass: 1, stiffness: ω², damping: 2ζω }) · ω = 2π / response.

const SPRINGS = {
  press: [0.22, 1.0], // 키 눌림
  needle: [0.28, 0.78], // 바늘 앉음 · 다이얼 지시 되돌림
  plate: [0.32, 1.0], // 라벨 교체 · 숫자 굴림 · 등장 · 알림 · 상태 교대
  tape: [0.18, 1.0], // 테이프 1초
  tabMinimize: [0.4, 1.0],
  accessory: [0.45, 0.9],
  sheetUp: [0.42, 0.92], // 키보드 올라옴 근사
  detent: [0.38, 0.88], // 목표 다이얼 칸 맞춤
  sheetDown: [0.35, 1.0], // 키보드 내려감 근사
  menu: [0.32, 0.85],
  zoomOpen: [0.5, 0.92],
  zoomClose: [0.42, 0.95],
} as const satisfies Record<string, readonly [response: number, zeta: number]>;

export type SpringName = keyof typeof SPRINGS;
export type SpringConfig = { stiffness: number; damping: number; mass: 1 };

export function springOf(name: SpringName): SpringConfig {
  const [response, zeta] = SPRINGS[name];
  const omega = (2 * Math.PI) / response;
  return { stiffness: omega * omega, damping: 2 * zeta * omega, mass: 1 };
}

/** 짧은 전환(스프링 아님 — 언어 1.8): 눌림 덮개 opacity */
export const PRESS_FADE_MS = 120;

export type Transition =
  | { type: 'spring'; config: SpringConfig }
  | { type: 'timing'; duration: number; easing: 'linear' }
  | { type: 'instant' };

/** 움직임 줄이기면 transform은 끝 값으로 바로 · opacity는 150ms linear(언어 1.8 · REQ-435) */
export function transitionFor(name: SpringName, o: { reduced: boolean; prop: 'transform' | 'opacity' }): Transition {
  if (!o.reduced) return { type: 'spring', config: springOf(name) };
  return o.prop === 'transform' ? { type: 'instant' } : { type: 'timing', duration: 150, easing: 'linear' };
}
