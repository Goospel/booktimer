import { tossLogin } from './toss';

/**
 * 서버 API 클라이언트 — fetch + `Authorization: Bearer`.
 *
 * <p>타입은 `web/api/TossAuthApiController` · `DashboardApiController`의 record가 단일 출처다(설계 §2.5).
 */

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'booktimer.token';

/** 토큰 보관 — WebView의 localStorage. 401을 만나면 폐기하고 재로그인한다. */
export const token = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (value: string): void => localStorage.setItem(TOKEN_KEY, value),
  clear: (): void => localStorage.removeItem(TOKEN_KEY),
};

/** 토큰이 없거나 만료됨 — 화면은 이걸 잡아 로그인 브릿지로 돌아간다. */
export class UnauthorizedError extends Error {
  constructor() {
    super('로그인이 필요합니다');
    this.name = 'UnauthorizedError';
  }
}

/** 그 외 실패 — 서버가 준 메시지를 그대로 사용자에게 보여준다(연결 코드 오류·409 등). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, body?: unknown): Promise<T> {
  const saved = token.get();
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (saved !== null) headers.Authorization = `Bearer ${saved}`;

  const response = await fetch(`${BASE_URL}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401) {
    token.clear();
    throw new UnauthorizedError();
  }
  // 본문을 텍스트로 먼저 받는다 — 성공 응답이 빈 본문일 수 있고(204 logout, 200 goal),
  // 에러 본문은 JSON이 아니라 평문 메시지다.
  const text = await response.text();
  if (!response.ok) throw new ApiError(response.status, text || `요청에 실패했어요 (${response.status})`);
  return (text === '' ? undefined : JSON.parse(text)) as T;
}

// ── 인증 ────────────────────────────────────────────────────────────────────

export interface TossAuthResponse {
  registered: boolean;
  token: string | null;
  loginId: string | null;
}

/** 세 인증 엔드포인트의 공통부 — 매번 fresh 인가코드로 신원을 다시 증명한다(서버에 pending 상태 없음). */
async function authenticate(path: string, extra?: Record<string, string>): Promise<TossAuthResponse> {
  const { authorizationCode, referrer } = await tossLogin();
  const result = await request<TossAuthResponse>(path, { authorizationCode, referrer, ...extra });
  // 토큰이 실린 응답만 저장한다 — 서버는 미등록(registered:false)일 때 토큰을 주지 않는다.
  if (result.token !== null) token.set(result.token);
  return result;
}

/** 조회만 — 미등록이면 `registered:false`(계정 미생성)라 미니앱이 선택 화면을 띄운다. */
export const login = (): Promise<TossAuthResponse> => authenticate('/api/toss/login');

/** "새로 시작" — 토스 신원으로 신규 계정 생성. 멱등. */
export const register = (): Promise<TossAuthResponse> => authenticate('/api/toss/register');

/** "기존 계정 연결" — 웹 설정에서 발급한 일회용 코드가 계정 소유 증명. */
export const linkAccount = (linkCode: string): Promise<TossAuthResponse> =>
  authenticate('/api/toss/link', { linkCode });

/** 서버 폐기가 실패해도 로컬 토큰은 반드시 버린다 — 안 그러면 죽은 토큰으로 계속 401을 맞는다. */
export async function logout(): Promise<void> {
  try {
    await request<void>('/api/toss/logout', {});
  } catch {
    // 폐기 실패는 무시 — 토큰 TTL이 알아서 끝낸다.
  } finally {
    token.clear();
  }
}

// ── 도메인 (기존 API 재사용) ─────────────────────────────────────────────────

export interface BookOption {
  id: number;
  title: string;
}

export interface ContributionDay {
  date: string | null;
  totalSeconds: number;
  level: number;
  manual: boolean;
}

export interface ContributionGraph {
  weeks: ContributionDay[][];
  monthLabels: { weekIndex: number; label: string }[];
  totalSeconds: number;
  activeDays: number;
  currentStreak: number;
  growthStageName: string;
  growthStageEmoji: string;
  growthStageLabel: string;
}

export interface TimerState {
  remainingSeconds: number;
  carriedDebtSeconds: number;
  todayGoalSeconds: number;
  carryover: boolean;
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  activeBookTotalSeconds: number;
  readingBooks: BookOption[];
  finishedBooks: BookOption[];
  recentBookId: number | null;
}

export interface DashboardResponse extends TimerState {
  nickname: string;
  loginId: string | null;
  profileCharacterCode: string | null;
  wantToReadBooks: BookOption[];
  graph: ContributionGraph;
  emailVerified: boolean;
}

export interface StopResponse {
  sessionId: number;
  untagged: boolean;
  timer: TimerState;
  graph: ContributionGraph;
}

export const fetchDashboard = (): Promise<DashboardResponse> => request('/api/dashboard');

/** bookId를 안 주면 책 미지정 세션으로 시작한다(종료 후 태깅). */
export const startSession = (bookId: number | null): Promise<TimerState> =>
  request('/api/sessions/start', { bookId });

export const stopSession = (): Promise<StopResponse> => request('/api/sessions/stop', {});

export const tagBook = (sessionId: number, bookId: number): Promise<{ sessionId: number; bookTitle: string }> =>
  request(`/api/sessions/${sessionId}/tag-book`, { bookId });

export const setGoal = (dailyIncrementSeconds: number): Promise<void> =>
  request('/api/miniapp/goal', { dailyIncrementSeconds });
