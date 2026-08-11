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
  /** 리워드 광고로 밀린 하루를 지울 수 있는지 — 빠뜨린 날 존재 + 오늘 미사용을 서버가 판정해 준다. */
  debtWaiverAvailable: boolean;
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

/** 용서 지급 결과 — `timer`가 동봉돼 부채·버튼 노출이 재조회 없이 갱신된다. */
export interface WaiveResponse {
  waivedDate: string;
  waivedSeconds: number;
  timer: TimerState;
}

/**
 * 밀린 하루 지우기 — 리워드 광고 시청 완료 후 호출한다.
 *
 * <p>**지울 날짜를 보내지 않는다** — 서버가 잔여 부채 최대인 날을 고르므로 클라이언트에 조작 표면이 없다.
 * 빈 본문은 `request()`가 POST로 보내게 하는 스위치다(본문 유무로 메서드를 정한다).
 * 409=오늘 이미 사용 / 400=지울 날 없음. 둘 다 서버가 평문 메시지를 준다.
 */
export const waiveDebt = (): Promise<WaiveResponse> =>
  request('/api/miniapp/debt-waiver', { body: {} });

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

// ── 소셜 (search·follow·profile·block·report 컨트롤러의 record가 타입 단일 출처) ──
//
// 소셜 API는 대상 사용자를 **loginId(공개 @핸들)로만** 식별한다 — 서버가 전부
// `findByLoginId`로 대상을 찾는다. 그래서 login_id가 없는 계정(미니앱 신규 가입)은
// 대상이 될 수 없고, 자기 자신조차 책방을 열 수 없다(설계 §5-1 — Social이 안내로 처리).

/** `search.UserSearchResult` — 검색·팔로우 목록·차단 목록이 공유하는 사용자 한 줄. */
export interface UserRow {
  loginId: string;
  nickname: string;
  publicBookCount: number;
  following: boolean;
  self: boolean;
}

/** `SearchApiController.SearchResponse` — 추천(recommendations)은 미니앱이 아직 쓰지 않아 옮기지 않는다. */
export interface UserSearchResponse {
  q: string | null;
  results: UserRow[];
  /** 내 @핸들 — 온보딩 전(login_id=null) 계정은 null이다(§5-1). */
  myLoginId: string | null;
  rateLimited: boolean;
}

/** 서버는 2글자 미만이면 빈 결과를 준다(열거 방지) — 실패가 아니라 0건이다. */
export const searchUsers = (q: string): Promise<UserSearchResponse> => request('/api/search', { query: { q } });

export type FollowListType = 'followers' | 'following';

export interface FollowListResponse {
  type: FollowListType;
  users: UserRow[];
  myLoginId: string | null;
}

export const fetchFollowList = (type: FollowListType): Promise<FollowListResponse> =>
  request('/api/follow-list', { query: { type } });

/** 팔로우 상태는 낙관 갱신하지 않는다 — 서버가 준 following이 유일한 진실(레이트리밋·차단이면 안 바뀐다). */
export const follow = (loginId: string): Promise<{ following: boolean }> =>
  request('/api/follow', { body: { loginId } });

export const unfollow = (loginId: string): Promise<{ following: boolean }> =>
  request('/api/unfollow', { body: { loginId } });

export interface ProfileTagChip {
  label: string;
  clickable: boolean;
}

/** `ProfileApiController.BookSummary` — status는 enum이 아니라 한글 라벨이다(필터는 서버에 맡긴다). */
export interface ProfileBook {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  status: string;
  seconds: number;
  purchaseLink: string | null;
}

/** `ProfileApiController.ProfileResponse` — 제휴 서점 플래그는 미니앱이 안 써서 옮기지 않는다. */
export interface ProfileResponse {
  loginId: string;
  nickname: string;
  profileCharacterCode: string | null;
  followerCount: number;
  followingCount: number;
  following: boolean;
  self: boolean;
  personality: string | null;
  personalityTags: ProfileTagChip[];
  books: ProfileBook[];
}

/** 차단·ADMIN·없는 아이디는 모두 404 — 존재를 누설하지 않는 서버 계약을 그대로 받는다. */
export const fetchProfile = (loginId: string): Promise<ProfileResponse> =>
  request('/api/profile', { query: { loginId } });

/** status 생략 = 전체. 라벨→enum 역매핑을 클라이언트가 흉내내지 않으려고 필터를 서버에 맡긴다. */
export const fetchProfileBooks = (loginId: string, status?: BookStatus): Promise<{ books: ProfileBook[] }> =>
  request('/api/profile/books', { query: { loginId, status } });

export const fetchPersonalityTagBooks = (loginId: string, tag: string): Promise<{ books: ProfileBook[] }> =>
  request('/api/profile/personality-tag', { query: { loginId, tag } });

