// API 클라이언트 계약(v1 REQ-30 — 미니앱 api.ts 이식). 토큰은 SecureStore(Keychain)에 둔다.
const mockStore = new Map<string, string>();
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  setItemAsync: jest.fn(async (k: string, v: string) => void mockStore.set(k, v)),
  deleteItemAsync: jest.fn(async (k: string) => void mockStore.delete(k)),
}));

import { account, ApiError, appleSignIn, failText, idTokenSignIn, library, margins, NetworkError, request, session, study, UnauthorizedError, writing } from './client';

const fetchMock = jest.fn();
global.fetch = fetchMock as unknown as typeof fetch;

function reply(status: number, body: string) {
  return { ok: status >= 200 && status < 300, status, text: async () => body } as Response;
}

beforeEach(() => {
  mockStore.clear();
  fetchMock.mockReset();
});

describe('REQ-30 · request', () => {
  test('REQ-30 · 401이면 토큰을 지우고 UnauthorizedError', async () => {
    await session.save({ token: 'tok', userId: 17, webAccount: true });
    fetchMock.mockResolvedValueOnce(reply(401, ''));
    await expect(request('/api/dashboard')).rejects.toBeInstanceOf(UnauthorizedError);
    expect(mockStore.size).toBe(0);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok');
  });

  test('REQ-30 · fetch 실패는 NetworkError', async () => {
    await session.save({ token: 'tok', userId: 17, webAccount: false });
    fetchMock.mockRejectedValueOnce(new TypeError('Network request failed'));
    await expect(request('/api/dashboard')).rejects.toBeInstanceOf(NetworkError);
    expect(mockStore.get('booktimer.token')).toBe('tok'); // 연결 실패는 로그아웃이 아니다
  });

  test('REQ-30 · 서버 문구를 그대로 쓴다', async () => {
    const msg = '코드가 맞지 않거나 시간이 지났어요 — 웹 설정에서 새 코드를 받아 주세요';
    fetchMock.mockResolvedValueOnce(reply(400, msg));
    const err = await request<never>('/api/x', { body: {} }).catch((e: ApiError) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe(msg);
    expect(err.status).toBe(400);
    // HTML 오류 페이지(전역 핸들러)는 문구로 쓰지 않는다
    fetchMock.mockResolvedValueOnce(reply(500, '<!DOCTYPE html><html>…'));
    const html = await request<never>('/api/x').catch((e: ApiError) => e);
    expect(html.message).toBe('요청에 실패했어요 (500)');
  });

  test('REQ-30 · 로그인 요청(anonymous)의 401은 서버 문구 그대로 ApiError, 세션은 그대로', async () => {
    await session.save({ token: 'tok', userId: 17, webAccount: false });
    fetchMock.mockResolvedValueOnce(reply(401, 'Apple 신원 확인 실패(서버 문구)'));
    const err = await request<never>('/api/app/auth/apple', { body: {}, anonymous: true }).catch((e: ApiError) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe('Apple 신원 확인 실패(서버 문구)');
    expect(err.status).toBe(401);
    expect(mockStore.get('booktimer.token')).toBe('tok');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
});

describe('Apple 로그인 흐름(v1 §4.3 — LOGIN 먼저, 없으면 같은 토큰으로 REGISTER/LINK)', () => {
  const body = (i: number) => JSON.parse(fetchMock.mock.calls[i][1].body);

  test('시작 모드: 모르는 신원이면 REGISTER(ageConfirmed) 뒤 세션을 저장한다', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(200, JSON.stringify({ registered: false, token: null, created: false, webAccount: false, userId: null })))
      .mockResolvedValueOnce(reply(200, JSON.stringify({ registered: true, token: 'new', created: true, webAccount: false, userId: 99 })));
    const r = await appleSignIn({ mode: 'start', identityToken: 'jwt', code: '' });
    expect(body(0)).toEqual({ identityToken: 'jwt', intent: 'LOGIN' });
    expect(body(1)).toEqual({ identityToken: 'jwt', intent: 'REGISTER', ageConfirmed: true });
    expect(r.notice).toBeUndefined();
    expect(await session.token()).toBe('new');
    expect(mockStore.get('booktimer.userId')).toBe('99');
  });

  test('웹 계정 모드: 모르는 신원이면 LINK(코드), 이미 앱 계정이 있으면 그 계정으로 들어오고 안내한다', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(200, JSON.stringify({ registered: false, token: null, created: false, webAccount: false, userId: null })))
      .mockResolvedValueOnce(reply(200, JSON.stringify({ registered: true, token: 'linked', created: false, webAccount: true, userId: 17 })));
    await appleSignIn({ mode: 'link', identityToken: 'jwt', code: ' ABCD1234 ' });
    expect(body(1)).toEqual({ identityToken: 'jwt', intent: 'LINK', linkCode: 'ABCD1234' });
    expect(mockStore.get('booktimer.webAccount')).toBe('true');

    fetchMock.mockReset();
    fetchMock.mockResolvedValueOnce(reply(200, JSON.stringify({ registered: true, token: 'app', created: false, webAccount: false, userId: 5 })));
    const r = await appleSignIn({ mode: 'link', identityToken: 'jwt', code: 'ABCD1234' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(r.notice).toContain('그 계정으로 들어왔어요');
    expect(await session.token()).toBe('app');
  });
});

describe('구글 · 카카오 로그인 흐름(PR-P ③ — LOGIN 먼저, 없으면 같은 토큰으로 REGISTER · LINK 없음)', () => {
  const body = (i: number) => JSON.parse(fetchMock.mock.calls[i][1].body);
  const url = (i: number) => fetchMock.mock.calls[i][0];
  const unknown = (extra: object = {}) =>
    reply(200, JSON.stringify({ registered: false, token: null, created: false, webAccount: false, userId: null, authProvider: null, appleLinked: false, googleLinked: false, unverifiedWebAccount: false, ...extra }));
  const joined = (extra: object = {}) =>
    reply(200, JSON.stringify({ registered: true, token: 'new', created: true, webAccount: false, userId: 99, authProvider: 'KAKAO', appleLinked: false, googleLinked: false, unverifiedWebAccount: false, ...extra }));
  const never = async () => { throw new Error('확인 창이 뜨면 안 된다'); };

  test('REQ-86 · 구글 · 카카오도 LOGIN 먼저, 모르는 신원이면 같은 토큰으로 REGISTER(ageConfirmed) 뒤 세션을 저장한다 — 문은 /api/app/auth/{google|kakao}, LINK는 보내지 않는다', async () => {
    for (const provider of ['google', 'kakao'] as const) {
      for (const ageConfirmed of [true, false]) {
        mockStore.clear();
        fetchMock.mockReset();
        fetchMock.mockResolvedValueOnce(unknown()).mockResolvedValueOnce(joined());
        expect(await idTokenSignIn({ provider, identityToken: 'jwt', ageConfirmed, confirmUnverified: never })).toEqual({});
        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect([url(0), url(1)]).toEqual([`https://booktimer.app/api/app/auth/${provider}`, `https://booktimer.app/api/app/auth/${provider}`]);
        expect(body(0)).toEqual({ identityToken: 'jwt', intent: 'LOGIN' });
        expect(body(1)).toEqual({ identityToken: 'jwt', intent: 'REGISTER', ageConfirmed }); // 화면 값 그대로 — 하드코딩 참이 아니다
        expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
        expect(await session.token()).toBe('new');
        expect(mockStore.get('booktimer.userId')).toBe('99');
      }
    }
    // 이미 있는 신원이면 LOGIN 한 번으로 끝난다
    mockStore.clear();
    fetchMock.mockReset();
    fetchMock.mockResolvedValueOnce(joined({ token: 'old', userId: 5 }));
    await idTokenSignIn({ provider: 'google', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: never });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(await session.token()).toBe('old');
  });

  test('REQ-156 · 구글 · 카카오 로그인도 원 가입 경로 · Apple 연결 · 구글 고정 여부를 기기에 남긴다', async () => {
    fetchMock.mockResolvedValueOnce(joined({ webAccount: true, userId: 17, authProvider: 'GOOGLE', appleLinked: true, googleLinked: true }));
    await idTokenSignIn({ provider: 'google', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: never });
    expect(await session.account()).toEqual({ authProvider: 'GOOGLE', appleLinked: true, googleLinked: true, webAccount: true });

    fetchMock.mockReset();
    fetchMock.mockResolvedValueOnce(unknown()).mockResolvedValueOnce(joined());
    await idTokenSignIn({ provider: 'kakao', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: never });
    expect(await session.account()).toEqual({ authProvider: 'KAKAO', appleLinked: false, googleLinked: false, webAccount: false });
  });

  test('REQ-79 · (앱) 구글 LOGIN이 인증 안 된 웹 계정을 알리면 확인을 받은 뒤에만 REGISTER한다 — 거절이면 REGISTER 0 · 세션 0', async () => {
    let asked = 0;
    fetchMock.mockResolvedValueOnce(unknown({ unverifiedWebAccount: true }));
    const no = await idTokenSignIn({ provider: 'google', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: async () => (asked++, false) });
    expect(no).toEqual({ cancelled: true });
    expect(asked).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(mockStore.size).toBe(0);

    fetchMock.mockReset();
    fetchMock.mockResolvedValueOnce(unknown({ unverifiedWebAccount: true })).mockResolvedValueOnce(joined({ authProvider: 'GOOGLE' }));
    const yes = await idTokenSignIn({ provider: 'google', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: async () => (asked++, true) });
    expect(yes).toEqual({});
    expect(asked).toBe(2);
    expect(body(1)).toEqual({ identityToken: 'jwt', intent: 'REGISTER', ageConfirmed: true });
    expect(await session.token()).toBe('new');
  });

  test('REQ-79 · (앱) 다른 구글 계정에 고정된 이메일(409)이면 서버 문구 그대로 던지고 세션은 그대로다', async () => {
    await session.save({ token: 'tok', userId: 3, webAccount: false });
    const msg = '이 이메일은 다른 구글 계정과 이어져 있어요';
    fetchMock.mockResolvedValueOnce(reply(409, msg));
    const err = await idTokenSignIn({ provider: 'google', identityToken: 'jwt', ageConfirmed: true, confirmUnverified: never }).catch((e: ApiError) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).message).toBe(msg);
    expect((err as ApiError).status).toBe(409);
    expect(mockStore.get('booktimer.token')).toBe('tok');
    expect(mockStore.get('booktimer.userId')).toBe('3');
  });
});

test('REQ-125 · 검색어는 encodeURIComponent로 싣는다', async () => {
  fetchMock.mockResolvedValueOnce(reply(200, JSON.stringify({ results: [] })));
  await library.search('데미안 & 헤세');
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/books/search?q=%EB%8D%B0%EB%AF%B8%EC%95%88%20%26%20%ED%97%A4%EC%84%B8');
});

test('REQ-40 · 쪽지 만들기 본문은 bookId · quote · text뿐이다(shared · bgCode 없음)', async () => {
  fetchMock.mockResolvedValueOnce(reply(200, '{}'));
  await margins.add({ bookId: 7, quote: null, text: '좋다' });
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ bookId: 7, quote: null, text: '좋다' });
});

