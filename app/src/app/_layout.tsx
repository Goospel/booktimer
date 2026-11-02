import AsyncStorage from '@react-native-async-storage/async-storage';
import { SplashScreen, Stack } from 'expo-router';
import { useEffect, useState } from 'react';
import { LogBox, View } from 'react-native';

// 앱 시작 때 위젯·Live Activity 레이아웃을 등록한다(보충 REQ-03 「위젯 레이아웃」).
import '../../widgets/ReadingWidget';
import '../../widgets/ReadingActivity';
import { session } from '../api/client';
import { startSlotSync } from '../lib/draftMigrate';
import { clearSurfaces } from '../lib/surfaces';
import { useAppFonts } from '../theme/fonts';
import { color } from '../theme/tokens';

// 스킴 둘째 칸(구글 역순 클라이언트 ID) 탓에 expo-linking이 시작마다 「multiple possible URI schemes」 경고를 낸다 —
// 첫 칸(앱 스킴)을 쓰는 것이 맞으므로 개발 화면의 노란 막대만 숨긴다(PR-L 설계 §3.7).
LogBox.ignoreLogs(['Linking found multiple possible URI schemes']);

// 스플래시 모듈(PR-L — expo-splash-screen)이 미색 올빼미 화면을 붙잡고 있다가 준비(글꼴 · 세션)되면 걷는다.
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
    // 로그인 · 401이 화면 틀을 바꾼다. 로그인 화면이 되면(로그아웃 · 탈퇴 · 401) 예약 알림 · LA · 위젯을 비운다 — 초안은 남긴다(PR-I §8-15)
    return session.subscribe((v) => {
      setSignedIn(v);
      if (!v) void clearSurfaces();
    });
  }, []);

  // 로그인된 채 켜지면(또는 로그인하면) 한 번 — 옛 글 화면의 기기 칸을 쪽지로 옮기고, 앱이 꺼져 남은 새 쪽지 칸을 보낸다(J5 · J100).
  // 화면을 막지 않는다 — 실패한 칸은 남아 다음 실행에 다시 간다.
  useEffect(() => {
    if (!signedIn) return;
    void session.userId().then(
      (userId) => {
        if (userId === null) return;
        return startSlotSync(AsyncStorage, userId);
      },
      () => undefined,
    );
  }, [signedIn]);

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
        {/* 쪽지(책생글 A1) — 화면이 머리 가운데 책 제목(책 없는 쪽지는 「책 없는 여백」) · 오른쪽 「내보내기」를 단다 */}
        <Stack.Screen name="margin/[bookId]" options={stacked} />
        {/* 계정 삭제(PR-I — 결정 A ① 쌓는 화면) */}
        <Stack.Screen name="account-delete" options={stacked} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="(auth)/login" />
      </Stack.Protected>
    </Stack>
  );
}
