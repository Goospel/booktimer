// 카카오 SDK 감싸개(PR-P ③ — REQ-173 · REQ-86 · REQ-87 · REQ-112). 진짜 카카오 모듈은 ESM 의존으로 jest가 못 읽을 수 있어 팩토리로만.
// 감싸개는 불러올 때 초기화하므로 바깥 변수를 팩토리에서 쓰지 않는다(TDZ) — 목은 requireMock으로 꺼낸다.
jest.mock('@react-native-kakao/core', () => ({ initializeKakaoSDK: jest.fn(async () => undefined) }));
jest.mock('@react-native-kakao/user', () => ({ login: jest.fn(), logout: jest.fn(async () => undefined), unlink: jest.fn(async () => undefined) }));
jest.mock('expo-application', () => ({ applicationId: 'com.goospel.chaeksaenggeul.dev' }));

import { kakaoIdToken, kakaoLogout, kakaoUnlink } from './kakao';
import { KAKAO_NATIVE_APP_KEYS } from './keys';

const core = jest.requireMock('@react-native-kakao/core') as { initializeKakaoSDK: jest.Mock };
const user = jest.requireMock('@react-native-kakao/user') as { login: jest.Mock; logout: jest.Mock; unlink: jest.Mock };
const initAtImport = core.initializeKakaoSDK.mock.calls.map((c) => c[0]); // 어떤 함수도 부르기 전 — 불러올 때 초기화했는가
const kakaoError = (code: string) => Object.assign(new Error(code), { code });

beforeEach(() => {
  user.login.mockReset();
});

test('REQ-173 · 카카오 SDK는 바이너리 번들 ID로 고른 키로 먼저 초기화한다', async () => {
  expect(initAtImport).toEqual([KAKAO_NATIVE_APP_KEYS.dev]); // 개발 번들 → 개발 키 · 불러올 때 한 번
  user.login.mockResolvedValueOnce({ idToken: 'k-jwt' });
  await kakaoIdToken();
  await kakaoLogout();
  await kakaoUnlink();
  expect(core.initializeKakaoSDK).toHaveBeenCalledTimes(1); // 다시 초기화하지 않는다
  const init = core.initializeKakaoSDK.mock.invocationCallOrder[0];
  for (const f of [user.login, user.logout, user.unlink]) expect(init).toBeLessThan(f.mock.invocationCallOrder[0]);
});

test('REQ-86 · 카카오 창을 닫거나 동의를 취소하면 null이고, ID 토큰이 없거나 다른 오류면 실패다', async () => {
  for (const code of ['Cancelled', 'AccessDenied']) {
    user.login.mockRejectedValueOnce(kakaoError(code));
    expect(await kakaoIdToken()).toBeNull();
  }
  user.login.mockRejectedValueOnce(kakaoError('KOE101'));
  await expect(kakaoIdToken()).rejects.toThrow('KOE101');
  user.login.mockResolvedValueOnce({ accessToken: 'a' }); // 콘솔 OpenID Connect가 꺼지면
  await expect(kakaoIdToken()).rejects.toThrow();
  user.login.mockResolvedValueOnce({ idToken: 'k-jwt' }); // 양성 대조
  expect(await kakaoIdToken()).toBe('k-jwt');
});

test('REQ-87 · 탈퇴 확인용 카카오는 카카오톡 대신 카카오계정 다시 로그인을 요구한다', async () => {
  user.login.mockResolvedValue({ idToken: 'k-jwt' });
  await kakaoIdToken({ reauth: true });
  expect(user.login.mock.calls[0][0]).toEqual({ useKakaoAccountLogin: true, prompts: ['Login'] });
  await kakaoIdToken(); // 양성 대조 — 로그인은 카카오톡(인자 없음)
  expect(user.login.mock.calls[1][0]).toBeUndefined();
});

test('REQ-112 · 카카오 연결 끊기가 실패하면 다시 던진다(탈퇴 흐름이 끊는 곳을 알리게)', async () => {
  user.unlink.mockRejectedValueOnce(kakaoError('KOE'));
  await expect(kakaoUnlink()).rejects.toThrow('KOE');
});