test('M1 · 쪽지 API 경로 · 메서드는 서버 AppMarginApiController와 같다', async () => {
  fetchMock.mockResolvedValue(reply(200, '{}'));
  await margins.ofBook(3);
  await margins.bookless();
  await margins.get(70);
  await margins.add({ bookId: null, quote: null, text: '책 없는 쪽지' });
  await margins.edit(70, { quote: null, text: '고친 쪽지', revision: 0, exported: true });
  await margins.delete(70);
  await margins.ai(70, { step: 'ASK', revision: 1, focus: null });
  const sent = fetchMock.mock.calls.map(([url, init]) => [url.replace('https://booktimer.app', ''), init.method, init.body && JSON.parse(init.body)]);
  expect(sent).toEqual([
    ['/api/app/books/3/margins', 'GET', undefined],
    ['/api/app/margins?book=none', 'GET', undefined],
    ['/api/app/margins/70', 'GET', undefined],
    ['/api/app/margins', 'POST', { bookId: null, quote: null, text: '책 없는 쪽지' }],
    ['/api/app/margins/70', 'PUT', { quote: null, text: '고친 쪽지', revision: 0, exported: true }],
    ['/api/app/margins/70', 'DELETE', undefined],
    ['/api/app/margins/70/ai', 'POST', { step: 'ASK', revision: 1, focus: null }],
  ]);
});

