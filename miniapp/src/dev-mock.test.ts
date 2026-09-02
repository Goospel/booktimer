import { afterEach, describe, expect, it, vi } from 'vitest';

import type {
  DashboardResponse,
  HomeFeedResponse,
  MarginEntry,
  MarginResponse,
  MonthlySection,
  MyBookSummary,
  PersonalityMutation,
  PersonalityStatus,
  ProfileBook,
  ShelfResponse,
  StopResponse,
  StudyCalendarResponse,
  StudyHistoryResponse,
  StudyState,
  TimerState,
} from './api';
import { ApiError } from './api';
// 소스 자체를 읽는다 — "목 코드가 프로드 번들에 안 들어간다"는 배선(dynamic import + DEV 게이트)이
// 지켜지는지는 실행이 아니라 소스 형태로만 계측된다(실행 시점엔 이미 dev 번들이다).
import apiSource from './api.ts?raw';
import { mockRequest } from './dev-mock';

/**
 * 브라우저 dev 목 모드 계측기 — 목은 "화면이 뜬다"가 목적이라, 각 핸들러가 화면이 실제로 읽는
 * 계약(잔디 방향·stop의 graph 동봉·뮤테이션 반영)을 지키는지만 본다. 픽스처 문구는 재지 않는다.
 */
