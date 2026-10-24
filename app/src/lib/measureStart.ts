// 측정 시작 · 정지(범위 보충 REQ-114 · PR-S1 사양 REQ-151) — 홈(독서)과 공부 탭이 같은 함수를 쓴다.
// 서버는 독서 · 공부 어느 쪽이든 재는 중이면 409인데 그 본문이 HTML이라 클라이언트 문구가 「요청에 실패했어요 (409)」가 된다
// → 앱 문구로 바꾸고 대시보드를 다시 읽어 진행 중인 측정이 보이게 한다.
import { ApiError, failText } from '../api/client';

export const BUSY_ELSEWHERE = '다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요';
export const ALREADY_STOPPED = '이미 끝난 측정이에요';
export const STOP_FAILED = '측정을 끝내지 못했어요 — 잠시 뒤 다시 눌러 주세요';

export async function startMeasure(
  deps: { start: (bookId: number | null) => Promise<unknown>; reload: () => Promise<unknown> },
  bookId: number | null,
): Promise<{ error?: string }> {
  try {
    await deps.start(bookId);
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      await deps.reload();
      return { error: BUSY_ELSEWHERE };
    }
    return { error: e instanceof Error ? e.message : String(e) };
  }
  await deps.reload();
  return {};
}

/** 정지 — 409(다른 곳에서 이미 끝난 측정)는 안내, 그 밖 실패는 서버 · 연결 문구(없으면 STOP_FAILED). 어느 경우든 다시 읽는다. */
export async function stopMeasure(deps: { stop: () => Promise<unknown>; reload: () => Promise<unknown> }): Promise<{ error?: string; info?: string }> {
  let r: { error?: string; info?: string } = {};
  try {
    await deps.stop();
  } catch (e) {
    r = e instanceof ApiError && e.status === 409 ? { info: ALREADY_STOPPED } : { error: failText(e, STOP_FAILED) };
  }
  await deps.reload();
  return r;
}
