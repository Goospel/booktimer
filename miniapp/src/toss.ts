import { TossAuth } from '@apps-in-toss/web-framework';

/** `appLogin()`이 주는 것 — 서버 `TossAuthRequest`와 같은 모양이라 그대로 실어 보낸다. */
export interface TossLoginResult {
  authorizationCode: string;
  referrer: 'DEFAULT' | 'SANDBOX';
}

/**
 * 토스 로그인 — 인가코드는 10분·일회성이라 인증이 필요한 시점마다 새로 부른다(무마찰).
 *
 * <p>SDK 3.x에서 최상위 `appLogin()`은 deprecated이고 `TossAuth.login`이 같은 시그니처의 후속이다.
 * 여기 한 함수로 감싸 둬서 SDK가 또 바뀌어도 고칠 곳이 한 줄이다.
 */
export function tossLogin(): Promise<TossLoginResult> {
  return TossAuth.login();
}
