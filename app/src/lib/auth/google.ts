// 구글 로그인 감싸개(PR-P ③ — REQ-173 · REQ-86 · REQ-87 · REQ-112). 구글 프로바이더(expo-auth-session/providers/google)는 쓰지 않는다(R-03 가드가 잡는다).
import { AuthRequest, exchangeCodeAsync, Prompt, ResponseType, revokeAsync } from 'expo-auth-session';

import { GOOGLE_IOS_CLIENT_ID, GOOGLE_REDIRECT_URI } from './keys';

const DISCOVERY = {
  authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
  tokenEndpoint: 'https://oauth2.googleapis.com/token',
  revocationEndpoint: 'https://oauth2.googleapis.com/revoke',
}; // 구글 프로바이더의 값과 같다(expo-auth-session/src/providers/Google.ts) — 내려받기 왕복 0

/**
 * 구글 창 → ID 토큰 + 액세스 토큰(탈퇴 철회용 — 저장하지 않는다). null = 닫음 · 거절.
 * reauth(탈퇴 확인 — D4 (가))면 사파리와 로그인을 나누지 않는 창 — 계정 목록 없이 다시 로그인한다(구글은 prompt=login을 받지 않는다).
 */
export async function googleSignIn(o: { reauth?: boolean } = {}): Promise<{ identityToken: string; accessToken: string | null } | null> {
  const request = new AuthRequest({
    clientId: GOOGLE_IOS_CLIENT_ID,
    redirectUri: GOOGLE_REDIRECT_URI,
    scopes: ['openid', 'email', 'profile'], // 웹 구글 로그인과 같은 범위(application.properties)
    responseType: ResponseType.Code,
    prompt: Prompt.SelectAccount, // 공유 사파리 세션의 다른 구글 계정이 조용히 열리지 않게
  }); // usePKCE 기본 참 · 공개 클라이언트라 비밀값 없음
  const r = await request.promptAsync(DISCOVERY, { preferEphemeralSession: !!o.reauth });
  if (r.type !== 'success') {
    if (r.type === 'error' && r.params?.error !== 'access_denied') throw r.error ?? new Error('google auth error');
    if (__DEV__) console.log('login closed', 'google', r.type);
    return null; // cancel · dismiss · locked · 거절
  }
  const token = await exchangeCodeAsync(
    { clientId: GOOGLE_IOS_CLIENT_ID, code: r.params.code, redirectUri: GOOGLE_REDIRECT_URI, extraParams: { code_verifier: request.codeVerifier ?? '' } },
    DISCOVERY,
  );
  if (!token.idToken) throw new Error('no id token');
  return { identityToken: token.idToken, accessToken: token.accessToken ?? null };
}

export async function revokeGoogle(accessToken: string) {
  await revokeAsync({ token: accessToken, clientId: GOOGLE_IOS_CLIENT_ID }, DISCOVERY);
  if (__DEV__) console.log('google revoke ok');
}
