import { ApiError } from './api';
import type {
  AuthorStories,
  BookOption,
  BookStatus,
  BookVisibility,
  ContributionDay,
  ContributionGraph,
  DashboardResponse,
  MyBookSummary,
  ProfileBook,
  RequestOptions,
  SearchRow,
  StoryCard,
  TimerState,
  UserRow,
} from './api';

/**
 * 브라우저 dev 목(mock) 서버 — `npm run dev:mock`에서만 로드된다(`api.ts`의 dynamic import).
 *
 * <p>왜 필요한가: 미니앱을 그냥 브라우저에서 열면 토스 SDK(`TossAuth.login`)가 없어 로그인에서 막혀,
 * UI 작업의 실시간 확인(HMR)이 아예 불가능했다. 샌드박스 앱의 dev 연결은 granite(RN) 전용 프로토콜이라
 * **웹 프레임워크 미니앱은 핫 리로드 자체가 없다**(2026-08-12 실측 · T-152). 그래서 서버 대신 이 모듈이
 * 픽스처를 돌려주고, 크롬에서 전 화면을 HMR로 돈다. 실기기 검증(SDK·실로그인)은 `deploy.sh`가 맡는다.
 *
 * <p>상태는 모듈 메모리다 — 새로고침하면 초기값으로 돌아간다. 목적이 "화면 전이가 자연스럽게 보인다"라
 * 영속은 과하다. 프로드 번들에는 이 파일이 한 바이트도 들어가지 않는다(`api.ts`의 DEV 게이트 + `deploy.sh`
 * 의 `__DEV_MOCK__` 부재 검사).
 */

/** 프로드 번들 격리 마커 — 이 문자열이 배포 번들에 있으면 `deploy.sh`가 배포를 막는다. */
const MARKER = '__DEV_MOCK__';

const MY_LOGIN_ID = 'testid';
const MY_NICKNAME = '목독서가';

const STATUS_LABEL: Record<BookStatus, string> = {
  READING: '읽는 중',
  FINISHED: '다 읽음',
  WANT_TO_READ: '읽고 싶어요',
};

/** `offsetDays` 일 전의 `YYYY-MM-DD`. 잔디·스토리 시각이 항상 "오늘 기준"으로 보이게 한다. */
function isoDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() - offsetDays);
  return d.toISOString().slice(0, 10);
}

function isoTime(offsetHours: number): string {
  return new Date(Date.now() - offsetHours * 3_600_000).toISOString();
}

// ── 잔디 ────────────────────────────────────────────────────────────────────

/** 오늘부터 거슬러 반복하는 레벨 패턴 — 결정론적이라 새로고침해도 잔디가 흔들리지 않는다(길이 11=불규칙해 보임). */
const LEVELS = [2, 3, 0, 4, 1, 2, 4, 0, 3, 1, 2];
const GRAPH_WEEKS = 20;

/**
 * 잔디 픽스처 — **`weeks[0]`이 최신 주**다. 서버가 뒤집어 보내는 계약이고(`ContributionGraph`),
 * 목이 이걸 어기면 화면 버그가 아니라 목 버그로 시간을 태운다(#730에서 실제로 겪은 방향 버그).
 */
function buildGraph(): ContributionGraph {
  const weeks: ContributionDay[][] = [];
  let totalSeconds = 0;
  let activeDays = 0;

  for (let w = 0; w < GRAPH_WEEKS; w++) {
    const days: ContributionDay[] = [];
    for (let d = 0; d < 7; d++) {
      // 주 사이는 최근→과거(왼→오른쪽, 서버 계약), 주 안은 과거→최근(위→아래).
      const offset = w * 7 + (6 - d);
      const level = LEVELS[offset % LEVELS.length];
      const seconds = level * 900;
      days.push({ date: isoDate(offset), totalSeconds: seconds, level, manual: offset % 13 === 5 });
      totalSeconds += seconds;
      if (level > 0) activeDays += 1;
    }
    weeks.push(days);
  }

  const monthLabels = weeks.flatMap((week, weekIndex) => {
    const month = week[6].date!.slice(5, 7);
    const previous = weekIndex === 0 ? '' : weeks[weekIndex - 1][6].date!.slice(5, 7);
    return month === previous ? [] : [{ weekIndex, label: `${Number(month)}월` }];
  });

  return {
    weeks,
    monthLabels,
    totalSeconds,
    activeDays,
    // 오늘(offset 0)부터 처음 0레벨을 만나기까지 = 연속일. 패턴에 0이 있어 반드시 끝난다.
    currentStreak: LEVELS.indexOf(0),
    growthStageName: 'SAPLING',
    growthStageEmoji: '🌿',
    growthStageLabel: '어린 나무',
  };
}