describe('dev-mock 핸들러', () => {
  it('대시보드 — 책과 함께 잔디를 준다. weeks[0]이 최신 주다(#730 규약)', async () => {
    const data = await mockRequest<DashboardResponse>('/api/dashboard', {});

    expect(data.readingBooks.length).toBeGreaterThan(0);
    // weeks[0]이 과거 주면 잔디가 좌우로 뒤집혀 그려진다 — 미니앱이 실제로 한 번 겪은 버그(#730).
    const latest = data.graph.weeks[0].at(-1)!.date!;
    const older = data.graph.weeks[1].at(-1)!.date!;
    expect(latest > older).toBe(true);
  });

  it('측정 시작 → 종료 — stop이 timer와 graph를 함께 준다(홈이 재조회 없이 갱신한다)', async () => {
    const started = await mockRequest<TimerState>('/api/sessions/start', { body: { bookId: 1 } });
    expect(started.hasActiveSession).toBe(true);
    expect(started.activeStartedAt).not.toBeNull();

    const stopped = await mockRequest<StopResponse>('/api/sessions/stop', { body: {} });
    expect(stopped.timer.hasActiveSession).toBe(false);
    expect(stopped.graph.weeks.length).toBeGreaterThan(0);
  });

  it('공부 시작 → 종료 — 종료분이 오늘 누적에 얹히고, 계약(409)까지 서버와 같다', async () => {
    const started = await mockRequest<StudyState>('/api/study/start', { body: {} });
    expect(started.hasActiveSession).toBe(true);
    expect(started.activeStartedAt).not.toBeNull();

    // 중복 시작은 서버처럼 409다 — 목이 서버보다 무르면 그 경로를 브라우저로 확인할 길이 없다.
    await expect(mockRequest('/api/study/start', { body: {} })).rejects.toMatchObject({ status: 409 });

    const stopped = await mockRequest<StudyState>('/api/study/stop', { body: {} });
    expect(stopped.hasActiveSession).toBe(false);
    expect(stopped.activeStartedAt).toBeNull();

    // 무세션 stop도 409(서버 계약 그대로).
    await expect(mockRequest('/api/study/stop', { body: {} })).rejects.toMatchObject({ status: 409 });
  });

  it('대시보드가 study 블록을 동봉한다 — 미니앱이 진입 모드를 여기서 정한다', async () => {
    const data = await mockRequest<DashboardResponse>('/api/dashboard', {});

    expect(data.study).toBeDefined();
    expect(data.study!.hasActiveSession).toBe(false);
  });

  it('공부 목표 — 저장하면 응답과 대시보드 study 블록에 그대로 실린다(음수는 서버처럼 400)', async () => {
    const saved = await mockRequest<StudyState>('/api/study/goal', { body: { dailyGoalSeconds: 5400 } });
    expect(saved.goalSeconds).toBe(5400);

    const data = await mockRequest<DashboardResponse>('/api/dashboard', {});
    expect(data.study!.goalSeconds).toBe(5400);

    // 음수 거부가 목에도 있어야 「저장 실패 문구」를 브라우저로 확인할 수 있다.
    await expect(mockRequest('/api/study/goal', { body: { dailyGoalSeconds: -1 } })).rejects.toMatchObject({
      status: 400,
    });
    // 거절된 값이 상태를 물들이지 않는다.
    expect((await mockRequest<StudyState>('/api/study/goal', { body: { dailyGoalSeconds: 0 } })).goalSeconds).toBe(0);

    // 0 = 「목표 없음」이고, 화면 두 곳이 그걸 읽는다 — 목이 0을 무시하면 「목표 없이 지내기」를
    // 브라우저로 밟아도 홈·달력이 옛 목표를 계속 그린다.
    expect((await mockRequest<DashboardResponse>('/api/dashboard', {})).study!.goalSeconds).toBe(0);
    const month = new Date().toISOString().slice(0, 7);
    expect(
      (await mockRequest<StudyCalendarResponse>('/api/study/calendar', { query: { month } })).goalSeconds,
    ).toBe(0);
  });

  it('공부 목표는 독서 목표를 건드리지 않는다 — 목에서도 두 목표가 갈려 있다', async () => {
    const before = await mockRequest<DashboardResponse>('/api/dashboard', {});
    await mockRequest('/api/study/goal', { body: { dailyGoalSeconds: 3600 } });
    const after = await mockRequest<DashboardResponse>('/api/dashboard', {});

    expect(after.todayGoalSeconds).toBe(before.todayGoalSeconds);
  });

  /**
   * `offsetDays`일 전의 `YYYY-MM-DD` — 목의 `isoDate`와 같은 셈법(UTC).
   *
   * <p>달은 <b>그 날짜에서 파생</b>한다. 「오늘의 달」로 고정하면 매달 1일에 어제가 지난달이 되어
   * 목록에서 빠지고, 테스트가 <b>한 달에 하루만</b> 붉어진다(달력이라 안 그러기 쉽다).
   */
  const daysAgo = (offsetDays: number) =>
    new Date(Date.now() - offsetDays * 86_400_000).toISOString().slice(0, 10);

  it('공부 일정 달력 — 체크가 저장되고 재조회에 그대로 실린다(3상태 순환의 왕복)', async () => {
    const yesterday = daysAgo(1);
    const month = yesterday.slice(0, 7);

    await mockRequest('/api/study/check', { body: { date: yesterday, kept: true } });
    const kept = await mockRequest<StudyCalendarResponse>('/api/study/calendar', { query: { month } });
    expect(kept.days.find((d) => d.date === yesterday)?.kept).toBe(true);

    await mockRequest('/api/study/check', { body: { date: yesterday, kept: false } });
    const missed = await mockRequest<StudyCalendarResponse>('/api/study/calendar', { query: { month } });
    expect(missed.days.find((d) => d.date === yesterday)?.kept).toBe(false);

    // null = 무기록 복귀 — 서버처럼 행을 지운다(그 날이 목록에서 빠지거나 kept가 null이다).
    await mockRequest('/api/study/check', { body: { date: yesterday, kept: null } });
    const cleared = await mockRequest<StudyCalendarResponse>('/api/study/calendar', { query: { month } });
    expect(cleared.days.find((d) => d.date === yesterday)?.kept ?? null).toBeNull();
  });

  it('공부 일정 달력 — 미래 날짜 체크는 서버처럼 400이다(클라 no-op의 이중 방어)', async () => {
    await expect(mockRequest('/api/study/check', { body: { date: daysAgo(-1), kept: true } })).rejects.toMatchObject({
      status: 400,
    });
  });

  it('공부 일정 달력 — 목표와 지난 며칠의 측정 픽스처가 실린다(점이 뜨는 경로를 브라우저로 밟는다)', async () => {
    await mockRequest('/api/study/goal', { body: { dailyGoalSeconds: 3600 } });
    // 어제가 든 달을 본다 — 측정 픽스처(1~3일 전)가 반드시 걸리는 달이다.
    const month = daysAgo(1).slice(0, 7);

    const calendar = await mockRequest<StudyCalendarResponse>('/api/study/calendar', { query: { month } });

    expect(calendar.goalSeconds).toBe(3600);
    expect(calendar.days.some((d) => d.studiedSeconds > 0)).toBe(true);
  });

  /**
   * 공부 기록 — 잔디 방향 규약(#730)과 목록 순서를 목도 지켜야 한다. 목이 서버와 다른 방향을 주면
   * 화면 버그가 아니라 목 버그로 시간을 태운다. 「수동 기록 없음」은 공부 원장의 사실이라 함께 잠근다.
   */
  it('공부 기록 — weeks[0]이 최신 주, 월은 최신 먼저, 월 합계 = 일 합, 수동 칸 0', async () => {
    const data = await mockRequest<StudyHistoryResponse>('/api/study/history', {});

    const latest = data.graph.weeks[0].at(-1)!.date!;
    const older = data.graph.weeks[1].at(-1)!.date!;
    expect(latest > older).toBe(true);

    expect(data.months.length).toBeGreaterThan(0);
    expect(data.months.every((m, i) => i === 0 || data.months[i - 1].month > m.month)).toBe(true);
    for (const month of data.months) {
      expect(month.totalSeconds).toBe(month.days.reduce((sum, d) => sum + d.totalSeconds, 0));
      // 달 안에서도 최신 일 먼저 — 화면이 다시 정렬하지 않는다.
      expect(month.days.every((d, i) => i === 0 || month.days[i - 1].date > d.date)).toBe(true);
    }

    expect(data.graph.weeks.every((w) => w.every((d) => !d.manual))).toBe(true);
  });

  it('공부는 독서 원장에 안 섞인다 — 목에서도 잔디·기록이 안 움직인다(격리를 목이 흉내낸다)', async () => {
    const before = await mockRequest<DashboardResponse>('/api/dashboard', {});
    await mockRequest('/api/study/start', { body: {} });
    await mockRequest('/api/study/stop', { body: {} });
    const after = await mockRequest<DashboardResponse>('/api/dashboard', {});

    expect(after.graph.totalSeconds).toBe(before.graph.totalSeconds);
    expect(after.remainingSeconds).toBe(before.remainingSeconds);
  });

  it('서재 추가 — 뮤테이션이 목록에 반영된다(화면 전이가 실제처럼 보이는 최소 조건)', async () => {
    const row = { title: '목 모드 신간', author: '테스터', isbn13: '9791111111111', coverUrl: null,
      publisher: null, purchaseLink: null, category: null, pubDate: null, owned: false };
    const added = await mockRequest<MyBookSummary>('/api/books', { body: { ...row, status: 'READING' } });

    const shelf = await mockRequest<ShelfResponse>('/api/books', {});
    expect(shelf.books.some((b) => b.id === added.id && b.title === '목 모드 신간')).toBe(true);

    await mockRequest('/api/books/' + added.id + '/delete', { body: {} });
    const after = await mockRequest<ShelfResponse>('/api/books', {});
    expect(after.books.some((b) => b.id === added.id)).toBe(false);
  });

  it('여백 작성 — 그 책의 여백 목록 맨 앞에 붙는다(최신순)', async () => {
    const created = await mockRequest<MarginEntry>('/api/stories', {
      body: { text: '목 여백', bookId: 11, bgCode: 'sea' },
    });

    const margin = await mockRequest<MarginResponse>('/api/stories/of/testid', { query: { bookId: 11 } });
    expect(margin.self).toBe(true);
    expect(margin.entries[0].id).toBe(created.id);
  });

  it('여백 조회 — 책 라벨이 함께 실린다(홈 소식에서 바로 들어와도 화면이 그려진다)', async () => {
    const margin = await mockRequest<MarginResponse>('/api/stories/of/nabi', { query: { bookId: 11 } });

    expect(margin.book.id).toBe(11);
    expect(margin.book.title.length).toBeGreaterThan(0);
    expect(margin.ownerNickname.length).toBeGreaterThan(0);
  });

  it('책방 책 목록에 lastStoryAt이 실린다 — 없으면 격자 발광을 브라우저로 볼 길이 없다', async () => {
    const { books } = await mockRequest<{ books: ProfileBook[] }>('/api/profile/books', { query: { loginId: 'nabi' } });

    expect(books.some((b) => b.lastStoryAt !== null)).toBe(true);
  });

  it('홈 소식에 STORY 묶음이 단수·복수 둘 다 있다 — 문구 두 갈래를 한 화면에서 확인한다', async () => {
    const feed = await mockRequest<HomeFeedResponse>('/api/home-feed', {});
    const stories = feed.social.filter((e) => e.type === 'STORY');

    expect(stories.some((e) => e.count === 1)).toBe(true);
    expect(stories.some((e) => e.count > 1)).toBe(true);
    // 탭하면 그 책의 여백으로 점프한다 — bookId가 비면 죽은 줄이 된다.
    expect(stories.every((e) => e.bookId !== null && e.excerpt !== null)).toBe(true);
  });

  it('날짜별 기록 — 최신 월 먼저, 달 안에서도 최신 일 먼저다(서버 순서를 그대로 흉내낸다)', async () => {
    const { months } = await mockRequest<{ months: MonthlySection[] }>('/api/history', {});

    expect(months.length).toBeGreaterThan(1);
    expect(months[0].month > months[1].month).toBe(true);
    // ⚠️ 달 안 순서는 **하루짜리가 아닌 달**에서 잰다 — 매달 1~2일엔 최신 달의 일자가 한 건뿐이라
    //    `months[0].days[1]`이 `undefined`가 된다(2026-09-01 실측 실패: 날짜에 따라 붉어지는 계측기였다).
    const multiDay = months.find((m) => m.days.length > 1);
    expect(multiDay).toBeDefined();
    const [first, second] = multiDay!.days;
    expect(first.date > second.date).toBe(true);
    // 월 합계가 일자 합과 어긋나면 화면의 월 머리글이 거짓말을 한다.
    expect(months[0].totalSeconds).toBe(months[0].days.reduce((sum, d) => sum + d.totalSeconds, 0));
  });

  it('홈 피드 — 뉴스를 켜고 미리보기(3줄)보다 많이 준다. 안 그러면 뉴스 탭·「더 보기」를 브라우저로 볼 길이 없다', async () => {
    const feed = await mockRequest<HomeFeedResponse>('/api/home-feed', {});

    // 서버는 수집기 점등 전까지 false라, 목까지 꺼 두면 「책 뉴스」 탭 UI가 영영 확인 불가가 된다.
    expect(feed.newsEnabled).toBe(true);
    expect(feed.news.length).toBeGreaterThan(0);
    expect(feed.social.length).toBeGreaterThan(3);
  });

  it('핸들 만들기 — 이미 핸들이 있는 기본 픽스처에선 409다(핸들 불변 규칙을 목도 지킨다)', async () => {
    // 핸들 없는 플로우(배너→시트)는 `myHandle` 초기값을 임시로 null로 바꿔 브라우저에서 확인한다 —
    // 여기서 계측할 수 있는 건 "핸들이 있으면 못 바꾼다"뿐이다(목이 서버보다 무르면 회귀를 못 잡는다).
    await expect(mockRequest('/api/miniapp/handle', { body: { loginId: 'newhandle' } })).rejects.toMatchObject({
      status: 409,
    });
  });

  it('회원 탈퇴 — 경로가 목에 있다(없으면 404로 죽어 시트를 브라우저로 확인할 수 없다)', async () => {
    await expect(mockRequest('/api/miniapp/delete-account', { body: { authorizationCode: 'c', referrer: 'r' } }))
      .resolves.toBeUndefined();
  });

  it('목에 없는 경로는 404로 던진다 — 조용히 undefined가 흘러 화면이 빈 채로 뜨지 않게', async () => {
    await expect(mockRequest('/api/does-not-exist', {})).rejects.toBeInstanceOf(ApiError);
    await expect(mockRequest('/api/does-not-exist', {})).rejects.toMatchObject({ status: 404 });
  });

  it('메서드까지 본다 — 같은 경로라도 계약에 없는 메서드는 404다', async () => {
    await expect(mockRequest('/api/dashboard', { method: 'DELETE' })).rejects.toMatchObject({ status: 404 });
  });
});

