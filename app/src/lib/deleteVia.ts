// 탈퇴 갈래 · 탈퇴 흐름(v1 REQ-87 · REQ-111 · REQ-112 · REQ-156 · REQ-157). 갈래 표는 전체를 갖고, 지금 바이너리가 다시 확인할 수 있는 제공자만 인자로 받는다.
import { ApiError, failText } from '../api/client';
import type { DeleteRequest } from '../api/types';

export type Via = 'APPLE' | 'GOOGLE' | 'KAKAO' | 'WEB';
type Confirmable = 'GOOGLE' | 'KAKAO';

/**
 * 지금 바이너리가 다시 확인할 수 있는 제공자 — PR-P가 'GOOGLE' · 'KAKAO'를 켠다(SDK가 그 빌드에 들어온다).
 * ⚠️ 불변식: 카카오 로그인과 같은 PR에서 'KAKAO'를 켠다 — 카카오 계정은 웹 로그인이 없어 웹 삭제 갈래로는 지울 수 없다.
 */
export const CAN_CONFIRM: ReadonlySet<Confirmable> = new Set(['GOOGLE', 'KAKAO']);
export const UNLINK_WAIT_MS = 5000; // 조절 상수 — 카카오 연결 끊기를 이만큼만 기다린다(REQ-112)

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

export type Reauth = Exclude<Via, 'WEB'>;
export type Confirmed = { identityToken: string; authorizationCode?: string | null; accessToken?: string | null };
type AppleCred = { identityToken: string; authorizationCode: string | null };
type Left = 'APPLE' | 'KAKAO';
const CONFIRM_FAILED: Record<Reauth, string> = {
  APPLE: APPLE_FAILED,
  GOOGLE: '구글 확인에 실패했어요 — 다시 시도해 주세요',
  KAKAO: '카카오 확인에 실패했어요 — 다시 시도해 주세요',
};
// 설계 §5 「탈퇴 뒤 로그인 화면 문구」의 「계정을 지웠어요 — 」 뒤쪽(피그마 S-LOGIN-LEFT 확정 전 — 바뀔 수 있다)
const LEFT_LINE: Record<Left, string> = {
  APPLE: 'Apple 로그인 연결은 아이폰 설정의 「Apple로 로그인」에서 끊어 주세요',
  KAKAO: '카카오 연결은 카카오톡 설정의 「연결된 서비스 관리」에서 끊어 주세요',
};

/** 탈퇴 뒤 로그인 화면 맨 위 문구 — 끊지 못한 연결이 있으면 끊는 곳을 덧붙인다(REQ-111 · 112). */
export function signedOutText(left: readonly Left[]): string {
  return ['계정을 지웠어요', ...left.map((k) => LEFT_LINE[k])].join(' — ');
}

/** ms 안에 이행되면 참 · 거부 · 시간 넘김은 거짓 — 기다림만 그만두고 호출은 취소하지 않는다. */
function settle(p: Promise<unknown>, ms: number): Promise<boolean> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return Promise.race([
    p.then(() => true, () => false),
    new Promise<boolean>((r) => {
      timer = setTimeout(() => r(false), ms);
    }),
  ]).finally(() => clearTimeout(timer));
}

