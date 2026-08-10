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

/** 호출 옵션 — 셋 다 선택. method 생략 시 body 유무로 GET/POST를 정한다(기존 호출부 계약). */
export interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, query } = options;
  const saved = token.get();
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (saved !== null) headers.Authorization = `Bearer ${saved}`;

  // undefined는 "안 보냄"이다 — 빈 문자열로 흘려보내면 서버가 빈 필터로 오해한다.
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) params.set(key, String(value));
  }
  const search = params.toString();

  const response = await fetch(`${BASE_URL}${path}${search === '' ? '' : `?${search}`}`, {
    method: options.method ?? (body === undefined ? 'GET' : 'POST'),
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
  if (!response.ok) throw new ApiError(response.status, errorMessage(response.status, text));
  return (text === '' ? undefined : JSON.parse(text)) as T;
}

/**
 * 사용자에게 보여줄 실패 문구 — 서버가 준 평문 메시지가 있으면 그대로 쓴다(연결 코드 오류 등은 문구가 곧 안내).
 *
 * <p>다만 `/api/**`의 예외는 `GlobalExceptionHandler`가 잡아 Thymeleaf `error` 뷰로 렌더하므로 본문이
 * HTML 페이지로 온다 — 그걸 그대로 띄우면 화면에 마크업이 쏟아진다. 그래서 HTML이면 버리고 상태코드로 대체한다.
 */
function errorMessage(status: number, body: string): string {
  const plain = body.trim();
  return plain === '' || plain.startsWith('<') ? `요청에 실패했어요 (${status})` : plain;
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
  const result = await request<TossAuthResponse>(path, { body: { authorizationCode, referrer, ...extra } });
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
    await request<void>('/api/toss/logout', { body: {} });
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

/** 작가 격언 — 서버가 셔플해 최대 10개를 실어 준다(`DashboardApiController.QuoteDto`). */
export interface QuoteDto {
  text: string;
  author: string;
}

export interface DashboardResponse extends TimerState {
  nickname: string;
  loginId: string | null;
  profileCharacterCode: string | null;
  wantToReadBooks: BookOption[];
  graph: ContributionGraph;
  quotes: QuoteDto[];
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
  request('/api/sessions/start', { body: { bookId } });

export const stopSession = (): Promise<StopResponse> => request('/api/sessions/stop', { body: {} });

export const tagBook = (sessionId: number, bookId: number): Promise<{ sessionId: number; bookTitle: string }> =>
  request(`/api/sessions/${sessionId}/tag-book`, { body: { bookId } });

export const setGoal = (dailyIncrementSeconds: number): Promise<void> =>
  request('/api/miniapp/goal', { body: { dailyIncrementSeconds } });

// ── 서재 (`web/api/BookApiController`의 record가 타입 단일 출처) ───────────────

export type BookStatus = 'WANT_TO_READ' | 'READING' | 'FINISHED';
export type BookVisibility = 'PRIVATE' | 'PUBLIC';

/** `BookApiController.MyBookSummary` — 미니앱이 쓰는 필드만 옮긴다(popularity·제휴 플래그는 웹 전용). */
export interface MyBookSummary {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  isbn13: string | null;
  status: BookStatus;
  statusLabel: string;
  visibility: BookVisibility;
  visibilityLabel: string;
  isPublic: boolean;
  seconds: number;
  purchaseLink: string | null;
}

/** `BookApiController.SearchRow` — `owned`는 서버가 계산해 주는 UI 표시용이라 추가 요청에 되돌려 보내지 않는다. */
export interface SearchRow {
  title: string;
  author: string | null;
  isbn13: string | null;
  coverUrl: string | null;
  publisher: string | null;
  purchaseLink: string | null;
  category: string | null;
  pubDate: string | null;
  owned: boolean;
}

export interface ShelfResponse {
  searchEnabled: boolean;
  books: MyBookSummary[];
}

export const fetchShelf = (): Promise<ShelfResponse> => request('/api/books');

/** 알라딘 1페이지. 서버는 외부 API 장애도 빈 결과로 격리하므로 실패와 0건이 같은 모양으로 온다. */
export const searchBooks = (q: string): Promise<{ results: SearchRow[] }> =>
  request('/api/books/search', { query: { q } });

/** 같은 ISBN을 이미 가졌으면 서버가 새 행을 만들지 않고 기존 책을 돌려준다(멱등). */
export const addBook = (row: SearchRow, status: BookStatus): Promise<MyBookSummary> =>
  request('/api/books', {
    body: {
      title: row.title,
      author: row.author,
      isbn13: row.isbn13,
      coverUrl: row.coverUrl,
      publisher: row.publisher,
      purchaseLink: row.purchaseLink,
      category: row.category,
      pubDate: row.pubDate,
      status,
    },
  });

export const changeBookStatus = (id: number, status: BookStatus): Promise<MyBookSummary> =>
  request(`/api/books/${id}/status`, { body: { status } });

export const setBookVisibility = (id: number, visibility: BookVisibility): Promise<MyBookSummary> =>
  request(`/api/books/${id}/visibility`, { body: { visibility } });

export const deleteBook = (id: number): Promise<{ deleted: boolean }> =>
  request(`/api/books/${id}/delete`, { body: {} });
