jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import { loginGate, loginProviders, type Provider, runLogin } from './loginGate';

// 시각 보충 §1.6 — Apple 창을 열기 전 관문(REQ-65).

test('REQ-65 · 시작 모드에서 나이 스위치가 꺼져 있으면 needAge다(Apple 창을 열지 않는다)', () => {
  expect(loginGate({ mode: 'start', ageOn: false, code: '' })).toBe('needAge');
  expect(loginGate({ mode: 'start', ageOn: false, code: 'ABCD1234' })).toBe('needAge');
});

test('REQ-65 · 웹 계정 모드에서 코드가 8자가 아니면 needCode이고, 나이 스위치는 보지 않는다', () => {
  expect(loginGate({ mode: 'link', ageOn: true, code: '' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: true, code: 'ABCD123' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: true, code: 'ABCD12345' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: false, code: 'ABCD1234' })).toBe('ok');
});

test('REQ-65 · 시작 모드 스위치 켬 · 웹 계정 모드 코드 8자면 ok다', () => {
  expect(loginGate({ mode: 'start', ageOn: true, code: '' })).toBe('ok');
  expect(loginGate({ mode: 'link', ageOn: false, code: ' ABCD1234 ' })).toBe('ok'); // 앞뒤 공백은 붙여넣기 흔적
});

// ── 3사 로그인(PR-P ③ — REQ-86) ───────────────────────────────────────────
function loginDeps(token: string | null = 'jwt', submit: () => Promise<{ notice?: string; cancelled?: true }> = async () => ({})) {
  const calls: string[] = [];
  const d = {
    open: async (pv: Provider) => (calls.push(`open:${pv}`), token),
    submit: async (pv: Provider, t: string) => (calls.push(`submit:${pv}:${t}`), submit()),
  };
  return { d, calls };
}

test('REQ-86 · 만 14세 스위치가 꺼져 있으면 세 로그인 창 모두 열지 않는다', async () => {
  const providers = loginProviders('start');
  expect(providers).toHaveLength(3); // 빈 목록이면 아래 반복이 늘 초록
  for (const provider of providers) {
    const { d, calls } = loginDeps();
    expect(await runLogin({ provider, mode: 'start', ageOn: false, code: '' }, d)).toEqual({ gate: 'needAge' });
    expect(calls).toEqual([]);
  }
});

test('REQ-86 · 웹 계정 모드에는 Apple만 있다', async () => {
  expect(loginProviders('start')).toEqual(['apple', 'google', 'kakao']);
  expect(loginProviders('link')).toEqual(['apple']);
  // 화면만 믿지 않는다 — 웹 계정 모드에서 구글 · 카카오를 부르면 코드 8자가 맞아도 창 · 서버 0
  for (const provider of ['google', 'kakao'] as const) {
    const { d, calls } = loginDeps();
    const r = await runLogin({ provider, mode: 'link', ageOn: true, code: 'ABCD1234' }, d);
    expect('error' in r).toBe(true);
    expect(calls).toEqual([]);
  }
  // 양성 대조 — 웹 계정 모드 Apple은 창 → 서버
  const apple = loginDeps();
  expect(await runLogin({ provider: 'apple', mode: 'link', ageOn: false, code: 'ABCD1234' }, apple.d)).toEqual({ done: true, notice: undefined });
  expect(apple.calls).toEqual(['open:apple', 'submit:apple:jwt']);
});

test('REQ-86 · 창을 닫으면 서버를 부르지 않고 조용히 끝난다', async () => {
  for (const provider of loginProviders('start')) {
    const { d, calls } = loginDeps(null);
    expect(await runLogin({ provider, mode: 'start', ageOn: true, code: '' }, d)).toEqual({ cancelled: true });
    expect(calls).toEqual([`open:${provider}`]);
  }
  // 서버 쪽 취소(미검증 웹 계정 확인 창에서 취소)도 조용히
  const declined = loginDeps('jwt', async () => ({ cancelled: true }));
  expect(await runLogin({ provider: 'google', mode: 'start', ageOn: true, code: '' }, declined.d)).toEqual({ cancelled: true });
  // 양성 대조 — 성공이면 done과 안내
  const ok = loginDeps('jwt', async () => ({ notice: '안내' }));
  expect(await runLogin({ provider: 'kakao', mode: 'start', ageOn: true, code: '' }, ok.d)).toEqual({ done: true, notice: '안내' });
  expect(ok.calls).toEqual(['open:kakao', 'submit:kakao:jwt']);
});

test('REQ-86 · 실패 문구는 서버 · 연결 문구 그대로, 그 밖은 그 제공자의 확인 실패 문구다', async () => {
  const run = async (provider: Provider, e: Error, where: 'open' | 'submit' = 'submit') => {
    const d = {
      open: async () => { if (where === 'open') throw e; return 'jwt'; },
      submit: async () => { throw e; },
    };
    return runLogin({ provider, mode: 'start', ageOn: true, code: '' }, d);
  };
  expect(await run('google', new ApiError(409, '다른 구글 계정에 고정된 이메일이에요'))).toEqual({ error: '다른 구글 계정에 고정된 이메일이에요' });
  expect(await run('kakao', new NetworkError())).toEqual({ error: new NetworkError().message });
  expect(await run('google', new Error('boom'), 'open')).toEqual({ error: '구글 로그인 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(await run('kakao', new Error('boom'))).toEqual({ error: '카카오 로그인 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(await run('apple', new Error('boom'), 'open')).toEqual({ error: 'Apple 로그인 확인에 실패했어요 — 다시 시도해 주세요' });
  expect(await run('google', new ApiError(500, '요청에 실패했어요 (500)'))).toEqual({ error: '구글 로그인 확인에 실패했어요 — 다시 시도해 주세요' });
});
