import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { SearchRow } from './api';
import {
  ApiError,
  REPORT_REASONS,
  STORY_BG_CODES,
  UnauthorizedError,
  addBook,
  blockUser,
  changeBookStatus,
  createHandle,
  createStory,
  deleteBook,
  deleteStory,
  fetchBlocks,
  fetchDashboard,
  fetchFollowList,
  fetchPersonalityTagBooks,
  fetchProfile,
  fetchProfileBooks,
  fetchShelf,
  fetchStoryFeed,
  fetchStoryViewers,
  follow,
  markStoryViewed,
  linkAccount,
  login,
  logout,
  register,
  reportUser,
  request,
  searchBooks,
  searchUsers,
  setBookVisibility,
  setGoal,
  token,
  unblockUser,
  unfollow,
  validateHandleFormat,
  waiveDebt,
} from './api';
import { tossLogin } from './toss';

vi.mock('./toss', () => ({ tossLogin: vi.fn() }));

const tossLoginMock = vi.mocked(tossLogin);

/** 응답 최소 스텁 — api.ts는 상태코드와 본문 텍스트만 본다. */
function response(status: number, body = '') {
  return { status, ok: status >= 200 && status < 300, text: async () => body };
}

function lastRequest() {
  const fetchMock = vi.mocked(globalThis.fetch);
  return fetchMock.mock.calls[fetchMock.mock.calls.length - 1] as [string, RequestInit];
}

function headerOf(name: string) {
  return (lastRequest()[1].headers as Record<string, string>)[name];
}

beforeEach(() => {
  const store = new Map<string, string>();
  // 실제 localStorage처럼 값을 문자열로 강제한다 — 그래야 null 저장이 "null" 문자열로 드러나 테스트에 걸린다.
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, String(v)),
    removeItem: (k: string) => void store.delete(k),
  });
  vi.stubGlobal('fetch', vi.fn());
  tossLoginMock.mockReset();
  tossLoginMock.mockResolvedValue({ authorizationCode: 'code-1', referrer: 'SANDBOX' });
});

describe('토스 인증 플로우', () => {
  it('registered:true면 토큰을 저장한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ registered: true, token: 'tok-abc', loginId: 'goospel' })) as never,
    );

    const result = await login();

    expect(result.registered).toBe(true);
    expect(result.loginId).toBe('goospel');
    expect(token.get()).toBe('tok-abc');
  });

  it('registered:false면 토큰을 저장하지 않는다 — 선택 화면으로 분기해야 하므로', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ registered: false, token: null, loginId: null })) as never,
    );

    const result = await login();

    expect(result.registered).toBe(false);
    expect(token.get()).toBeNull();
  });

  it('인증 호출마다 appLogin을 새로 불러 fresh 인가코드를 보낸다 — 인가코드는 일회성', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ registered: true, token: 'tok', loginId: null })) as never,
    );
    tossLoginMock
      .mockResolvedValueOnce({ authorizationCode: 'first', referrer: 'SANDBOX' })
      .mockResolvedValueOnce({ authorizationCode: 'second', referrer: 'SANDBOX' });

    await login();
    expect(JSON.parse(lastRequest()[1].body as string).authorizationCode).toBe('first');

    await register();
    expect(tossLoginMock).toHaveBeenCalledTimes(2);
    expect(JSON.parse(lastRequest()[1].body as string).authorizationCode).toBe('second');
  });

  it('linkAccount는 인가코드와 함께 연결 코드를 보낸다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ registered: true, token: 'tok', loginId: 'goospel' })) as never,
    );

    await linkAccount('ABCD1234');

    const [url, init] = lastRequest();
    expect(url).toContain('/api/toss/link');
    expect(JSON.parse(init.body as string)).toEqual({
      authorizationCode: 'code-1',
      referrer: 'SANDBOX',
      linkCode: 'ABCD1234',
    });
  });

  it('logout은 서버 호출이 실패해도 로컬 토큰을 폐기한다', async () => {
    token.set('tok');
    vi.mocked(globalThis.fetch).mockRejectedValue(new Error('네트워크 끊김'));

    await logout();

    expect(token.get()).toBeNull();
  });
});

