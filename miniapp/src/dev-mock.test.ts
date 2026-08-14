import { afterEach, describe, expect, it, vi } from 'vitest';

import type {
  DashboardResponse,
  HomeFeedResponse,
  MonthlySection,
  MyBookSummary,
  ShelfResponse,
  StopResponse,
  StoryCard,
  StoryFeedResponse,
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

  it('스토리 작성 — 내 스토리 그룹(mine)에 붙는다', async () => {
    const created = await mockRequest<StoryCard>('/api/stories', { body: { text: '목 스토리', bookId: null, bgCode: 'sea' } });

    const feed = await mockRequest<StoryFeedResponse>('/api/stories/feed', {});
    expect(feed.mine!.stories.some((s) => s.id === created.id)).toBe(true);
  });

  it('날짜별 기록 — 최신 월 먼저, 달 안에서도 최신 일 먼저다(서버 순서를 그대로 흉내낸다)', async () => {
    const { months } = await mockRequest<{ months: MonthlySection[] }>('/api/history', {});

    expect(months.length).toBeGreaterThan(1);
    expect(months[0].month > months[1].month).toBe(true);
    const [first, second] = months[0].days;
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