/** 그 제공자 창에서 방금 받은 토큰을 바로 보낸다(저장하지 않는다 — 서버 발급 10분 검사). 실패 · 취소는 아무것도 지우지 않는다. */
export async function deleteAccount(
  p: { via: Reauth; appleLinked: boolean },
  deps: {
    confirm: () => Promise<Confirmed | null>;
    apple: () => Promise<AppleCred | null>; // REQ-111 — via가 APPLE이 아니고 appleLinked일 때만 부른다
    remove: (body: DeleteRequest) => Promise<unknown>;
    unlinkKakao: () => Promise<unknown>;
    revokeGoogle: (accessToken: string) => Promise<unknown>;
    clearDrafts: () => Promise<void>;
    clearSession: (notice: string) => Promise<void>; // 화면이 그 문구를 signedOutNotice에 싣고 세션을 지운다
  },
): Promise<{ done: true } | { cancelled: true } | { error: string; web?: true }> {
  let cred: Confirmed | null;
  try {
    cred = await deps.confirm();
  } catch {
    return { error: CONFIRM_FAILED[p.via] };
  }
  if (cred === null) return { cancelled: true };
  const left: Left[] = [];
  const body: DeleteRequest = { provider: p.via, identityToken: cred.identityToken };
  if (p.via === 'APPLE') body.authorizationCode = cred.authorizationCode ?? null;
  else if (p.appleLinked) {
    const a = await deps.apple().catch(() => null); // 닫거나 실패해도 지운다 — 서버는 철회만 건너뛰고 WARN(AppAccountApiController :127-142)
    if (a) Object.assign(body, { appleIdentityToken: a.identityToken, appleAuthorizationCode: a.authorizationCode });
    else left.push('APPLE'); // 조용히 넘기지 않는다 — 탈퇴 뒤 문구가 끊는 곳을 알린다
  }
  try {
    await deps.remove(body);
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) return { error: e.message, web: true };
    return { error: failText(e, DELETE_FAILED) };
  }
  // REQ-112 — 204 뒤에만. 카카오는 5초까지 기다려 실패를 문구로 알리고(카카오 「탈퇴에 연결 해제 필수」 · 서버 회수 길 없음), 구글 철회는 최선 노력
  if (p.via === 'KAKAO' && !(await settle(deps.unlinkKakao(), UNLINK_WAIT_MS))) left.push('KAKAO');
  if (p.via === 'GOOGLE' && cred.accessToken) void deps.revokeGoogle(cred.accessToken).catch(() => undefined);
  try {
    await deps.clearDrafts();
  } catch {
    // 초안을 못 지워도 세션은 지운다 — 서버 계정은 이미 지웠다
  }
  await deps.clearSession(signedOutText(left));
  return { done: true };
}

/**
 * 탈퇴 화면 문구(REQ-87). 피그마 F-B 확정 전 — 범위 보충 원문(`plans/2026-10-22-app-feature-scope.md` REQ-87 · §3.1)으로 채웠다.
 * 테스트는 갈래만 본다 — 어느 상수를 돌려주는가(문구 원문은 단언하지 않는다 · 범위 보충 :681).
 */
export const DELETE_LINE = {
  APPLE: 'Apple로 한 번 더 확인하면 바로 지워져요',
  GOOGLE: '웹에서도 쓰던 계정이에요 — 웹·토스 미니앱 기록까지 모두 지워져요',
  GOOGLE_EMAIL: '이메일로 가입한 웹 계정이에요 — 비밀번호를 묻지 않고 구글 확인만으로 웹·토스 미니앱 기록까지 모두 지워져요',
  KAKAO: '카카오로 한 번 더 확인하면 바로 지워져요',
} as const;
/** via ≠ APPLE이고 appleLinked일 때 화면이 한 줄 더(REQ-111 — 범위 보충 원문, F-B 확정 전) */
export const APPLE_AGAIN_LINE = 'Apple 로그인 연결도 끊을게요';

/** 갈래 → 화면 문구 · 단추. WEB은 null(웹 갈래 화면 그대로). */
export function deleteCopy(a: { via: Via; authProvider: string | null }): { line: string; button: string } | null {
  switch (a.via) {
    case 'APPLE':
      return { line: DELETE_LINE.APPLE, button: 'Apple로 확인하고 지우기' };
    case 'GOOGLE':
      return { line: a.authProvider === 'GOOGLE' ? DELETE_LINE.GOOGLE : DELETE_LINE.GOOGLE_EMAIL, button: '구글로 확인하고 지우기' };
    case 'KAKAO':
      return { line: DELETE_LINE.KAKAO, button: '카카오로 확인하고 지우기' };
    default:
      return null;
  }
}