// ── 상태 (모듈 메모리) ───────────────────────────────────────────────────────

function shelfBook(
  id: number,
  title: string,
  author: string,
  status: BookStatus,
  seconds: number,
): MyBookSummary {
  return {
    id,
    title,
    author,
    coverUrl: null, // 표지는 CoverInitial 자리표지로 그려진다 — 목이 외부 이미지에 의존하지 않게
    isbn13: `978895460${1000 + id}`,
    status,
    statusLabel: STATUS_LABEL[status],
    visibility: 'PUBLIC',
    visibilityLabel: '공개',
    isPublic: true,
    seconds,
    purchaseLink: null,
  };
}

const books: MyBookSummary[] = [
  shelfBook(1, '미움받을 용기', '기시미 이치로', 'READING', 7_200),
  shelfBook(2, '사피엔스', '유발 하라리', 'READING', 3_600),
  shelfBook(3, '데미안', '헤르만 헤세', 'FINISHED', 18_000),
  shelfBook(4, '코스모스', '칼 세이건', 'WANT_TO_READ', 0),
];

const state = {
  goalSeconds: 1_800,
  remainingSeconds: 900,
  carriedDebtSeconds: 600,
  activeStartedAt: null as string | null,
  activeBookId: null as number | null,
  nextId: 500,
};

const nextId = (): number => (state.nextId += 1);

const bookOptions = (status: BookStatus): BookOption[] =>
  books.filter((b) => b.status === status).map(({ id, title }) => ({ id, title }));

function timerState(): TimerState {
  const active = books.find((b) => b.id === state.activeBookId) ?? null;
  return {
    remainingSeconds: state.remainingSeconds,
    carriedDebtSeconds: state.carriedDebtSeconds,
    todayGoalSeconds: state.goalSeconds,
    carryover: true,
    hasActiveSession: state.activeStartedAt !== null,
    activeStartedAt: state.activeStartedAt,
    activeBookTitle: active?.title ?? null,
    activeBookTotalSeconds: active?.seconds ?? 0,
    readingBooks: bookOptions('READING'),
    finishedBooks: bookOptions('FINISHED'),
    recentBookId: 1,
    // 광고 SDK가 없는 브라우저라 버튼은 뜨지 않는다(`.env.mock`이 광고 그룹 ID를 비운다) — 값은 실제처럼 둔다.
    debtWaiverAvailable: true,
  };
}

const users: UserRow[] = [
  { loginId: 'nabi', nickname: '나비독서', publicBookCount: 12, following: true, self: false },
  { loginId: 'underline', nickname: '밑줄러', publicBookCount: 5, following: false, self: false },
  { loginId: 'jieun', nickname: '지은의서재', publicBookCount: 31, following: true, self: false },
  { loginId: MY_LOGIN_ID, nickname: MY_NICKNAME, publicBookCount: 3, following: false, self: true },
];

const blocked: UserRow[] = [
  { loginId: 'spammer', nickname: '광고봇', publicBookCount: 0, following: false, self: false },
];

const profileBook = (id: number, title: string, author: string, status: BookStatus, seconds: number): ProfileBook => ({
  id,
  title,
  author,
  coverUrl: null,
  status: STATUS_LABEL[status],
  seconds,
  purchaseLink: null,
});

const profileBooks: ProfileBook[] = [
  profileBook(11, '아무튼, 서재', '김민지', 'READING', 5_400),
  profileBook(12, '밑줄 긋는 사람', '이수연', 'FINISHED', 12_600),
  profileBook(13, '해변의 카프카', '무라카미 하루키', 'WANT_TO_READ', 0),
];

const storyCard = (id: number, text: string, bgCode: string, hoursAgo: number, viewed: boolean): StoryCard => ({
  id,
  text,
  bgCode,
  bookTitle: '미움받을 용기',
  bookCoverUrl: null,
  createdAt: isoTime(hoursAgo),
  viewed,
});

