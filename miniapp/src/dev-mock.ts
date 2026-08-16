import { ApiError } from './api';
import type {
  BookOption,
  BookStatus,
  BookVisibility,
  ContributionDay,
  ContributionGraph,
  DailyRecord,
  DashboardResponse,
  HomeFeedResponse,
  MarginEntry,
  MarginResponse,
  MonthlySection,
  NewsItem,
  MyBookSummary,
  PersonalityEntry,
  PersonalityMutation,
  PersonalityStatus,
  ProfileBook,
  RequestOptions,
  SearchRow,
  SocialEvent,
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

/**
 * 내 공개 핸들(=서버가 대시보드·검색에 실어 주는 `myLoginId`). 핸들 만들기(`POST /api/miniapp/handle`)로
 * 바뀌므로 상수가 아니라 모듈 상태다.
 *
 * <p>**핸들 없는 신규 가입 플로우(배너 → 시트)를 브라우저로 보려면 초기값을 임시로 `null`로 바꾼다.**
 * 기본값은 기존 화면·스토어 스크린샷 호환을 위해 유지한다(핸들 있는 상태가 정상 사용 흐름).
 */
let myHandle: string | null = MY_LOGIN_ID;

/**
 * 버리고 간 옛 핸들 = 아이디 변경권 소진 표식(`null`이면 평생 1번이 아직 남아 있다).
 *
 * <p>**소진 화면(버튼 없이 「이미 사용했어요 (이전: @…)」)을 브라우저로 보려면 초기값을 임시로
 * `'oldname'`으로 바꾼다** — `myHandle=null` 스위치와 같은 방식. 기본값은 미소진(정상 사용 흐름).
 */
let myPreviousHandle: string | null = null;

/** 내 표시 이름 — 닉네임 변경(`POST /api/miniapp/nickname`)으로 바뀌므로 상수가 아니라 모듈 상태다. */
let myNickname: string = MY_NICKNAME;

const STATUS_LABEL: Record<BookStatus, string> = {
  READING: '읽는 중',
  FINISHED: '다 읽음',
  WANT_TO_READ: '읽고 싶어요',
};

/** `offsetDays` 일 전의 `YYYY-MM-DD`. 잔디·여백 시각이 항상 "오늘 기준"으로 보이게 한다. */
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

/**
 * 표지 목 — 외부 이미지에 의존하지 않게 인라인 SVG data URI로 만든다(목은 오프라인에서도 떠야 한다).
 * 표지가 붙은 책과 안 붙은 책을 섞어 둬야 캐러셀에서 자리 표지 폴백까지 눈으로 확인된다.
 */
const mockCover = (label: string, bg: string): string =>
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 84"><rect width="60" height="84" fill="${bg}"/>` +
      `<text x="30" y="48" font-size="28" text-anchor="middle" fill="rgba(44,42,36,0.6)">${label}</text></svg>`,
  );

function shelfBook(
  id: number,
  title: string,
  author: string,
  status: BookStatus,
  seconds: number,
  coverUrl: string | null = null,
  visibility: BookVisibility = 'PUBLIC',
): MyBookSummary {
  return {
    id,
    title,
    author,
    coverUrl,
    isbn13: `978895460${1000 + id}`,
    status,
    statusLabel: STATUS_LABEL[status],
    visibility,
    visibilityLabel: visibility === 'PUBLIC' ? '공개' : '비공개',
    isPublic: visibility === 'PUBLIC',
    seconds,
    purchaseLink: null,
  };
}

const books: MyBookSummary[] = [
  shelfBook(1, '미움받을 용기', '기시미 이치로', 'READING', 7_200, mockCover('용', '#D8CBB4')),
  shelfBook(2, '사피엔스', '유발 하라리', 'READING', 3_600), // 표지 없는 책 — 캐러셀의 자리 표지 경로
  shelfBook(3, '데미안', '헤르만 헤세', 'FINISHED', 18_000, mockCover('데', '#C7D3C0')),
  shelfBook(4, '코스모스', '칼 세이건', 'WANT_TO_READ', 0),
  // 비공개 + 여백 글 1개 — 가시성 캡션(「나만 봐요」)과 공개 전환 확인 시트를 눈으로 보려면 이 조합이 필요하다.
  shelfBook(5, '아무도 안 보는 노트', '나', 'READING', 1_200, mockCover('노', '#CBBFD8'), 'PRIVATE'),
];

