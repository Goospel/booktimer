// Live Activity 판정(v1 REQ-35 · §4.10). 앱이 앞으로 올 때 · 시작/정지 때 서버 상태와 이 기기의 활동을 비교한다.
// 웹 · 미니앱에서 시작한 측정도 앱을 여는 순간 잡힌다. 활동은 표시 전용이다(버튼 없음).

/** 서버 6시간 상한 + 스윕 최대 10분 — 표시가 여기서 멈추고 흐려진다. */
export const DISPLAY_CAP_MS = (6 * 60 + 10) * 60 * 1000;

export interface ServerReading {
  active: boolean;
  startedAt: number | null;
  bookTitle: string | null;
}

/** 이 기기가 띄운 활동이 보이는 값(없으면 null). */
export interface LocalActivity {
  startedAt: number;
  bookTitle: string | null;
}

export type ActivityAction = 'start' | 'end' | 'restart' | 'none';

export function liveActivityAction(server: ServerReading, local: LocalActivity | null): ActivityAction {
  if (!server.active || server.startedAt === null) return local ? 'end' : 'none';
  if (!local) return 'start';
  return local.startedAt === server.startedAt && local.bookTitle === server.bookTitle ? 'none' : 'restart';
}

/** OS가 스스로 흐르게 그리는 구간 — 상한에서 멈추고(pause) 같은 시각에 stale이 된다. */
export function activityTimes(startedAt: number) {
  const upperMs = startedAt + DISPLAY_CAP_MS;
  return { lowerMs: startedAt, upperMs, pauseMs: upperMs, staleMs: upperMs };
}
