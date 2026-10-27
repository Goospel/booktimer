jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import { CAN_CONFIRM, confirmFor, DELETE_FAILED, DELETE_LINE, deleteAccount, deleteCopy, deleteVia, signedOutText, UNLINK_WAIT_MS } from './deleteVia';

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

test('REQ-156 · 지금 바이너리는 구글 · 카카오로 다시 확인할 수 있다 — 구글 · 카카오 · 구글 고정 계정이 앱 안 갈래다', () => {
  expect([...CAN_CONFIRM].sort()).toEqual(['GOOGLE', 'KAKAO']);
  expect(deleteVia(acc('GOOGLE', true, true))).toBe('GOOGLE');
  expect(deleteVia(acc('KAKAO'))).toBe('KAKAO');
  expect(deleteVia(acc('LOCAL', true, true))).toBe('GOOGLE');
  expect(deleteVia(acc('APPLE'))).toBe('APPLE'); // 양성 대조
  expect(deleteVia(acc('LOCAL', false, true))).toBe('WEB'); // 구글 고정이 없으면 그대로 웹
});

test('REQ-156 · 로그인 정보가 없는 옛 세션은 webAccount로 고른다(거짓 = Apple · 참 = 웹)', () => {
  expect(deleteVia(acc(null, false, false))).toBe('APPLE');
  expect(deleteVia(acc(null, false, true))).toBe('WEB');
});

const APPLE_P = { via: 'APPLE', appleLinked: false } as const;

type Cred = { identityToken: string; authorizationCode?: string | null; accessToken?: string | null };
function deps(over: Partial<Parameters<typeof deleteAccount>[1]> = {}, cred: Cred = { identityToken: 'jwt', authorizationCode: 'code' }) {
  const calls: string[] = [];
  const bodies: unknown[] = [];
  const notices: string[] = [];
  const d = {
    confirm: async () => (calls.push('confirm'), cred),
    apple: async () => (calls.push('apple'), { identityToken: 'apple-jwt', authorizationCode: 'apple-code' }),
    remove: async (b: unknown) => (calls.push('remove'), bodies.push(b), undefined),
    unlinkKakao: async () => void calls.push('unlinkKakao'),
    revokeGoogle: async (t: string) => void calls.push(`revokeGoogle:${t}`),
    clearDrafts: async () => void calls.push('clearDrafts'),
    clearSession: async (n: string) => void (calls.push('clearSession'), notices.push(n)),
    ...over,
  };
  return { d, calls, bodies, notices };
}

test('REQ-157 · 탈퇴가 204면 기기 초안을 지운 뒤 세션을 지운다', async () => {
  const { d, calls, bodies } = deps();
  expect(await deleteAccount(APPLE_P, d)).toEqual({ done: true });
  expect(calls).toEqual(['confirm', 'remove', 'clearDrafts', 'clearSession']);
  expect(bodies).toEqual([{ provider: 'APPLE', identityToken: 'jwt', authorizationCode: 'code' }]);
});

test('REQ-157 · 탈퇴가 204인데 기기 초안 지우기가 실패해도 세션은 지운다(서버 계정은 이미 지웠다)', async () => {
  const calls: string[] = [];
  const { d } = deps({
    clearDrafts: async () => { calls.push('clearDrafts'); throw new Error('kv'); },
    clearSession: async () => void calls.push('clearSession'),
  });
  expect(await deleteAccount(APPLE_P, d)).toEqual({ done: true });
  expect(calls.filter((c) => c === 'clearSession')).toHaveLength(1);
});