describe('Bearer 호출·에러 계약', () => {
  it('토큰이 있으면 Authorization: Bearer 헤더를 붙인다', async () => {
    token.set('tok-xyz');
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"nickname":"구스펠"}') as never);

    await fetchDashboard();

    expect(headerOf('Authorization')).toBe('Bearer tok-xyz');
  });

  it('토큰이 없으면 Authorization 헤더를 붙이지 않는다 — 로그인 요청은 아직 토큰이 없다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ registered: false, token: null, loginId: null })) as never,
    );

    await login();

    expect(headerOf('Authorization')).toBeUndefined();
  });

  it('401이면 토큰을 폐기하고 UnauthorizedError를 던진다 — 재로그인 플로우 진입점', async () => {
    token.set('만료된-토큰');
    vi.mocked(globalThis.fetch).mockResolvedValue(response(401) as never);

    await expect(fetchDashboard()).rejects.toBeInstanceOf(UnauthorizedError);
    expect(token.get()).toBeNull();
  });

  it('409면 서버 메시지를 담은 ApiError를 던진다 — 이미 연결된 계정을 사용자에게 알린다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(409, '이미 다른 토스 계정과 연결된 계정입니다') as never,
    );

    const error = await linkAccount('ABCD1234').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).message).toBe('이미 다른 토스 계정과 연결된 계정입니다');
    expect(token.get()).toBeNull();
  });

  it('fetch 자체가 실패하면 한국어 안내로 바꿔 던진다 — 영문 "Failed to fetch"가 그대로 노출됐다', async () => {
    token.set('tok');
    vi.mocked(globalThis.fetch).mockRejectedValue(new TypeError('Failed to fetch'));

    const error = (await fetchDashboard().catch((e: unknown) => e)) as Error;

    expect(error.message).toBe('네트워크 연결을 확인해 주세요');
    expect(error.message).not.toContain('Failed to fetch');
    // 401 재로그인 분기와 섞이면 안 된다 — 끊긴 네트워크로 토큰을 버리면 복구가 재로그인이 된다.
    expect(error.name).not.toBe('UnauthorizedError');
    expect(token.get()).toBe('tok');
  });

  it('본문 없는 200도 성공으로 처리한다 — /api/miniapp/goal은 빈 본문을 준다', async () => {
    token.set('tok');
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '') as never);

    await expect(setGoal(3600)).resolves.toBeUndefined();
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ dailyIncrementSeconds: 3600 });
  });
});

describe('리워드 광고 보상 (waiveDebt)', () => {
  beforeEach(() => {
    token.set('tok');
  });

  it('POST /api/miniapp/debt-waiver — 지울 날짜를 보내지 않는다(서버가 고른다)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, '{"waivedDate":"2026-08-09","waivedSeconds":1800,"timer":{}}') as never,
    );

    const result = await waiveDebt();

    const [url, init] = lastRequest();
    expect(url).toContain('/api/miniapp/debt-waiver');
    expect(init.method).toBe('POST');
    // 요청 본문에 날짜가 없다는 것이 이 API의 계약이다 — 조작 표면 제거(설계 §4).
    expect(JSON.parse(init.body as string)).toEqual({});
    expect(result.waivedSeconds).toBe(1800);
    expect(result.waivedDate).toBe('2026-08-09');
  });

  it('오늘 이미 사용(409)은 서버 평문 메시지를 그대로 올린다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(409, '오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.') as never,
    );

    const error = await waiveDebt().catch((e: unknown) => e);

    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).message).toBe('오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.');
  });
});

