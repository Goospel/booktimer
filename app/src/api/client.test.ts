// API 클라이언트 계약(v1 REQ-30 — 미니앱 api.ts 이식). 토큰은 SecureStore(Keychain)에 둔다.
const mockStore = new Map<string, string>();
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  setItemAsync: jest.fn(async (k: string, v: string) => void mockStore.set(k, v)),
  deleteItemAsync: jest.fn(async (k: string) => void mockStore.delete(k)),
}));

import { ApiError, appleSignIn, failText, library, margins, NetworkError, request, session, UnauthorizedError } from './client';

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

test('REQ-125 · 검색어는 encodeURIComponent로 싣는다', async () => {
  fetchMock.mockResolvedValueOnce(reply(200, JSON.stringify({ results: [] })));
  await library.search('데미안 & 헤세');
  expect(fetchMock.mock.calls[0][0]).toBe('https://booktimer.app/api/books/search?q=%EB%8D%B0%EB%AF%B8%EC%95%88%20%26%20%ED%97%A4%EC%84%B8');
});

test('REQ-40 · 여백 생성 본문은 bookId · quote · text뿐이다(shared · bgCode 없음)', async () => {
  fetchMock.mockResolvedValueOnce(reply(200, '{}'));
  await margins.create({ bookId: 7, quote: '  ', thought: ' 좋다 ' });
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ bookId: 7, quote: null, text: '좋다' });
});

test('실패 문구 · 서버 평문과 연결 문구는 그대로, 문구 없는 실패(HTML 오류 페이지)는 화면 문구로 바꾼다', () => {
  const fb = '상태를 바꾸지 못했어요 — 잠시 뒤 다시 해 주세요';
  expect(failText(new ApiError(404, '책을 찾을 수 없어요'), fb)).toBe('책을 찾을 수 없어요');
  expect(failText(new NetworkError(), fb)).toBe(new NetworkError().message);
  expect(failText(new ApiError(400, '요청에 실패했어요 (400)'), fb)).toBe(fb);
  expect(failText(new Error('boom'), fb)).toBe(fb);
});