test('REQ-157 · Apple 창을 닫거나 탈퇴가 실패하면 아무것도 지우지 않는다(409면 웹 설정으로 안내)', async () => {
  const cleared = (calls: string[]) => calls.filter((c) => c.startsWith('clear'));

  const closed = deps({ confirm: async () => null });
  expect(await deleteAccount(APPLE_P, closed.d)).toEqual({ cancelled: true });
  expect(closed.calls).toEqual([]);

  const fail = (e: Error) => deps({ remove: async () => { throw e; } });
  const forbidden = fail(new ApiError(403, '본인 확인에 실패해 탈퇴를 진행하지 않았어요'));
  expect(await deleteAccount(APPLE_P, forbidden.d)).toEqual({ error: '본인 확인에 실패해 탈퇴를 진행하지 않았어요' });

  const web = fail(new ApiError(409, '웹에서 만든 계정은 웹 설정에서 지울 수 있어요'));
  expect(await deleteAccount(APPLE_P, web.d)).toEqual({ error: '웹에서 만든 계정은 웹 설정에서 지울 수 있어요', web: true });

  const offline = fail(new NetworkError());
  expect(await deleteAccount(APPLE_P, offline.d)).toEqual({ error: '인터넷에 연결되지 않았어요 — 연결한 뒤 다시 해 주세요' });

  const html = fail(new ApiError(500, '요청에 실패했어요 (500)'));
  expect(await deleteAccount(APPLE_P, html.d)).toEqual({ error: DELETE_FAILED });

  for (const x of [closed, forbidden, web, offline, html]) expect(cleared(x.calls)).toEqual([]);
});

