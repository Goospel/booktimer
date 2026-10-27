import { acceptResult, isResumeFromBackground, RESUME_RELEASE_MS, shouldRelease } from './loginResume';

// U-98 ⑤ — 카카오톡에서 아무것도 안 누르고 앱 전환기로 돌아오면 SDK 약속이 끝나지 않아 busy가 굳는다(2026-10-27 iPad 실측).

test('U-98 ⑤ · 앱 복귀는 background → active만이다(Apple · 구글 시스템 창의 inactive는 아니다)', () => {
  expect(isResumeFromBackground('background', 'active')).toBe(true);
  expect(isResumeFromBackground('inactive', 'active')).toBe(false);
  expect(isResumeFromBackground('active', 'active')).toBe(false);
  expect(isResumeFromBackground('active', 'background')).toBe(false);
});

test('U-98 ⑤ · 2초 뒤 그 시도가 아직 최신이고 busy면 푼다', () => {
  expect(shouldRelease({ attempt: 3, latest: 3, busy: true })).toBe(true);
});

test('U-98 ⑤ · 새 시도가 시작됐거나 이미 busy가 아니면 풀지 않는다', () => {
  expect(shouldRelease({ attempt: 3, latest: 4, busy: true })).toBe(false);
  expect(shouldRelease({ attempt: 3, latest: 3, busy: false })).toBe(false);
});

test('U-98 ⑤ · 옛 시도의 늦은 결과는 버리고 최신 시도의 결과는 받는다', () => {
  expect(acceptResult(3, 4)).toBe(false);
  expect(acceptResult(4, 4)).toBe(true);
});

test('U-98 ⑤ · 풀기 대기는 2초 근처다(1초 이하면 정상 복귀를 끊고, 5초 이상이면 사람이 막혔다고 느낀다)', () => {
  expect(RESUME_RELEASE_MS).toBeGreaterThan(1000);
  expect(RESUME_RELEASE_MS).toBeLessThan(5000);
});
