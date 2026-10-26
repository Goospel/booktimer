// 카카오톡 로그인 복귀 주소(kakao<앱 키>://oauth?code=…)는 카카오 SDK 몫이다 — 라우터가 화면으로 열지 않게 버린다.
// Expo 58 장면 수명주기는 이 주소를 AppDelegate(카카오 처리)에 넘긴 뒤 RN Linking에도 다시 알린다(SceneEventForwarder) —
// 버리지 않으면 「Unmatched Route」가 뜬다(개발 #2 G5 실측 2026-10-26). null이면 expo-router가 이동하지 않는다.
export function redirectSystemPath({ path }: { path: string; initial: boolean }) {
  return path.startsWith('kakao') ? null : path;
}
