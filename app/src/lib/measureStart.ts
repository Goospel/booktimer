// 측정 시작(범위 보충 REQ-114). 서버는 독서 · 공부 어느 쪽이든 재는 중이면 409인데 그 본문이 HTML이라
// 클라이언트 문구가 「요청에 실패했어요 (409)」가 된다 → 앱 문구로 바꾸고 대시보드를 다시 읽어 진행 중인 측정이 보이게 한다.
import { ApiError } from '../api/client';

export const BUSY_ELSEWHERE = '다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요';

export async function startReading(
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