describe('request() — 메서드·쿼리 확장', () => {
  beforeEach(() => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{}') as never);
  });

  it('query를 조립하고 undefined 값은 생략한다', async () => {
    await request('/api/books/search', { query: { q: '자바 & 스프링', page: 2, cursor: undefined } });

    const params = new URL(lastRequest()[0]).searchParams;
    expect(params.get('q')).toBe('자바 & 스프링'); // 인코딩 왕복 — 공백·&가 살아 돌아온다
    expect(params.get('page')).toBe('2'); // 숫자도 문자열로 직렬화
    expect(params.has('cursor')).toBe(false);
  });

  it('query가 없거나 전부 undefined면 물음표를 붙이지 않는다', async () => {
    await request('/api/dashboard', { query: { cursor: undefined } });

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/dashboard');
  });

  it('DELETE 메서드를 그대로 전달하고 본문 없이 보낸다', async () => {
    await request('/api/stories/7', { method: 'DELETE' });

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/stories/7');
    expect(init.method).toBe('DELETE');
    expect(init.body).toBeUndefined();
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
  });

  it('body가 있으면 method 없이도 POST — 기존 호출부 하위호환', async () => {
    await setGoal(60);

    const [, init] = lastRequest();
    expect(init.method).toBe('POST');
    expect(headerOf('Content-Type')).toBe('application/json');
  });

  it('body도 method도 없으면 GET — 기존 조회 호출부 하위호환', async () => {
    await fetchDashboard();

    const [, init] = lastRequest();
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
  });

  it('확장 경로에서도 401은 토큰 폐기 + UnauthorizedError', async () => {
    token.set('만료된-토큰');
    vi.mocked(globalThis.fetch).mockResolvedValue(response(401) as never);

    await expect(request('/api/stories/7', { method: 'DELETE' })).rejects.toBeInstanceOf(UnauthorizedError);
    expect(token.get()).toBeNull();
  });
});

describe('서재 API', () => {
  const searchRow: SearchRow = {
    title: '자바 최적화',
    author: '벤저민 J. 에번스',
    isbn13: '9791162241776',
    coverUrl: 'https://img/cover.jpg',
    publisher: '한빛미디어',
    purchaseLink: 'https://aladin/1',
    category: '컴퓨터/모바일',
    pubDate: '2019-01-01',
    owned: false,
  };

  beforeEach(() => {
    token.set('tok');
  });

  it('책장은 GET /api/books로 받아 섹션 분류용 status를 그대로 전달한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(
        200,
        JSON.stringify({
          searchEnabled: true,
          books: [{ id: 7, title: '자바 최적화', status: 'READING', isPublic: false }],
        }),
      ) as never,
    );

    const shelf = await fetchShelf();

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/books');
    expect(init.method).toBe('GET');
    expect(shelf.searchEnabled).toBe(true);
    expect(shelf.books[0].status).toBe('READING');
  });

  it('검색은 q를 쿼리로 보내고 0건이면 빈 목록을 준다 — 결과 없음 안내의 근거', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, JSON.stringify({ results: [] })) as never);

    const result = await searchBooks('없는 책 & 제목');

    expect(new URL(lastRequest()[0]).searchParams.get('q')).toBe('없는 책 & 제목');
    expect(result.results).toEqual([]);
  });

  it('책 추가는 검색결과의 서버 필드만 보낸다 — owned는 UI 전용이라 서버로 새지 않는다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ id: 9, title: '자바 최적화', status: 'READING' })) as never,
    );

    const added = await addBook(searchRow, 'READING');

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/books');
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({
      title: '자바 최적화',
      author: '벤저민 J. 에번스',
      isbn13: '9791162241776',
      coverUrl: 'https://img/cover.jpg',
      publisher: '한빛미디어',
      purchaseLink: 'https://aladin/1',
      category: '컴퓨터/모바일',
      pubDate: '2019-01-01',
      status: 'READING',
    });
    expect(added.id).toBe(9);
  });

  it('상태 변경·공개 토글·삭제는 각 책 경로로 POST한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"id":7}') as never);

    await changeBookStatus(7, 'FINISHED');
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/books/7/status');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ status: 'FINISHED' });

    await setBookVisibility(7, 'PUBLIC');
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/books/7/visibility');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ visibility: 'PUBLIC' });

    await deleteBook(7);
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/books/7/delete');
    expect(lastRequest()[1].method).toBe('POST');
  });

  it('없는 책(404)은 상태를 실은 ApiError로 갈라진다 — 401 재로그인과 섞이지 않는다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(404, '책을 찾을 수 없습니다') as never);

    const error = await deleteBook(7).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).message).toBe('책을 찾을 수 없습니다');
    expect(token.get()).toBe('tok'); // 401이 아니므로 토큰은 살아 있다
  });

  it('HTML 에러 페이지 본문은 사용자에게 노출하지 않는다 — 서버가 error 뷰를 렌더해 돌려준다', async () => {
    // 실측: /api/** 예외는 GlobalExceptionHandler가 잡아 Thymeleaf error 뷰(HTML)로 응답한다.
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(500, '<!DOCTYPE html><html><body>요청을 처리할 수 없습니다.</body></html>') as never,
    );

    const error = (await fetchShelf().catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(500);
    expect(error.message).not.toContain('<');
    expect(error.message).toContain('500');
  });
});