const myStories: StoryCard[] = [storyCard(901, '오늘은 30분만 읽자고 앉았는데 한 시간을 넘겼다.', 'paper', 3, true)];

const storyGroups: AuthorStories[] = [
  {
    loginId: 'nabi',
    nickname: '나비독서',
    profileCharacterCode: null,
    allViewed: false,
    stories: [storyCard(911, '밑줄 그은 문장이 오늘의 나를 설명한다.', 'night', 5, false)],
  },
  {
    loginId: 'jieun',
    nickname: '지은의서재',
    profileCharacterCode: null,
    allViewed: true,
    stories: [storyCard(921, '완독. 마지막 장을 아껴 읽었다.', 'forest', 20, true)],
  },
];

const searchRows: SearchRow[] = [
  {
    title: '불편한 편의점',
    author: '김호연',
    isbn13: '9791168340084',
    coverUrl: null,
    publisher: '나무옆의자',
    purchaseLink: null,
    category: '소설',
    pubDate: '2021-04-20',
    owned: false,
  },
  {
    title: '역행자',
    author: '자청',
    isbn13: '9791165341909',
    coverUrl: null,
    publisher: '웅진지식하우스',
    purchaseLink: null,
    category: '자기계발',
    pubDate: '2022-05-30',
    owned: false,
  },
  {
    title: '미움받을 용기',
    author: '기시미 이치로',
    isbn13: '9788996991342',
    coverUrl: null,
    publisher: '인플루엔셜',
    purchaseLink: null,
    category: '인문',
    pubDate: '2014-11-17',
    owned: true,
  },
];

// ── 라우팅 ──────────────────────────────────────────────────────────────────

interface Ctx {
  /** 경로 캡처(`/api/books/{id}/...`)의 id. 캡처가 없는 경로에선 `NaN`이다. */
  id: number;
  body: Record<string, unknown>;
  query: Record<string, string | number | undefined>;
}

type Method = 'GET' | 'POST' | 'DELETE';

