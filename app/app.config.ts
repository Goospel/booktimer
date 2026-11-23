import type { ExpoConfig } from 'expo/config';

// 네이티브 기준(보충 설계 REQ-03). 이 파일의 평가된 값은 지문에 들어간다 — 값을 바꾸면 네이티브 빌드다
// (REQ-69: version을 올리고 app/fingerprint.lock.json을 고친다 · extra도 지문 안이다).
// 환경값(API 주소 등)은 여기 두지 않는다. JS가 process.env.EXPO_PUBLIC_API_BASE_URL을 바로 읽는다.
// eas init이 dynamic config를 못 써서 손으로 넣었다(보충 §4.4). 비밀이 아니다.
const PROJECT_ID = '33d5c65d-1850-415f-af4e-73824db0194f';

// 개발 변형(2026-10-25 설계 REQ-158) — eas.json development 프로필만 APP_VARIANT=development를 준다.
// 그 밖(TestFlight · 스토어 · CI · 지문 검사)은 아래 production 값 그대로다(REQ-160).
// 판단 · 값은 이 파일 안에만 둔다 — 로컬 파일을 import하면 그 파일이 지문 원천이 된다(@expo/fingerprint는 app.config.ts만 빼고 불러온 로컬 모듈을 해시한다).
const DEV = process.env.APP_VARIANT === 'development';

// 로그인 값(범위 보충 §3.1 — 비밀이 아니다, 앱 번들에 그대로 실린다. PR-L 설계 REQ-166 · 167).
// 구글 iOS 클라이언트는 두 변형이 함께 쓴다(개발 변형 §4.7 · U-83). 카카오 키는 변형마다 다르다 — 같으면 카카오톡 로그인
// 복귀 주소(kakao<키>://)가 두 앱에 겹쳐 TestFlight 앱의 로그인이 개발 앱으로 샐 수 있다.
const GOOGLE_IOS_URL_SCHEME = 'com.googleusercontent.apps.20889772436-j1vun6jjup350gg3l26igcc0421tanph';
const KAKAO_NATIVE_APP_KEY = DEV ? 'bfbfa22db12bfb12770dbac22e8215b0' : '0fb70962dd03100ad2d0efa10c947c31';
// 바탕 = tokens.ts palette.light.bg · palette.dark.bg(app-config.test.ts가 대조한다 — 여기서 import하면 tokens.ts가 지문 원천이 된다).
const BG_LIGHT = '#E7E9EB';
const BG_DARK = '#0E0F10';
// 한글 name은 Expo sanitizedName에서 'app'이 되어 CFBundleName = $(PRODUCT_NAME) = app → 구글 로그인 시스템 창에 「app」(2026-10-29) — CFBundleName을 이 값으로 덮는다.
const NAME = DEV ? '책생글 개발' : '책생글';
// 마이크 문구는 두 플러그인이 같은 상수를 쓴다 — expo-camera의 false(지우기)가 배열 앞이면 설치본 withMod가 앞 플러그인 동작을 마지막에 돌려
// 뒤 플러그인 문구까지 지운다(2026-11-23 스파이크). 문구는 목소리를 저장 · 전송하지 않는다는 사실만 적는다(기기 안 인식 — 여백 말로 쓰기 설계).
const MIC_USAGE = '말로 쓰기를 누르면 목소리를 듣고 이 기기 안에서 글로 옮겨요. 목소리는 저장하거나 보내지 않아요.';
// 음성 인식 권한은 묻지 않는다(기기 안만 — 사용자 결정 2026-11-23). 모듈 바이너리가 그 API를 참조해 문구가 없으면 업로드가 거절된다(ITMS-90683)
const SPEECH_USAGE = '말로 쓰기에서 들은 목소리를 이 기기 안에서 글로 바꿔요.';