/** 차단하면 상대가 검색·목록에서 사라진다 — 이 목록이 미니앱의 유일한 차단 해제 경로다. */
export const fetchBlocks = (): Promise<{ blocked: UserRow[]; myLoginId: string | null }> => request('/api/blocks');

export const blockUser = (loginId: string): Promise<{ blocked: boolean }> =>
  request('/api/block', { body: { loginId } });

export const unblockUser = (loginId: string): Promise<{ blocked: boolean }> =>
  request('/api/unblock', { body: { loginId } });

/** 서버 `ReportReason` enum과 값이 어긋나면 조용히 OTHER로 접수된다 — 값·순서를 그대로 옮긴다. */
export const REPORT_REASONS = [
  { value: 'SPAM', label: '스팸/광고' },
  { value: 'HARASSMENT', label: '괴롭힘/욕설' },
  { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠' },
  { value: 'OTHER', label: '기타' },
] as const;

export type ReportReason = (typeof REPORT_REASONS)[number]['value'];

export const reportUser = (loginId: string, reason: ReportReason, detail: string): Promise<{ reported: boolean }> =>
  request('/api/report', { body: { loginId, reason, detail } });

// ── 독서 스토리 (`web/api/StoryApiController` + `story` record가 타입 단일 출처) ──
//
// 소셜 API와 달리 스토리는 loginId에 묶이지 않는다 — 피드가 **내 활성 스토리를 `mine` 필드로 따로**
// 실어 주므로, 핸들 없는 계정(미니앱 신규 가입)도 자기 스토리를 보고·삭제할 수 있다.
// 그래서 미니앱은 `/api/stories/of/{loginId}`를 쓰지 않는다(설계 §5-1 재확인).

/** `story.StoryCard` — 책 라벨은 표시 시점에 공개 책만 실린다(비공개 전환 시 문장만 남는다). */
export interface StoryCard {
  id: number;
  text: string;
  bgCode: string | null;
  bookTitle: string | null;
  bookCoverUrl: string | null;
  createdAt: string;
  viewed: boolean;
}

/** `story.AuthorStories` — 스트립의 링 하나. loginId는 내 그룹(mine)에서 null일 수 있다. */
export interface AuthorStories {
  loginId: string | null;
  nickname: string;
  profileCharacterCode: string | null;
  allViewed: boolean;
  stories: StoryCard[];
}

export interface StoryFeedResponse {
  mine: AuthorStories | null;
  groups: AuthorStories[];
}

export interface StoryViewerEntry {
  loginId: string;
  nickname: string;
  profileCharacterCode: string | null;
  viewedAt: string;
}

/**
 * 배경 팔레트 — 서버 `Story.BG_CODES`와 **값이 같아야** 400이 안 난다(닫힌 코드: 자유 hex 금지).
 * 색은 웹 `app.css`의 `.story-bg-*`와 같은 값이라 웹·미니앱이 같은 카드로 보인다.
 */
export const STORY_BG_CODES = [
  { code: 'paper', background: '#f6f1e7', color: '#2c2a24' },
  { code: 'night', background: '#1f2233', color: '#f2f2f6' },
  { code: 'forest', background: '#23402f', color: '#eef5ee' },
  { code: 'sunset', background: '#c96a4a', color: '#fff7ef' },
  { code: 'sea', background: '#2b5d73', color: '#eef7fa' },
  { code: 'plum', background: '#5a3b5e', color: '#f7eef8' },
] as const;

export const fetchStoryFeed = (): Promise<StoryFeedResponse> => request('/api/stories/feed');

/** bookId·bgCode는 선택이지만 null을 명시해 보낸다 — 서버 record가 세 필드를 다 받는다. */
export const createStory = (text: string, bookId: number | null, bgCode: string | null): Promise<StoryCard> =>
  request('/api/stories', { body: { text, bookId, bgCode } });

/** 없거나 남의 것이면 404 — 존재를 누설하지 않는 서버 계약(IDOR)을 그대로 받는다. */
export const deleteStory = (id: number): Promise<void> => request(`/api/stories/${id}`, { method: 'DELETE' });

/** 서버가 `@RequestBody`를 안 받으므로 본문 없이 POST한다. 멱등(중복 열람은 서버가 no-op). */
export const markStoryViewed = (id: number): Promise<void> => request(`/api/stories/${id}/view`, { method: 'POST' });

/** 작성자 본인만 — 남의 스토리면 404. 차단 관계 열람자는 서버가 이미 걸러 준다. */
export const fetchStoryViewers = (id: number): Promise<StoryViewerEntry[]> => request(`/api/stories/${id}/viewers`);
