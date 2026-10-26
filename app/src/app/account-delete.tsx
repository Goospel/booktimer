// 계정 삭제(PR-I · 결정 A ① 쌓는 화면 — 피그마 S-SET-DELETE · S-SET-DELETE-WEB). 확인 둘 = 이 화면 + Apple 창. 뒤로가 취소다.
// Apple 토큰은 저장하지 않는다 — 창에서 받자마자 보낸다(서버 발급 10분 검사). 웹 갈래에선 아무것도 지우지 않는다.
// 구글 · 카카오 갈래는 SDK가 들어오는 PR-P가 같은 화면에 붙인다(lib/deleteVia CAN_CONFIRM).
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as AppleAuthentication from 'expo-apple-authentication';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Linking, View } from 'react-native';

import { account, session, writing } from '../api/client';
import { Button, Notice, Screen, Txt } from '../components/ui';
import { deleteAccount, deleteVia, type Via, WEB_DELETE_URL } from '../lib/deleteVia';
import { draftSync } from '../lib/draftSync';
import { signedOutNotice } from '../lib/logout';
import { color, space } from '../theme/tokens';

const openWeb = () => void Linking.openURL(WEB_DELETE_URL);

export default function AccountDelete() {
  const [info, setInfo] = useState<{ via: Via; pending: number; userId: number | null } | null>(null);
  const [notice, setNotice] = useState<{ message: string; web?: true } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const [acc, userId] = await Promise.all([session.account(), session.userId()]);
      const pending = userId === null ? 0 : await draftSync({ kv: AsyncStorage, userId, put: writing.save }).pending();
      setInfo({ via: deleteVia(acc), pending, userId });
    })().catch(() => setInfo({ via: 'WEB', pending: 0, userId: null })); // 못 읽으면 아무것도 지우지 않는 웹 갈래
  }, []);

  async function onDelete(userId: number | null) {
    setNotice(null);
    setBusy(true);
    try {
      // PR-P ③ 로직 단계 — 이 화면은 아직 Apple 갈래만 그린다(구글 · 카카오 갈래 · apple 재확인 연결은 피그마 확정 뒤 화면 단계)
      const r = await deleteAccount({ via: 'APPLE', appleLinked: false }, {
        apple: async () => null,
        unlinkKakao: async () => undefined,
        revokeGoogle: async () => undefined,
        confirm: async () => {
          try {
            // 이메일 · 이름은 받지 않는다 — 신원 재확인만
            const c = await AppleAuthentication.signInAsync({ requestedScopes: [] });
            if (!c.identityToken) throw new Error('no identity token');
            return { identityToken: c.identityToken, authorizationCode: c.authorizationCode ?? null };
          } catch (e) {
            if ((e as { code?: string }).code === 'ERR_REQUEST_CANCELED') return null; // 창을 닫았다
            throw e;
          }
        },
        remove: account.remove,
        clearDrafts: async () => {
          if (userId !== null) await draftSync({ kv: AsyncStorage, userId, put: writing.save }).clear();
        },
        // 표지는 세션을 지우기 전에 — 지우는 순간 루트 틀이 로그인 화면을 그린다
        clearSession: async () => {
          signedOutNotice.set('계정을 지웠어요');
          await session.clear();
        },
      });
      if ('error' in r) setNotice({ message: r.error, web: r.web });
    } finally {
      setBusy(false); // 세션 지우기가 실패해도 단추가 busy로 굳지 않게
    }
  }

  return (
    <Screen stacked scroll>
      <Txt v="D2">계정 삭제</Txt>
      {info === null ? (
        <View style={{ flexDirection: 'row', gap: space[2], alignItems: 'center' }}>
          <ActivityIndicator color={color.ink} />
          <Txt v="L1" style={{ color: color.text }}>
            불러오는 중이에요
          </Txt>
        </View>
      ) : info.via === 'APPLE' ? (
        <>
          <View style={{ gap: space[2] }}>
            <Txt v="Q1">계정을 지우면 이 계정의 책·여백·글과 독서·공부 기록이 모두 지워지고 되돌릴 수 없어요</Txt>
            <Txt v="L1" style={{ color: color.text }}>
              Apple로 한 번 더 확인하면 바로 지워져요
            </Txt>
            {info.pending > 0 ? (
              <Txt v="L1" style={{ color: color.text }}>
                이 기기에만 있는 저장 안 된 글 {info.pending}편도 함께 지워져요
              </Txt>
            ) : null}
          </View>
          {notice ? (
            <Notice message={notice.message} tone="error" action={notice.web ? { label: '웹 설정 열기', onPress: openWeb } : undefined} />
          ) : null}
          <View style={{ marginTop: space[2] }}>
            <Button kind="ink" label="Apple로 확인하고 지우기" busy={busy} onPress={() => void onDelete(info.userId)} />
          </View>
        </>
      ) : (
        <>
          <View style={{ gap: space[2] }}>
            <Txt v="Q1">웹(booktimer.app)에서 만든 계정이라 웹 설정에서 지울 수 있어요</Txt>
            <Txt v="L1" style={{ color: color.text }}>
              지우면 웹·토스 미니앱 기록까지 모두 지워져요
            </Txt>
            <Txt v="L1" style={{ color: color.text }}>
              웹에 로그인한 뒤 설정 맨 아래 「회원 탈퇴」에서 지울 수 있어요
            </Txt>
          </View>
          <View style={{ marginTop: space[2] }}>
            <Button kind="ink" label="웹 설정 열기" onPress={openWeb} />
          </View>
        </>
      )}
    </Screen>
  );
}
