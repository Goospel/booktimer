import { vi } from 'vitest';

import type { ContributionDay, ContributionGraph } from './api';

/** 테스트 공용 픽스처 — 잔디 그래프와 TDS Provider 껍데기(ui/app 테스트가 같은 데이터를 쓴다). */

export function day(level: number, extra: Partial<ContributionDay> = {}): ContributionDay {
  return { date: '2026-08-10', totalSeconds: 600, level, manual: false, ...extra };
}

export const graph: ContributionGraph = {
  weeks: [
    [day(0), day(2), day(4)],
    [day(1, { manual: true }), day(3), day(0, { date: null })],
  ],
  monthLabels: [{ weekIndex: 0, label: '8월' }],
  totalSeconds: 3600,
  activeDays: 4,
  currentStreak: 2,
  growthStageName: 'SPROUT',
  growthStageEmoji: '🌱',
  growthStageLabel: '새싹',
};

/** TDS 컴포넌트는 ThemeProvider(=TDSMobileProvider) 없이는 렌더되지 않는다 — main.tsx와 같은 껍데기. */
export const userAgent = { fontA11y: undefined, fontScale: undefined, isAndroid: false, isIOS: true };

/**
 * 인메모리 localStorage 심기 — 홈이 알림 동의 캐시를 렌더 중에 읽어서, 홈을 그리는 테스트는 전부 필요하다
 * (테스트 환경이 node라 전역이 없다). 실제처럼 값을 문자열로 강제한다.
 */
export function stubLocalStorage(): void {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, String(v)),
    removeItem: (k: string) => void store.delete(k),
  });
}