test('실패 문구 · 서버 평문과 연결 문구는 그대로, 문구 없는 실패(HTML 오류 페이지)는 화면 문구로 바꾼다', () => {
  const fb = '상태를 바꾸지 못했어요 — 잠시 뒤 다시 해 주세요';
  expect(failText(new ApiError(404, '책을 찾을 수 없어요'), fb)).toBe('책을 찾을 수 없어요');
  expect(failText(new NetworkError(), fb)).toBe(new NetworkError().message);
  expect(failText(new ApiError(400, '요청에 실패했어요 (400)'), fb)).toBe(fb);
  expect(failText(new Error('boom'), fb)).toBe(fb);
});

test('REQ-98 · 공부 시작은 {bookId}, 정지는 {}를 싣는다', async () => {
  fetchMock.mockResolvedValue(reply(200, '{}'));
  await study.start(3);
  await study.start(null);
  await study.stop();
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/study/start');
  expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ bookId: 3 });
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ bookId: null });
  expect(fetchMock.mock.calls[2][0]).toBe('https://booktimer.app/api/study/stop');
  expect(fetchMock.mock.calls[2][1].method).toBe('POST');
  expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({});
});

test('REQ-99 · 공부 서재 빼기는 POST /api/study/books/{id}/delete다', async () => {
  fetchMock.mockResolvedValue(reply(200, '{"deleted":true}'));
  await study.remove(12);
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/study/books/12/delete');
  expect(fetchMock.mock.calls[0][1].method).toBe('POST');
});

