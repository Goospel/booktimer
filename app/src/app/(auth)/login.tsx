// 로그인 첫 화면(시각 보충 §1.6 ② · 문구 §8 (가) · PR-P 피그마 S-LOGIN-3 안A) — 만 14세 스위치 + Apple · Google · 카카오 + 「웹에서 쓰던 계정이 있어요」.
// 웹 계정 모드만 다음 화면(연결 코드 칸)이 있고 Apple만 보인다(REQ-86 — loginProviders). 나이 스위치는 웹 가입 때 확인해 그 화면엔 없다.
import * as AppleAuthentication from 'expo-apple-authentication';
import { useEffect, useRef, useState } from 'react';
import { Alert, AppState, Image, type ImageSourcePropType, Linking, Pressable, View } from 'react-native';

import { appleSignIn, idTokenSignIn } from '../../api/client';
import { Button, Field, Notice, Screen, SwitchRow, Txt } from '../../components/ui';
import { appleCredential } from '../../lib/auth/apple';
import { googleSignIn } from '../../lib/auth/google';
import { kakaoIdToken } from '../../lib/auth/kakao';
import { type LoginMode, LINK_CODE_LENGTH, loginProviders, type Provider, runLogin } from '../../lib/loginGate';
import { guardOpen, isResumeFromBackground, type LoginTrack, RESUME_RELEASE_MS, shouldRelease } from '../../lib/loginResume';
import { signedOutNotice } from '../../lib/logout';
import { brand } from '../../theme/brand';
import { color, space } from '../../theme/tokens';

const BUTTON_HEIGHT = 48;

// 구글 · 카카오 공식 자산(피그마가 내보낸 PNG — 다시 그리지 않는다). 표식 크기 · 표식 뒤 사이는 피그마 사양 §15 그대로
const MARK: Record<'google' | 'kakao', { src: ImageSourcePropType; size: number; gap: number; label: string }> = {
  google: { src: require('../../../assets/brand/google-g.png'), size: 20, gap: 12, label: 'Google 계정으로 로그인' },
  kakao: { src: require('../../../assets/brand/kakao-symbol.png'), size: 14, gap: 8, label: '카카오 로그인' },
};

/** 구글 · 카카오 단추 — 높이 48 · 모서리 12 · 좌우 16 · 시스템 Medium 16(큰 글자 고정 — 공식 단추 비율) · 표식+글자 묶음 가운데. */
function BrandButton(p: { kind: 'google' | 'kakao'; busy: boolean; onPress: () => void }) {
  const m = MARK[p.kind];
  const c = brand[p.kind];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={m.label}
      accessibilityState={{ disabled: p.busy, busy: p.busy }}
      onPress={p.busy ? undefined : p.onPress}
      style={({ pressed }) => ({
        height: BUTTON_HEIGHT,
        borderRadius: brand.radius,
        backgroundColor: c.fill,
        borderWidth: c.stroke ? 1 : 0,
        borderColor: c.stroke ?? undefined,
        paddingHorizontal: space[4],
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: m.gap,
        opacity: p.busy ? 0.5 : pressed ? 0.7 : 1,
      })}
    >
      <Image source={m.src} style={{ width: m.size, height: m.size }} />
      <Txt v="BT" maxFontSizeMultiplier={1} numberOfLines={1} style={{ color: c.text }}>
        {m.label}
      </Txt>
    </Pressable>
  );
}

/** 구글이 같은 이메일의 인증 안 된 웹 계정을 알릴 때(REQ-79 — 피그마 S-LOGIN-UNVERIFIED). 「새로 시작하기」만 참. */
const confirmUnverified = () =>
  new Promise<boolean>((ok) =>
    Alert.alert(
      '새로 시작할까요?',
      '이 이메일로 만든, 인증을 마치지 않은 웹 계정이 있어요. 새로 시작하면 그 계정과 기록이 지워져요. 웹에서 이메일 인증을 마치면 그 계정으로 들어올 수 있어요',
      [
        { text: '취소', style: 'cancel', onPress: () => ok(false) },
        { text: '새로 시작하기', style: 'destructive', onPress: () => ok(true) },
      ],
      { cancelable: false },
    ),
  );

