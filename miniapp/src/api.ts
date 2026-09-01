import { cacheClear, cacheDrop } from './cache';
import { tossLogin } from './toss';

/**
 * 서버 API 클라이언트 — fetch + `Authorization: Bearer`.
 *
 * <p>타입은 `web/api/TossAuthApiController` · `DashboardApiController`의 record가 단일 출처다(설계 §2.5).
 */

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'booktimer.token';

/**
 * 브라우저 dev 목 모드 — `npm run dev:mock`(`.env.mock`의 `VITE_DEV_MOCK=1`)에서만 켜진다.
 *
 * <p>`import.meta.env.DEV`가 프로드 빌드에서 리터럴 `false`로 치환되므로 이 플래그를 쓰는 분기와
 * 그 안의 dynamic import가 통째로 잘려 나간다 — 목 픽스처는 배포 번들에 실리지 않는다.
 * 검증은 `deploy.sh`의 `__DEV_MOCK__` 부재 검사(음성 체크)가 맡는다.
 */
const DEV_MOCK = import.meta.env.DEV && import.meta.env.VITE_DEV_MOCK === '1';

/** 토큰 보관 — WebView의 localStorage. 401을 만나면 폐기하고 재로그인한다. */
export const token = {
  // 목 모드는 더미 토큰이 항상 있는 것으로 둔다 — 토스 SDK 없는 브라우저에서 로그인 브릿지를 건너뛴다.
  get: (): string | null => (DEV_MOCK ? 'dev-mock-token' : localStorage.getItem(TOKEN_KEY)),
  set: (value: string): void => localStorage.setItem(TOKEN_KEY, value),
  clear: (): void => {
    localStorage.removeItem(TOKEN_KEY);
    // 로그아웃·401·탈퇴가 전부 이 문을 지난다 — 남의 계정 데이터가 다음 로그인 첫 렌더에 새면 안 된다.
    cacheClear();
  },
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

/**
 * 요청이 서버에 닿지도 못함 — 비행기모드·터널·서버 다운. `fetch`가 던지는 건 영문 `TypeError`
 * ("Failed to fetch")라, 화면들이 `e.message`를 그대로 띄우는 계약상 그게 사용자에게 노출됐다.
 * 이름을 따로 둬 401(`UnauthorizedError`) 재로그인 분기와 섞이지 않게 한다.
 */
export class NetworkError extends Error {
  constructor() {
    super('네트워크 연결을 확인해 주세요');
    this.name = 'NetworkError';
  }
}

/** 호출 옵션 — 셋 다 선택. method 생략 시 body 유무로 GET/POST를 정한다(기존 호출부 계약). */
export interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined>;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // 목 모드에서는 서버에 나가지 않는다 — dynamic import라 프로드 번들엔 이 모듈이 들어가지 않는다.
  if (DEV_MOCK) return (await import('./dev-mock')).mockRequest<T>(path, options);

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

  // 연결 실패는 여기서만 잡는다 — 응답을 받은 뒤의 실패(상태코드)는 아래 계약이 그대로 처리한다.
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}${search === '' ? '' : `?${search}`}`, {
      method: options.method ?? (body === undefined ? 'GET' : 'POST'),
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new NetworkError();
  }

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

/**
 * 회원 탈퇴 — fresh 인가코드로 신원을 다시 증명한 뒤, 서버가 계정과 전 기록을 지운다.
 *
 * <p>미니앱 전용 계정에는 이 경로밖에 없다: 비밀번호가 없어 웹 로그인이 불가하고, 핸들(login_id)이
 * null일 수 있어 웹 소셜 탈퇴의 @핸들 재입력도 성립하지 않는다. 확인 수단이 인증 3종과 같은
 * {@link tossLogin}인 이유가 그것이다 — 토스 앱 본인 인증을 통과한 사람만 자기 계정을 지운다.
 *
 * <p><b>성공했을 때만 토큰을 버린다</b> — 로그아웃과 반대 방향이다. 400(인가코드 만료)·403(신원 불일치)이면
 * 계정이 그대로 살아 있으므로, 여기서 토큰까지 버리면 지우지도 못한 채 로그인 화면으로 쫓겨난다.
 * 서버가 이 두 실패에 401을 쓰지 않는 것도 같은 이유다(401은 {@link request}가 토큰을 지우는 신호다).
 */
export async function deleteAccount(): Promise<void> {
  const { authorizationCode, referrer } = await tossLogin();
  await request<void>('/api/miniapp/delete-account', { body: { authorizationCode, referrer } });
  token.clear(); // 계정이 사라진 뒤에만 — 실패는 위에서 던져 여기 오지 않는다
}

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
  /** 표지 주소 — 손으로 넣은 책은 `null`이라 첫 글자 자리 표지로 떨어진다(검색 등록만 채운다). */
  coverUrl: string | null;
  author: string | null;
  /**
   * 공개 책인가 — 홈에서 곧장 여는 작성 화면의 가시성 캡션 재료다(게이트가 아니다: 비공개 책에도
   * 여백을 쓸 수 있다). 옛 서버는 안 보내므로 `undefined`이고, 그때는 공개로 간주한다({@link visibilityNotice}).
   */
  isPublic?: boolean;
}

export interface ContributionDay {
  date: string | null;
  totalSeconds: number;
  level: number;
  manual: boolean;
}

export interface ContributionGraph {
  /**
   * `weeks[0]` = 최신 주(왼쪽). 서버가 뒤집어 보낸다(`ContributionGraphBuilder` 참조) —
   * oldest-first로 가정하지 말 것. 최근 N주는 `slice(0, N)`이고 monthLabels도 이 순서 기준이다.
   */
  weeks: ContributionDay[][];
  monthLabels: { weekIndex: number; label: string }[];
  totalSeconds: number;
  activeDays: number;
  currentStreak: number;
  // 식물 성장 단계는 2026-08-29에 폐기했다 — 서버 응답에도 더 이상 없다.
}

export interface TimerState {
  remainingSeconds: number;
  carriedDebtSeconds: number;
  todayGoalSeconds: number;
  /**
   * 오늘 읽은 초 — <b>완료 세션 합</b>이고 상한이 없다. 측정 중 몫은 화면이 `activeStartedAt`으로 매초
   * 더한다(공부 모드 `todaySeconds`와 같은 분업).
   *
   * <p>`remainingSeconds`에서 역산하지 <b>않는다</b>: 서버 부채는 `max(0, 목표 − 읽은 양)`이라 0에서
   * 바닥을 쳐, 역산한 표시값이 목표에서 천장을 친다. 그래서 목표를 넘겨 읽다가 중지하면 화면이 정확히
   * 목표값으로 되돌아갔다(초과분은 과거 날 상환에 소비돼 응답에 흔적이 없어 역산이 불가능하다).
   */
  todayReadSeconds: number;
  carryover: boolean;
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  activeBookTotalSeconds: number;
  /**
   * 지금 측정 중인 책 — 홈의 「읽는 중」 카드가 **표지를 그리는 재료**다.
   *
   * <p>`null`은 책 없이 측정 중이거나 측정 중이 아니라는 뜻이고, `undefined`는 이 필드를 아직 안 주는
   * 옛 서버다(둘 다 카드는 「책 없이」로 선다 — 배포 순서에 화면이 의존하지 않는다).
   *
   * <p>`activeBookTitle`과 겹쳐 보여도 지우지 않는다: 제목 한 줄은 웹 SSR이 쓰고 있다.
   */
  activeBook?: BookOption | null;
  readingBooks: BookOption[];
  finishedBooks: BookOption[];
  recentBookId: number | null;
  /** 리워드 광고로 밀린 하루를 지울 수 있는지 — 지울 빠뜨린 날이 남았는지를 서버가 판정해 준다(횟수 제한 없음). */
  debtWaiverAvailable: boolean;
}

/**
 * 공부 모드 상태 — 독서({@link TimerState})와 <b>다른 타입</b>인 것이 이 기능의 요구 그 자체다.
 * 목표·부채·책이 없어 실을 것이 셋뿐이고, 서버 원장도 별도 테이블(`study_session`)이라 섞일 길이 없다.
 *
 * <p>`todaySeconds`는 <b>완료 세션 합</b>이다 — 측정 중 몫은 화면이 `activeStartedAt`으로 매초 더한다.
 */
export interface StudyState {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  todaySeconds: number;
  /**
   * 공부 하루 목표(초) — {@code 0}이면 목표 없음. <b>독서 목표와 완전 별개</b>이고 이월·부채가 없다.
   *
   * <p>선택 필드인 이유는 옛 서버 방어다(2차 이전 서버는 이 필드를 안 준다) — 소비처는 {@code ?? 0}으로
   * 「목표 없음」 화면(1차와 같은 렌더)으로 떨어진다.
   */
  goalSeconds?: number;
}

/** 공부 기록이 없는 상태 — 옛 서버(이 필드를 안 주는)와 붙었을 때의 폴백이기도 하다. */
export const IDLE_STUDY: StudyState = {
  hasActiveSession: false,
  activeStartedAt: null,
  todaySeconds: 0,
  goalSeconds: 0,
};

// 서버는 작가 격언(`quotes`)도 실어 보내지만 미니앱은 쓰지 않는다 — 웹 대시보드 전용이라 필드를 받지 않는다.
export interface DashboardResponse extends TimerState {
  nickname: string;
  loginId: string | null;
  /**
   * 버리고 간 옛 @아이디 — `null`이면 평생 1회 변경권이 아직 남아 있다. 설정 화면이 이 한 필드로
   * 「아이디 바꾸기」 버튼을 켜고 끄고, 소진 표시에 옛 값을 적는다(옛 서버가 주는 `undefined`도 미소진).
   */
  previousLoginId: string | null;
  profileCharacterCode: string | null;
  wantToReadBooks: BookOption[];
  graph: ContributionGraph;
  emailVerified: boolean;
  /** 공부 모드 상태 — `undefined`는 이 필드를 아직 안 주는 옛 서버다(화면은 {@link IDLE_STUDY}로 떨어진다). */
  study?: StudyState;
}

export interface StopResponse {
  sessionId: number;
  untagged: boolean;
  /**
   * 이번 종료가 이 사용자의 첫 완료 기록인지 — 홈이 축하 배너 + 잔디 하이라이트를 띄우는 스위치.
   * 서버가 완료 세션 수가 '정확히 1'이 된 순간에만 참으로 준다(수동 기록도 완료 세션이라 함께 세어진다).
   */
  firstCompletedSession: boolean;
  timer: TimerState;
  graph: ContributionGraph;
}

export const fetchDashboard = (): Promise<DashboardResponse> => request('/api/dashboard');

/** `session.BookRead` — 그날 이 책만 읽은 시간. 같은 책의 여러 세션은 서버가 한 줄로 합쳐 준다. */
export interface BookRead {
  title: string;
  coverUrl: string | null;
  seconds: number;
}

/** `session.DailyReadingRecord` — 세션 "횟수"는 서버가 일부러 안 준다(1분짜리 측정까지 세어 숫자만 부푼다). */
export interface DailyRecord {
  /** `YYYY-MM-DD`(유저 타임존 기준). */
  date: string;
  totalSeconds: number;
  /**
   * 그날 읽은 책 — 제목별 합산, <b>오래 읽은 순</b>(서버가 정한 순서다. 화면이 다시 정렬하지 않는다).
   *
   * <p>`totalSeconds`는 이 목록의 합보다 <b>클 수 있다</b> — 책을 안 고르고 잰 세션의 시간은 총합에만
   * 들어가고 여기엔 안 잡힌다. 그 차액은 화면이 「책 안 고른 기록」 줄로 밝힌다(`bookRows`).
   */
  books: BookRead[];
  manuallyFilled: boolean;
}

/** `session.MonthlyReadingSection` — 최신 월 먼저, 각 달 안에서도 최신 일 먼저(서버가 그 순서로 준다). */
export interface MonthlySection {
  /** `YYYY-MM` — Jackson이 `YearMonth`를 이 모양으로 직렬화한다. */
  month: string;
  totalSeconds: number;
  days: DailyRecord[];
}

/**
 * 날짜별 독서 기록 — 기록 탭이 잔디 아래에 그린다.
 *
 * <p>`/api/history`는 웹 history 섬이 쓰던 그 엔드포인트다(`HistoryApiController`). Bearer 토큰이 붙으면
 * 미니앱 시큐리티 체인으로 라우팅되므로 서버는 한 줄도 고치지 않는다(`SecurityConfig.isMiniappApiRequest`).
 * 같이 오는 `nickname`·`graph`·`weeklyShortfall`은 미니앱이 안 써서 타입에 옮기지 않는다.
 */
export const fetchHistory = (): Promise<{ months: MonthlySection[] }> => request('/api/history');

// ── 홈 피드 (`web/api/HomeFeedApiController`의 record가 타입 단일 출처) ────────

/**
 * `HomeFeedApiController.SocialEvent` — 팔로우한 사람의 PUBLIC 책 활동 한 줄.
 *
 * <p>`STORY`는 **사람+책 단위로 묶인** 여백 이벤트다(서버가 묶는다) — 세 필드는 그 행에만 실린다.
 * `STARTED`·`FINISHED`는 bookId·excerpt가 null이고 count가 0이다.
 */
export interface SocialEvent {
  loginId: string;
  nickname: string;
  bookTitle: string;
  type: 'STARTED' | 'FINISHED' | 'STORY';
  occurredAt: string;
  /** STORY 행만 — 탭하면 그 책의 여백으로 점프한다. */
  bookId: number | null;
  /** STORY 행만 — 그 묶음 **최신** 글의 발췌(서버가 80자로 잘라 준다). */
  excerpt: string | null;
  /** 그 묶음의 글 수(1이면 단수 문구, 2 이상이면 「글 N개」). STORY가 아니면 0. */
  count: number;
  /** 그 책의 표지 주소 — 세 종류 모두 채워 온다. 없으면 null(첫 글자 자리 표지로 떨어진다). */
  coverUrl: string | null;
}

/** `HomeFeedApiController.NewsItem` */
export interface NewsItem {
  title: string;
  link: string;
  publishedAt: string;
  /** 내 어느 책의 기사인지 보여주는 라벨. */
  bookTitle: string;
  /**
   * 매체명. 수집원이 구글 뉴스 RSS라 `link`가 전부 구글 리다이렉트라서 호스트명으로는 파생할 수 없다
   * — 서버가 RSS의 `<source>` 엘리먼트에서 뽑아 준다. 옛 행은 null일 수 있어 클라이언트가 폴백을 둔다.
   */
  source: string | null;
}

/**
 * 홈 피드 박스 데이터 — 대시보드에 동봉하지 않고 따로 받는다: 히어로(타이머) 렌더가 피드 쿼리에
 * 인질로 잡히지 않고, 계약이 독립이라 서버·미니앱 배포가 묶이지 않는다(`Social`·`Library`의 자체 fetch 선례).
 *
 * <p>`newsEnabled`는 **「책 뉴스」 탭 노출 스위치**다 — 꺼져 있으면 false + `news`가 빈 배열로 와서
 * 미니앱이 탭 머리 자체를 안 그린다(죽은 탭 금지). 구글 뉴스 RSS는 공식 API가 아니라 형식이 바뀔 수
 * 있어 서버 `booktimer.news.enabled`가 킬스위치로 남아 있고, **게이트가 서버에 있어 껐다 켜는 데
 * 미니앱 재배포가 필요 없다**(`VITE_*` 빌드타임 게이트보다 나은 자리).
 */
/**
 * `HomeFeedApiController.ReaderStatus` — 「함께 읽는 사람」 한 줄.
 *
 * <p><b>이 목록의 불변식은 한 줄이다: 공개 책의 독서만 본다.</b> 그래서 새로 공개되는 것이 없고,
 * 「내 상태를 누구에게 보일까」를 새로 묻는 설정도 없다 — 책마다 이미 있는 공개 스위치가 그 설정이다.
 * 비공개로 읽는 중인 사람은 세 필드가 전부 null로 와서 화면에서 「공개된 기록이 없어요」가 된다.
 */
export interface ReaderStatus {
  loginId: string;
  nickname: string;
  /** 서로 팔로우 중인가 — 「서로」 배지 + 정렬 우선. 관계 모델은 단방향 그대로다. */
  mutual: boolean;
  /** 지금 읽는 중인 **공개** 책 제목. 비공개 책·미태깅 세션·미독서면 null. */
  readingBookTitle: string | null;
  /** 그 세션 시작 시각. `readingBookTitle`과 항상 함께 채워지거나 함께 null. */
  readingSince: string | null;
  /** 마지막 **공개** 독서 시각. 공개 기록이 없으면 null. */
  lastReadAt: string | null;
  /** 그때 읽던 책 제목. `lastReadAt`과 **같은 세션**에서 온다(서버가 한 행으로 뽑는다). */
  lastReadBookTitle: string | null;
}

export interface HomeFeedResponse {
  social: SocialEvent[];
  newsEnabled: boolean;
  news: NewsItem[];
  /**
   * 「함께 읽는 사람」 목록(상한 30명, 서버가 정렬해서 준다 — 읽는 중 → 맞팔 → 최근순).
   *
   * <p>서버가 이 필드를 아직 안 내려주는 구버전과도 붙을 수 있어야 해서 미니앱은 `?? []`로 읽는다 —
   * 서버·미니앱 배포 순서에 화면이 의존하지 않는다.
   */
  readers: ReaderStatus[];
}

export const fetchHomeFeed = (): Promise<HomeFeedResponse> => request('/api/home-feed');

/** bookId를 안 주면 책 미지정 세션으로 시작한다(종료 후 태깅). */
export const startSession = (bookId: number | null): Promise<TimerState> =>
  request('/api/sessions/start', { body: { bookId } });

export const stopSession = (): Promise<StopResponse> => request('/api/sessions/stop', { body: {} });

export const tagBook = (sessionId: number, bookId: number): Promise<{ sessionId: number; bookTitle: string }> =>
  request(`/api/sessions/${sessionId}/tag-book`, { body: { bookId } });

/**
 * 진행 중 세션의 측정 대상 교체 — `bookId`가 `null`이면 「책 없이」로 되돌린다. 측정은 멈추지 않고,
 * 지금까지 잰 시간이 통째로 새 책에 붙는다.
 *
 * <p>위 `tagBook`과 달리 **세션 id를 안 보낸다** — 서버가 「내 진행 중 세션」을 직접 찾으므로 요청에
 * 세션 좌표가 없다. 진행 중 측정이 없으면 409다(방금 끝난 뒤 도착한 요청도 여기로 떨어진다).
 */
export const changeActiveBook = (bookId: number | null): Promise<TimerState> =>
  request('/api/sessions/active/book', { body: { bookId } });

/**
 * 공부 측정 시작·종료 — 독서와 <b>다른 엔드포인트</b>다(원장이 다르므로 문도 다르다).
 * 409 계약은 독서와 같다: 중복 시작 / 무세션 종료. 독서 측정 중에도 시작은 409다(이중 계측 금지).
 */
export const startStudy = (): Promise<StudyState> => request('/api/study/start', { body: {} });

export const stopStudy = (): Promise<StudyState> => request('/api/study/stop', { body: {} });

export const setGoal = (dailyIncrementSeconds: number): Promise<void> =>
  request('/api/miniapp/goal', { body: { dailyIncrementSeconds } });

/**
 * 공부 하루 목표 설정 — <b>독서와 다른 문</b>이다(원장이 다르므로 문도 다르다). 응답이 갱신된
 * {@link StudyState}라 저장 직후 화면이 재조회 없이도 새 목표를 안다.
 */
export const setStudyGoal = (dailyGoalSeconds: number): Promise<StudyState> =>
  request('/api/study/goal', { body: { dailyGoalSeconds } });

/**
 * 공부 일정 달력의 하루 — 자동 정보(측정)와 원장(판정)이 <b>한 칸에 나란히</b> 온다.
 *
 * <p>`kept`가 `null`이면 <b>무기록</b>이다(서버엔 행 자체가 없다). 이 3상태가 화면 순환의 전부라
 * 다른 필드로 상태를 파생하지 않는다 — `studiedSeconds > 0`은 「점」일 뿐 판정이 아니다.
 */
export interface StudyCalendarDay {
  /** `YYYY-MM-DD`(유저 타임존의 달력 날짜). */
  date: string;
  studiedSeconds: number;
  kept: boolean | null;
}

/** `days`는 <b>데이터 있는 날만</b> 날짜순으로 온다(희소) — 화면이 빈 칸을 채운다. */
export interface StudyCalendarResponse {
  goalSeconds: number;
  days: StudyCalendarDay[];
}

/**
 * 그 달의 달력을 받는다.
 *
 * <p>달을 경로에 이어 붙이지 않고 `query`로 넘기는 이유: 목 모드 라우터가 <b>경로 문자열 그대로</b>
 * 정규식에 물리므로, `?month=…`를 경로에 넣으면 목이 그 경로를 못 찾는다(404). 실 요청에서는
 * {@link request}가 같은 쿼리스트링을 만들어 준다.
 *
 * @param month `YYYY-MM`
 */
export const fetchStudyCalendar = (month: string): Promise<StudyCalendarResponse> =>
  request('/api/study/calendar', { query: { month } });

/**
 * 그날의 일정 판정을 남긴다 — `kept`가 `null`이면 무기록으로 되돌린다(3상태 순환의 마지막 칸).
 *
 * <p>미래 날짜는 400이다(화면도 흐리게 눌러 막지만, 서버가 유저 타임존으로 다시 판정한다).
 */
export const setStudyCheck = (
  date: string,
  kept: boolean | null,
): Promise<{ date: string; kept: boolean | null }> => request('/api/study/check', { body: { date, kept } });

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
  /** 이 책 여백에 남긴 글 수 — 공개 전환 확인 시트의 재료다. 옛 서버는 안 보낸다(`undefined` = 0 취급). */
  storyCount?: number;
}

/** `BookApiController.SearchRow` — `owned`는 서버가 계산해 주는 UI 표시용이라 추가 요청에 되돌려 보내지 않는다. */
export interface SearchRow {
  title: string;
  /**
   * 알라딘 원문 — <b>담기가 이 값을 서버로 되돌려 저장한다</b>. 목록에 그릴 땐 {@link authorShort}를
   * 쓴다: 여길 축약본으로 바꾸면 책 저자가 「미겔 데 세르반떼스 외 32명」으로 영구 저장된다.
   */
  author: string | null;
  /**
   * 목록 표시용 한 줄(「이름」/「이름 외 N명」) — 서버가 대표 글쓴이로 줄여 준다.
   *
   * <p>선택 필드인 이유는 <b>배포 순서</b>다: 미니앱이 서버보다 먼저 나가면 옛 서버는 이 필드를 안 준다.
   * 그때 저자 줄이 빈칸이 되지 않게 화면이 {@link author}로 떨어진다.
   */
  authorShort?: string | null;
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

/**
 * 알라딘 1페이지. 서버는 외부 API 장애도 빈 결과로 격리하므로 실패와 0건이 같은 모양으로 온다.
 *
 * <p>`marginCounts`는 isbn13 → 함께 걸린 글 수(검색 행의 「여백 N」 배지). **0인 책은 키 자체가 없다** —
 * 화면이 0 배지를 안 그리는 근거가 응답에 그대로 있다. 옛 서버는 맵 자체를 안 보낸다(`undefined`).
 */
export const searchBooks = (q: string): Promise<{ results: SearchRow[]; marginCounts?: Record<string, number> }> =>
  request('/api/books/search', { query: { q } });

/**
 * 「책 추가」 화면의 추천 — 제목·근거를 <b>서버가 문장으로</b> 만들어 준다.
 *
 * <p>어느 전략(내 저자 / 베스트셀러)으로 뽑혔는지는 알려주지 않는다. 화면은 라벨을 그대로 그리므로
 * 서버가 전략을 늘려도 여기는 안 바뀐다. 뽑을 것이 없으면 `title`이 null — 그러면 카드를 그리지 않는다.
 */
export interface Recommendation {
  title: string | null;
  reason: string | null;
  results: SearchRow[];
}

export const fetchRecommendation = (): Promise<Recommendation> => request('/api/books/recommend');

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

/**
 * `ExploreApiController.ExploreBookDto` — 둘러보기 카드에 세우는 책.
 *
 * ⚠️ 누적 시간·마지막으로 읽은 시각이 <b>일부러 없다</b>. 서버가 그것으로 정렬은 하지만 응답에 담지
 * 않는다 — 「언제 읽었는가」는 낯선 사람에게 보여줄 것이 아니라는 결정이다(2026-08-20). 필드를 늘리기
 * 전에 그 결정을 먼저 보라.
 */
export interface ExploreBook {
  title: string;
  coverUrl: string | null;
}

/** `ExploreApiController.ExploreUserDto` — 사람 한 줄 + 그 사람의 공개 책(겹친 책이 앞, 최대 4권). */
export interface ExploreUser extends UserRow {
  books: ExploreBook[];
}

/** `ExploreApiController.ExploreResponse` — 한도에 닿으면 빈 목록 + `rateLimited`(조용한 0건과 구분). */
export interface ExploreResponse {
  users: ExploreUser[];
  rateLimited: boolean;
}

/**
 * 둘러보기 — 검색어 없이 볼 것을 받는다. 서버가 <b>매 호출 섞어</b> 주므로 화면에 들어올 때마다 얼굴이 바뀐다
 * (그래서 「다른 사람 보기」 같은 새로고침 손잡이를 두지 않는다 — 재진입이 곧 새로고침이다).
 */
export const fetchExplore = (): Promise<ExploreResponse> => request('/api/explore');

/** 서버 `User.LOGIN_ID_PATTERN`과 같은 규칙 — 정규화(소문자) 후 3~20자 [a-z0-9_]. */
const HANDLE_PATTERN = /^[a-z0-9_]{3,20}$/;

/**
 * 핸들 형식 프리검증 — 통과면 `null`, 아니면 사용자에게 보여줄 안내 문구(순수 함수).
 *
 * <p>**형식만** 본다. 예약어·중복은 서버(400/409 평문)가 유일한 권위다 — 클라이언트가 규칙을 복제하면
 * 서버와 어긋난 순간 조용히 거짓말을 한다. 대문자는 오류가 아니다(서버가 소문자로 정규화해 저장).
 */
export function validateHandleFormat(raw: string): string | null {
  return HANDLE_PATTERN.test(raw.trim().toLowerCase())
    ? null
    : '영문·숫자·밑줄(_) 3~20자로 지어 주세요.';
}

/**
 * 아이디 바꾸기 프리검증 — 형식에 더해 <b>지금 아이디와 같은지</b>까지 본다(순수 함수).
 *
 * <p>변경은 평생 1번뿐이라 "같은 값"으로 왕복하는 것 자체가 손해다 — 서버 400을 기다리지 않고
 * 그 자리에서 막는다. 예약어·중복은 여전히 서버가 유일한 권위다({@link validateHandleFormat}과 같은 이유).
 *
 * @param current 지금 쓰는 핸들(서버가 정규화해 준 소문자 값)
 */
export function validateHandleChange(raw: string, current: string): string | null {
  const format = validateHandleFormat(raw);
  if (format !== null) return format;
  return raw.trim().toLowerCase() === current ? '지금 아이디와 같아요. 다른 아이디를 지어 주세요.' : null;
}

/** `POST /api/miniapp/handle` — 성공하면 서버가 정규화한 핸들. 400(형식·예약어)·409(중복·이미 있음)는 ApiError. */
export const createHandle = (loginId: string): Promise<{ loginId: string }> =>
  request('/api/miniapp/handle', { body: { loginId } });

/**
 * `POST /api/miniapp/handle/change` — 아이디를 평생 1번 바꾼다. 성공하면 서버가 정규화한 새 핸들.
 * 400(형식·예약어·지금과 동일)·409(소진·중복)는 ApiError의 평문 그대로 시트에 띄운다.
 * 만들기와 <b>경로가 다르다</b> — 같은 경로면 서버가 생성 의미로 읽어 문구가 뭉개진다.
 */
export const changeHandle = (loginId: string): Promise<{ loginId: string }> =>
  request('/api/miniapp/handle/change', { body: { loginId } });

/** 서버 `User.NICKNAME_MAX_LENGTH`와 같은 값 — 갈라지면 프리검증이 조용히 거짓말한다(통과시켰는데 서버가 400). */
export const NICKNAME_MAX_LENGTH = 30;

/**
 * 닉네임 형식 프리검증 — 통과면 `null`, 아니면 사용자에게 보여줄 안내 문구(순수 함수).
 *
 * <p>규칙은 길이뿐이다(공백만 불가 + 상한). 핸들과 달리 문자 종류 제한이 없어 이모지·공백 섞인 이름도
 * 정상이다 — 서버가 그렇게 저장한다. 앞뒤 공백은 서버처럼 떼고 센다(전송도 trim한 값으로 한다).
 */
export function validateNicknameFormat(raw: string): string | null {
  const trimmed = raw.trim();
  return trimmed === '' || trimmed.length > NICKNAME_MAX_LENGTH
    ? `1~${NICKNAME_MAX_LENGTH}자로 입력해 주세요.`
    : null;
}

/** `POST /api/miniapp/nickname` — 성공하면 서버가 저장한 닉네임. 400(공백·상한 초과)은 ApiError 평문. */
export const updateNickname = (nickname: string): Promise<{ nickname: string }> =>
  request('/api/miniapp/nickname', { body: { nickname } });

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
  /**
   * 이 책의 여백에 마지막으로 글이 달린 시각 — 격자 발광의 재료다.
   *
   * <p>글이 없는 책은 null이다. **팔로우와 무관하게** 실린다(2026-08-22 — 여백 목록 자체가 공개 책이면
   * 누구에게나 열려서, 발광만 막으면 「글은 보이는데 격자는 안 빛나는」 어긋남이 된다).
   * 24시간 창 판정은 클라가 한다({@link import('./screens/Story').hasFreshStory}) — 서버는 원시 사실만 준다.
   * 성향 태그 드릴다운(`/api/profile/personality-tag`)은 항상 null이다(그 임시 목록엔 발광이 없다).
   */
  lastStoryAt: string | null;
}

/** `ProfileApiController.UserBrief` — 공통 친구 줄에 이름으로 적히는 사람. */
export interface UserBrief {
  loginId: string;
  nickname: string;
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
  /**
   * 공통 친구 — 내가 팔로우하는 사람 중 이 사람도 팔로우하는 사람. <b>이름은 2명까지만</b> 오고 나머지
   * 수는 {@link mutualFollowerCount}에 있다. 옛 서버는 안 보낸다(`undefined` = 줄을 안 그린다).
   */
  mutualFollowers?: UserBrief[];
  /** 공통 친구 <b>전체</b> 수 — 「외 N명」이 여기서 나온다(받은 이름 개수로 세면 항상 「외 0명」이 된다). */
  mutualFollowerCount?: number;
  /** 이 사람이 <b>나를</b> 팔로우하는가 — {@link ProfileResponse.following}과 방향이 반대다. */
  followsMe?: boolean;
}

/** 차단·ADMIN·없는 아이디는 모두 404 — 존재를 누설하지 않는 서버 계약을 그대로 받는다. */
export const fetchProfile = (loginId: string): Promise<ProfileResponse> =>
  request('/api/profile', { query: { loginId } });

/** status 생략 = 전체. 라벨→enum 역매핑을 클라이언트가 흉내내지 않으려고 필터를 서버에 맡긴다. */
export const fetchProfileBooks = (loginId: string, status?: BookStatus): Promise<{ books: ProfileBook[] }> =>
  request('/api/profile/books', { query: { loginId, status } });

export const fetchPersonalityTagBooks = (loginId: string, tag: string): Promise<{ books: ProfileBook[] }> =>
  request('/api/profile/personality-tag', { query: { loginId, tag } });

// ── 성향(책BTI) 추출 — 광고 관문 (`web/api/PersonalityApiController`의 record가 타입 단일 출처) ──

/**
 * 광고를 <b>보여주기 전에</b> 알아야 하는 것들. 잔여는 광고 경로 천장(하루 총량) 기준이다 —
 * 웹의 무광고 3회 잔여와는 다른 값이고, 미니앱은 광고 경로만 쓰므로 이것 하나만 받는다.
 */
export interface PersonalityStatus {
  coldStart: boolean;
  hasSelected: boolean;
  adRefreshRemaining: number;
  adRefreshLimit: number;
  /**
   * 분석 히스토리(최신순, 최대 3) — 보관함이 옛 서술과 새 서술을 나란히 놓는 재료.
   * 옛 서버(필드 추가 전)는 이걸 안 주므로 화면이 `?? []`로 접는다(손잡이 숨김 = fail-closed).
   */
  entries?: PersonalityEntry[];
}

/** 서버 `EntryDto` 전체 — 보관함이 서술·시각 라벨·stale까지 그린다(관문만 있던 시절의 3필드 축소를 해제). */
export interface PersonalityEntry {
  id: number;
  narrative: string;
  generatedAt: string | null;
  /** 서버가 **사용자 타임존으로** 포맷한 "yyyy-MM-dd HH:mm" — 클라가 포맷을 복제하지 않는다. */
  generatedAtLabel: string;
  selected: boolean;
  /** 이 분석 이후 책장이 바뀌었는가 — "책이 안 바뀌면 문구도 안 바뀐다"를 카드에서 설명해 준다. */
  stale: boolean;
}

export interface PersonalityMutation {
  view: {
    state: 'READY' | 'COLD_START' | 'FALLBACK';
    narrative: string | null;
    entries: PersonalityEntry[];
  };
  refreshRemaining: number;
  refreshLimit: number;
}

/**
 * 관문 사전 판정 — <b>부작용이 없는 유일한 성향 GET</b>이다. 웹이 쓰는 `GET /api/personality`는
 * 히스토리가 비면 첫 분석을 LLM으로 만들어 버려(=광고 없이 공짜 분석) 관문을 무력화한다.
 */
export const fetchPersonalityStatus = (): Promise<PersonalityStatus> => request('/api/personality/status');

/** 광고 경로 전용 — 웹 `/refresh`(천장 3)가 아니라 `/ad-refresh`(천장 = 하루 총량)를 부른다. */
export const adRefreshPersonality = (): Promise<PersonalityMutation> =>
  request('/api/personality/ad-refresh', { method: 'POST' });

/** 대표 승격 — 서버 재분석은 후보만 추가하므로, 미니앱은 최신 행을 이걸로 대표에 올린다. */
export const selectPersonality = (id: number): Promise<PersonalityMutation> =>
  request(`/api/personality/select/${id}`, { method: 'POST' });

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

// ── 여백 (`web/api/StoryApiController` + `story` record가 타입 단일 출처) ──
//
// 여백은 **책에 딸린 자리**다(2026-08-16 재설계) — 글은 책 하나에 귀속되고, 목록은
// 「누구의(loginId) + 어느 책(bookId)」 두 축으로만 열린다. 그래서 소셜 API처럼 loginId가 필요하고,
// 핸들 없는 계정에는 작성 진입점이 없다(그들의 글은 예전에도 아무에게도 안 보였다 — 설계 §0 비목표).
// URL·타입 이름이 `story`로 남은 것은 서버 경로가 `/api/stories`이기 때문(#814 결정).

/** `story.MarginEntry` — 여백에 남긴 글 한 장. 책 라벨은 응답 헤더에 한 번만 실린다. */
export interface MarginEntry {
  id: number;
  /** 내 주석 — **언제나 있다**(인용만 남기는 글은 서버가 거부한다). 카드의 본문이다. */
  text: string;
  /**
   * 책에서 옮긴 문장 — 인용 없이 남긴 글(옛 글 포함)이면 `null`이고, 그러면 카드가 2026-08-20 이전과
   * 똑같이 그려진다. 빈 문자열은 오지 않는다(서버가 null로 떨어뜨린다).
   */
  quote: string | null;
  bgCode: string | null;
  createdAt: string;
  /** 이 글에 달린 좋아요 수. 누가 눌렀는지는 여기 오지 않는다 — 명단은 눌러야 여는 별도 조회다. */
  likeCount: number;
  /** 내가 눌렀는가. **자기 글도 true가 될 수 있다**(자기 좋아요 허용 — 2026-08-20). */
  liked: boolean;
  /**
   * 「함께 걸림」인가 — 이 글이 **같은 책을 보는 누구에게나** 열려 있는가(2026-08-22 책축 개방).
   *
   * <p>선택 필드인 이유는 **배포 순서**다: 미니앱이 서버보다 먼저 나가면 옛 서버는 이 필드를 안 준다.
   * 그때 `undefined`는 **꺼짐으로 읽는다** — 안 걸린 글을 걸렸다고 말하는 쪽이 더 위험한 거짓말이다.
   *
   * <p><b>이 값은 노출 권한이 아니다</b>(2026-08-22 팔로우 축 제거): 노출 = 책 PUBLIC, 그게 전부다.
   * shared가 정하는 것은 **책축 목록에 실려 발견되는가**뿐이고, 안 올린 글도 공개 책이면 읽힌다.
   */
  shared?: boolean;
}

/** `story.MarginBook` — 여백이 열린 책. 비공개 책은 **주인에게만** 온다(남에게는 404). */
export interface MarginBook {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  /** 비공개 책이면 false — 가시성 캡션의 재료다. 옛 서버는 안 보낸다(`undefined` = 공개로 간주). */
  isPublic?: boolean;
  /**
   * 책축(「모두」 탭)의 좌표 — `null`이면 그 책엔 책축 자체가 없다(수동 등록 등 isbn 없는 책).
   * 옛 서버는 안 보낸다(`undefined`) — 그래서 판정은 언제나 `!= null`로 한다(둘을 한 번에 건진다).
   */
  isbn13?: string | null;
}

/**
 * `story.MarginResponse` — **자기완결**이다: 책 라벨·주인·관계가 함께 실려 화면이 다른 요청 없이 그려진다
 * (진입로가 둘이라 그렇다 — 책방 격자에서 오면 클라가 책을 알지만, 홈 소식에서 점프하면 모른다).
 *
 * <p>`entries`는 **공개 책이면 누구에게나** 실린다(2026-08-22 팔로우 축 제거). 비공개 책은 주인
 * 아니면 응답 자체가 404다. 서버가 함께 주던 `following`은 이 변경으로 아무도 안 읽게 돼 뺐다.
 */
export interface MarginResponse {
  book: MarginBook;
  ownerNickname: string;
  self: boolean;
  entries: MarginEntry[];
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

/**
 * 책 하나의 여백 — 차단·ADMIN·미존재·남의 책 id(IDOR)·PRIVATE 책은 모두 404다(존재 비노출).
 * 공개 책이면 **팔로우와 무관하게** 글 목록이 그대로 온다(2026-08-22 — 팔로우는 열람 권한이 아니다).
 */
export const fetchBookMargin = (loginId: string, bookId: number): Promise<MarginResponse> =>
  request(`/api/stories/of/${loginId}`, { query: { bookId } });

/** bookId는 **필수**다 — 여백은 책에 딸린 자리라 책 없는 글은 서버가 400으로 거절한다. */
/** `quote`는 맨 뒤다 — `text`와 같은 타입이라 붙여 두면 순서를 바꿔 넣어도 컴파일러가 안 잡는다. */
export const createStory = (
  text: string,
  bookId: number,
  bgCode: string | null,
  quote: string | null,
  shared: boolean,
): Promise<MarginEntry> => {
  // 요청 **전에** 버린다 — 실패해도 캐시가 없어 다음 조회가 서버 진실로 간다(fail-safe).
  // 여백 쓰기 경로는 셋(홈 문·서재 손잡이·책방 composer)이지만 전부 이 함수를 지나므로 여기가 근본 자리다.
  cacheDrop('margin:');
  return request('/api/stories', { body: { text, bookId, bgCode, quote, shared } });
};

/** 없거나 남의 것이면 404 — 존재를 누설하지 않는 서버 계약(IDOR)을 그대로 받는다. */
export const deleteStory = (id: number): Promise<void> => {
  cacheDrop('margin:'); // 지운 글이 옛 스냅으로 되살아나지 않게
  return request(`/api/stories/${id}`, { method: 'DELETE' });
};

/** `StoryService.LikeState` — 누르기·취소 직후의 갱신값. 클라가 개수를 추측하지 않게 서버가 센 값을 준다. */
export interface LikeState {
  likeCount: number;
  liked: boolean;
}

/**
 * 좋아요를 누른다. **멱등**이라 재전송해도 취소되지 않는다 — 그래서 토글 단일 엔드포인트가 아니다
 * (모바일 타임아웃 뒤 재시도가 흔한데, 토글이면 그 재시도가 좋아요를 지운다).
 * 안 보이는 글(비공개 책·차단)은 404 — 존재를 누설하지 않는다. 내 글은 누를 수 있다(2026-08-20).
 */
export const likeStory = (id: number): Promise<LikeState> =>
  request(`/api/stories/${id}/like`, { method: 'POST' });

/** 좋아요를 취소한다. 서버는 여기에 노출 게이트를 걸지 않는다 — 언팔한 뒤에도 되돌릴 수 있어야 한다. */
export const unlikeStory = (id: number): Promise<LikeState> =>
  request(`/api/stories/${id}/like`, { method: 'DELETE' });

/**
 * 그 글에 좋아요를 누른 사람들 — 카드의 「좋아요 N명」이 여는 명단(최근순).
 *
 * <p>서버가 <b>차단 관계·핸들 없는 사람</b>을 이미 걸러 준다 — 화면은 받은 행을 그대로 그린다.
 * 안 보이는 글의 명단은 404다(목록과 같은 게이트).
 */
export const fetchStoryLikers = (id: number): Promise<UserRow[]> => request(`/api/stories/${id}/likes`);

// ── 책축 여백 (2026-08-22) ───────────────────────────────────────────────────
//
// 여백에 닿는 둘째 좌표계다: 사람이 아니라 **책 하나(isbn13)**. 「함께 걸기」를 켠 글만 실리고,
// 팔로우와 무관하게 같은 책을 보는 누구에게나 열린다. 노출 판정은 전부 서버 쿼리에 있다
// (책 PUBLIC AND shared AND 차단 아님 AND ADMIN 아님 AND 핸들 있음) — 화면은 재현하지 않는다.

/** `story.BookMarginLabel` — 책축 화면의 헤더. **주인 이름이 없다**(이 화면의 주인공은 책이다). */
export interface BookMarginLabel {
  isbn13: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
}

/** `story.SharedMarginEntry` — 책축 카드. 사람축 {@link MarginEntry}에 **작성자 줄**이 더해진 모양. */
export interface SharedMarginEntry {
  id: number;
  text: string;
  quote: string | null;
  bgCode: string | null;
  createdAt: string;
  likeCount: number;
  liked: boolean;
  /** 작성자 핸들 — 탭하면 그의 책방으로 가는 좌표다. 서버가 핸들 없는 작성자를 이미 걸러 준다. */
  authorLoginId: string;
  authorNickname: string;
}

/**
 * `story.BookMarginResponse` — 책 하나에 함께 걸린 글 전부.
 *
 * <p>`totalCount`는 **상한과 무관한 진짜 값**이고 `entries`는 100장에서 잘린다 — 헤더의 N과 카드 수가
 * 어긋날 수 있다는 뜻이며, 그건 결함이 아니라 계약이다.
 *
 * <p>`myBookId`는 내가 이 책을 서재에 가졌는가다. `null`이면 글쓰기가 없고 「담기」 안내로 갈린다.
 */
export interface BookMarginAllResponse {
  book: BookMarginLabel;
  myBookId: number | null;
  totalCount: number;
  entries: SharedMarginEntry[];
}

/**
 * 「이 책의 여백」 — 함께 걸린 글도 내 책도 없으면 404다(그릴 헤더가 없다는 뜻이지 권한 실패가 아니지만,
 * 화면 입장에선 구분할 이유가 없다). 하이픈이 붙어 있어도 서버가 한 번 더 정규화해 같은 책에 닿는다.
 */
export const fetchBookMarginAll = (isbn: string): Promise<BookMarginAllResponse> =>
  request(`/api/stories/book/${encodeURIComponent(isbn)}`);

/**
 * 「함께 걸기」를 켠다 — {@link likeStory}와 같은 이유로 **멱등 POST**다(토글 단일 엔드포인트면
 * 모바일 타임아웃 재전송이 방금 켠 것을 꺼 버린다). 남의 글은 404(IDOR — 존재 비노출).
 */
export const shareStory = (id: number): Promise<{ shared: boolean }> => {
  cacheDrop('margin:');
  return request(`/api/stories/${id}/share`, { method: 'POST' });
};

/** 「함께 걸기」를 끈다 — 다음 조회부터 책축 목록·검색 배지에서 빠진다. */
export const unshareStory = (id: number): Promise<{ shared: boolean }> => {
  cacheDrop('margin:');
  return request(`/api/stories/${id}/share`, { method: 'DELETE' });
};
