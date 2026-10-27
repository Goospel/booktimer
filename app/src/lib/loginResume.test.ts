jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { runLogin } from './loginGate';
import { guardOpen, isResumeFromBackground, type LoginTrack, shouldRelease } from './loginResume';

// U-98 ⑤ — 카카오톡에서 아무것도 안 누르고 앱 전환기로 돌아오면 SDK 약속이 끝나지 않아 busy가 굳는다(2026-10-27 iPad 실측).

function deferred() {
  let resolve!: (v: string | null) => void;
  const promise = new Promise<string | null>((r) => (resolve = r));
  return { promise, resolve };
}
const track = (): LoginTrack => ({ attempt: 0, opening: 0, busy: false });
const tick = () => new Promise((r) => setTimeout(r, 0));

test('U-98 ⑤ · 앱 복귀는 background → active만이다(Apple · 구글 시스템 창의 inactive는 아니다)', () => {
  expect(isResumeFromBackground('background', 'active')).toBe(true);
  expect(isResumeFromBackground('inactive', 'active')).toBe(false);
  expect(isResumeFromBackground('active', 'active')).toBe(false);
  expect(isResumeFromBackground('active', 'background')).toBe(false);
});

test('U-98 ⑤ · 제공자 창을 기다리는 동안만 풀고, 서버에 보낸 뒤에는 풀지 않는다', async () => {
  const t = track();
  t.attempt = 1;
  t.busy = true;
  const win = deferred();
  const pending = guardOpen(t, 1, () => win.promise)('kakao');
  expect(shouldRelease(t, 1)).toBe(true); // 창 대기 중 — 막힌 SDK 경우
  win.resolve('jwt');
  expect(await pending).toBe('jwt');
  expect(shouldRelease(t, 1)).toBe(false); // 토큰을 받았다 = 서버 제출 단계
});

test('U-98 ⑤ · 새 시도가 시작됐거나 이미 busy가 아니면 풀지 않는다', () => {
  const t = track();
  t.attempt = 1;
  t.busy = true;
  void guardOpen(t, 1, () => new Promise<string | null>(() => {}))('kakao');
  expect(shouldRelease(t, 1)).toBe(true); // 양성 대조
  t.attempt = 2;
  expect(shouldRelease(t, 1)).toBe(false);
  t.attempt = 1;
  t.busy = false;
  expect(shouldRelease(t, 1)).toBe(false);
});

test('U-98 ⑤ · 창이 돌아오기 전에 새 시도가 시작되면 늦은 토큰은 서버로 가지 않는다', async () => {
  const t = track();
  t.attempt = 1;
  const win = deferred();
  const submits: string[] = [];
  const r = runLogin(
    { provider: 'kakao', mode: 'start', ageOn: true, code: '' },
    { open: guardOpen(t, 1, () => win.promise), submit: async (pv, tok) => (submits.push(`${pv}:${tok}`), {}) },
  );
  await tick();
  t.attempt = 2; // 풀린 단추로 다른 로그인을 눌렀다
  win.resolve('late-jwt');
  expect(await r).toEqual({ cancelled: true });
  expect(submits).toEqual([]);
});

test('U-98 ⑤ · 새 시도가 없으면 늦게 온 정상 토큰도 서버로 간다', async () => {
  const t = track();
  t.attempt = 1;
  const win = deferred();
  const submits: string[] = [];
  const r = runLogin(
    { provider: 'kakao', mode: 'start', ageOn: true, code: '' },
    { open: guardOpen(t, 1, () => win.promise), submit: async (pv, tok) => (submits.push(`${pv}:${tok}`), {}) },
  );
  await tick();
  win.resolve('jwt');
  expect(await r).toEqual({ done: true, notice: undefined });
  expect(submits).toEqual(['kakao:jwt']);
});

test('U-98 ⑤ · 옛 창이 늦게 돌아와도 새 시도의 「창 대기 중」을 지우지 않는다', async () => {
  const t = track();
  t.attempt = 1;
  const oldWin = deferred();
  const oldOpen = guardOpen(t, 1, () => oldWin.promise)('kakao');
  t.attempt = 2;
  t.busy = true;
  void guardOpen(t, 2, () => new Promise<string | null>(() => {}))('google');
  oldWin.resolve('late-jwt');
  expect(await oldOpen).toBeNull();
  expect(shouldRelease(t, 2)).toBe(true);
});
