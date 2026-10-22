import { buildInfoLine } from './buildInfo';

// 설정 탭 맨 아래 줄(앞당김 보충 REQ-72) — 「보냈다」는 도달의 증거가 아니라서 기기에서 읽는다.

const BASE = { version: '0.1.0', channel: 'production', updatesEnabled: true };

test('REQ-72 · 업데이트로 뜬 앱은 업데이트 id 앞 8자를 낸다', () => {
  expect(buildInfoLine({ ...BASE, updateId: '0192f3a4-b5c6-7d8e-9f00-112233445566', isEmbeddedLaunch: false })).toBe(
    'v0.1.0 · production · 0192f3a4',
  );
});

test('REQ-72 · 내장 번들로 뜬 앱은 「내장」을 낸다', () => {
  expect(buildInfoLine({ ...BASE, updateId: '0192f3a4-b5c6-7d8e-9f00-112233445566', isEmbeddedLaunch: true })).toBe(
    'v0.1.0 · production · 내장',
  );
});

test('REQ-72 · expo-updates가 꺼진 개발 빌드는 「개발」을 낸다', () => {
  expect(
    buildInfoLine({ version: '0.1.0', channel: null, updateId: null, isEmbeddedLaunch: true, updatesEnabled: false }),
  ).toBe('v0.1.0 · 채널 없음 · 개발');
});