const state = {
  goalSeconds: 1_800,
  remainingSeconds: 900,
  carriedDebtSeconds: 600,
  activeStartedAt: null as string | null,
  activeBookId: null as number | null,
  /** 이 세션에서 끝낸 측정 수 — 첫 종료(=1)에만 축하 배너가 뜬다. 새로고침하면 0으로 돌아가 다시 볼 수 있다. */
  completedSessions: 0,
  nextId: 500,
};

const nextId = (): number => (state.nextId += 1);

/**
 * 날짜별 기록 목 — 잔디와 **같은 패턴·같은 초**에서 만든다. 둘이 어긋나면(잔디는 초록인데 목록은 빔)
 * 화면 버그가 아니라 목 버그로 시간을 태운다. 서버처럼 최신 월 먼저, 달 안에서도 최신 일 먼저다.
 */
function buildMonths(): MonthlySection[] {
  const byMonth = new Map<string, DailyRecord[]>();

  for (let offset = 0; offset < GRAPH_WEEKS * 7; offset++) {
    const level = LEVELS[offset % LEVELS.length];
    if (level === 0) continue; // 안 읽은 날은 행이 없다(잔디는 회색 칸으로만 남는다).
    const date = isoDate(offset);
    // 책 미지정 날·두 권 읽은 날을 섞어 둬야 제목 줄 생략과 쉼표 나열이 둘 다 눈에 보인다.
    const titles =
      offset % 5 === 0 ? [] : offset % 4 === 1 ? [books[0].title, books[1].title] : [books[offset % books.length].title];
    const days = byMonth.get(date.slice(0, 7));
    const record = { date, totalSeconds: level * 900, bookTitles: titles, manuallyFilled: offset % 13 === 5 };
    if (days === undefined) byMonth.set(date.slice(0, 7), [record]);
    else days.push(record);
  }

  return [...byMonth].map(([month, days]) => ({
    month,
    totalSeconds: days.reduce((sum, d) => sum + d.totalSeconds, 0),
    days,
  }));
}

const bookOptions = (status: BookStatus): BookOption[] =>
  books
    .filter((b) => b.status === status)
    // `isPublic`은 홈 문으로 곧장 연 작성 화면의 가시성 캡션 재료다(게이트가 아니다).
    .map(({ id, title, coverUrl, author, isPublic }) => ({ id, title, coverUrl, author, isPublic }));

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
    // 서버는 `startedAt desc`로 고른다 — **활성 세션도 그 정렬에 들어가** 측정 중이면 그 책이 최신이다.
    // 상수로 두면 홈 여백 문이 측정 중에 엉뚱한 책을 가리켜(브라우저 실측 2026-08-16) 목이 거짓 신호를 준다.
    recentBookId: state.activeBookId ?? 1,
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
  // 실제 값은 아래 `booksFor`가 여백 픽스처에서 파생한다 — 두 곳에 적으면 글을 남겨도 발광이 안 바뀐다.
  lastStoryAt: null,
});

/** 성향 분석 하루 총량(서버 `User.DAILY_PERSONALITY_TOTAL_LIMIT`) — 잔여 차감·소진 429를 흉내내는 기준. */
const PERSONALITY_LIMIT = 10;
/** 서버 `ReadingPersonalityService.MAX_HISTORY` — 넘치면 대표를 뺀 가장 오래된 후보부터 버린다. */
const PERSONALITY_MAX_HISTORY = 3;
let personalityRemaining = PERSONALITY_LIMIT;

/**
 * 재분석이 낳는 서술들 — <b>서로 뚜렷이 다르게</b> 써 둔다. 보관함의 존재 이유가 "문구가 바뀌었는지 눈으로
 * 확인"이라, 목이 같은 문구만 돌려주면 브라우저 검증이 원래 문제(구별 불가)를 그대로 재현한다.
 */