test('REQ-157 · Apple 창이 실패하면 Apple 확인 실패 문구이고 아무것도 보내지 않는다', async () => {
  const { d, calls } = deps({ confirm: async () => { throw new Error('auth failed'); } });
  expect(await deleteAccount(APPLE_P, d)).toEqual({ error: 'Apple 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(calls).toEqual([]);
});

// ── 구글 · 카카오 탈퇴(PR-P ③ — REQ-87 · 111 · 112) ─────────────────────────────
const GOOGLE_CRED = { identityToken: 'g-jwt', accessToken: 'g-access' };
const KAKAO_CRED = { identityToken: 'k-jwt' };
const credFor = (via: 'GOOGLE' | 'KAKAO') => (via === 'GOOGLE' ? GOOGLE_CRED : KAKAO_CRED);
const after = (via: 'GOOGLE' | 'KAKAO') => (via === 'GOOGLE' ? 'revokeGoogle:g-access' : 'unlinkKakao');

test('REQ-111 · Apple이 붙은 계정은 탈퇴 전에 Apple 창을 한 번 더 연다', async () => {
  for (const via of ['GOOGLE', 'KAKAO'] as const) {
    const linked = deps({}, credFor(via));
    expect(await deleteAccount({ via, appleLinked: true }, linked.d)).toEqual({ done: true });
    expect(linked.calls).toEqual(['confirm', 'apple', 'remove', after(via), 'clearDrafts', 'clearSession']);
    expect(linked.bodies).toEqual([{ provider: via, identityToken: credFor(via).identityToken, appleIdentityToken: 'apple-jwt', appleAuthorizationCode: 'apple-code' }]);

    const plain = deps({}, credFor(via));
    await deleteAccount({ via, appleLinked: false }, plain.d);
    expect(plain.calls).not.toContain('apple');
    expect(plain.bodies).toEqual([{ provider: via, identityToken: credFor(via).identityToken }]);
  }
  // Apple 갈래 자체는 Apple 창이 곧 재확인이다 — 한 번 더 열지 않는다
  const apple = deps();
  await deleteAccount({ via: 'APPLE', appleLinked: true }, apple.d);
  expect(apple.calls).not.toContain('apple');
});

test('REQ-111 · Apple 창을 닫거나 실패해도 Apple 없이 지우고, 탈퇴 뒤 문구에 Apple 연결을 끊는 곳을 덧붙인다', async () => {
  expect(signedOutText([])).toBe('계정을 지웠어요');
  expect(signedOutText(['APPLE'])).not.toBe(signedOutText([]));
  expect(signedOutText(['APPLE'])).not.toBe(signedOutText(['KAKAO']));
  for (const apple of [async () => null, async () => { throw new Error('apple failed'); }]) {
    const { d, calls, bodies, notices } = deps({ apple }, GOOGLE_CRED);
    expect(await deleteAccount({ via: 'GOOGLE', appleLinked: true }, d)).toEqual({ done: true });
    expect(calls.filter((c) => c === 'remove')).toHaveLength(1);
    expect(bodies).toEqual([{ provider: 'GOOGLE', identityToken: 'g-jwt' }]);
    expect(notices).toEqual([signedOutText(['APPLE'])]);
  }
  // 양성 대조 — Apple까지 받으면 기본 문구만
  const ok = deps({}, GOOGLE_CRED);
  await deleteAccount({ via: 'GOOGLE', appleLinked: true }, ok.d);
  expect(ok.notices).toEqual(['계정을 지웠어요']);
});

test('REQ-112 · 탈퇴가 204일 때만 카카오 연결 끊기와 구글 철회를 부른다', async () => {
  for (const via of ['GOOGLE', 'KAKAO'] as const) {
    const ok = deps({}, credFor(via));
    expect(await deleteAccount({ via, appleLinked: false }, ok.d)).toEqual({ done: true });
    expect(ok.calls.filter((c) => c === after(via))).toHaveLength(1);
    expect(ok.calls.filter((c) => c === after(via === 'GOOGLE' ? 'KAKAO' : 'GOOGLE'))).toHaveLength(0); // 다른 제공자는 부르지 않는다

    for (const e of [new ApiError(400, '확인 시간이 지났어요'), new ApiError(403, '본인 확인에 실패해 탈퇴를 진행하지 않았어요'), new ApiError(409, '웹 설정에서 지울 수 있어요'), new NetworkError()]) {
      const fail = deps({ remove: async () => { throw e; } }, credFor(via));
      expect('error' in (await deleteAccount({ via, appleLinked: false }, fail.d))).toBe(true);
      expect(fail.calls.filter((c) => c.startsWith('unlinkKakao') || c.startsWith('revokeGoogle'))).toEqual([]);
    }
  }
});

test('REQ-112 · 카카오 연결 끊기가 실패하거나 5초를 넘기면 탈퇴는 끝내고 끊는 곳을 알린다', async () => {
  const rejected = deps({ unlinkKakao: async () => { throw new Error('KOE'); } }, KAKAO_CRED);
  expect(await deleteAccount({ via: 'KAKAO', appleLinked: false }, rejected.d)).toEqual({ done: true });
  expect(rejected.notices).toEqual([signedOutText(['KAKAO'])]);

  jest.useFakeTimers();
  try {
    const hung = deps({ unlinkKakao: () => new Promise(() => undefined) }, KAKAO_CRED);
    const p = deleteAccount({ via: 'KAKAO', appleLinked: false }, hung.d);
    await jest.advanceTimersByTimeAsync(UNLINK_WAIT_MS - 1);
    expect(hung.notices).toEqual([]); // 5초까지는 기다린다
    await jest.advanceTimersByTimeAsync(1);
    expect(await p).toEqual({ done: true });
    expect(hung.notices).toEqual([signedOutText(['KAKAO'])]);
  } finally {
    jest.useRealTimers();
  }

  // 양성 대조 — 끊기가 되면 기본 문구만
  const ok = deps({}, KAKAO_CRED);
  await deleteAccount({ via: 'KAKAO', appleLinked: false }, ok.d);
  expect(ok.notices).toEqual(['계정을 지웠어요']);

  // 구글 철회는 기다리지 않는다 — 끝나지 않아도 탈퇴는 바로 끝난다(실제 타이머)
  const slow = deps({ revokeGoogle: () => new Promise(() => undefined) }, GOOGLE_CRED);
  expect(await deleteAccount({ via: 'GOOGLE', appleLinked: false }, slow.d)).toEqual({ done: true });
  expect(slow.notices).toEqual(['계정을 지웠어요']);
});

test('REQ-87 · 구글 · 카카오 창을 닫거나 실패하면 아무것도 지우지 않는다(실패 문구는 그 제공자 것)', async () => {
  for (const via of ['GOOGLE', 'KAKAO'] as const) {
    const closed = deps({ confirm: async () => null });
    expect(await deleteAccount({ via, appleLinked: true }, closed.d)).toEqual({ cancelled: true });
    expect(closed.calls).toEqual([]);
  }
  const g = deps({ confirm: async () => { throw new Error('google failed'); } });
  expect(await deleteAccount({ via: 'GOOGLE', appleLinked: true }, g.d)).toEqual({ error: '구글 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(g.calls).toEqual([]);
  const k = deps({ confirm: async () => { throw new Error('kakao failed'); } });
  expect(await deleteAccount({ via: 'KAKAO', appleLinked: false }, k.d)).toEqual({ error: '카카오 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(k.calls).toEqual([]);
});

test('REQ-87 · LOCAL · TOSS의 구글 갈래는 이메일 가입 문구이고 원 가입 구글 문구와 다르다', () => {
  // 문구 원문은 단언하지 않는다(범위 보충 :681) — 어느 상수를 돌려주는가만 잠근다
  expect(deleteCopy({ via: 'GOOGLE', authProvider: 'LOCAL' })?.line).toBe(DELETE_LINE.GOOGLE_EMAIL);
  expect(deleteCopy({ via: 'GOOGLE', authProvider: 'TOSS' })?.line).toBe(DELETE_LINE.GOOGLE_EMAIL);
  expect(deleteCopy({ via: 'GOOGLE', authProvider: 'GOOGLE' })?.line).toBe(DELETE_LINE.GOOGLE);
  expect(DELETE_LINE.GOOGLE).not.toBe(DELETE_LINE.GOOGLE_EMAIL);
  expect(deleteCopy({ via: 'GOOGLE', authProvider: 'LOCAL' })?.button).toBe(deleteCopy({ via: 'GOOGLE', authProvider: 'GOOGLE' })?.button);
  expect(deleteCopy({ via: 'APPLE', authProvider: 'APPLE' })?.line).toBe(DELETE_LINE.APPLE);
  expect(deleteCopy({ via: 'KAKAO', authProvider: 'KAKAO' })?.line).toBe(DELETE_LINE.KAKAO);
  expect(deleteCopy({ via: 'WEB', authProvider: 'LOCAL' })).toBeNull();
});

// D4 (가) — 탈퇴 확인은 로그인과 다른 창(다시 로그인)이어야 한다. 화면의 익명 함수에 두면 { reauth: true }를 지워도 아무 테스트도 깨지지 않는다
function confirmDeps() {
  const calls: unknown[][] = [];
  const d = {
    apple: async () => (calls.push(['apple']), { identityToken: 'apple-jwt', authorizationCode: 'apple-code' }),
    google: async (o: { reauth: true }) => (calls.push(['google', o]), { identityToken: 'g-jwt', accessToken: 'g-at' }),
    kakao: async (o: { reauth: true }) => (calls.push(['kakao', o]), 'k-jwt'),
  };
  return { d, calls };
}

test('REQ-87 · 탈퇴 확인용 구글 · 카카오 창은 다시 로그인(reauth)으로 열고, Apple은 Apple 신원 재확인 창이다', async () => {
  const g = confirmDeps();
  expect(await confirmFor('GOOGLE', g.d)()).toEqual({ identityToken: 'g-jwt', accessToken: 'g-at' });
  expect(g.calls).toEqual([['google', { reauth: true }]]);
  const k = confirmDeps();
  expect(await confirmFor('KAKAO', k.d)()).toEqual({ identityToken: 'k-jwt' });
  expect(k.calls).toEqual([['kakao', { reauth: true }]]);
  const a = confirmDeps();
  expect(await confirmFor('APPLE', a.d)()).toEqual({ identityToken: 'apple-jwt', authorizationCode: 'apple-code' });
  expect(a.calls).toEqual([['apple']]);
});

test('REQ-87 · 탈퇴 확인 창을 닫으면 null이고 실패는 그대로 던진다(deleteAccount가 그 제공자 실패 문구로 바꾼다)', async () => {
  const closed = { apple: async () => null, google: async () => null, kakao: async () => null };
  for (const via of ['APPLE', 'GOOGLE', 'KAKAO'] as const) expect(await confirmFor(via, closed)()).toBeNull();
  const boom = new Error('kakao failed');
  await expect(confirmFor('KAKAO', { ...closed, kakao: async () => { throw boom; } })()).rejects.toBe(boom);
});
