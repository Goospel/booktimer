// Apple 창 → 신원 토큰(PR-P ③). 로그인 · 탈퇴 · 탈퇴 때 Apple 한 번 더(REQ-111)가 같이 쓴다. 저장하지 않는다 — 받자마자 서버로.
import * as AppleAuthentication from 'expo-apple-authentication';

/** null = 창을 닫음. 이메일 · 이름은 받지 않는다 — 계정 이메일은 늘 합성 주소다(v1 §4.3 데이터 최소화). */
export async function appleCredential(): Promise<{ identityToken: string; authorizationCode: string | null } | null> {
  try {
    const c = await AppleAuthentication.signInAsync({ requestedScopes: [] });
    if (!c.identityToken) throw new Error('no identity token');
    return { identityToken: c.identityToken, authorizationCode: c.authorizationCode ?? null };
  } catch (e) {
    if ((e as { code?: string }).code === 'ERR_REQUEST_CANCELED') return null; // 창을 닫았다
    throw e;
  }
}
