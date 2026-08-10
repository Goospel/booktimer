import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { SearchRow } from './api';
import {
  ApiError,
  UnauthorizedError,
  addBook,
  changeBookStatus,
  deleteBook,
  fetchDashboard,
  fetchShelf,
  linkAccount,
  login,
  logout,
  register,
  request,
  searchBooks,
  setBookVisibility,
  setGoal,
  token,
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

  it('본문 없는 200도 성공으로 처리한다 — /api/miniapp/goal은 빈 본문을 준다', async () => {
    token.set('tok');
    vi.mocked(globalThis.fetch).mockResolvedValue(response(200, '') as never);

    await expect(setGoal(3600)).resolves.toBeUndefined();
    expect(JSON.parse(lastRequest()[1].body as string)).toEqual({ dailyIncrementSeconds: 3600 });
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
