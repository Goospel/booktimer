// 서버 API 클라이언트 — 미니앱 api.ts 계약 이식(v1 REQ-30). 토큰·userId·webAccount는 SecureStore(Keychain).
// 주소는 설정에 두지 않는다(지문) — JS 기본값이 TestFlight·업데이트 모두의 주소다(앞당김 §6.6).
import * as SecureStore from 'expo-secure-store';

import type { AppAuthResponse, AppleIntent, Dashboard, DraftSummary } from './types';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://booktimer.app';

const KEY = { token: 'booktimer.token', userId: 'booktimer.userId', webAccount: 'booktimer.webAccount' } as const;

// ── 세션 — 로그인 상태가 바뀌면(로그인 · 401) 화면 틀이 다시 그린다 ───────────
const listeners = new Set<(signedIn: boolean) => void>();
export const session = {
  token: () => SecureStore.getItemAsync(KEY.token),
  async save(p: { token: string; userId: number; webAccount: boolean }) {
    await SecureStore.setItemAsync(KEY.userId, String(p.userId));
    await SecureStore.setItemAsync(KEY.webAccount, String(p.webAccount));
    await SecureStore.setItemAsync(KEY.token, p.token);
    listeners.forEach((l) => l(true));
  },
  async clear() {
    await Promise.all(Object.values(KEY).map((k) => SecureStore.deleteItemAsync(k)));
    listeners.forEach((l) => l(false));
  },
  subscribe(l: (signedIn: boolean) => void) {
    listeners.add(l);
    return () => void listeners.delete(l);
  },
};

/** 토큰이 없거나 만료됨 — 화면 틀이 로그인 화면으로 돌아간다. */
export class UnauthorizedError extends Error {
  constructor() {
    super('다시 로그인해 주세요');
    this.name = 'UnauthorizedError';
  }
}

/** 그 밖의 실패 — 서버가 준 문구를 그대로 띄운다(연결 코드 오류 · 409 등). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** 요청이 서버에 닿지도 못함 — fetch가 던지는 영문 TypeError를 화면에 내지 않는다. */
export class NetworkError extends Error {
  constructor() {
    super('인터넷에 연결되지 않았어요 — 연결한 뒤 다시 해 주세요');
    this.name = 'NetworkError';
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** 토큰을 싣지 않고 401도 세션을 지우지 않는다(로그인 요청). */
  anonymous?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, anonymous } = options;
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const saved = anonymous ? null : await session.token();
  if (saved !== null) headers.Authorization = `Bearer ${saved}`;

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? (body === undefined ? 'GET' : 'POST'),
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new NetworkError();
  }

  if (response.status === 401 && !anonymous) {
    await session.clear();
    throw new UnauthorizedError();
  }
  // 오류 본문은 평문 문구다. 성공 본문은 비어 있을 수 있다.
  const text = await response.text();
  if (!response.ok) throw new ApiError(response.status, errorMessage(response.status, text));
  return (text === '' ? undefined : JSON.parse(text)) as T;
}

/** 서버 평문 문구가 있으면 그대로. 전역 핸들러의 HTML 오류 페이지면 버리고 상태 코드로 대신한다. */
function errorMessage(status: number, body: string): string {
  const plain = body.trim();
  return plain === '' || plain.startsWith('<') ? `요청에 실패했어요 (${status})` : plain;
}

// ── Apple 로그인(v1 §4.3) — LOGIN 먼저, 없으면 같은 토큰으로 REGISTER(시작) · LINK(웹 계정) ──
const ALREADY_APP_ACCOUNT =
  '이 Apple ID로 만든 책생글 계정이 있어서 그 계정으로 들어왔어요 — 웹 계정과 이으려면 설정에서 이 계정을 지운 뒤 다시 연결해 주세요';

const auth = (identityToken: string, intent: AppleIntent, extra?: object) =>
  request<AppAuthResponse>('/api/app/auth/apple', { body: { identityToken, intent, ...extra }, anonymous: true });

export async function appleSignIn(p: { mode: 'start' | 'link'; identityToken: string; code: string }): Promise<{ notice?: string }> {
  let res = await auth(p.identityToken, 'LOGIN');
  const existed = res.registered;
  if (!existed) {
    res = p.mode === 'start'
      ? await auth(p.identityToken, 'REGISTER', { ageConfirmed: true })
      : await auth(p.identityToken, 'LINK', { linkCode: p.code.trim() });
  }
  if (!res.registered || res.token === null || res.userId === null) throw new ApiError(500, '요청에 실패했어요 (500)');
  await session.save({ token: res.token, userId: res.userId, webAccount: res.webAccount });
  return existed && p.mode === 'link' ? { notice: ALREADY_APP_ACCOUNT } : {};
}

// ── 독서 측정 · 하루 목표(PR-F) — 앱은 /api/app/goal 별칭만 부른다(REQ-109) ──────────
export const reading = {
  dashboard: () => request<Dashboard>('/api/dashboard'),
  start: (bookId: number | null) => request<unknown>('/api/sessions/start', { body: { bookId } }),
  stop: () => request<unknown>('/api/sessions/stop', { method: 'POST' }),
  setGoal: (seconds: number) => request<void>('/api/app/goal', { body: { dailyIncrementSeconds: seconds } }),
  /** 최근 고친 글 20편(본문 없음) — 홈 「이어 쓰기」 */
  drafts: () => request<DraftSummary[]>('/api/app/drafts'),
};
