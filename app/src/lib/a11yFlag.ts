// 접근성 설정 하나(굵은 텍스트 · 동작 줄이기)를 지금 값으로 따르는 훅 — 앱이 떠 있는 동안 설정을 바꿔도 다시 그린다.
// 구독은 첫 렌더 때 한 번만(모듈 import에는 부작용이 없다 — ui.test가 ui.tsx의 base를 읽는다).
import { useSyncExternalStore } from 'react';
import { AccessibilityInfo } from 'react-native';

type Event = Parameters<typeof AccessibilityInfo.addEventListener>[0];

export function a11yFlag(read: () => Promise<boolean>, event: Event): () => boolean {
  let on = false;
  let started = false;
  const listeners = new Set<() => void>();
  const set = (v: boolean) => {
    if (v === on) return;
    on = v;
    listeners.forEach((l) => l());
  };
  const subscribe = (cb: () => void) => {
    if (!started) {
      started = true;
      read().then(set, () => {});
      AccessibilityInfo.addEventListener(event, set as (v: unknown) => void);
    }
    listeners.add(cb);
    return () => listeners.delete(cb);
  };
  return () => useSyncExternalStore(subscribe, () => on);
}
