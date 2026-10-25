import config from './app.config';
import type { ExpoConfig } from 'expo/config';
import { existsSync, readFileSync } from 'fs';
import { join } from 'path';
import { color } from './src/theme/tokens';

// 콘솔 값(개발 변형 §18 — 비밀 아님). 구글 칸은 클라이언트 ID에서 계산한다 — 손으로 옮긴 값이 아니다(PR-L 설계 REQ-166)
const GOOGLE_IOS_CLIENT_ID = '20889772436-j1vun6jjup350gg3l26igcc0421tanph.apps.googleusercontent.com';
const GOOGLE_SCHEME = 'com.googleusercontent.apps.' + GOOGLE_IOS_CLIENT_ID.replace('.apps.googleusercontent.com', '');
const KAKAO = { prod: '0fb70962dd03100ad2d0efa10c947c31', dev: 'bfbfa22db12bfb12770dbac22e8215b0' };

/** 그 이름의 [이름, 옵션] 플러그인들의 옵션 목록(중복이면 둘 이상). */
function pluginProps(c: ExpoConfig, name: string): unknown[] {
  return (c.plugins ?? []).filter((p) => Array.isArray(p) && p[0] === name).map((p) => (p as [string, unknown])[1]);
}
const kakaoKey = (c: ExpoConfig) =>
  (pluginProps(c, '@react-native-kakao/core')[0] as { nativeAppKey?: string } | undefined)?.nativeAppKey;

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

test('REQ-158 · 개발 변형은 이름 · 앱 스킴 · 번들 ID · 카카오 네이티브 앱 키만 바꾸고 나머지 설정은 production과 같다', () => {
  const prod = configWith(undefined);
  const dev = configWith('development');
  expect(dev.name).toBe('책생글 개발');
  expect((dev.scheme as string[])[0]).toBe('booktimer-dev');
  expect(dev.ios?.bundleIdentifier).toBe('com.goospel.chaeksaenggeul.dev');
  expect(kakaoKey(dev)).toBe(KAKAO.dev);
  // 바뀌어도 되는 칸 = 이름 · 앱 스킴(첫 칸) · 번들 ID · 카카오 키 한 칸(개발 변형 §4.7). 구글 역순 스킴(둘째 칸)은 같아야 한다.
  // 플러그인 · 권한 문구 · version · runtimeVersion · updates · 위젯 설정이 갈라지면 개발 빌드가 TF 바이너리의 예행이 아니게 된다
  const rest = (c: ExpoConfig) => ({
    ...c,
    name: null,
    scheme: (c.scheme as string[]).slice(1),
    ios: { ...c.ios, bundleIdentifier: null },
    plugins: c.plugins?.map((p) =>
      Array.isArray(p) && p[0] === '@react-native-kakao/core' ? [p[0], { ...(p[1] as object), nativeAppKey: null }] : p,
    ),
  });
  expect(rest(dev)).toEqual(rest(prod));
});

test('REQ-159 · APP_VARIANT가 정확히 development가 아니면 production 값이다', () => {
  for (const v of [undefined, '', 'production', 'preview', 'dev', 'Development']) {
    const c = configWith(v);
    expect([v, c.name, (c.scheme as string[])[0], c.ios?.bundleIdentifier, kakaoKey(c)]).toEqual([
      v,
      '책생글',
      'booktimer',
      'com.goospel.chaeksaenggeul',
      KAKAO.prod,
    ]);
  }
});

test('REQ-103 · iPad를 지원하고 전체 화면을 강제하지 않으며 iPhone은 세로다', () => {
  // iPad 네 방향(UISupportedInterfaceOrientations~ipad)은 손으로 적지 않는다 — Expo 기본 플러그인 withRequiresFullScreen이
  // supportsTablet && !requireFullScreen일 때 쓴다(@expo/config-plugins RequiresFullScreen.js:62-78). 평가값은 U-95(introspect)가 본다
  const c = configWith(undefined);
  expect(c.ios?.supportsTablet).toBe(true);
  expect(c.ios?.requireFullScreen).not.toBe(true); // 화면 분할(멀티태스킹) · 네 방향의 조건
  expect(c.orientation).toBe('portrait');
});

test('REQ-103 · version은 1.0.0이고 지문 잠금 파일의 version과 같다', () => {
  expect(configWith(undefined).version).toBe('1.0.0');
  expect(require('./fingerprint.lock.json').version).toBe('1.0.0');
});

test('REQ-166 · 스킴은 [앱 스킴, 구글 역순 클라이언트 ID] 두 칸이고 앱 스킴이 맨 앞이다', () => {
  expect(configWith(undefined).scheme).toEqual(['booktimer', GOOGLE_SCHEME]);
  expect(configWith('development').scheme).toEqual(['booktimer-dev', GOOGLE_SCHEME]);
});

