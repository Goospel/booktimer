// 계정 삭제(PR-I · 결정 A ① 쌓는 화면 — 피그마 S-SET-DELETE · S-SET-DELETE-WEB · PR-P S-SET-DELETE-GOOGLE · -GOOGLE-EMAIL · -KAKAO).
// 확인 둘 = 이 화면 + 그 제공자 창(구글 · 카카오는 다시 로그인 — D4 (가)). 뒤로가 취소다.
// 토큰은 저장하지 않는다 — 창에서 받자마자 보낸다(서버 발급 10분 검사). 웹 갈래에선 아무것도 지우지 않는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Linking, View } from 'react-native';

import { account, session, writing } from '../api/client';
import { Button, Notice, Screen, Txt } from '../components/ui';
import { appleCredential } from '../lib/auth/apple';
import { googleSignIn, revokeGoogle } from '../lib/auth/google';
import { kakaoIdToken, kakaoUnlink } from '../lib/auth/kakao';
import { APPLE_AGAIN_LINE, confirmFor, deleteAccount, deleteCopy, deleteVia, type Via, WEB_DELETE_URL } from '../lib/deleteVia';
import { draftSync } from '../lib/draftSync';
import { signedOutNotice } from '../lib/logout';
import { color, space } from '../theme/tokens';

const openWeb = () => void Linking.openURL(WEB_DELETE_URL);

type Info = { via: Via; authProvider: string | null; appleLinked: boolean; pending: number; userId: number | null };

export default function AccountDelete() {
  const [info, setInfo] = useState<Info | null>(null);
  const [notice, setNotice] = useState<{ message: string; web?: true } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const [acc, userId] = await Promise.all([session.account(), session.userId()]);
      const pending = userId === null ? 0 : await draftSync({ kv: AsyncStorage, userId, put: writing.save }).pending();
      setInfo({ via: deleteVia(acc), authProvider: acc.authProvider, appleLinked: acc.appleLinked, pending, userId });
    })().catch(() => setInfo({ via: 'WEB', authProvider: null, appleLinked: false, pending: 0, userId: null })); // 못 읽으면 아무것도 지우지 않는 웹 갈래
  }, []);

  async function onDelete(i: Info) {
    if (i.via === 'WEB') return;
    setNotice(null);
    setBusy(true);
    try {
      const r = await deleteAccount(
        { via: i.via, appleLinked: i.appleLinked },
        {
          confirm: confirmFor(i.via, { apple: appleCredential, google: googleSignIn, kakao: kakaoIdToken }),
          apple: appleCredential,
          remove: account.remove,
          unlinkKakao: kakaoUnlink,
          revokeGoogle,
          clearDrafts: async () => {
            if (i.userId !== null) await draftSync({ kv: AsyncStorage, userId: i.userId, put: writing.save }).clear();
          },
          // 표지는 세션을 지우기 전에 — 지우는 순간 루트 틀이 로그인 화면을 그린다
          clearSession: async (message) => {
            signedOutNotice.set(message);
            await session.clear();
          },
        },
      );
      if ('error' in r) setNotice({ message: r.error, web: r.web });
    } finally {
      setBusy(false); // 세션 지우기가 실패해도 단추가 busy로 굳지 않게
    }
  }

  const copy = info && deleteCopy({ via: info.via, authProvider: info.authProvider });

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
      ) : copy ? (
        <>
          <View style={{ gap: space[2] }}>
            <Txt v="Q1">계정을 지우면 이 계정의 책·여백·글과 독서·공부 기록이 모두 지워지고 되돌릴 수 없어요</Txt>
            <Txt v="L1" style={{ color: color.text }}>
              {copy.line}
            </Txt>
            {info.via !== 'APPLE' && info.appleLinked ? (
              <Txt v="L1" style={{ color: color.text }}>
                {APPLE_AGAIN_LINE}
              </Txt>
            ) : null}
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
            <Button kind="ink" label={copy.button} busy={busy} onPress={() => void onDelete(info)} />
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
