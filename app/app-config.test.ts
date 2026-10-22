import config from './app.config';

// 네이티브 설정 지킴 테스트 — 스켈레톤이 값을 이미 가져 처음부터 통과한다. 돌연변이 설정으로 실패를 한 번 보였다(PR-E).

/** 설정 객체 안에서 그 키가 나오는 횟수(중첩 전부). */
function countKey(node: unknown, key: string): number {
  if (node === null || typeof node !== 'object') return 0;
  return Object.entries(node).reduce((n, [k, v]) => n + (k === key ? 1 : 0) + countKey(v, key), 0);
}

test('REQ-45 · iOS 수출 규정 답은 ios.config 한 곳에만 false로 있다', () => {
  expect(config.ios?.config?.usesNonExemptEncryption).toBe(false);
  expect(countKey(config, 'usesNonExemptEncryption')).toBe(1);
  expect(countKey(config, 'ITSAppUsesNonExemptEncryption')).toBe(0);
});

test('REQ-69 · runtimeVersion은 appVersion 정책 객체다', () => {
  expect(config.runtimeVersion).toEqual({ policy: 'appVersion' });
});
