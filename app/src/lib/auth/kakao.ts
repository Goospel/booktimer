// 카카오 SDK 감싸개(PR-P ③ — REQ-173 · REQ-86 · REQ-87 · REQ-112). 토큰은 받자마자 돌려주고 저장하지 않는다(SDK는 자기 키체인에 둔다 —
// 로그아웃 logout · 탈퇴 unlink가 지운다).
import { initializeKakaoSDK } from '@react-native-kakao/core';
import { login, logout, unlink } from '@react-native-kakao/user';
import * as Application from 'expo-application';

import { kakaoKeyFor } from './keys';

// 앱 시작 때(이 모듈을 불러올 때) 초기화한다 — core · user는 다른 네이티브 모듈이라 「초기화 직후 첫 로그인」의 순서가 보장되지 않는다(추론).
// 카카오톡 복귀 처리기(AppDelegate)도 초기화된 SDK를 본다.
let ready = false;
function init() {
  if (ready) return;
  void initializeKakaoSDK(kakaoKeyFor(Application.applicationId));
  ready = true;
}
init();

const CLOSED = new Set(['Cancelled', 'AccessDenied']); // 창 닫기 · 동의 화면 취소(reason 이름 — 기기 확인은 U-98)

/**
 * 카카오톡(없으면 카카오계정 시트) → ID 토큰(null = 닫음). reauth(탈퇴 확인 — D4 (가))면 카카오톡을 건너뛰고 카카오계정 다시 로그인.
 * 이메일 · 닉네임은 받지 않는다(동의항목 최소 — U-50). 저장하지 않는다.
 */
export async function kakaoIdToken(o: { reauth?: boolean } = {}): Promise<string | null> {
  init();
  try {
    // prompts는 계정 로그인에서만 받는다(SDK kAssert)
    const t = await login(o.reauth ? { useKakaoAccountLogin: true, prompts: ['Login'] } : undefined);
    if (!t.idToken) throw new Error('no id token'); // 콘솔 OpenID Connect가 꺼지면 여기 — 그때 실패 문구
    return t.idToken;
  } catch (e) {
    const code = (e as { code?: string }).code ?? '';
    if (!CLOSED.has(code)) throw e;
    if (__DEV__) console.log('login closed', 'kakao', code); // U-98의 성공 신호 — 토큰 없음
    return null;
  }
}

export async function kakaoLogout() {
  init();
  await logout();
  if (__DEV__) console.log('kakao logout ok');
}

/** 실패는 개발 로그 한 줄 뒤 다시 던진다 — 탈퇴 흐름이 로그인 화면 문구로 끊는 곳을 알린다(REQ-112). */
export async function kakaoUnlink() {
  init();
  try {
    await unlink();
  } catch (e) {
    const code = (e as { code?: string }).code ?? ''; // 인자에 식을 넣지 않는다 — REQ-174 허용 목록은 리터럴 · code만
    if (__DEV__) console.warn('kakao unlink failed', code);
    throw e;
  }
  if (__DEV__) console.log('kakao unlink ok');
}
