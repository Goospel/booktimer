// 햅틱 지도(언어 1.9) — 순간 → 세기는 순수, 발화는 haptic() 하나만 expo-haptics를 부른다.
// 시스템 햅틱을 끄면 iOS가 알아서 울리지 않는다(U-233). 700ms 문은 화면의 gate가 맡는다 — 막힌 누름은 haptic 줄에 오지 못한다(D16).
import * as Haptics from 'expo-haptics';

export type Moment = 'measure' | 'dialSegment' | 'goalMet' | 'detent' | 'questionArrive' | 'questionPick' | 'goalDial';
export type HapticKind = 'rigid' | 'selection' | 'success' | 'light';

const MAP: Record<Moment, HapticKind> = {
  measure: 'rigid', // 측정 시작 · 종료(홈 · 공부 · 책 화면 · ⌥P)
  dialSegment: 'selection', // 다이얼 새 칸(목표 전)
  goalMet: 'success',
  detent: 'selection', // 손으로 디텐트를 바꿀 때만
  questionArrive: 'light',
  questionPick: 'selection',
  goalDial: 'selection', // 서재 목표 다이얼 칸 바뀜
};
export const kindOf = (m: Moment): HapticKind => MAP[m];

// 한 순간 하나(사양 결정 3) — 센 쪽이 덮는다: 목표 달성이 같은 순간의 다이얼 selection을 덮는다
const RANK: Record<HapticKind, number> = { success: 3, rigid: 2, light: 1, selection: 0 };
export function pickOne(moments: Moment[]): Moment | null {
  return moments.reduce<Moment | null>((best, m) => (best === null || RANK[MAP[m]] > RANK[MAP[best]] ? m : best), null);
}

export function haptic(kind: HapticKind): void {
  const p =
    kind === 'rigid'
      ? Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid)
      : kind === 'light'
        ? Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light)
        : kind === 'success'
          ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
          : Haptics.selectionAsync();
  p.catch(() => {}); // 햅틱 실패가 측정을 막지 않는다
}