describe('소셜 API — 검색·팔로우·책방·차단·신고', () => {
  beforeEach(() => {
    token.set('tok');
  });

  it('유저 검색은 q를 쿼리로 보내고, 0건이면 빈 결과를 준다 — 검색 유도 문구의 근거', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ q: 'zz', results: [], recommendations: [], myLoginId: 'me', rateLimited: false })) as never,
    );

    const page = await searchUsers('zz');

    const [url, init] = lastRequest();
    expect(new URL(url).pathname).toBe('/api/search');
    expect(new URL(url).searchParams.get('q')).toBe('zz');
    expect(init.method).toBe('GET');
    expect(page.results).toEqual([]);
    expect(page.rateLimited).toBe(false);
  });

  it('미니앱 신규 계정(login_id=null)은 myLoginId가 null로 온다 — 내 책방 진입을 막는 근거(§5-1)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ q: null, results: [], recommendations: [], myLoginId: null, rateLimited: false })) as never,
    );

    expect((await searchUsers('goo')).myLoginId).toBeNull();
  });

  it('팔로우 목록은 type을 쿼리로 갈라 받는다 — 팔로잉/팔로워가 같은 경로를 쓴다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ type: 'following', users: [], myLoginId: 'me' })) as never,
    );

    await fetchFollowList('following');

    const url = new URL(lastRequest()[0]);
    expect(url.pathname).toBe('/api/follow-list');
    expect(url.searchParams.get('type')).toBe('following');
  });

  it('팔로우·언팔로우는 loginId를 실어 보내고 서버가 준 following으로 버튼 상태를 정한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"following":true}') as never);

    await expect(follow('goospel')).resolves.toEqual({ following: true });
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/follow');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ loginId: 'goospel' });

    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"following":false}') as never);

    await expect(unfollow('goospel')).resolves.toEqual({ following: false });
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/unfollow');
  });

  it('자기 자신 팔로우는 서버가 400으로 거절한다 — 그 문구를 그대로 보여준다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(400, '자기 자신은 팔로우할 수 없습니다') as never);

    const error = (await follow('me').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(400);
    expect(error.message).toBe('자기 자신은 팔로우할 수 없습니다');
  });

  it('책방은 loginId 쿼리로 받는다 — self면 팔로우 버튼을 숨기는 근거가 응답에 실려 온다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(
        200,
        JSON.stringify({
          loginId: 'goospel',
          nickname: '구스펠',
          profileCharacterCode: null,
          followerCount: 3,
          followingCount: 5,
          following: false,
          self: true,
          personality: '한우물형',
          personalityTags: [{ label: '한우물형', clickable: true }],
          books: [],
        }),
      ) as never,
    );

    const profile = await fetchProfile('goospel');

    expect(new URL(lastRequest()[0]).searchParams.get('loginId')).toBe('goospel');
    expect(profile.self).toBe(true);
    expect(profile.personalityTags[0].clickable).toBe(true);
  });

  it('차단·비공개 상대의 책방은 404 — 존재를 누설하지 않는 서버 계약을 그대로 전달한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(404, '프로필을 찾을 수 없습니다') as never);

    const error = (await fetchProfile('hidden').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(404);
    expect(error.message).toBe('프로필을 찾을 수 없습니다');
  });

  it('책방 책 목록은 status 필터를 쿼리로 보내고, 전체(undefined)면 파라미터를 생략한다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"books":[]}') as never);

    await fetchProfileBooks('goospel', 'FINISHED');
    expect(new URL(lastRequest()[0]).searchParams.get('status')).toBe('FINISHED');

    await fetchProfileBooks('goospel');
    const url = new URL(lastRequest()[0]);
    expect(url.pathname).toBe('/api/profile/books');
    expect(url.searchParams.has('status')).toBe(false);
  });

  it('책BTI 태그 드릴다운은 loginId와 tag를 함께 보낸다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"books":[]}') as never);

    await fetchPersonalityTagBooks('goospel', '한우물형');

    const url = new URL(lastRequest()[0]);
    expect(url.pathname).toBe('/api/profile/personality-tag');
    expect(url.searchParams.get('loginId')).toBe('goospel');
    expect(url.searchParams.get('tag')).toBe('한우물형');
  });

  it('차단·차단해제는 blocked 상태를 돌려준다 — 차단 목록이 유일한 해제 경로다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"blocked":true}') as never);

    await expect(blockUser('spammer')).resolves.toEqual({ blocked: true });
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/block');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ loginId: 'spammer' });

    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"blocked":false}') as never);

    await expect(unblockUser('spammer')).resolves.toEqual({ blocked: false });
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/unblock');

    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ blocked: [], myLoginId: 'me' })) as never,
    );
    await fetchBlocks();
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/blocks');
    expect(lastRequest()[1].method).toBe('GET');
  });

  it('신고는 사유와 상세를 함께 보낸다 — 사유 목록은 서버 enum과 같은 값이어야 접수된다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"reported":true}') as never);

    await expect(reportUser('spammer', 'SPAM', '광고 도배')).resolves.toEqual({ reported: true });
    expect(lastRequest()[0]).toBe('http://localhost:8080/api/report');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({
      loginId: 'spammer',
      reason: 'SPAM',
      detail: '광고 도배',
    });
    // 서버 ReportReason enum(SPAM·HARASSMENT·INAPPROPRIATE·OTHER)과 값이 어긋나면 전부 OTHER로 접수된다.
    expect(REPORT_REASONS.map((r) => r.value)).toEqual(['SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'OTHER']);
  });
});

