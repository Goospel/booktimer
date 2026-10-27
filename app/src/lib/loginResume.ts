// 앱 복귀 뒤 로그인 busy 풀기(U-98 ⑤ 사소 보정 — 설계 §6.3 login.tsx 주석). import 0 — 테스트가 그대로 읽는다.
// 카카오톡에서 아무것도 안 누르고 앱 전환기로 돌아오면 SDK 약속이 끝나지 않아 busy가 굳는다(2026-10-27 iPad 실측).
export const RESUME_RELEASE_MS = 2000; // 조절 상수 — 복귀 뒤 이만큼 기다려도 같은 시도가 busy면 푼다

/** 앱 복귀 = background → active만. Apple · 구글 시스템 창은 inactive를 거쳐 돌아와 해당 없다. */
export function isResumeFromBackground(prev: string | null | undefined, next: string): boolean {
  return prev === 'background' && next === 'active';
}

/** 대기 뒤에도 그 시도가 최신이고 아직 busy면 푼다. */
export function shouldRelease(p: { attempt: number; latest: number; busy: boolean }): boolean {
  return p.busy && acceptResult(p.attempt, p.latest);
}

/** 결과(토큰 · 알림 · 확인 창)는 그 시도가 아직 최신일 때만 받는다 — 새 시도가 없으면 늦게 온 정상 결과도 받는다. */
export function acceptResult(attempt: number, latest: number): boolean {
  return attempt === latest;
}