const config: ExpoConfig = {
  owner: 'goospels-app',
  name: NAME,
  slug: 'chaeksaenggeul',
  // 앱 스킴이 맨 앞이어야 한다 — expo-linking이 첫 칸으로 앱 주소를 만든다(개발 변형 §4.7). 둘째 칸 = 구글 역순 클라이언트 ID.
  scheme: [DEV ? 'booktimer-dev' : 'booktimer', GOOGLE_IOS_URL_SCHEME],
  version: '1.3.1', // 1.3.0(U1) + 음성 인식 모듈 — 지문이 바뀌어 CI가 version · 잠금을 함께 요구한다
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic', // 다크 스플래시가 Info.plist를 Automatic으로 바꾼다 — 둘을 같이(withIosSplashInfoPlist)
  // 루트 뷰 · 창 바탕(expo-system-ui) — iPad 회전 · 화면 분할 때 가장자리가 흰색으로 번쩍이지 않게(PR-L 설계 §4.3). 다크는 런타임 SystemUI(U2a)
  backgroundColor: BG_LIGHT,
  ios: {
    deploymentTarget: '26.0', // 위젯 확장도 이 값을 읽는다(expo-widgets withIosWidgets.js)
    bundleIdentifier: DEV ? 'com.goospel.chaeksaenggeul.dev' : 'com.goospel.chaeksaenggeul',
    // iPad 정식 지원(REQ-103). iPad 네 방향(UISupportedInterfaceOrientations~ipad — 화면 분할 · 매직 키보드 가로 · ITMS-90474)은
    // Expo 기본 플러그인 withRequiresFullScreen이 supportsTablet + requireFullScreen 끔(기본)에서 쓴다 — 손으로 적지 않는다.
    // iPhone은 위 orientation 그대로 세로. 평가값은 PR-L 설계 U-95(introspect)가 본다.
    supportsTablet: true,
    // 아이콘-안3 올빼미(아이콘 사양 §12) — 세 장 모두 불투명 1024. 틴트는 검정 바탕(Expo가 투명 자리를 흰색으로 채운다)
    icon: {
      light: './assets/icon-ios-light.png',
      dark: './assets/icon-ios-dark.png',
      tinted: './assets/icon-ios-tinted.png',
    },
    usesAppleSignIn: true,
    infoPlist: {
      CFBundleName: NAME,
    },
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
        microphonePermission: MIC_USAGE,
      },
    ],
    // 여백 말로 쓰기(기기 안 인식). JS는 U6에서 requireOptionalNativeModule로만 읽는다 — 모듈 없는 1.3.0 바이너리에서도 화면이 돈다
    ['expo-speech-recognition', { microphonePermission: MIC_USAGE, speechRecognitionPermission: SPEECH_USAGE }],
    [
      'expo-widgets',
      {
        enablePushNotifications: false,
        frequentUpdates: false,
        widgets: [
          {
            name: 'ReadingWidget',
            displayName: '책생글',
            description: '오늘 읽은 시간과 읽은 날',
            ios: { supportedFamilies: ['systemSmall', 'systemLarge'] }, // 큰 크기 = 이번 달 달력(위젯 확장 설계 2026-10-28)
          },
        ],
      },
    ],
    ['expo-notifications', { enableRemoteNotifications: false }],
    ['expo-secure-store', { faceIDPermission: false }],
    'expo-updates',
    // 스플래시(아이콘 사양 §14.5) — 1024 정사각 투명 PNG를 180pt 칸 가운데에 → 올빼미 폭 119.76pt. 앱 코드는 import하지 않는다(expo-router가 부른다).
    // dark = 같은 그림 · 올빼미만 다크 ink(사용자 결정 2026-11-20 질문 3 (가)). dark가 있으면 이 플러그인이 UIUserInterfaceStyle을 Automatic으로 쓴다 — 위 userInterfaceStyle과 같이
    [
      'expo-splash-screen',
      {
        image: './assets/splash-owl.png',
        imageWidth: 180,
        resizeMode: 'contain',
        backgroundColor: BG_LIGHT,
        dark: { image: './assets/splash-owl-dark.png', backgroundColor: BG_DARK },
      },
    ],
    // 내장 Plex 넷(REQ-470) — 원본 OTF 그대로(서브셋 · 이름 바꾸기 금지 · 라이선스 assets/fonts/LICENSE-IBM-Plex.txt). 이름은 tokens.ts font
    [
      'expo-font',
      {
        fonts: [
          './assets/fonts/IBMPlexSansKR-Regular.otf',
          './assets/fonts/IBMPlexSansKR-Medium.otf',
          './assets/fonts/IBMPlexSansKR-SemiBold.otf',
          './assets/fonts/IBMPlexMono-Medium.otf',
        ],
      },
    ],
    // prebuild가 자동 적용하는 목록에도 있지만 적어 둔다 — JS가 import하지 않는 패키지라 「안 쓰는 의존성」으로 지워지지 않게
    'expo-system-ui',
    // 카카오 로그인(범위 보충 §3.1 K1 · 카카오 RN SDK 3.0.0). ios를 빼면 이 플러그인은 iOS에 아무것도 쓰지 않는다(플러그인 원문).
    // handleKakaoOpenUrl = SDK 58 AppDelegate에 카카오톡 복귀 처리기를 만든다(라이브러리 ADR 0001)
    ['@react-native-kakao/core', { nativeAppKey: KAKAO_NATIVE_APP_KEY, ios: { handleKakaoOpenUrl: true } }],
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
