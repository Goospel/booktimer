import config from './app.config';
import type { ExpoConfig } from 'expo/config';

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

/** APP_VARIANT를 바꿔 설정 파일을 새로 평가한다 — 모듈 맨 위에서 env를 읽으므로 isolateModules로 다시 싣는다. */
function configWith(variant: string | undefined): ExpoConfig {
  const saved = process.env.APP_VARIANT;
  if (variant === undefined) delete process.env.APP_VARIANT;
  else process.env.APP_VARIANT = variant;
  try {
    let loaded!: ExpoConfig;
    jest.isolateModules(() => {
      loaded = require('./app.config').default;
    });
    return loaded;
  } finally {
    if (saved === undefined) delete process.env.APP_VARIANT;
    else process.env.APP_VARIANT = saved;
  }
}

test('REQ-158 · 개발 변형은 이름 · 스킴 · 번들 ID만 바꾸고 나머지 설정은 production과 같다', () => {
  const prod = configWith(undefined);
  const dev = configWith('development');
  expect(dev.name).toBe('책생글 개발');
  expect(dev.scheme).toBe('booktimer-dev');
  expect(dev.ios?.bundleIdentifier).toBe('com.goospel.chaeksaenggeul.dev');
  // 플러그인 · 권한 문구 · version · runtimeVersion · updates · 위젯 설정이 갈라지면 개발 빌드가 TF 바이너리의 예행이 아니게 된다
  const rest = (c: ExpoConfig) => ({ ...c, name: null, scheme: null, ios: { ...c.ios, bundleIdentifier: null } });
  expect(rest(dev)).toEqual(rest(prod));
});

test('REQ-159 · APP_VARIANT가 정확히 development가 아니면 production 값이다', () => {
  for (const v of [undefined, '', 'production', 'preview', 'dev', 'Development']) {
    const c = configWith(v);
    expect([v, c.name, c.scheme, c.ios?.bundleIdentifier]).toEqual([v, '책생글', 'booktimer', 'com.goospel.chaeksaenggeul']);
  }
});

test('REQ-161 · eas.json — development 프로필만 APP_VARIANT=development, production은 production, 그 밖 프로필엔 없다', () => {
  const build: Record<string, { env?: Record<string, string> }> = require('./eas.json').build;
  expect(build.development.env?.APP_VARIANT).toBe('development');
  expect(build.production.env?.APP_VARIANT).toBe('production');
  for (const [name, p] of Object.entries(build)) {
    if (name !== 'development' && name !== 'production') expect([name, p.env?.APP_VARIANT]).toEqual([name, undefined]);
  }
});