/**
 * 목 모드 배선 — 게이트가 `VITE_DEV_MOCK`에 실제로 걸려 있는가. 이게 어긋나면 `dev:mock`이 조용히
 * 서버를 때려(로그인 안 된 401) "목이 안 먹는다"로 시간을 태운다. env를 갈아끼우고 모듈을 다시 불러야
 * 재는 값이라(플래그는 모듈 로드 시 한 번 정해진다) 이 한 건만 격리해서 본다.
 */
describe('목 모드 배선', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('VITE_DEV_MOCK=1이면 request()가 fetch 없이 목을 돌려주고, 토큰은 더미로 존재한다', async () => {
    vi.stubEnv('VITE_DEV_MOCK', '1');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    vi.resetModules(); // 플래그는 모듈 로드 시 결정되므로 새 인스턴스를 받아야 한다

    const api = await import('./api');
    // 더미 토큰이 없으면 App이 로그인 브릿지로 가고, 브라우저엔 토스 SDK가 없어 거기서 막힌다.
    expect(api.token.get()).not.toBeNull();
    const dashboard = await api.fetchDashboard();

    expect(dashboard.graph.weeks.length).toBeGreaterThan(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('플래그가 없으면(평소 dev·프로드) 목을 타지 않고 서버로 나간다', async () => {
    const fetchMock = vi.fn(async () => ({ status: 200, ok: true, text: async () => '{}' }));
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} });
    vi.resetModules();

    const api = await import('./api');
    await api.fetchDashboard();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

/**
 * 프로드 번들 격리 — 목 픽스처가 배포 번들에 실리면 안 된다(용량·정보노출).
 *
 * <p>산출물(dist) 자체 검사는 빌드가 전제라 `deploy.sh`의 `__DEV_MOCK__` 부재 검사(음성 체크)가 맡는다.
 * 여기서는 그 격리를 성립시키는 **소스 배선**만 본다 — 정적 import 하나면 게이트를 지나서도 번들에 실린다.
 */
describe('프로드 번들 격리 배선', () => {
  it('api.ts가 dev-mock을 정적으로 import하지 않는다 — 하면 무조건 번들에 실린다', () => {
    expect(apiSource).not.toMatch(/^import .*dev-mock/m);
  });

  it('목 라우팅은 dynamic import이고 import.meta.env.DEV 게이트 뒤에 있다 — 프로드에서 통째로 잘린다', () => {
    expect(apiSource).toContain("await import('./dev-mock')");
    expect(apiSource).toMatch(/import\.meta\.env\.DEV && import\.meta\.env\.VITE_DEV_MOCK === '1'/);
  });
});

/**
 * 성향 추출 관문 — 브라우저엔 광고 SDK가 없어 광고 자체는 못 보지만, 그 뒤 흐름(잔여 차감 → 소진 429)은
 * 목이 흉내내야 「내일 다시」 화면을 눈으로 확인할 수 있다. 잔여는 모듈 상태라 새로고침이 초기화다.
 */
describe('dev-mock 성향 관문', () => {
  it('status → ad-refresh → status 에서 잔여가 줄어든다', async () => {
    const before = await mockRequest<PersonalityStatus>('/api/personality/status', {});
    await mockRequest<PersonalityMutation>('/api/personality/ad-refresh', { method: 'POST' });
    const after = await mockRequest<PersonalityStatus>('/api/personality/status', {});

    expect(after.adRefreshRemaining).toBe(before.adRefreshRemaining - 1);
  });

  it('ad-refresh 응답에 미선택 최신 행이 실려 select 체이닝이 목에서도 돈다', async () => {
    const result = await mockRequest<PersonalityMutation>('/api/personality/ad-refresh', { method: 'POST' });
    const newest = result.view.entries[0];

    expect(newest).toBeDefined();
    await expect(mockRequest(`/api/personality/select/${newest.id}`, { method: 'POST' })).resolves.toBeDefined();
  });

  it('잔여를 다 쓰면 429로 던진다 — 서버의 소진 응답을 흉내낸다', async () => {
    let remaining = (await mockRequest<PersonalityStatus>('/api/personality/status', {})).adRefreshRemaining;
    while (remaining > 0) {
      await mockRequest('/api/personality/ad-refresh', { method: 'POST' });
      remaining -= 1;
    }

    await expect(mockRequest('/api/personality/ad-refresh', { method: 'POST' })).rejects.toMatchObject({ status: 429 });
  });
});

/**
 * 아이디 바꾸기 — 목이 소진 상태까지 재현해야 「이미 사용했어요」 화면을 브라우저로 볼 수 있다.
 * 모듈 상태를 영구히 바꾸므로(새로고침이 초기화) 이 파일 맨 끝에 둔다.
 */
describe('dev-mock 아이디 바꾸기', () => {
  it('바꾸면 대시보드가 새 아이디와 옛 아이디를 함께 주고, 두 번째는 소진 409다', async () => {
    const before = await mockRequest<DashboardResponse>('/api/dashboard', {});
    expect(before.previousLoginId).toBeNull(); // 기본 픽스처는 미소진 — 버튼이 뜨는 상태

    await mockRequest('/api/miniapp/handle/change', { body: { loginId: 'NewName_1' } });

    const after = await mockRequest<DashboardResponse>('/api/dashboard', {});
    expect(after.loginId).toBe('newname_1'); // 서버처럼 소문자로 정규화한다
    expect(after.previousLoginId).toBe(before.loginId);

    await expect(
      mockRequest('/api/miniapp/handle/change', { body: { loginId: 'againagain' } }),
    ).rejects.toMatchObject({ status: 409 });
  });
});