const PERSONALITY_NARRATIVES = [
  // 대표 문구는 **3줄을 넘긴다** — 운영 LLM 서술이 이만큼 길고, 그래야 책방 bio의 접기·「더보기」가 브라우저에서 확인된다.
  '밑줄을 아끼지 않는 완독형이에요. 한 권을 붙잡으면 끝까지 가고, 마음에 닿은 문장은 그냥 지나치지 않아요. ' +
    '소설과 인문을 오가며 사람의 마음이 어떻게 움직이는지를 자꾸 들여다보고, 읽은 뒤에는 그 문장을 오래 곱씹는 편이에요. ' +
    '빠르게 많이 읽기보다 한 권을 깊게 읽어 자기 언어로 만드는 데서 즐거움을 찾는 독자입니다.',
  '밤에 소설만 파는 몰입형이에요. 이야기 속에 오래 머무는 걸 좋아하고, 하루의 끝을 책으로 닫아요.',
  '분야를 넓게 오가는 탐험형이에요. 한 주제에 오래 머물기보다 새 영역을 자꾸 기웃거려요.',
  '짧게 자주 읽는 습관형이에요. 한 번에 길게 읽진 않지만 하루도 거르지 않아요.',
];
/** 시드가 앞의 둘을 이미 썼다 — 재분석은 그 다음부터 돈다(첫 재분석이 시드와 같은 문구면 변화가 안 보인다). */
let narrativeCursor = 1;

/** 목의 라벨 — 서버는 사용자 타임존으로 "yyyy-MM-dd HH:mm"을 만들어 준다. 목은 ISO를 잘라 흉내만 낸다. */
const personalityLabel = (iso: string) => iso.slice(0, 16).replace('T', ' ');

function personalityEntry(narrative: string, hoursAgo: number, selected: boolean, stale = false): PersonalityEntry {
  const generatedAt = isoTime(hoursAgo);
  return { id: nextId(), narrative, generatedAt, generatedAtLabel: personalityLabel(generatedAt), selected, stale };
}

/**
 * 분석 히스토리 — 최신이 앞. **시드 2건**이라 목 모드를 열면 보관함 손잡이가 곧바로 서고, 두 서술을 나란히
 * 비교하는 화면에 광고 없이 도달할 수 있다(0건으로 두면 그 화면은 브라우저에서 영영 못 본다).
 * 손잡이 숨김(1건 이하)을 보려면 이 배열을 임시로 줄인다 — `myHandle=null` 스위치와 같은 방식.
 */
const personalityEntries: PersonalityEntry[] = [
  personalityEntry(PERSONALITY_NARRATIVES[0], 5, true),
  personalityEntry(PERSONALITY_NARRATIVES[1], 72, false, true), // 그 뒤 책장이 바뀐 옛 분석
];

/**
 * 책방에 걸리는 문구 = 대표 entry의 서술. 고정 문자열로 두면 보관함에서 성향을 바꿔도 책방이 안 변해
 * "대표가 옮겨갔다"를 브라우저로 확인할 수 없다 — 검증 경로가 목뿐이라 이 연결이 필요하다.
 */
const selectedNarrative = (): string =>
  personalityEntries.find((e) => e.selected)?.narrative ?? PERSONALITY_NARRATIVES[0];

const profileBooks: ProfileBook[] = [
  profileBook(11, '아무튼, 서재', '김민지', 'READING', 5_400),
  profileBook(12, '밑줄 긋는 사람', '이수연', 'FINISHED', 12_600),
  profileBook(13, '해변의 카프카', '무라카미 하루키', 'WANT_TO_READ', 0),
];

const marginEntry = (id: number, text: string, bgCode: string, hoursAgo: number): MarginEntry => ({
  id,
  text,
  bgCode,
  createdAt: isoTime(hoursAgo),
});

/**
 * 책별 여백 — 키는 `profileBooks`의 책 id다(여백은 책에 딸린 자리라 사람이 아니라 책이 축이다).
 *
 * <p>세 상태를 일부러 다 깔아 둔다: **3시간 전 글**(=발광이 브라우저에 보인다) / **30시간 전 글**
 * (=발광 없이 글만 있다 — 24시간 경계의 반대편) / **0장**(=빈 상태 문구).
 */
