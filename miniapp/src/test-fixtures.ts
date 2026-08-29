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
};

/**
 * TDS 컴포넌트는 ThemeProvider(=TDSMobileProvider) 없이는 렌더되지 않는다 — main.tsx와 같은 껍데기.
 * `colorPreference`도 main.tsx와 맞춘다 — 앱은 라이트 고정인데 하니스만 다른 테마로 그리면
 * 색을 보는 단언(예: 버튼 채움색)이 프로덕션과 다른 팔레트를 재는 셈이 된다.
 */
export const userAgent = {
  fontA11y: undefined,
  fontScale: undefined,
  isAndroid: false,
  isIOS: true,
  colorPreference: 'light' as const,
};

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
    // 열거(length·key)까지 흉내낸다 — 접두어로 코치마크 기록만 골라 지우는 쪽이 이 API로 훑는다.
    get length() {
      return store.size;
    },
    key: (i: number) => [...store.keys()][i] ?? null,
  });
}
