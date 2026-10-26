// Apple 창 감싸개(PR-P ③ — REQ-86). 로그인 · 탈퇴 · Apple 한 번 더가 같이 쓴다.
jest.mock('expo-apple-authentication', () => ({ signInAsync: jest.fn() }));

import { appleCredential } from './apple';

const apple = jest.requireMock('expo-apple-authentication') as { signInAsync: jest.Mock };
const appleError = (code: string) => Object.assign(new Error(code), { code });

beforeEach(() => apple.signInAsync.mockReset());

test('REQ-86 · Apple 창을 닫으면 null이고 ID 토큰이 없으면 실패다 — 이메일 · 이름은 요청하지 않는다', async () => {
  apple.signInAsync.mockResolvedValueOnce({ identityToken: 'a-jwt', authorizationCode: 'a-code' });
  expect(await appleCredential()).toEqual({ identityToken: 'a-jwt', authorizationCode: 'a-code' });
  expect(apple.signInAsync).toHaveBeenCalledWith({ requestedScopes: [] });

  apple.signInAsync.mockResolvedValueOnce({ identityToken: 'a-jwt', authorizationCode: null });
  expect(await appleCredential()).toEqual({ identityToken: 'a-jwt', authorizationCode: null });

  apple.signInAsync.mockRejectedValueOnce(appleError('ERR_REQUEST_CANCELED'));
  expect(await appleCredential()).toBeNull();

  apple.signInAsync.mockResolvedValueOnce({ identityToken: null, authorizationCode: 'a-code' });
  await expect(appleCredential()).rejects.toThrow();

  apple.signInAsync.mockRejectedValueOnce(appleError('ERR_REQUEST_FAILED'));
  await expect(appleCredential()).rejects.toThrow('ERR_REQUEST_FAILED');
});
