// 앱이 부르는 경로의 응답형만(v1 §5.2). 단일 출처는 서버 record다.

/** `AppAuthApiController.Intent` */
export type AppleIntent = 'LOGIN' | 'REGISTER' | 'LINK';

/** `AppAuthApiController.AppAuthResponse` — registered가 false면 token·userId가 null이다. */
export interface AppAuthResponse {
  registered: boolean;
  token: string | null;
  created: boolean;
  webAccount: boolean;
  userId: number | null;
  /** 원 가입 경로(`AuthProvider` 이름) — 탈퇴 갈래(lib/deleteVia). 미가입이면 null */
  authProvider: string | null;
  appleLinked: boolean;
  googleLinked: boolean;
  unverifiedWebAccount: boolean;
}

/** `DashboardApiController.BookOption` — 손으로 넣은 책이면 coverUrl · author가 null이다. */
export interface BookOption {
  id: number;
  title: string;
  coverUrl: string | null;
  author: string | null;
}

/**
 * `GET /api/dashboard`(`DashboardResponse`) 중 앱이 읽는 것만. 밀린 시간 필드는 일부러 없다(REQ-91 — 금지 표).
 * `todayReadSeconds`는 끝난 세션만이다 — 진행 중 몫은 앱이 얹는다(lib/goalProgress).
 */
export interface Dashboard {
  todayGoalSeconds: number;
  todayReadSeconds: number;
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  activeBook: BookOption | null;
  readingBooks: BookOption[];
  wantToReadBooks: BookOption[];
  recentBookId: number | null;
  /** 목표를 채운 날의 연속(REQ-89 — 서버 PR-F). 옛 서버엔 없다. */
  graph: { goalStreak?: number };
  /** 공부 모드(PR-S1 — `StudyApiController.StudyState`). 독서 · 공부는 서로를 막는다(409). */
  study: StudyState;
}

// ── 공부 타이머 · 공부 서재(PR-S1) — 회독 · 회당 시간 · 링크 필드는 일부러 없다(v1 REQ-98 — 금지 표) ──

/** `StudyBookApiController.StudyBookRow` 중 앱이 읽는 것만. totalSeconds 0 = 아직 안 잼. */
export interface StudyBook {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  isbn13: string | null;
  totalSeconds: number;
}

/** `StudyApiController.StudyState` 중 앱이 읽는 것만. todaySeconds는 끝난 세션만 — 진행 중 몫은 앱이 얹는다(lib/studyTimer). books = 등록 최신 먼저. */
export interface StudyState {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  todaySeconds: number;
  activeBook: StudyBook | null;
  recentBookId: number | null;
  books: StudyBook[];
}

/** `StudyBookApiController.StudyShelfResponse` 중 앱이 읽는 것만 — 담기 화면의 「담음」 판정용. */
export interface StudyShelfResponse {
  books: StudyBook[];
}

/** `StudyBookApiController.AddRequest` 중 6필드 — 독서 담기의 상태 · 분류 · 출간일은 없다(linkUrl은 웹 「직접 추가」 전용). */
export interface StudyAddRequest {
  title: string;
  author: string | null;
  isbn13: string | null;
  coverUrl: string | null;
  publisher: string | null;
  purchaseLink: string | null;
}

/** `WritingApiController.DraftSummary` — 책이 풀린 글이면 bookId · bookTitle이 null, 제목은 비어 있을 수 있다. */
export interface DraftSummary {
  id: number;
  bookId: number | null;
  bookTitle: string | null;
  title: string | null;
  /** 첫 비공백 줄 80자 */
  preview: string;
  chars: number;
  updatedAt: string;
  exported: boolean;
}

// ── 서재 · 책 담기 · 책 재료 · 여백(PR-G) ─────────────────────────────────────

/** `book.BookStatus` enum 이름. 화면 낱말은 앱 것(lib/shelf `SECTIONS`) — 서버 `statusLabel`은 읽지 않는다. */
export type ShelfStatus = 'READING' | 'FINISHED' | 'WANT_TO_READ';

