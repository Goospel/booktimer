// 로그아웃(v1 REQ-38 · REQ-155) · 로그인 화면의 일회성 알림. 기기 표면(예약 알림 · LA · 위젯) 정리는 루트의 세션 구독이 한다 —
// 로그인 화면이 되는 모든 길(로그아웃 · 탈퇴 · 401)을 한 곳에서 받는다.

/** 미전송 기기 초안이 있으면 확인(거절이면 아무것도 안 함) → 서버 토큰 폐기(최선 노력) → 초안 → 세션 순서로 지운다. */
export async function logout(deps: {
  pending: () => Promise<number>;
  confirm: () => Promise<boolean>;
  revoke: () => Promise<unknown>;
  clearDrafts: () => Promise<void>;
  clearSession: () => Promise<void>;
}): Promise<'done' | 'kept'> {
  if ((await deps.pending()) > 0 && !(await deps.confirm())) return 'kept';
  try {
    await deps.revoke();
  } catch {
    // 연결 · 401이어도 기기에선 로그아웃한다 — 폐기 못 한 토큰은 기기에서 지워져 아무도 갖지 않는다
  }
  try {
    await deps.clearDrafts();
  } catch {
    // 초안을 못 지워도 세션은 지운다 — 서버 토큰은 이미 폐기했다
  }
  await deps.clearSession();
  return 'done';
}

let pendingNotice: string | null = null;
/** 탈퇴가 세션을 지우기 전에 set — 로그인 화면이 첫 렌더 뒤 take(한 번만) */
export const signedOutNotice = {
  set(message: string) {
    pendingNotice = message;
  },
  take(): string | null {
    const m = pendingNotice;
    pendingNotice = null;
    return m;
  },
};
