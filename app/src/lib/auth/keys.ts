// 로그인 값(비밀 아님 — 범위 보충 §3.1). app.config.ts가 이 파일을 import하면 지문 원천이 된다 — 값을 따로 적고 테스트가 대조한다(REQ-173).
// 키를 바꾸는 날은 app.config.ts(네이티브 — 새 빌드) · 이 파일 · 서버 application.properties를 함께 바꾼다.
export const GOOGLE_IOS_CLIENT_ID = '20889772436-j1vun6jjup350gg3l26igcc0421tanph.apps.googleusercontent.com';
export const GOOGLE_IOS_URL_SCHEME = 'com.googleusercontent.apps.' + GOOGLE_IOS_CLIENT_ID.replace('.apps.googleusercontent.com', '');
/** 역순 스킴 — 번들 ID 스킴이 아니다(PR-L 설계 §3.7 · U-83). 콜백은 ASWebAuthenticationSession이 받아 두 앱 사이에서 섞이지 않는다 */
export const GOOGLE_REDIRECT_URI = `${GOOGLE_IOS_URL_SCHEME}:/oauthredirect`;
export const KAKAO_NATIVE_APP_KEYS = { prod: '0fb70962dd03100ad2d0efa10c947c31', dev: 'bfbfa22db12bfb12770dbac22e8215b0' } as const;
export const DEV_BUNDLE_ID = 'com.goospel.chaeksaenggeul.dev';

/** 바이너리 번들 ID로 고른다 — Metro 환경값과 무관(PR-L 설계 §4.6). 못 읽으면 운영 키(TF 앱 쪽). */
export function kakaoKeyFor(bundleId: string | null): string {
  return bundleId === DEV_BUNDLE_ID ? KAKAO_NATIVE_APP_KEYS.dev : KAKAO_NATIVE_APP_KEYS.prod;
}
