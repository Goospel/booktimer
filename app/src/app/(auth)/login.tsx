// 로그인 첫 화면(시각 보충 §1.6 ② · 문구 §8 (가)) — 만 14세 스위치 + Apple로 계속하기 + 「웹에서 쓰던 계정이 있어요」.
// 웹 계정 모드만 다음 화면(연결 코드 칸)이 있다. 나이 스위치는 웹 가입 때 확인해 그 화면엔 없다.
import * as AppleAuthentication from 'expo-apple-authentication';
import { useEffect, useState } from 'react';
import { Alert, Image, Linking, View } from 'react-native';

import { ApiError, appleSignIn, NetworkError } from '../../api/client';
import { Button, Field, Notice, Screen, SwitchRow, Txt } from '../../components/ui';
import { type LoginMode, LINK_CODE_LENGTH, loginGate } from '../../lib/loginGate';
import { signedOutNotice } from '../../lib/logout';
import { color, space } from '../../theme/tokens';

const APPLE_HEIGHT = 48;

export default function Login() {
  const [mode, setMode] = useState<LoginMode>('start');
  const [ageOn, setAgeOn] = useState(false);
  const [code, setCode] = useState('');
  const [notice, setNotice] = useState<{ message: string; tone: 'info' | 'error' } | null>(null);
  const [busy, setBusy] = useState(false);

  // 탈퇴 뒤 한 번만 「계정을 지웠어요」(PR-I). 효과에 둔다 — 개발 모드의 두 번 부름에서 둘째(빈 값)는 덮지 않는다
  useEffect(() => {
    const m = signedOutNotice.take();
    if (m) setNotice({ message: m, tone: 'info' });
  }, []);

  async function onApple() {
    const gate = loginGate({ mode, ageOn, code });
    if (gate === 'needAge') return setNotice({ message: '만 14세 이상인지 먼저 확인해 주세요', tone: 'error' });
    if (gate === 'needCode') return setNotice({ message: '연결 코드 8자리를 먼저 넣어 주세요', tone: 'error' });
    setNotice(null);
    setBusy(true);
    try {
      // 이메일·이름은 받지 않는다 — 계정 이메일은 늘 합성 주소다(v1 §4.3 데이터 최소화)
      const cred = await AppleAuthentication.signInAsync({ requestedScopes: [] });
      if (!cred.identityToken) throw new Error('no identity token');
      const r = await appleSignIn({ mode, identityToken: cred.identityToken, code });
      if (r.notice) Alert.alert('', r.notice); // 화면 틀이 탭으로 바뀌어도 남는 시스템 확인 창
    } catch (e) {
      if ((e as { code?: string }).code === 'ERR_REQUEST_CANCELED') return; // 사용자가 Apple 창을 닫았다
      const message =
        e instanceof ApiError || e instanceof NetworkError ? e.message : 'Apple 로그인 확인에 실패했어요 — 다시 시도해 주세요';
      setNotice({ message, tone: 'error' });
    } finally {
      setBusy(false);
    }
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
        <AppleAuthentication.AppleAuthenticationButton
          buttonType={AppleAuthentication.AppleAuthenticationButtonType.CONTINUE}
          buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.BLACK}
          cornerRadius={APPLE_HEIGHT / 2}
          style={{ height: APPLE_HEIGHT, opacity: busy ? 0.5 : 1 }}
          onPress={busy ? () => {} : onApple}
        />
        {mode === 'start' ? (
          <Button kind="outline" label="웹에서 쓰던 계정이 있어요" onPress={() => switchMode('link')} />
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