test('REQ-167 · 카카오 플러그인은 한 번만 있고 변형별 네이티브 앱 키와 ios.handleKakaoOpenUrl true를 갖는다', () => {
  // ios를 빼면 플러그인이 iOS에 아무것도 쓰지 않는다(플러그인 원문) — 정확히 같은 객체로 잠근다(android 키도 0)
  expect(pluginProps(configWith(undefined), '@react-native-kakao/core')).toEqual([
    { nativeAppKey: KAKAO.prod, ios: { handleKakaoOpenUrl: true } },
  ]);
  expect(pluginProps(configWith('development'), '@react-native-kakao/core')).toEqual([
    { nativeAppKey: KAKAO.dev, ios: { handleKakaoOpenUrl: true } },
  ]);
});

test('REQ-168 · 카카오 두 패키지는 같은 정확 버전 3.x이고 expo-font · expo-asset은 정확 핀이며 핵심 네이티브 패키지는 잠금 파일에 한 벌씩이다', () => {
  const pkg = require('./package.json');
  const lock = require('./package-lock.json');
  const core = pkg.dependencies['@react-native-kakao/core'];
  expect(core).toMatch(/^3\.\d+\.\d+$/); // ^ · ~ 없음 — npm latest 태그는 2.x다
  expect(pkg.dependencies['@react-native-kakao/user']).toBe(core); // user peer가 core를 정확히 요구한다
  for (const n of ['expo-font', 'expo-asset']) {
    expect([n, pkg.dependencies[n]]).toEqual([n, expect.stringMatching(/^\d+\.\d+\.\d+$/)]);
    expect([n, lock.packages[`node_modules/${n}`]?.version]).toEqual([n, pkg.dependencies[n]]);
  }
  for (const n of ['expo-font', 'expo-asset', 'expo-modules-core', 'expo-constants', 'expo-linking', 'expo-application', 'react-native']) {
    const copies = Object.keys(lock.packages).filter((k) => k === `node_modules/${n}` || k.endsWith(`/node_modules/${n}`));
    expect([n, copies]).toEqual([n, [`node_modules/${n}`]]);
  }
});

test('REQ-169 · 스플래시는 expo-splash-screen 한 곳에 image · imageWidth 180 · contain · 종이색으로 있고 dark가 없으며 루트 배경도 종이색이다', () => {
  const c = configWith(undefined);
  expect(pluginProps(c, 'expo-splash-screen')).toEqual([
    { image: './assets/splash-owl.png', imageWidth: 180, resizeMode: 'contain', backgroundColor: color.paper },
  ]);
  expect(c.backgroundColor).toBe(color.paper);
  expect(c.plugins).toContain('expo-system-ui');
});

test('REQ-170 · ios.icon은 light · dark · tinted 세 PNG를 가리키고 그 파일이 있다', () => {
  const icon = configWith(undefined).ios?.icon;
  expect(icon).toEqual({ light: './assets/icon-ios-light.png', dark: './assets/icon-ios-dark.png', tinted: './assets/icon-ios-tinted.png' });
  for (const p of Object.values(icon as Record<string, string>)) expect([p, existsSync(join(__dirname, p))]).toEqual([p, true]);
});

test('REQ-171 · 키 명령 모듈 이름이 JS · Swift · 모듈 설정 · podspec에서 같고 Swift · podspec 줄끝이 LF다', () => {
  const dir = join(__dirname, 'modules/key-commands');
  const swift = readFileSync(join(dir, 'ios/KeyCommandsModule.swift'));
  const podspec = readFileSync(join(dir, 'ios/KeyCommands.podspec'));
  const ts = readFileSync(join(dir, 'index.ts'), 'utf8');
  expect(JSON.parse(readFileSync(join(dir, 'expo-module.config.json'), 'utf8'))).toEqual({
    platforms: ['apple'],
    apple: { modules: ['KeyCommandsModule'] },
  });
  for (const lit of [
    'public final class KeyCommandsModule: Module',
    'Name("KeyCommands")',
    'Events("onMeasureShortcut")',
    'AsyncFunction("setEnabled")',
    'sendEvent("onMeasureShortcut")',
  ]) {
    expect([lit, swift.toString('utf8').includes(lit)]).toEqual([lit, true]);
  }
  for (const lit of ["requireOptionalNativeModule<KeyCommandsNative>('KeyCommands')", "'onMeasureShortcut'", 'setEnabled(']) {
    expect([lit, ts.includes(lit)]).toEqual([lit, true]);
  }
  expect(podspec.toString('utf8')).toMatch(/s\.name\s*=\s*'KeyCommands'/);
  expect(podspec.toString('utf8')).toMatch(/s\.swift_version\s*=\s*'5\.9'/);
  // ios/ 폴더는 지문 dir 원천(바이트째) — CR 바이트가 있으면 Windows 로컬 지문이 CI와 갈린다
  expect([swift.includes(0x0d), podspec.includes(0x0d)]).toEqual([false, false]);
});

test('REQ-161 · eas.json — development 프로필만 APP_VARIANT=development, production은 production, 그 밖 프로필엔 없다', () => {
  const build: Record<string, { env?: Record<string, string> }> = require('./eas.json').build;
  expect(build.development.env?.APP_VARIANT).toBe('development');
  expect(build.production.env?.APP_VARIANT).toBe('production');
  for (const [name, p] of Object.entries(build)) {
    if (name !== 'development' && name !== 'production') expect([name, p.env?.APP_VARIANT]).toEqual([name, undefined]);
  }
});
