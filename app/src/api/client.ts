// 서버 API 클라이언트 — 미니앱 api.ts 계약 이식(v1 REQ-30). 토큰·userId·webAccount는 SecureStore(Keychain).
// 주소는 설정에 두지 않는다(지문) — JS 기본값이 TestFlight·업데이트 모두의 주소다(앞당김 §6.6).
import * as SecureStore from 'expo-secure-store';

import type {
  AddRequest,
  AppAuthResponse,
  AiView,
  AppleIntent,
  ConsentView,
  CreateMargin,
  Dashboard,
  DeleteRequest,
  EditMargin,
  MarginSummary,
  MarginView,
  MaterialsView,
  MyBookSummary,
  SearchResponse,
  ShelfResponse,
  ShelfStatus,
  Step,
  StudyAddRequest,
  StudyBook,
  StudyShelfResponse,
} from './types';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'https://booktimer.app';

const KEY = {
  token: 'booktimer.token',
  userId: 'booktimer.userId',
  webAccount: 'booktimer.webAccount',
  authProvider: 'booktimer.authProvider',
  appleLinked: 'booktimer.appleLinked',
  googleLinked: 'booktimer.googleLinked',
} as const;

/** 마지막 로그인 응답의 로그인 정보 — 탈퇴 갈래(lib/deleteVia). PR-I 전에 로그인한 세션은 authProvider가 null이다. */
export interface AccountInfo {
  authProvider: string | null;
  appleLinked: boolean;
  googleLinked: boolean;
  webAccount: boolean;
}

