// 위젯 측정 단추(위젯 확장 설계 REQ-180~183). 위젯 런타임은 서버를 못 부른다(동기 JSC · 네트워크 없음 — 설계 §3.2) → 앱을 열고, 앱이 그 화면의 측정 처리기를 누른다.
// 주소는 이 세 상수뿐이다 — 개발 클라이언트가 남의 JS를 싣는 `__expo_*` 매개변수가 없다(CLAUDE.md 개발 앱 링크 규칙 · D8).
export const WIDGET_URL = {
  start: 'booktimer://widget/start',
  stopReading: 'booktimer://widget/stop-reading',
  stopStudy: 'booktimer://widget/stop-study',
} as const;

export type WidgetMode = 'reading' | 'study';
export type WidgetAction = 'start' | 'stop';
export interface WidgetIntent {
  action: WidgetAction;
  mode: WidgetMode;
  at: number;
}

/** 조절 상수 — 위젯을 누르고 앱이 첫 대시보드를 읽기까지(차가운 시작 · 느린 연결). 넘으면 버린다 — 로그인 화면을 거쳐 한참 뒤 시작되지 않게(U-107 ③이 잰다). */
export const WIDGET_INTENT_TTL_MS = 20_000;

const KINDS = {
  start: { action: 'start', mode: 'reading', route: '/' },
  'stop-reading': { action: 'stop', mode: 'reading', route: '/' },
  'stop-study': { action: 'stop', mode: 'study', route: '/study' },
} as const;

/** 위젯 주소면 동작 · 모드 · 갈 탭, 아니면 null. 스킴은 보지 않는다 — 위젯 주소는 그 위젯의 앱으로 온다(Apple 원문 · 개발 앱 · TF 앱 둘 다). */
export function parseWidgetUrl(url: string): (typeof KINDS)[keyof typeof KINDS] | null {
  const m = /^[a-z][a-z0-9+.-]*:\/\/widget\/(start|stop-reading|stop-study)\/?(?:[?#].*)?$/i.exec(url);
  return m ? KINDS[m[1] as keyof typeof KINDS] : null;
}

let pending: WidgetIntent | null = null;
export const widgetIntent = {
  put(i: WidgetIntent) {
    pending = i; // 한 칸 — 뒤 요청이 앞 요청을 덮는다
  },
  /** 그 모드 화면이 새로 읽은 대시보드 뒤에 한 번. 다른 모드면 남긴다 · 오래됐으면 버린다. */
  take(mode: WidgetMode, now: number): WidgetIntent | null {
    if (pending === null || pending.mode !== mode) return null;
    const i = pending;
    pending = null;
    return now - i.at <= WIDGET_INTENT_TTL_MS ? i : null;
  },
};

/** 새로 읽은 상태로 정한다 — 낡은 위젯의 「측정 종료」가 새 측정을 시작하지 않게(REQ-183). */
export function widgetDecision(action: WidgetAction, measuring: boolean): 'press' | 'already-stopped' | 'none' {
  if (action === 'start') return measuring ? 'none' : 'press';
  return measuring ? 'press' : 'already-stopped';
}