const marginEntries: Record<number, MarginEntry[]> = {
  // 3장인 것은 소식의 STORY 묶음(count: 3)과 같은 책이라서다 — 실서버에선 둘이 같은 행에서 나오므로
  // 「소식은 3개인데 열어 보니 2장」 같은 조합이 나올 수 없다. 목이 그걸 어기면 검증자를 헷갈리게 한다.
  11: [
    marginEntry(901, '오늘은 30분만 읽자고 앉았는데 한 시간을 넘겼다.', 'paper', 3),
    marginEntry(902, '밑줄 그은 문장이 오늘의 나를 설명한다.', 'night', 26),
    marginEntry(903, '서재라는 말이 이렇게 넓은 줄 몰랐다.', 'sea', 44),
  ],
  12: [marginEntry(911, '완독. 마지막 장을 아껴 읽었다.', 'forest', 30)],
  13: [],
  // 내 서재의 비공개 책(id 5) — 공개 전환 확인 시트의 「글 1개가 보여요」가 이 한 장에서 나온다.
  5: [marginEntry(921, '이건 아무에게도 안 보이는 메모.', 'plum', 5)],
};

/** 그 책 여백의 최신 글 시각 — 격자 발광의 재료다. 글을 남기면 이 값이 따라 움직인다. */
const lastStoryAt = (bookId: number): string | null => marginEntries[bookId]?.[0]?.createdAt ?? null;

/**
 * 그 사람의 책방 책 — **여백 시각은 본인·팔로워에게만** 실린다(서버 프라이버시 게이트를 그대로 흉내낸다).
 * 비팔로워(`밑줄러`)로 열면 발광이 통째로 사라지는 것이 브라우저에서 확인된다.
 */
function booksFor(loginId: string): ProfileBook[] {
  const user = mustFindUser(loginId);
  const visible = user.self || user.following;
  return profileBooks.map((b) => ({ ...b, lastStoryAt: visible ? lastStoryAt(b.id) : null }));
}

/**
 * 홈 피드 목 — **미리보기 3줄을 넘겨야** 「더 보기」 펼침이 브라우저에서 확인된다. 시각은 방금·시간·
 * 어제·N일 전이 다 나오게 흩어 둔다(상대 시각 표기가 한 화면에서 전부 눈에 보이도록).
 *
 * <p>STORY는 **1장·3장 두 건**을 둔다 — 단수(「글을 남겼어요」)·복수(「글 3개를 남겼어요」) 문구가
 * 한 화면에 같이 서야 두 갈래를 눈으로 가른다. 탭하면 그 책의 여백으로 점프한다(bookId).
 */
const socialEvents: SocialEvent[] = [
  { loginId: 'nabi', nickname: '나비독서', bookTitle: '데미안', type: 'FINISHED', occurredAt: isoTime(0.2),
    bookId: null, excerpt: null, count: 0 },
  { loginId: 'nabi', nickname: '나비독서', bookTitle: '아무튼, 서재', type: 'STORY', occurredAt: isoTime(3),
    bookId: 11, excerpt: '오늘은 30분만 읽자고 앉았는데 한 시간을 넘겼다.', count: 3 },
  { loginId: 'underline', nickname: '밑줄러', bookTitle: '사피엔스', type: 'STARTED', occurredAt: isoTime(3.5),
    bookId: null, excerpt: null, count: 0 },
  { loginId: 'jieun', nickname: '지은의서재', bookTitle: '밑줄 긋는 사람', type: 'STORY', occurredAt: isoTime(9),
    // 80자 넘는 원문은 서버가 79자 + … 로 잘라 준다 — 클라의 1줄 clamp가 그 위에 얹히는지 눈으로 본다.
    bookId: 12, excerpt: '읽다 말고 한참을 창밖만 봤다. 문장 하나가 오래 걸려서, 다음 장으로 넘어가는 게 아까웠고 그렇게 저녁이 다 갔다…', count: 1 },
  { loginId: 'jieun', nickname: '지은의서재', bookTitle: '미움받을 용기', type: 'FINISHED', occurredAt: isoTime(20),
    bookId: null, excerpt: null, count: 0 },
  { loginId: 'nabi', nickname: '나비독서', bookTitle: '코스모스', type: 'STARTED', occurredAt: isoTime(30),
    bookId: null, excerpt: null, count: 0 },
  { loginId: 'jieun', nickname: '지은의서재', bookTitle: '총, 균, 쇠', type: 'FINISHED', occurredAt: isoTime(96),
    bookId: null, excerpt: null, count: 0 },
];

