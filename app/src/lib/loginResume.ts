// 앱 복귀 뒤 로그인 busy 풀기(U-98 ⑤ 사소 보정 — 설계 §6.3 login.tsx 주석). import 0 — 테스트가 그대로 읽는다.
// 카카오톡에서 아무것도 안 누르고 앱 전환기로 돌아오면 SDK 약속이 끝나지 않아 busy가 굳는다(2026-10-27 iPad 실측).
// 조절 상수 — 1초 이하면 동의하고 돌아오는 정상 복귀를 끊고, 5초 이상이면 사람이 막혔다고 느낀다. 기기 재측정으로 고친다
export const RESUME_RELEASE_MS = 2000;

/** 화면 한 장의 로그인 상태. attempt = 최신 시도 번호 · opening = 제공자 창을 기다리는 시도(0 = 없음). */
export type LoginTrack = { attempt: number; opening: number; busy: boolean };

/** 앱 복귀 = background → active만. Apple · 구글 시스템 창은 inactive를 거쳐 돌아와 해당 없다. */
export function isResumeFromBackground(prev: string | null | undefined, next: string): boolean {
  return prev === 'background' && next === 'active';
}

/** 제공자 창 감싸기 — 창이 떠 있는 동안만 opening = 그 시도. 돌아왔을 때 새 시도가 시작됐으면 늦은 토큰은 닫음(null)이라 서버로 가지 않는다. */
export function guardOpen<P>(t: LoginTrack, attempt: number, open: (pv: P) => Promise<string | null>) {
  return async (pv: P): Promise<string | null> => {
    t.opening = attempt;
    try {
      const token = await open(pv);
      return t.attempt === attempt ? token : null;
    } finally {
      if (t.opening === attempt) t.opening = 0; // 새 시도의 「창 대기 중」은 지우지 않는다
    }
  };
}

/** 복귀 대기 뒤에도 그 시도가 최신이고, 아직 제공자 창을 기다리며 busy면 푼다 — 서버 제출 중에는 풀지 않는다. */
export function shouldRelease(t: LoginTrack, attempt: number): boolean {
  return t.busy && t.attempt === attempt && t.opening === attempt;
}
