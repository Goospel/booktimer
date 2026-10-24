// 탈퇴 갈래 · Apple 탈퇴 흐름(v1 REQ-87 · REQ-156 · REQ-157). 갈래 표는 전체를 갖고, 지금 바이너리가 다시 확인할 수 있는 제공자만 인자로 받는다.
import { ApiError, failText } from '../api/client';

export type Via = 'APPLE' | 'GOOGLE' | 'KAKAO' | 'WEB';
type Confirmable = 'GOOGLE' | 'KAKAO';

/**
 * 지금 바이너리가 다시 확인할 수 있는 제공자 — PR-P가 'GOOGLE' · 'KAKAO'를 켠다(SDK가 그 빌드에 들어온다).
 * ⚠️ 불변식: 카카오 로그인과 같은 PR에서 'KAKAO'를 켠다 — 카카오 계정은 웹 로그인이 없어 웹 삭제 갈래로는 지울 수 없다.
 */
export const CAN_CONFIRM: ReadonlySet<Confirmable> = new Set();

export function deleteVia(
  a: { authProvider: string | null; googleLinked: boolean; webAccount: boolean },
  can: ReadonlySet<Confirmable> = CAN_CONFIRM,
): Via {
  switch (a.authProvider) {
    case null:
      return a.webAccount ? 'WEB' : 'APPLE'; // PR-I 전에 로그인한 세션 — 지금 바이너리에선 이것만으로 답이 같다
    case 'APPLE':
      return 'APPLE';
    case 'GOOGLE':
    case 'KAKAO':
      return can.has(a.authProvider) ? a.authProvider : 'WEB';
    case 'LOCAL':
    case 'TOSS':
      return a.googleLinked && can.has('GOOGLE') ? 'GOOGLE' : 'WEB';
    default:
      return 'WEB';
  }
}

export const WEB_DELETE_URL = 'https://booktimer.app/settings#delete';
export const APPLE_FAILED = 'Apple 확인에 실패했어요 — 다시 시도해 주세요';
export const DELETE_FAILED = '계정을 지우지 못했어요 — 잠시 뒤 다시 해 주세요';

/** Apple 창에서 방금 받은 토큰을 바로 보낸다(저장하지 않는다 — 서버 발급 10분 검사). 실패 · 취소는 아무것도 지우지 않는다. */
export async function deleteAccount(deps: {
  confirm: () => Promise<{ identityToken: string; authorizationCode: string | null } | null>;
  remove: (body: { provider: 'APPLE'; identityToken: string; authorizationCode: string | null }) => Promise<unknown>;
  clearDrafts: () => Promise<void>;
  clearSession: () => Promise<void>;
}): Promise<{ done: true } | { cancelled: true } | { error: string; web?: true }> {
  let cred: Awaited<ReturnType<typeof deps.confirm>>;
  try {
    cred = await deps.confirm();
  } catch {
    return { error: APPLE_FAILED };
  }
  if (cred === null) return { cancelled: true };
  try {
    await deps.remove({ provider: 'APPLE', identityToken: cred.identityToken, authorizationCode: cred.authorizationCode });
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) return { error: e.message, web: true };
    return { error: failText(e, DELETE_FAILED) };
  }
  await deps.clearDrafts();
  await deps.clearSession();
  return { done: true };
}