/**
 * 핸들(@아이디) 만들기 — 토스로 가입한 계정(login_id=null)이 소셜에 발견되게 하는 유일한 경로.
 *
 * <p>클라이언트는 **형식만** 프리검증한다(즉시 피드백). 예약어·중복은 흉내내지 않는다 — 서버가 유일한
 * 권위이고(400/409 평문), 클라이언트가 규칙을 복제하면 서버와 어긋난 순간 조용히 거짓말을 한다.
 */
describe('핸들 형식 프리검증 (validateHandleFormat)', () => {
  it('3~20자 [a-z0-9_]는 통과한다 — 경계(3자·20자) 포함', () => {
    expect(validateHandleFormat('abc')).toBeNull();
    expect(validateHandleFormat('a'.repeat(20))).toBeNull();
    expect(validateHandleFormat('book_worm_1')).toBeNull();
  });

  it('대문자는 오류가 아니다 — 서버가 소문자로 정규화해 저장한다', () => {
    expect(validateHandleFormat('Valid_1')).toBeNull();
  });

  it('짧거나 길면 안내 문구를 준다 — 2자·21자 경계', () => {
    expect(validateHandleFormat('ab')).not.toBeNull();
    expect(validateHandleFormat('a'.repeat(21))).not.toBeNull();
  });

  it('빈 입력·공백뿐인 입력은 안내 문구를 준다', () => {
    expect(validateHandleFormat('')).not.toBeNull();
    expect(validateHandleFormat('   ')).not.toBeNull();
  });

  it('한글·공백·기호가 섞이면 안내 문구를 준다 — 서버 LOGIN_ID_PATTERN과 같은 규칙', () => {
    expect(validateHandleFormat('한글아이디')).not.toBeNull();
    expect(validateHandleFormat('has space')).not.toBeNull();
    expect(validateHandleFormat('dot.name')).not.toBeNull();
  });

  it('앞뒤 공백은 서버처럼 떼고 본다 — 키보드가 붙인 공백으로 막히지 않게', () => {
    expect(validateHandleFormat('  goospel  ')).toBeNull();
  });
});