// ── 설정 · 로그아웃 · 계정 삭제(PR-I) ─────────────────────────────────────────
test('REQ-156 · 로그인하면 원 가입 경로·Apple 연결·구글 고정 여부를 기기에 남기고 세션을 지우면 함께 지운다', async () => {
  fetchMock.mockResolvedValueOnce(
    reply(200, JSON.stringify({ registered: true, token: 't', created: false, webAccount: true, userId: 17, authProvider: 'GOOGLE', appleLinked: true, googleLinked: true, unverifiedWebAccount: false })),
  );
  await appleSignIn({ mode: 'start', identityToken: 'jwt', code: '' });
  expect(await session.account()).toEqual({ authProvider: 'GOOGLE', appleLinked: true, googleLinked: true, webAccount: true });

  await session.clear();
  expect(await session.account()).toEqual({ authProvider: null, appleLinked: false, googleLinked: false, webAccount: false });
  expect(mockStore.size).toBe(0);

  // PR-I 전에 로그인한 옛 세션 — 셋 없이 저장돼 있다
  await session.save({ token: 'old', userId: 3, webAccount: false });
  expect(await session.account()).toEqual({ authProvider: null, appleLinked: false, googleLinked: false, webAccount: false });
});

test('REQ-154 · 동의 상태는 GET /api/app/ai-consent로 읽고, 거두기는 PUT agree:false다', async () => {
  fetchMock.mockResolvedValue(reply(200, JSON.stringify({ aiConsent: false, aiConsentAt: null })));
  await account.consent();
  await writing.consent(false);
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/app/ai-consent');
  expect(fetchMock.mock.calls[0][1].method).toBe('GET');
  expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  expect(fetchMock.mock.calls[1][0]).toBe('https://booktimer.app/api/app/ai-consent');
  expect(fetchMock.mock.calls[1][1].method).toBe('PUT');
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ agree: false });
});

test('REQ-155 · 로그아웃은 POST /api/app/logout이다', async () => {
  await session.save({ token: 'tok', userId: 17, webAccount: false });
  fetchMock.mockResolvedValueOnce(reply(204, ''));
  await account.logout();
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/app/logout');
  expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok');
});

test('REQ-157 · 탈퇴는 provider APPLE과 방금 받은 identityToken·authorizationCode를 싣는다', async () => {
  fetchMock.mockResolvedValueOnce(reply(204, ''));
  await account.remove({ provider: 'APPLE', identityToken: 'jwt', authorizationCode: 'code' });
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/app/account/delete');
  expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ provider: 'APPLE', identityToken: 'jwt', authorizationCode: 'code' });
});