const newsItems: NewsItem[] = [
  {
    title: '헤르만 헤세 『데미안』 출간 100년, 다시 읽히는 이유',
    link: 'https://news.google.com/rss/articles/CBMiW0FVX3lxTFBPUWlJU3V5OEowTDhVUFp5VUhy?oc=5',
    publishedAt: isoTime(5),
    bookTitle: '데미안',
    source: '한국일보',
  },
  {
    title: '“인류는 어떻게 지구를 지배했나” 『사피엔스』 개정판 출간',
    link: 'https://news.google.com/rss/articles/CBMiX0FVX3lxTE5oZV9XWEM3UlpvekcwVXA0Qkh4?oc=5',
    publishedAt: isoTime(26),
    bookTitle: '사피엔스',
    source: '한겨레',
  },
  {
    title: '아들러 심리학 열풍… 『미움받을 용기』 재조명',
    link: 'https://news.google.com/rss/articles/CBMibEFVX3lxTE5vWTZMWWFBVVdGaVhuN0NnVEFP?oc=5',
    publishedAt: isoTime(70),
    bookTitle: '미움받을 용기',
    source: '매일경제',
  },
  {
    title: '칼 세이건 탄생 90주년 특별전, 『코스모스』 초판본 공개',
    link: 'https://news.google.com/rss/articles/CBMiQ0FVX3lxTFBPUWlJU3V5OEowTDhVUFp5?oc=5',
    publishedAt: isoTime(400),
    bookTitle: '코스모스',
    source: '경향신문',
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
  /** 같은 캡처의 원문 — 숫자가 아닌 경로 변수(`/api/stories/of/{loginId}`)가 쓴다. */
  param: string;
  body: Record<string, unknown>;
  query: Record<string, string | number | undefined>;
}

type Method = 'GET' | 'POST' | 'DELETE';

/** 순서대로 첫 일치를 쓴다. 패턴은 앵커돼 있어 `/api/books`와 `/api/books/search`가 섞이지 않는다. */
const routes: [Method, RegExp, (ctx: Ctx) => unknown][] = [
  // 인증 — 목 모드는 토큰이 항상 있어 여기까지 오지 않지만, 로그아웃 후 로그인 화면을 눌러 볼 수 있게 둔다.
  ['POST', /^\/api\/toss\/(login|register|link)$/, () => ({ registered: true, token: 'dev-mock-token', loginId: MY_LOGIN_ID })],
  ['POST', /^\/api\/toss\/logout$/, () => undefined],
  // 회원 탈퇴 — 서버는 토스 재인증으로 신원을 확인하지만, 브라우저엔 토스 SDK가 없어 그 확인을 흉내낼 수
  // 없다. 여기서는 204(빈 응답)만 돌려주고 시트 문구·개폐·성공 후 화면 전이만 눈으로 확인한다.
  ['POST', /^\/api\/miniapp\/delete-account$/, () => undefined],

  ['GET', /^\/api\/dashboard$/, (): DashboardResponse => ({
    ...timerState(),
    nickname: myNickname,
    loginId: myHandle,
    previousLoginId: myPreviousHandle,
    profileCharacterCode: null,
    wantToReadBooks: bookOptions('WANT_TO_READ'),
    graph: buildGraph(),
    emailVerified: true,
  })],

  // 웹 history 섬이 쓰던 그 엔드포인트 — 미니앱 기록 탭이 잔디 아래 목록에 쓴다(서버 무변경).
  ['GET', /^\/api\/history$/, () => ({ months: buildMonths() })],

  // 홈 피드 박스 — **목에서는 뉴스를 켜 둔다**: 서버 킬스위치를 내리면 「책 뉴스」 탭이 안 그려져,
  // 목까지 꺼 두면 그 탭 UI를 브라우저로 확인할 길이 아예 없다.
  // 링크가 구글 리다이렉트인 것도 실제와 같게 뒀다 — 출처를 링크에서 파생하면 전부 news.google.com이
  // 되는 걸 목 모드에서 그대로 재현해야 회귀를 눈으로 잡을 수 있다.
  ['GET', /^\/api\/home-feed$/, (): HomeFeedResponse => ({
    social: socialEvents,
    newsEnabled: true,
    news: newsItems,
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
    state.completedSessions += 1;
    // graph를 동봉해야 홈이 재조회 없이 잔디를 갱신한다(`StopResponse` 계약).
    return {
      sessionId: nextId(),
      untagged,
      firstCompletedSession: state.completedSessions === 1,
      timer: timerState(),
      graph: buildGraph(),
    };
  }],
  ['POST', /^\/api\/sessions\/(\d+)\/tag-book$/, ({ id, body }) => {
    const book = books.find((b) => b.id === body.bookId);
    return { sessionId: id, bookTitle: book?.title ?? '알 수 없는 책' };
  }],

  ['POST', /^\/api\/miniapp\/goal$/, ({ body }) => {
    state.goalSeconds = body.dailyIncrementSeconds as number;
    return undefined;
  }],
  // 핸들 만들기 — 서버처럼 소문자로 정규화해 돌려준다(클라이언트가 입력값을 그대로 믿지 않는지 눈으로 확인).
  // 중복(409)도 실물처럼 재현한다: 목 사용자(nabi 등)의 아이디를 넣어 보면 시트가 서버 문구를 띄운다.
  ['POST', /^\/api\/miniapp\/handle$/, ({ body }) => {
    if (myHandle !== null) throw new ApiError(409, '이미 아이디가 있어요.');
    const normalized = String(body.loginId ?? '').trim().toLowerCase();
    if (!/^[a-z0-9_]{3,20}$/.test(normalized)) {
      throw new ApiError(400, '사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자로 지어 주세요.');
    }
    if (users.some((u) => u.loginId === normalized)) {
      throw new ApiError(409, '이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요.');
    }
    myHandle = normalized;
    // 내 행의 핸들도 같이 바꾼다 — 안 그러면 방금 만든 핸들로 「내 책방 보기」가 404가 난다(목만의 함정).
    const me = users.find((u) => u.self);
    if (me !== undefined) me.loginId = normalized;
    return { loginId: normalized };
  }],
  // 아이디 바꾸기 — 서버 가드 순서를 그대로 재현한다(소진 → 형식 → 지금과 동일 → 중복).
  // 소진 화면은 `myPreviousHandle` 초기값을 임시로 바꿔 본다(위 스위치 주석).
  ['POST', /^\/api\/miniapp\/handle\/change$/, ({ body }) => {
    if (myHandle === null || myPreviousHandle !== null) {
      throw new ApiError(409, '아이디 변경은 평생 1번이에요. 이미 사용했어요.');
    }
    const normalized = String(body.loginId ?? '').trim().toLowerCase();
    if (!/^[a-z0-9_]{3,20}$/.test(normalized) || normalized === myHandle) {
      throw new ApiError(400, '사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자, 지금 아이디와 달라야 해요.');
    }
    if (users.some((u) => u.loginId === normalized)) {
      throw new ApiError(409, '이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요.');
    }
    myPreviousHandle = myHandle;
    myHandle = normalized;
    // 생성 핸들러와 같은 이유로 내 행도 동기화한다 — 안 그러면 「내 책방」이 옛 아이디로 404가 난다.
    const me = users.find((u) => u.self);
    if (me !== undefined) me.loginId = normalized;
    return { loginId: normalized };
  }],
  // 닉네임 변경 — 서버처럼 trim한 값을 저장하고 그대로 에코한다(대시보드 인사말이 바뀌는지 눈으로 확인).
  ['POST', /^\/api\/miniapp\/nickname$/, ({ body }) => {
    const trimmed = String(body.nickname ?? '').trim();
    if (trimmed === '' || trimmed.length > 30) {
      throw new ApiError(400, '닉네임은 공백 없이 30자 이내로 입력해 주세요.');
    }
    myNickname = trimmed;
    // 내 행의 닉네임도 같이 바꾼다 — 안 그러면 검색·팔로우 목록의 내 이름만 옛것으로 남는다(목만의 함정).
    const me = users.find((u) => u.self);
    if (me !== undefined) me.nickname = trimmed;
    return { nickname: trimmed };
  }],
  // 리워드 광고는 브라우저에 SDK가 없어 애초에 도달하지 않는다 — 혹시 눌러도 무해하게 실패시킨다.
  ['POST', /^\/api\/miniapp\/debt-waiver$/, () => {
    throw new ApiError(400, '목 모드에서는 광고 보상을 쓸 수 없어요');
  }],

  // ── 서재 ──
  // `storyCount`는 응답 시점에 센다 — 글을 남기면 공개 전환 확인 시트의 개수도 따라 움직여야 한다.
  ['GET', /^\/api\/books$/, () => ({
    searchEnabled: true,
    books: books.map((b) => ({ ...b, storyCount: marginEntries[b.id]?.length ?? 0 })),
  })],
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
    return { q, results, myLoginId: myHandle, rateLimited: false };
  }],
  ['GET', /^\/api\/follow-list$/, ({ query }) => ({
    type: query.type,
    users: query.type === 'following' ? users.filter((u) => u.following) : users.filter((u) => !u.self),
    myLoginId: myHandle,
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
      personality: selectedNarrative(),
      personalityTags: [
        { label: '소설', clickable: true },
        { label: '인문', clickable: true },
        { label: '완독형', clickable: false },
      ],
      books: booksFor(user.loginId),
    };
  }],
  ['GET', /^\/api\/profile\/books$/, ({ query }) => {
    const rows = booksFor(String(query.loginId));
    return {
      books: query.status === undefined ? rows : rows.filter((b) => b.status === STATUS_LABEL[query.status as BookStatus]),
    };
  }],
  // 드릴다운은 성향 근거 확인용 임시 목록이라 발광이 없다 — 서버도 lastStoryAt을 채우지 않는다.
  ['GET', /^\/api\/profile\/personality-tag$/, () => ({ books: profileBooks.slice(0, 2) })],

  // ── 성향 추출 광고 관문 ──
  // 광고 자체는 브라우저에 SDK가 없어 못 뜨지만, 그 뒤 흐름(잔여 차감 → 소진 안내)은 여기서 눈으로 확인한다.
  // 콜드스타트·소진 화면은 personalityRemaining 초기값을 임시로 0으로 바꿔 본다(myHandle=null 스위치와 같은 방식).
  ['GET', /^\/api\/personality\/status$/, (): PersonalityStatus => ({
    coldStart: false,
    hasSelected: personalityEntries.some((e) => e.selected),
    adRefreshRemaining: personalityRemaining,
    adRefreshLimit: PERSONALITY_LIMIT,
    // 보관함의 재료 — 서버 status가 최신순으로 실어 주는 그 배열이다.
    entries: [...personalityEntries],
  })],
  ['POST', /^\/api\/personality\/ad-refresh$/, (): PersonalityMutation => {
    if (personalityRemaining <= 0) {
      throw new ApiError(429, '오늘 분석 횟수를 모두 썼어요.');
    }
    personalityRemaining -= 1;
    // 서버 `reanalyze`는 새 행을 **후보로만** 추가한다(첫 분석만 자동 대표) — 그 계약을 그대로 흉내낸다.
    const first = personalityEntries.length === 0;
    narrativeCursor = (narrativeCursor + 1) % PERSONALITY_NARRATIVES.length;
    personalityEntries.unshift(personalityEntry(PERSONALITY_NARRATIVES[narrativeCursor], 0, first));
    // 서버 `evictOldestNonSelected` — 상한을 넘으면 대표를 뺀 가장 오래된 후보를 버린다(대표는 보호).
    if (personalityEntries.length > PERSONALITY_MAX_HISTORY) {
      const victim = personalityEntries.map((e, i) => [e, i] as const).reverse().find(([e]) => !e.selected);
      if (victim !== undefined) personalityEntries.splice(victim[1], 1);
    }
    return {
      view: { state: 'READY', narrative: personalityEntries[0].narrative, entries: [...personalityEntries] },
      refreshRemaining: personalityRemaining,
      refreshLimit: PERSONALITY_LIMIT,
    };
  }],
  ['POST', /^\/api\/personality\/select\/(\d+)$/, ({ id }) => {
    personalityEntries.forEach((e) => { e.selected = e.id === id; });
    return {
      view: { state: 'READY', narrative: selectedNarrative(), entries: [...personalityEntries] },
      refreshRemaining: personalityRemaining,
      refreshLimit: PERSONALITY_LIMIT,
    };
  }],

  ['GET', /^\/api\/blocks$/, () => ({ blocked: [...blocked], myLoginId: myHandle })],
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

  // ── 여백 ──
  // 「누구의 + 어느 책」 두 축. 응답은 자기완결이라(책 라벨 동봉) 홈 소식에서 곧장 점프해도 화면이 그려진다.
  ['GET', /^\/api\/stories\/of\/([^/]+)$/, ({ param, query }): MarginResponse => {
    const user = mustFindUser(param);
    const bookId = Number(query.bookId);
    const book = marginBookOf(bookId, user.self);
    // 서버는 남의 책 id(IDOR)·남의 PRIVATE 책·미존재를 전부 404로 준다 — 목도 존재를 누설하지 않는다.
    if (book === null) throw new ApiError(404, '글을 찾을 수 없습니다');
    const following = user.self ? false : user.following;
    return {
      book,
      ownerNickname: user.nickname,
      self: user.self,
      following,
      // 비팔로워에게는 빈 배열 — 글이 있는지 없는지도 새지 않는다.
      entries: user.self || following ? [...(marginEntries[bookId] ?? [])] : [],
    };
  }],
  ['POST', /^\/api\/stories$/, ({ body }) => {
    const bookId = Number(body.bookId);
    // 쓰는 것은 언제나 내 책이다 — 내 서재 책(홈·서재 문)과 내 책방 책(격자)이 둘 다 여기로 온다.
    if (marginBookOf(bookId, true) === null) throw new ApiError(400, '글을 남길 수 없습니다');
    const entry: MarginEntry = {
      id: nextId(),
      text: body.text as string,
      bgCode: (body.bgCode as string | null) ?? null,
      createdAt: new Date().toISOString(),
    };
    // 맨 앞 = 최신(서버가 최신순으로 준다). 이 한 줄이 곧 격자 발광 갱신이다(`lastStoryAt`이 여기서 파생).
    marginEntries[bookId] = [entry, ...(marginEntries[bookId] ?? [])];
    return entry;
  }],
  ['DELETE', /^\/api\/stories\/(\d+)$/, ({ id }) => {
    for (const bookId of Object.keys(marginEntries)) {
      const entries = marginEntries[Number(bookId)];
      const index = entries.findIndex((e) => e.id === id);
      if (index >= 0) {
        entries.splice(index, 1);
        return undefined;
      }
    }
    throw new ApiError(404, '글을 찾을 수 없습니다');
  }],
];

