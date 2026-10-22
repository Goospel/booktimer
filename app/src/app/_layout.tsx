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
        <Stack.Screen name="(tabs)" />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="(auth)/login" />
      </Stack.Protected>
    </Stack>
  );
}