export default function Login() {
  const [mode, setMode] = useState<LoginMode>('start');
  const [ageOn, setAgeOn] = useState(false);
  const [code, setCode] = useState('');
  const [notice, setNotice] = useState<{ message: string; tone: 'info' | 'error' } | null>(null);
  const [busy, setBusy] = useState(false);
  // U-98 ⑤ — 카카오톡에서 그냥 돌아오면 SDK 약속이 끝나지 않는다. 시도 번호로 옛 결과를 버리고, 앱 복귀 2초 뒤 같은 시도가 아직 창을 기다리며 busy면 푼다
  const track = useRef<LoginTrack>({ attempt: 0, opening: 0, busy: false }).current;
  const markBusy = (v: boolean) => {
    track.busy = v;
    setBusy(v);
  };

  // 탈퇴 뒤 한 번만 「계정을 지웠어요 …」(PR-I · REQ-111 · 112). 효과에 둔다 — 개발 모드의 두 번 부름에서 둘째(빈 값)는 덮지 않는다
  useEffect(() => {
    const m = signedOutNotice.take();
    if (m) setNotice({ message: m, tone: 'info' });
  }, []);

  useEffect(() => {
    let prev = AppState.currentState;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const sub = AppState.addEventListener('change', (next) => {
      if (isResumeFromBackground(prev, next)) {
        const attempt = track.attempt;
        clearTimeout(timer);
        timer = setTimeout(() => {
          if (shouldRelease(track, attempt)) markBusy(false);
        }, RESUME_RELEASE_MS);
      }
      prev = next;
    });
    return () => {
      sub.remove();
      clearTimeout(timer);
    };
  }, []);

  async function onLogin(provider: Provider) {
    const attempt = ++track.attempt;
    const isLatest = () => track.attempt === attempt;
    setNotice(null);
    markBusy(true);
    const r = await runLogin(
      { provider, mode, ageOn, code },
      {
        // 새 시도가 시작됐으면 늦게 온 토큰은 서버로 보내지 않는다(닫음 처리) — guardOpen
        open: guardOpen(track, attempt, async (pv: Provider) =>
          pv === 'apple'
            ? ((await appleCredential())?.identityToken ?? null)
            : pv === 'google'
              ? ((await googleSignIn())?.identityToken ?? null)
              : await kakaoIdToken(),
        ),
        submit: (pv, token) =>
          pv === 'apple'
            ? appleSignIn({ mode, identityToken: token, code })
            : idTokenSignIn({ provider: pv, identityToken: token, ageConfirmed: mode === 'start' && ageOn, confirmUnverified }),
      },
    );
    if (!isLatest()) return; // 옛 시도의 늦은 결과 — busy · 알림 · 확인 창은 새 시도의 몫
    markBusy(false);
    if ('gate' in r) {
      setNotice({ message: r.gate === 'needAge' ? '만 14세 이상인지 먼저 확인해 주세요' : '연결 코드 8자리를 먼저 넣어 주세요', tone: 'error' });
    } else if ('error' in r) setNotice({ message: r.error, tone: 'error' });
    else if ('done' in r && r.notice) Alert.alert('', r.notice); // 화면 틀이 탭으로 바뀌어도 남는 시스템 확인 창
  }

  function switchMode(next: LoginMode) {
    setMode(next);
    setNotice(null);
  }

  return (
    <Screen scroll>
      {mode === 'start' ? (
        <View style={{ gap: space[3], paddingTop: space[8] }}>
          <Image
            source={require('../../../assets/woodcut-owl.png')}
            style={{ width: 96, height: 96, tintColor: color.ink }}
            resizeMode="contain"
          />
          <Txt v="M1">책생글</Txt>
          <Txt v="Q1" style={{ color: color.text }}>
            읽으며 건진 문장으로, 내 글을 써요
          </Txt>
          <SwitchRow
            label="만 14세 이상이에요"
            value={ageOn}
            onChange={(v) => {
              setAgeOn(v);
              if (v) setNotice(null);
            }}
            help="책생글은 만 14세 이상만 쓸 수 있어요"
          />
        </View>
      ) : (
        <View style={{ gap: space[3], paddingTop: space[8] }}>
          <Txt v="D2">웹 계정 잇기</Txt>
          <Txt v="Q1" style={{ color: color.text }}>
            웹(booktimer.app)에서 쓰던 책과 여백을 이 앱에서 이어서 쓸 수 있어요
          </Txt>
          <Field
            label="연결 코드"
            placeholder="8자리"
            value={code}
            onChangeText={setCode}
            autoCapitalize="characters"
            autoCorrect={false}
            maxLength={LINK_CODE_LENGTH + 2}
          />
          <Txt v="L1" style={{ color: color.muted }}>
            웹 booktimer.app에 로그인한 뒤 설정 {'>'} 앱 연결에서 받은 코드예요. 5분 동안 한 번만 쓸 수 있어요
          </Txt>
        </View>
      )}

      {notice ? <Notice message={notice.message} tone={notice.tone} /> : null}

      <View style={{ gap: space[3] }}>
        {loginProviders(mode).map((pv) =>
          pv === 'apple' ? (
            <AppleAuthentication.AppleAuthenticationButton
              key={pv}
              buttonType={AppleAuthentication.AppleAuthenticationButtonType.SIGN_IN}
              buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.BLACK}
              cornerRadius={brand.radius}
              style={{ height: BUTTON_HEIGHT, opacity: busy ? 0.5 : 1 }}
              onPress={busy ? () => {} : () => void onLogin('apple')}
            />
          ) : (
            <BrandButton key={pv} kind={pv} busy={busy} onPress={() => void onLogin(pv)} />
          ),
        )}
        {mode === 'start' ? (
          <View style={{ gap: space[1] }}>
            <Button kind="outline" label="웹에서 쓰던 계정이 있어요" onPress={() => switchMode('link')} />
            <Txt v="L1" style={{ color: color.muted }}>
              웹에서 구글로 가입했거나 구글 계정의 이메일로 가입했다면 위 Google 버튼으로 바로 들어와요
            </Txt>
          </View>
        ) : (
          <Button kind="text" label="처음이라면 돌아가기" onPress={() => switchMode('start')} />
        )}
      </View>

      <View style={{ flexDirection: 'row', justifyContent: 'center', gap: space[2] }}>
        <Button kind="text" label="처리방침" onPress={() => Linking.openURL('https://booktimer.app/privacy')} />
        <Button kind="text" label="이용약관" onPress={() => Linking.openURL('https://booktimer.app/terms')} />
      </View>
    </Screen>
  );
}
