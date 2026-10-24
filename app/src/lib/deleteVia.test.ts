jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import { CAN_CONFIRM, DELETE_FAILED, deleteAccount, deleteVia } from './deleteVia';

// 탈퇴 갈래(v1 REQ-87 · REQ-156) · Apple 탈퇴 흐름(REQ-157).
const ALL = new Set<'GOOGLE' | 'KAKAO'>(['GOOGLE', 'KAKAO']);
const acc = (authProvider: string | null, googleLinked = false, webAccount = false) => ({ authProvider, googleLinked, webAccount });

test('REQ-87 · 원 가입이 APPLE·GOOGLE·KAKAO면 그 제공자로 앱 안에서 지운다', () => {
  expect(deleteVia(acc('APPLE'), ALL)).toBe('APPLE');
  expect(deleteVia(acc('GOOGLE', true, true), ALL)).toBe('GOOGLE');
  expect(deleteVia(acc('KAKAO'), ALL)).toBe('KAKAO');
});

test('REQ-87 · LOCAL·TOSS는 googleLinked면 구글로, 아니면 웹 삭제 화면으로 보낸다', () => {
  expect(deleteVia(acc('LOCAL', true, true), ALL)).toBe('GOOGLE');
  expect(deleteVia(acc('TOSS', false, true), ALL)).toBe('WEB');
  expect(deleteVia(acc('LOCAL', false, true), ALL)).toBe('WEB');
});

test('REQ-156 · 지금 바이너리(구글·카카오 확인 수단 없음)에선 구글·카카오·구글 고정 계정도 웹 삭제 화면으로 보낸다', () => {
  expect(CAN_CONFIRM.size).toBe(0);
  expect(deleteVia(acc('GOOGLE', true, true))).toBe('WEB');
  expect(deleteVia(acc('KAKAO'))).toBe('WEB');
  expect(deleteVia(acc('LOCAL', true, true))).toBe('WEB');
  expect(deleteVia(acc('APPLE'))).toBe('APPLE'); // 양성 대조
});

test('REQ-156 · 로그인 정보가 없는 옛 세션은 webAccount로 고른다(거짓 = Apple · 참 = 웹)', () => {
  expect(deleteVia(acc(null, false, false))).toBe('APPLE');
  expect(deleteVia(acc(null, false, true))).toBe('WEB');
});

function deps(over: Partial<Parameters<typeof deleteAccount>[0]> = {}) {
  const calls: string[] = [];
  const bodies: unknown[] = [];
  const d = {
    confirm: async () => (calls.push('confirm'), { identityToken: 'jwt', authorizationCode: 'code' }),
    remove: async (b: unknown) => (calls.push('remove'), bodies.push(b), undefined),
    clearDrafts: async () => void calls.push('clearDrafts'),
    clearSession: async () => void calls.push('clearSession'),
    ...over,
  };
  return { d, calls, bodies };
}

test('REQ-157 · 탈퇴가 204면 기기 초안을 지운 뒤 세션을 지운다', async () => {
  const { d, calls, bodies } = deps();
  expect(await deleteAccount(d)).toEqual({ done: true });
  expect(calls).toEqual(['confirm', 'remove', 'clearDrafts', 'clearSession']);
  expect(bodies).toEqual([{ provider: 'APPLE', identityToken: 'jwt', authorizationCode: 'code' }]);
});

test('REQ-157 · 탈퇴가 204인데 기기 초안 지우기가 실패해도 세션은 지운다(서버 계정은 이미 지웠다)', async () => {
  const calls: string[] = [];
  const { d } = deps({
    clearDrafts: async () => { calls.push('clearDrafts'); throw new Error('kv'); },
    clearSession: async () => void calls.push('clearSession'),
  });
  expect(await deleteAccount(d)).toEqual({ done: true });
  expect(calls.filter((c) => c === 'clearSession')).toHaveLength(1);
});

test('REQ-157 · Apple 창을 닫거나 탈퇴가 실패하면 아무것도 지우지 않는다(409면 웹 설정으로 안내)', async () => {
  const cleared = (calls: string[]) => calls.filter((c) => c.startsWith('clear'));

  const closed = deps({ confirm: async () => null });
  expect(await deleteAccount(closed.d)).toEqual({ cancelled: true });
  expect(closed.calls).toEqual([]);

  const fail = (e: Error) => deps({ remove: async () => { throw e; } });
  const forbidden = fail(new ApiError(403, '본인 확인에 실패해 탈퇴를 진행하지 않았어요'));
  expect(await deleteAccount(forbidden.d)).toEqual({ error: '본인 확인에 실패해 탈퇴를 진행하지 않았어요' });

  const web = fail(new ApiError(409, '웹에서 만든 계정은 웹 설정에서 지울 수 있어요'));
  expect(await deleteAccount(web.d)).toEqual({ error: '웹에서 만든 계정은 웹 설정에서 지울 수 있어요', web: true });

  const offline = fail(new NetworkError());
  expect(await deleteAccount(offline.d)).toEqual({ error: '인터넷에 연결되지 않았어요 — 연결한 뒤 다시 해 주세요' });

  const html = fail(new ApiError(500, '요청에 실패했어요 (500)'));
  expect(await deleteAccount(html.d)).toEqual({ error: DELETE_FAILED });

  for (const x of [closed, forbidden, web, offline, html]) expect(cleared(x.calls)).toEqual([]);
});

test('REQ-157 · Apple 창이 실패하면 Apple 확인 실패 문구이고 아무것도 보내지 않는다', async () => {
  const { d, calls } = deps({ confirm: async () => { throw new Error('auth failed'); } });
  expect(await deleteAccount(d)).toEqual({ error: 'Apple 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(calls).toEqual([]);
});