/** `BookApiController.MyBookSummary` 중 앱이 읽는 것만(statusLabel · 공개 바꾸기 필드 없음). */
export interface MyBookSummary {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  status: ShelfStatus;
  isPublic: boolean;
  seconds: number;
  storyCount: number;
}

/** `BookApiController.ShelfResponse` — 독서 서재 책만(공부 서재는 따로). */
export interface ShelfResponse {
  books: MyBookSummary[];
}

/** `BookApiController.SearchRow` — ⚠️ author는 원문(저장값), authorShort는 목록 표시용이다. */
export interface SearchRow {
  title: string;
  author: string | null;
  authorShort: string | null;
  isbn13: string | null;
  coverUrl: string | null;
  publisher: string | null;
  purchaseLink: string | null;
  category: string | null;
  pubDate: string | null;
  owned: boolean;
}

export interface SearchResponse {
  results: SearchRow[];
}

/** `BookApiController.AddRequest` — status가 없으면 서버가 WANT_TO_READ로 담는다. */
export interface AddRequest {
  title: string;
  author: string | null;
  isbn13: string | null;
  coverUrl: string | null;
  publisher: string | null;
  purchaseLink: string | null;
  category: string | null;
  pubDate: string | null;
  status: ShelfStatus;
}

/** `writing.BookLine` */
export interface BookLine {
  id: number;
  title: string;
  author: string | null;
  coverUrl: string | null;
  status: ShelfStatus;
  readSeconds: number;
  sessions: number;
  isPublic: boolean;
}

/** `WritingApiController.MarginItem` — 생각(text)은 비어 있을 수 있다(인용만 남긴 여백 — REQ-93). */
export interface MarginItem {
  id: number;
  quote: string | null;
  text: string | null;
  createdAt: string;
}

/**
 * `WritingApiController.MaterialsView` 중 앱이 읽는 것만. 여백은 최근순(최대 200).
 * aiIncluded = AI에 보내는 여백 수 · aiExcluded = 상한에 걸려 빠지는 수(「재료 N개 중 M개를 보냈어요」).
 */
export interface MaterialsView {
  book: BookLine;
  margins: MarginItem[];
  aiIncluded: number;
  aiExcluded: number;
  drafts: DraftSummary[];
  aiConsent: boolean;
}

// ── 글쓰기 · AI 질문(PR-H) — 서버 WritingApiController · QuestionLog record 그대로 ──────

/** `writing.Step` */
export type Step = 'ASK' | 'FOLLOW';

/** `writing.Question` — anchor = 재료 · 사용자 글에서 그대로 따온 조각(없으면 null) */
export interface Question {
  text: string;
  anchor: string | null;
}

/** `QuestionLog.Round` — focus = FOLLOW가 답하던 질문 */
export interface Round {
  step: Step;
  at: string;
  focus: string | null;
  bodyChars: number;
  questions: Question[];
}

/** `writing.QuestionLog` — rounds는 먼저 받은 것부터, 비어 있어도 null이 아니다. */
export interface QuestionLog {
  rounds: Round[];
}

/** `WritingApiController.DraftView` — 글 한 편(본문 포함). 책이 풀린 글이면 bookId · bookTitle이 null. */
export interface DraftView {
  id: number;
  bookId: number | null;
  bookTitle: string | null;
  title: string | null;
  body: string;
  revision: number;
  questions: QuestionLog;
  aiUpdatedAt: string | null;
  aiRemainingToday: number;
  aiConsent: boolean;
}

/** `WritingAiService.AiView` — 본문 · 제목 · revision이 없다(REQ-47). */
export interface AiView {
  questions: QuestionLog;
  aiUpdatedAt: string;
  aiRemainingToday: number;
}

/** `WritingApiController.SaveDraft` — exported는 공유 시트 sharedAction일 때만 true. */
export interface SaveDraft {
  title: string;
  body: string;
  revision: number;
  exported?: boolean;
}

/** `WritingApiController.ConsentView` */
export interface ConsentView {
  aiConsent: boolean;
  aiConsentAt: string | null;
}