/** 순서대로 첫 일치를 쓴다. 패턴은 앵커돼 있어 `/api/books`와 `/api/books/search`가 섞이지 않는다. */
const routes: [Method, RegExp, (ctx: Ctx) => unknown][] = [
  // 인증 — 목 모드는 토큰이 항상 있어 여기까지 오지 않지만, 로그아웃 후 로그인 화면을 눌러 볼 수 있게 둔다.
  ['POST', /^\/api\/toss\/(login|register|link)$/, () => ({ registered: true, token: 'dev-mock-token', loginId: MY_LOGIN_ID })],
  ['POST', /^\/api\/toss\/logout$/, () => undefined],

  ['GET', /^\/api\/dashboard$/, (): DashboardResponse => ({
    ...timerState(),
    nickname: MY_NICKNAME,
    loginId: MY_LOGIN_ID,
    profileCharacterCode: null,
    wantToReadBooks: bookOptions('WANT_TO_READ'),
    graph: buildGraph(),
    quotes: [
      { text: '독서는 완성된 사람을 만들고, 사색은 사려 깊은 사람을 만든다.', author: '벤저민 프랭클린' },
      { text: '오늘의 독서가 내일의 나를 만든다.', author: '목 모드' },
    ],
    emailVerified: true,
  })],

  // ── 타이머 ──
  ['POST', /^\/api\/sessions\/start$/, ({ body }) => {
    state.activeBookId = (body.bookId as number | null) ?? null;
    state.activeStartedAt = new Date().toISOString();
    return timerState();
  }],
  ['POST', /^\/api\/sessions\/stop$/, () => {
    const elapsed = state.activeStartedAt === null ? 0 : Math.floor((Date.now() - Date.parse(state.activeStartedAt)) / 1000);
    const untagged = state.activeBookId === null;
    state.remainingSeconds = Math.max(0, state.remainingSeconds - elapsed);
    const book = books.find((b) => b.id === state.activeBookId);
    if (book !== undefined) book.seconds += elapsed;
    state.activeStartedAt = null;
    state.activeBookId = null;
    // graph를 동봉해야 홈이 재조회 없이 잔디를 갱신한다(`StopResponse` 계약).
    return { sessionId: nextId(), untagged, timer: timerState(), graph: buildGraph() };
  }],
  ['POST', /^\/api\/sessions\/(\d+)\/tag-book$/, ({ id, body }) => {
    const book = books.find((b) => b.id === body.bookId);
    return { sessionId: id, bookTitle: book?.title ?? '알 수 없는 책' };
  }],

  ['POST', /^\/api\/miniapp\/goal$/, ({ body }) => {
    state.goalSeconds = body.dailyIncrementSeconds as number;
    return undefined;
  }],
  // 리워드 광고는 브라우저에 SDK가 없어 애초에 도달하지 않는다 — 혹시 눌러도 무해하게 실패시킨다.
  ['POST', /^\/api\/miniapp\/debt-waiver$/, () => {
    throw new ApiError(400, '목 모드에서는 광고 보상을 쓸 수 없어요');
  }],

  // ── 서재 ──
  ['GET', /^\/api\/books$/, () => ({ searchEnabled: true, books: [...books] })],
  // 픽스처 3권을 제목·저자로 필터한다 — 결과 있는 화면을 보려면 '미움'·'역행' 같은 조각을 넣는다.
  ['GET', /^\/api\/books\/search$/, ({ query }) => {
    const q = String(query.q ?? '').trim();
    return { results: q === '' ? [] : searchRows.filter((r) => r.title.includes(q) || (r.author ?? '').includes(q)) };
  }],
  ['POST', /^\/api\/books$/, ({ body }) => {
    // 같은 ISBN은 기존 책을 그대로 준다(서버 멱등 계약).
    const existing = books.find((b) => b.isbn13 !== null && b.isbn13 === body.isbn13);
    if (existing !== undefined) return existing;
    const added = shelfBook(nextId(), body.title as string, (body.author as string) ?? '', body.status as BookStatus, 0);
    added.isbn13 = (body.isbn13 as string | null) ?? null;
    books.push(added);
    return added;
  }],
  ['POST', /^\/api\/books\/(\d+)\/status$/, ({ id, body }) => {
    const book = mustFindBook(id);
    book.status = body.status as BookStatus;
    book.statusLabel = STATUS_LABEL[book.status];
    return book;
  }],
  ['POST', /^\/api\/books\/(\d+)\/visibility$/, ({ id, body }) => {
    const book = mustFindBook(id);
    book.visibility = body.visibility as BookVisibility;
    book.isPublic = book.visibility === 'PUBLIC';
    book.visibilityLabel = book.isPublic ? '공개' : '비공개';
    return book;
  }],
  ['POST', /^\/api\/books\/(\d+)\/delete$/, ({ id }) => {
    books.splice(books.indexOf(mustFindBook(id)), 1);
    return { deleted: true };
  }],

  // ── 소셜 ──
  ['GET', /^\/api\/search$/, ({ query }) => {
    const q = String(query.q ?? '').trim();
    // 서버는 2글자 미만이면 빈 결과다(열거 방지) — 실패가 아니라 0건이라는 계약을 그대로 흉내낸다.
    const results = q.length < 2 ? [] : users.filter((u) => u.nickname.includes(q) || u.loginId.includes(q));
    return { q, results, myLoginId: MY_LOGIN_ID, rateLimited: false };
  }],
  ['GET', /^\/api\/follow-list$/, ({ query }) => ({
    type: query.type,
    users: query.type === 'following' ? users.filter((u) => u.following) : users.filter((u) => !u.self),
    myLoginId: MY_LOGIN_ID,
  })],
  ['POST', /^\/api\/follow$/, ({ body }) => ({ following: setFollowing(body.loginId as string, true) })],
  ['POST', /^\/api\/unfollow$/, ({ body }) => ({ following: setFollowing(body.loginId as string, false) })],

  ['GET', /^\/api\/profile$/, ({ query }) => {
    const user = mustFindUser(String(query.loginId));
    return {
      loginId: user.loginId,
      nickname: user.nickname,
      profileCharacterCode: null,
      followerCount: 42,
      followingCount: 17,
      following: user.following,
      self: user.self,
      personality: '밑줄을 아끼지 않는 완독형',
      personalityTags: [
        { label: '소설', clickable: true },
        { label: '인문', clickable: true },
        { label: '완독형', clickable: false },
      ],
      books: profileBooks,
    };
  }],
  ['GET', /^\/api\/profile\/books$/, ({ query }) => ({
    books: query.status === undefined ? profileBooks : profileBooks.filter((b) => b.status === STATUS_LABEL[query.status as BookStatus]),
  })],
  ['GET', /^\/api\/profile\/personality-tag$/, () => ({ books: profileBooks.slice(0, 2) })],

  ['GET', /^\/api\/blocks$/, () => ({ blocked: [...blocked], myLoginId: MY_LOGIN_ID })],
  ['POST', /^\/api\/block$/, ({ body }) => {
    const user = mustFindUser(body.loginId as string);
    if (!blocked.some((b) => b.loginId === user.loginId)) blocked.push({ ...user, following: false });
    return { blocked: true };
  }],
  ['POST', /^\/api\/unblock$/, ({ body }) => {
    const index = blocked.findIndex((b) => b.loginId === body.loginId);
    if (index >= 0) blocked.splice(index, 1);
    return { blocked: false };
  }],
  ['POST', /^\/api\/report$/, () => ({ reported: true })],

  // ── 스토리 ──
  ['GET', /^\/api\/stories\/feed$/, () => ({
    mine:
      myStories.length === 0
        ? null
        : {
            loginId: MY_LOGIN_ID,
            nickname: MY_NICKNAME,
            profileCharacterCode: null,
            allViewed: myStories.every((s) => s.viewed),
            stories: [...myStories],
          },
    groups: storyGroups,
  })],
  ['POST', /^\/api\/stories$/, ({ body }) => {
    const card: StoryCard = {
      id: nextId(),
      text: body.text as string,
      bgCode: (body.bgCode as string | null) ?? null,
      bookTitle: books.find((b) => b.id === body.bookId)?.title ?? null,
      bookCoverUrl: null,
      createdAt: new Date().toISOString(),
      viewed: true,
    };
    myStories.unshift(card);
    return card;
  }],
  ['DELETE', /^\/api\/stories\/(\d+)$/, ({ id }) => {
    const index = myStories.findIndex((s) => s.id === id);
    if (index < 0) throw new ApiError(404, '없는 스토리예요');
    myStories.splice(index, 1);
    return undefined;
  }],
  ['POST', /^\/api\/stories\/(\d+)\/view$/, ({ id }) => {
    for (const group of storyGroups) {
      const story = group.stories.find((s) => s.id === id);
      if (story !== undefined) {
        story.viewed = true;
        group.allViewed = group.stories.every((s) => s.viewed);
      }
    }
    return undefined;
  }],
  ['GET', /^\/api\/stories\/(\d+)\/viewers$/, () => [
    { loginId: 'nabi', nickname: '나비독서', profileCharacterCode: null, viewedAt: isoTime(1) },
    { loginId: 'jieun', nickname: '지은의서재', profileCharacterCode: null, viewedAt: isoTime(2) },
  ]],
];

