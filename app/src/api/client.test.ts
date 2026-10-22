// API 클라이언트 계약(v1 REQ-30 — 미니앱 api.ts 이식). 토큰은 SecureStore(Keychain)에 둔다.
const mockStore = new Map<string, string>();
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  setItemAsync: jest.fn(async (k: string, v: string) => void mockStore.set(k, v)),
  deleteItemAsync: jest.fn(async (k: string) => void mockStore.delete(k)),
}));

import { ApiError, appleSignIn, NetworkError, request, session, UnauthorizedError } from './client';

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
