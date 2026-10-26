// 로그인 관문 — Apple 창을 열기 전에 본다(시각 보충 §1.6 · REQ-65).
// 시작 모드 = 만 14세 스위치가 켜져 있어야 한다. 웹 계정 모드 = 연결 코드 8자(나이는 웹 가입 때 확인했다).
import { failText } from '../api/client';

export type LoginMode = 'start' | 'link';
export type GateResult = 'ok' | 'needAge' | 'needCode';

export const LINK_CODE_LENGTH = 8;

export function loginGate(p: { mode: LoginMode; ageOn: boolean; code: string }): GateResult {
  if (p.mode === 'start') return p.ageOn ? 'ok' : 'needAge';
  return p.code.trim().length === LINK_CODE_LENGTH ? 'ok' : 'needCode';
}

// ── 3사 로그인(PR-P ③ — REQ-86) ───────────────────────────────────────────
export type Provider = 'apple' | 'google' | 'kakao';
const NAME: Record<Provider, string> = { apple: 'Apple', google: '구글', kakao: '카카오' };

/** 모드마다 보이는 로그인 창, 화면 순서대로(REQ-86). 웹 계정 모드는 연결 코드가 Apple 문에만 있어 Apple만. */
export function loginProviders(mode: LoginMode): readonly Provider[] {
  return mode === 'start' ? ['apple', 'google', 'kakao'] : ['apple'];
}

export type LoginResult = { gate: 'needAge' | 'needCode' } | { cancelled: true } | { done: true; notice?: string } | { error: string };

/** 관문 → 그 제공자 창 → 서버(REQ-86). 관문에 걸리면 어느 창도 열지 않고, 창을 닫으면 서버를 부르지 않는다. */
export async function runLogin(
  p: { provider: Provider; mode: LoginMode; ageOn: boolean; code: string },
  deps: {
    open: (provider: Provider) => Promise<string | null>; // identityToken — null = 닫음
    submit: (provider: Provider, identityToken: string) => Promise<{ notice?: string; cancelled?: true }>;
  },
): Promise<LoginResult> {
  // 화면만 믿지 않는다 — 웹 계정 모드의 관문은 나이를 보지 않으므로 구글 · 카카오 REGISTER가 그 모드로 새면 안 된다
  if (!loginProviders(p.mode).includes(p.provider)) return { error: `${NAME[p.provider]} 로그인은 이 화면에서 쓸 수 없어요` };
  const gate = loginGate(p);
  if (gate !== 'ok') return { gate };
  try {
    const token = await deps.open(p.provider);
    if (token === null) return { cancelled: true };
    const r = await deps.submit(p.provider, token);
    return r.cancelled ? { cancelled: true } : { done: true, notice: r.notice };
  } catch (e) {
    return { error: failText(e, `${NAME[p.provider]} 로그인 확인에 실패했어요 — 다시 시도해 주세요`) };
  }
}
