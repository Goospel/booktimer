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
}
