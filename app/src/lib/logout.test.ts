jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { NetworkError, UnauthorizedError } from '../api/client';
import { logout, signedOutNotice } from './logout';

// 로그아웃(v1 REQ-38 · REQ-155) · 로그인 화면 일회성 알림.
function deps(pending: number, ok: boolean, revoke: () => Promise<unknown> = async () => undefined) {
  const calls: string[] = [];
  const d = {
    pending: async () => pending,
    confirm: async () => (calls.push('confirm'), ok),
    revoke: async () => (calls.push('revoke'), revoke()),
    clearDrafts: async () => void calls.push('clearDrafts'),
    clearSession: async () => void calls.push('clearSession'),
    authProvider: null as string | null,
    kakaoLogout: async (): Promise<unknown> => void calls.push('kakaoLogout'),
  };
  return { d, calls };
}

test('REQ-112 · 카카오 세션 로그아웃은 SDK 로그아웃을 부른다', async () => {
  const kakao = deps(0, true);
  kakao.d.authProvider = 'KAKAO';
  expect(await logout(kakao.d)).toBe('done');
  expect(kakao.calls).toEqual(['revoke', 'kakaoLogout', 'clearDrafts', 'clearSession']);

  for (const authProvider of ['GOOGLE', 'APPLE', null]) {
    const other = deps(0, true);
    other.d.authProvider = authProvider;
    expect(await logout(other.d)).toBe('done');
    expect(other.calls).toEqual(['revoke', 'clearDrafts', 'clearSession']); // 구글 · Apple은 기기에 토큰을 두지 않는다
  }

  // 카카오 쪽이 실패해도 기기에선 로그아웃한다
  const failed = deps(0, true);
  failed.d.authProvider = 'KAKAO';
  failed.d.kakaoLogout = async () => { failed.calls.push('kakaoLogout'); throw new Error('kakao'); };
  expect(await logout(failed.d)).toBe('done');
  expect(failed.calls).toEqual(['revoke', 'kakaoLogout', 'clearDrafts', 'clearSession']);
});

test('REQ-38 · 로그아웃 때 미전송이 있으면 확인 단계, 없으면 바로 지운다', async () => {
  const kept = deps(2, false);
  expect(await logout(kept.d)).toBe('kept');
  expect(kept.calls).toEqual(['confirm']);

  const yes = deps(2, true);
  expect(await logout(yes.d)).toBe('done');
  expect(yes.calls).toEqual(['confirm', 'revoke', 'clearDrafts', 'clearSession']);

  const none = deps(0, false);
  expect(await logout(none.d)).toBe('done');
  expect(none.calls).toEqual(['revoke', 'clearDrafts', 'clearSession']);
});

test('REQ-155 · 서버 토큰 폐기가 실패해도(연결·401) 기기에서는 로그아웃한다', async () => {
  for (const e of [new NetworkError(), new UnauthorizedError()]) {
    const { d, calls } = deps(0, true, async () => { throw e; });
    expect(await logout(d)).toBe('done');
    expect(calls.filter((c) => c === 'clearDrafts')).toHaveLength(1);
    expect(calls.filter((c) => c === 'clearSession')).toHaveLength(1);
  }
});

test('REQ-155 · 기기 초안 지우기가 실패해도 세션은 지운다(서버 토큰은 이미 폐기했다)', async () => {
  const { d, calls } = deps(0, true);
  d.clearDrafts = async () => { calls.push('clearDrafts'); throw new Error('kv'); };
  expect(await logout(d)).toBe('done');
  expect(calls.filter((c) => c === 'clearSession')).toHaveLength(1);
});

test('REQ-155 · 일회성 알림은 한 번만 꺼낸다', () => {
  signedOutNotice.set('계정을 지웠어요');
  expect(signedOutNotice.take()).toBe('계정을 지웠어요');
  expect(signedOutNotice.take()).toBeNull();
});
