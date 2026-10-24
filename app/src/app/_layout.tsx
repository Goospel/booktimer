import { SplashScreen, Stack } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';

// 앱 시작 때 위젯·Live Activity 레이아웃을 등록한다(보충 REQ-03 「위젯 레이아웃」).
import '../../widgets/ReadingWidget';
import '../../widgets/ReadingActivity';
import { session } from '../api/client';
import { useAppFonts } from '../theme/fonts';
import { color } from '../theme/tokens';

// 지금 바이너리엔 스플래시 모듈이 없어 아무 일도 안 한다(expo-router가 선택 모듈로 부른다 — 시각 §0.2-6). PR-L부터 실제로 붙잡는다.
SplashScreen.preventAutoHideAsync();

const stacked = {
  headerShown: true,
  // 머리 모델 하나 — 유리 캡슐(확정 그림). iOS 버전과 무관하게 내용은 머리 밑 y=0부터, 화면이 useHeaderHeight로 띄운다
  headerTransparent: true,
  title: '',
  headerBackButtonDisplayMode: 'minimal',
  headerTintColor: color.ink,
  contentStyle: { backgroundColor: color.paper },
} as const;

export default function RootLayout() {
  const fontsOk = useAppFonts(); // 실패해도 참 — 빈 화면에 멈추지 않는다(REQ-55)
  const [signedIn, setSignedIn] = useState<boolean | null>(null);

  useEffect(() => {
    session.token().then(
      (t) => setSignedIn(t !== null),
      () => setSignedIn(false),
    );
    return session.subscribe(setSignedIn); // 로그인 · 401이 화면 틀을 바꾼다
  }, []);

  const ready = fontsOk && signedIn !== null;
  useEffect(() => {
    if (ready) SplashScreen.hideAsync();
  }, [ready]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: color.paper }} />;
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: color.paper } }}>
      <Stack.Protected guard={signedIn}>
        {/* 제목은 화면에 안 보이고 뒤로 기록 메뉴 · VoiceOver에만 쓰인다 — 없으면 route 이름 「(tabs)」가 뜬다(U-60) */}
        <Stack.Screen name="(tabs)" options={{ title: '책생글' }} />
        {/* 쌓는 화면(PR-G · PR-H) — 탭을 덮는다. 뒤로 단추는 갈매기만(「(tabs)」 글자 방지 — U-60) */}
        <Stack.Screen name="add" options={stacked} />
        <Stack.Screen name="book/[id]" options={stacked} />
        <Stack.Screen name="margin/[bookId]" options={stacked} />
        {/* 글쓰기(PR-H) — 화면이 머리 가운데 책 제목 · 오른쪽 「내보내기」를 단다 */}
        <Stack.Screen name="draft/[id]" options={stacked} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="(auth)/login" />
      </Stack.Protected>
    </Stack>
  );
}