// ── 세션 — 로그인 상태가 바뀌면(로그인 · 401) 화면 틀이 다시 그린다 ───────────
const listeners = new Set<(signedIn: boolean) => void>();
export const session = {
  token: () => SecureStore.getItemAsync(KEY.token),
  /** 기기 초안 칸의 주인(v1 REQ-38) — 로그인할 때 저장한 값 */
  async userId(): Promise<number | null> {
    const v = await SecureStore.getItemAsync(KEY.userId);
    return v === null ? null : Number(v);
  },
  async account(): Promise<AccountInfo> {
    const [authProvider, appleLinked, googleLinked, webAccount] = await Promise.all(
      [KEY.authProvider, KEY.appleLinked, KEY.googleLinked, KEY.webAccount].map((k) => SecureStore.getItemAsync(k)),
    );
    return { authProvider, appleLinked: appleLinked === 'true', googleLinked: googleLinked === 'true', webAccount: webAccount === 'true' };
  },
  async save(p: { token: string; userId: number; webAccount: boolean; authProvider?: string | null; appleLinked?: boolean; googleLinked?: boolean }) {
    await SecureStore.setItemAsync(KEY.userId, String(p.userId));
    await SecureStore.setItemAsync(KEY.webAccount, String(p.webAccount));
    if (p.authProvider) await SecureStore.setItemAsync(KEY.authProvider, p.authProvider);
    else await SecureStore.deleteItemAsync(KEY.authProvider);
    await SecureStore.setItemAsync(KEY.appleLinked, String(!!p.appleLinked));
    await SecureStore.setItemAsync(KEY.googleLinked, String(!!p.googleLinked));
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

/** 화면 알림 문구 — 서버 평문 · 연결 문구는 그대로, 문구 없는 실패(위 「요청에 실패했어요 (N)」 · 그 밖 예외)는 화면 문구로. */
export function failText(e: unknown, fallback: string): string {
  if (e instanceof NetworkError) return e.message;
  if (e instanceof ApiError && !/^요청에 실패했어요 \(\d+\)$/.test(e.message)) return e.message;
  return fallback;
}

// ── Apple 로그인(v1 §4.3) — LOGIN 먼저, 없으면 같은 토큰으로 REGISTER(시작) · LINK(웹 계정) ──
const ALREADY_APP_ACCOUNT =
  '이 Apple ID로 만든 책생글 계정이 있어서 그 계정으로 들어왔어요 — 웹 계정과 이으려면 설정에서 이 계정을 지운 뒤 다시 연결해 주세요';

type AuthBody = { identityToken: string; intent: AppleIntent; ageConfirmed?: boolean; linkCode?: string };
const auth = (provider: 'apple' | 'google' | 'kakao', body: AuthBody) =>
  request<AppAuthResponse>(`/api/app/auth/${provider}`, { body, anonymous: true });

async function saveSession(res: AppAuthResponse) {
  if (!res.registered || res.token === null || res.userId === null) throw new ApiError(500, '요청에 실패했어요 (500)');
  await session.save({
    token: res.token,
    userId: res.userId,
    webAccount: res.webAccount,
    authProvider: res.authProvider ?? null, // 옛 서버엔 없다
    appleLinked: !!res.appleLinked,
    googleLinked: !!res.googleLinked,
  });
}

export async function appleSignIn(p: { mode: 'start' | 'link'; identityToken: string; code: string }): Promise<{ notice?: string }> {
  let res = await auth('apple', { identityToken: p.identityToken, intent: 'LOGIN' });
  const existed = res.registered;
  if (!existed) {
    res = p.mode === 'start'
      ? await auth('apple', { identityToken: p.identityToken, intent: 'REGISTER', ageConfirmed: true })
      : await auth('apple', { identityToken: p.identityToken, intent: 'LINK', linkCode: p.code.trim() });
  }
  await saveSession(res);
  return existed && p.mode === 'link' ? { notice: ALREADY_APP_ACCOUNT } : {};
}

/**
 * 구글 · 카카오(REQ-86 · 79) — LOGIN 먼저, 없으면 같은 토큰으로 REGISTER. 같은 이메일의 인증 안 된 웹 계정이 있으면(구글) 확인 뒤에만. LINK는 없다.
 * ageConfirmed는 화면 값 그대로 보낸다(호출부 = `mode === 'start' && ageOn`) — 서버가 앱이 보낸 값을 믿는다(AppAuthApiController.java:114 · :147).
 */
export async function idTokenSignIn(p: {
  provider: 'google' | 'kakao';
  identityToken: string;
  ageConfirmed: boolean;
  confirmUnverified: () => Promise<boolean>;
}): Promise<{ cancelled?: true }> {
  let res = await auth(p.provider, { identityToken: p.identityToken, intent: 'LOGIN' });
  if (!res.registered) {
    if (res.unverifiedWebAccount && !(await p.confirmUnverified())) return { cancelled: true }; // 아무것도 저장 · 삭제하지 않는다
    res = await auth(p.provider, { identityToken: p.identityToken, intent: 'REGISTER', ageConfirmed: p.ageConfirmed });
  }
  await saveSession(res);
  return {};
}

// ── 독서 측정 · 하루 목표(PR-F) — 앱은 /api/app/goal 별칭만 부른다(REQ-109) ──────────
export const reading = {
  dashboard: () => request<Dashboard>('/api/dashboard'),
  start: (bookId: number | null) => request<unknown>('/api/sessions/start', { body: { bookId } }),
  stop: () => request<unknown>('/api/sessions/stop', { method: 'POST' }),
  setGoal: (seconds: number) => request<void>('/api/app/goal', { body: { dailyIncrementSeconds: seconds } }),
};

// ── 서재 · 책 담기 · 책 재료(PR-G) — 독서 서재 책만. 공개 범위는 웹이 바꾼다(REQ-40 — 그 문을 부르지 않는다) ──
export const library = {
  shelf: () => request<ShelfResponse>('/api/books'),
  /** 검색어는 인코딩한다(한글 · 공백 · & — REQ-125) */
  search: (q: string) => request<SearchResponse>(`/api/books/search?q=${encodeURIComponent(q)}`),
  /** 바코드 ISBN-13 한 건 조회(ItemLookUp) */
  byIsbn: (isbn: string) => request<SearchResponse>(`/api/books/search?type=isbn&q=${encodeURIComponent(isbn)}`),
  add: (body: AddRequest) => request<MyBookSummary>('/api/books', { body }),
  setStatus: (id: number, status: ShelfStatus) => request<MyBookSummary>(`/api/books/${id}/status`, { body: { status } }),
  materials: (id: number) => request<MaterialsView>(`/api/app/books/${id}/materials`),
};

// ── 여백 쪽지(책생글 M1 — /api/app/margins) — 본문에 공유 · 배경색 필드를 싣지 않는다(REQ-40). 응답은 늘 revision을 준다. 오류는 평문(404 남의·없는 쪽지 · 409 낡은 판) ──
// 앱은 이제 /api/stories를 부르지 않는다(설계 §5.1 — 웹 · 미니앱 경로는 남는다).
export const margins = {
  ofBook: (bookId: number) => request<MarginSummary[]>(`/api/app/books/${bookId}/margins`),
  /** 책 없는 쪽지(C9) */
  bookless: () => request<MarginSummary[]>('/api/app/margins?book=none'),
  get: (id: number) => request<MarginView>(`/api/app/margins/${id}`),
  /** 첫 저장 때 행을 만든다(J6) — 201 */
  add: (body: CreateMargin) => request<MarginView>('/api/app/margins', { body }),
  edit: (id: number, body: EditMargin) => request<MarginView>(`/api/app/margins/${id}`, { method: 'PUT', body }),
  delete: (id: number) => request<void>(`/api/app/margins/${id}`, { method: 'DELETE' }),
  /** v1 /drafts/{id}/ai와 같은 몸 · 같은 상태 코드 — 응답에 생각 · revision이 없다(REQ-47) */
  ai: (id: number, body: { step: Step; revision: number; focus: string | null }) => request<AiView>(`/api/app/margins/${id}/ai`, { body }),
};

// ── AI 동의(PR-H) — 시행일 전이면 503(서버 문구가 날짜를 낸다). 옛 글 경로는 부르지 않는다(D9 · REQ-224 — 기기 칸 옮기기만 예외) ──
export const writing = {
  consent: (agree: boolean) => request<ConsentView>('/api/app/ai-consent', { method: 'PUT', body: { agree } }),
};

// ── 설정 · 로그아웃 · 계정 삭제(PR-I) — 동의 거두기는 writing.consent(false). 탈퇴 토큰은 Apple 창에서 방금 받은 것(저장하지 않는다) ──
export const account = {
  logout: () => request<void>('/api/app/logout', { method: 'POST' }),
  remove: (body: DeleteRequest) => request<void>('/api/app/account/delete', { body }),
  consent: () => request<ConsentView>('/api/app/ai-consent'),
};

// ── 공부 타이머 · 공부 서재(PR-S1) — 회당 시간 · 회독 문은 부르지 않는다(v1 REQ-98 — 금지 표). 공부 탭은 대시보드 study로 그린다 ──
export const study = {
  start: (bookId: number | null) => request<unknown>('/api/study/start', { body: { bookId } }),
  stop: () => request<unknown>('/api/study/stop', { body: {} }),
  /** 담기 화면의 「담음」 판정용(공부 서재 isbn) */
  shelf: () => request<StudyShelfResponse>('/api/study/books'),
  add: (body: StudyAddRequest) => request<StudyBook>('/api/study/books', { body }),
  /** 필기가 있으면 409 평문(서버 문구 그대로 보인다 — REQ-99) */
  remove: (id: number) => request<unknown>(`/api/study/books/${id}/delete`, { method: 'POST' }),
};