/**
 * 여백을 열 수 있는 책 — 없으면 `null`(=404).
 *
 * <p>두 갈래를 합친다: 책방 격자의 공개 책(`profileBooks`)과 **내 서재 책**(`books`). 후자가 이번에
 * 열린 길이다 — 홈 문·서재 문이 서재 책을 가리키므로, 여기서 안 찾으면 문을 눌러도 404가 난다.
 * 남의 비공개 책은 애초에 이 목에 없다(서버가 존재 자체를 누설하지 않는 것과 같은 자리).
 */
function marginBookOf(bookId: number, self: boolean): MarginResponse['book'] | null {
  const social = profileBooks.find((b) => b.id === bookId);
  if (social !== undefined) {
    return { id: social.id, title: social.title, author: social.author, coverUrl: social.coverUrl, isPublic: true };
  }
  if (!self) return null;
  const mine = books.find((b) => b.id === bookId);
  return mine === undefined
    ? null
    : { id: mine.id, title: mine.title, author: mine.author, coverUrl: mine.coverUrl, isPublic: mine.isPublic };
}

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
      return handler({ id: Number(match[1]), param: match[1], body, query }) as T;
    }
  }
  throw new ApiError(404, `${MARKER} 목에 없는 경로: ${method} ${path}`);
}