function mustFindBook(id: number): MyBookSummary {
  const book = books.find((b) => b.id === id);
  if (book === undefined) throw new ApiError(404, '없는 책이에요');
  return book;
}

function mustFindUser(loginId: string): UserRow {
  const user = users.find((u) => u.loginId === loginId);
  // 서버는 차단·없는 아이디를 모두 404로 준다(존재 누설 방지) — 목도 같은 모양으로 던진다.
  if (user === undefined) throw new ApiError(404, '없는 사용자예요');
  return user;
}

function setFollowing(loginId: string, following: boolean): boolean {
  mustFindUser(loginId).following = following;
  return following;
}

/**
 * `api.ts`의 `request()` 대역 — 경로+메서드로 픽스처를 돌려준다.
 *
 * <p>**목에 없는 경로는 404로 던진다.** 조용히 `undefined`를 흘리면 화면이 빈 채로 떠서 "목이 빠진 것"과
 * "화면이 깨진 것"을 구별할 수 없다 — 브라우저 확인의 신뢰가 거기서 무너진다.
 */
export async function mockRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? (options.body === undefined ? 'GET' : 'POST');
  const body = (options.body ?? {}) as Record<string, unknown>;
  const query = options.query ?? {};

  for (const [routeMethod, pattern, handler] of routes) {
    const match = pattern.exec(path);
    if (match !== null && routeMethod === method) {
      return handler({ id: Number(match[1]), body, query }) as T;
    }
  }
  throw new ApiError(404, `${MARKER} 목에 없는 경로: ${method} ${path}`);
}
