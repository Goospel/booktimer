// 로그인 관문 — Apple 창을 열기 전에 본다(시각 보충 §1.6 · REQ-65).
// 시작 모드 = 만 14세 스위치가 켜져 있어야 한다. 웹 계정 모드 = 연결 코드 8자(나이는 웹 가입 때 확인했다).

export type LoginMode = 'start' | 'link';
export type GateResult = 'ok' | 'needAge' | 'needCode';

export const LINK_CODE_LENGTH = 8;

export function loginGate(p: { mode: LoginMode; ageOn: boolean; code: string }): GateResult {
  if (p.mode === 'start') return p.ageOn ? 'ok' : 'needAge';
  return p.code.trim().length === LINK_CODE_LENGTH ? 'ok' : 'needCode';
}
