import type { ExpoConfig } from 'expo/config';

// 네이티브 기준(보충 설계 REQ-03). 이 파일의 값은 지문에 들어간다 — 스켈레톤 뒤로는 바꾸지 않는다(REQ-02).
// 환경값(API 주소 등)은 여기 두지 않는다. JS가 process.env.EXPO_PUBLIC_API_BASE_URL을 바로 읽는다.
// eas init이 dynamic config를 못 써서 손으로 넣었다(보충 §4.4). 비밀이 아니다.
const PROJECT_ID = '33d5c65d-1850-415f-af4e-73824db0194f';

const config: ExpoConfig = {
  owner: 'goospels-app',
  name: '책생글',
  slug: 'chaeksaenggeul',
  scheme: 'booktimer',
  version: '0.1.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'light',
  ios: {
    bundleIdentifier: 'com.goospel.chaeksaenggeul',
    supportsTablet: false,
    usesAppleSignIn: true,
    config: {
      usesNonExemptEncryption: false,
    },
  },
  android: {
    adaptiveIcon: {
      backgroundColor: '#E6F4FE',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
  },
  web: {
    favicon: './assets/favicon.png',
  },
  plugins: [
    'expo-router',
    'expo-status-bar',
    'expo-dev-client',
    'expo-apple-authentication',
    [
      'expo-camera',
      {
        cameraPermission: '책 뒤표지의 바코드를 읽어 책을 등록해요',
        microphonePermission: false,
      },
    ],
    [
      'expo-widgets',
      {
        enablePushNotifications: false,
        frequentUpdates: false,
        widgets: [
          {
            name: 'ReadingWidget',
            displayName: '책생글',
            description: '오늘 읽은 시간',
            ios: { supportedFamilies: ['systemSmall'] },
          },
        ],
      },
    ],
    ['expo-notifications', { enableRemoteNotifications: false }],
    ['expo-secure-store', { faceIDPermission: false }],
    'expo-updates',
  ],
  runtimeVersion: { policy: 'appVersion' },
  updates: {
    url: `https://u.expo.dev/${PROJECT_ID}`,
  },
  extra: {
    eas: { projectId: PROJECT_ID },
  },
};

export default config;
