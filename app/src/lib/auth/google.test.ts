// 구글 로그인 감싸개(PR-P ③ — REQ-173 · REQ-86 · REQ-87 · REQ-112). expo-auth-session은 팩토리로만 — 창 · 네트워크 0.
jest.mock('expo-auth-session', () => ({
  AuthRequest: jest.fn(),
  exchangeCodeAsync: jest.fn(),
  revokeAsync: jest.fn(async () => true),
  Prompt: { SelectAccount: 'select_account' },
  ResponseType: { Code: 'code' },
}));

import { googleSignIn, revokeGoogle } from './google';
import { GOOGLE_IOS_CLIENT_ID, GOOGLE_REDIRECT_URI } from './keys';

const auth = jest.requireMock('expo-auth-session') as { AuthRequest: jest.Mock; exchangeCodeAsync: jest.Mock; revokeAsync: jest.Mock };
let prompt: jest.Mock;

beforeEach(() => {
  prompt = jest.fn();
  auth.AuthRequest.mockReset().mockImplementation(function (this: Record<string, unknown>) {
    this.codeVerifier = 'verifier-123';
    this.promptAsync = prompt;
  });
  auth.exchangeCodeAsync.mockReset();
  auth.revokeAsync.mockClear();
});

test('REQ-173 · 구글 요청은 역순 스킴 redirectUri · PKCE · openid email profile · 계정 고르기로 열고 받은 코드를 code_verifier와 바꾼다', async () => {
  prompt.mockResolvedValueOnce({ type: 'success', params: { code: 'auth-code' } });
  auth.exchangeCodeAsync.mockResolvedValueOnce({ idToken: 'g-jwt', accessToken: 'g-access' });
  expect(await googleSignIn()).toEqual({ identityToken: 'g-jwt', accessToken: 'g-access' });
  const config = auth.AuthRequest.mock.calls[0][0];
  expect(config).toEqual({
    clientId: GOOGLE_IOS_CLIENT_ID,
    redirectUri: GOOGLE_REDIRECT_URI,
    scopes: ['openid', 'email', 'profile'],
    responseType: 'code',
    prompt: 'select_account',
  });
  expect(config.usePKCE).toBeUndefined(); // 기본 참 — 끄지 않는다
  expect(prompt.mock.calls[0][0]).toEqual(expect.objectContaining({ authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth' }));
  expect(auth.exchangeCodeAsync).toHaveBeenCalledWith(
    { clientId: GOOGLE_IOS_CLIENT_ID, code: 'auth-code', redirectUri: GOOGLE_REDIRECT_URI, extraParams: { code_verifier: 'verifier-123' } },
    expect.objectContaining({ tokenEndpoint: 'https://oauth2.googleapis.com/token' }),
  );
});

test('REQ-87 · 탈퇴 확인용 구글 창은 사파리와 로그인을 나누지 않는다', async () => {
  prompt.mockResolvedValue({ type: 'success', params: { code: 'auth-code' } });
  auth.exchangeCodeAsync.mockResolvedValue({ idToken: 'g-jwt', accessToken: 'g-access' });
  await googleSignIn({ reauth: true });
  expect(prompt.mock.calls[0][1]).toEqual({ preferEphemeralSession: true });
  await googleSignIn(); // 양성 대조 — 로그인은 공유 세션
  expect(prompt.mock.calls[1][1]).toEqual({ preferEphemeralSession: false });
});

test('REQ-86 · 구글 창을 닫거나 거절하면 null이고 코드를 바꾸지 않는다', async () => {
  for (const r of [{ type: 'cancel' }, { type: 'dismiss' }, { type: 'locked' }, { type: 'error', params: { error: 'access_denied' }, error: new Error('denied') }]) {
    prompt.mockResolvedValueOnce(r);
    expect(await googleSignIn()).toBeNull();
  }
  expect(auth.exchangeCodeAsync).not.toHaveBeenCalled();

  prompt.mockResolvedValueOnce({ type: 'error', params: { error: 'server_error' }, error: new Error('server_error') });
  await expect(googleSignIn()).rejects.toThrow('server_error');
  prompt.mockResolvedValueOnce({ type: 'success', params: { code: 'auth-code' } });
  auth.exchangeCodeAsync.mockResolvedValueOnce({ accessToken: 'g-access' }); // ID 토큰 없음
  await expect(googleSignIn()).rejects.toThrow();
});

test('REQ-112 · 구글 철회는 그 액세스 토큰과 클라이언트 ID로 revocationEndpoint를 부른다', async () => {
  await revokeGoogle('g-access');
  expect(auth.revokeAsync).toHaveBeenCalledTimes(1);
  expect(auth.revokeAsync).toHaveBeenCalledWith(
    { token: 'g-access', clientId: GOOGLE_IOS_CLIENT_ID },
    expect.objectContaining({ revocationEndpoint: 'https://oauth2.googleapis.com/revoke' }),
  );
});