describe('핸들 만들기 API (createHandle)', () => {
  beforeEach(() => {
    token.set('tok');
  });

  it('POST /api/miniapp/handle — 입력을 그대로 보내고 서버가 정규화한 값을 돌려받는다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '{"loginId":"bookworm_1"}') as never);

    const result = await createHandle('BookWorm_1');

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/miniapp/handle');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ loginId: 'BookWorm_1' });
    // 정규화는 서버 몫 — 클라이언트가 소문자로 미리 바꿔 보내면 정규화 규칙이 두 곳으로 갈라진다.
    expect(result.loginId).toBe('bookworm_1');
  });

  it('중복(409)은 서버 평문 메시지를 그대로 올린다 — 시트가 그 문구를 띄운다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(409, '이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요.') as never,
    );

    const error = (await createHandle('bookclub').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(409);
    expect(error.message).toBe('이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요.');
  });
});

describe('독서 스토리 API', () => {
  it('피드는 GET — 팔로우가 없으면 mine·groups가 빈 응답이다(실패가 아니라 0건)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify({ mine: null, groups: [] })) as never,
    );

    const feed = await fetchStoryFeed();

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/stories/feed');
    expect(lastRequest()[1].method).toBe('GET');
    expect(feed.mine).toBeNull();
    expect(feed.groups).toEqual([]);
  });

  it('피드는 내 스토리를 groups가 아니라 mine으로 준다 — 핸들 없는 계정도 자기 목록을 본다(설계 §5-1)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(
        200,
        JSON.stringify({
          mine: { loginId: null, nickname: '나', profileCharacterCode: null, allViewed: true, stories: [{ id: 9 }] },
          groups: [],
        }),
      ) as never,
    );

    const feed = await fetchStoryFeed();

    expect(feed.mine?.loginId).toBeNull(); // 핸들 없이도 mine이 채워진다 = /of/{loginId} 불필요
    expect(feed.mine?.stories[0].id).toBe(9);
  });

  it('작성은 문장·책·배경을 함께 보낸다 — 책 미첨부는 null(생략이 아니라 명시)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, JSON.stringify({ id: 3, text: '한 문장' })) as never);

    await createStory('한 문장', null, 'paper');

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/stories');
    expect(lastRequest()[1].method).toBe('POST');
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ text: '한 문장', bookId: null, bgCode: 'paper' });
  });

  it('배경 코드는 서버 팔레트(Story.BG_CODES)와 같은 값이어야 400이 안 난다', () => {
    expect(STORY_BG_CODES.map((bg) => bg.code)).toEqual(['paper', 'night', 'forest', 'sunset', 'sea', 'plum']);
  });

  it('삭제는 DELETE + 본문 없음, 남의 스토리는 404를 그대로 전달한다(IDOR 비노출 계약)', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '') as never);

    await deleteStory(7);

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/stories/7');
    expect(lastRequest()[1].method).toBe('DELETE');
    expect(lastRequest()[1].body).toBeUndefined();

    vi.mocked(globalThis.fetch).mockResolvedValue(response(404, '<html>error</html>') as never);
    const error = (await deleteStory(8).catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(404);
  });

  it('열람 기록은 POST이고 본문이 없다 — 서버가 @RequestBody를 안 받는다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '') as never);

    await markStoryViewed(7);

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/stories/7/view');
    expect(lastRequest()[1].method).toBe('POST');
    expect(lastRequest()[1].body).toBeUndefined();
  });

  it('뷰어 목록은 그 스토리 경로의 GET이다', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      response(200, JSON.stringify([{ loginId: 'a', nickname: 'A', profileCharacterCode: null, viewedAt: 'x' }])) as never,
    );

    const viewers = await fetchStoryViewers(7);

    expect(lastRequest()[0]).toBe('http://localhost:8080/api/stories/7/viewers');
    expect(lastRequest()[1].method).toBe('GET');
    expect(viewers[0].nickname).toBe('A');
  });
});
