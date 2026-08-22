import { getCsrfToken } from '../follow'
import type { UserRowData } from '../follow'
import type { LikeState, MarginResponse } from './storyFeed'

/**
 * 여백 API 래퍼 (sns-design §13.4) — fetch 컨벤션은 shared/follow.ts와 동일:
 * `credentials: 'same-origin'` + mutation은 `X-CSRF-TOKEN` 헤더.
 */

/**
 * 책 하나의 여백 — 「누구의」(loginId) + 「어느 책」(bookId) 두 축.
 * 노출 게이트는 전부 서버(차단·IDOR·PRIVATE → 404). 공개 책이면 팔로우와 무관하게 목록이 온다.
 * 404·비정형 응답은 null로 수렴 — 패널이 "불러오지 못했어요"로 그린다(기존 방어 관례).
 */
export async function fetchMargin(loginId: string, bookId: number): Promise<MarginResponse | null> {
    const res = await fetch(`/api/stories/of/${encodeURIComponent(loginId)}?bookId=${bookId}`,
        { credentials: 'same-origin' })
    if (!res.ok) return null
    const data = await res.json()
    return data && Array.isArray(data.entries) ? data : null
}

/** 작성 — 상태코드 분기(429 레이트리밋·400 검증)는 작성 모달이 하므로 Response를 그대로 돌려준다. */
/** `quote`(책에서 옮긴 문장)는 선택 — 비우면 `null`을 보낸다(빈 문자열은 서버가 어차피 null로 떨어뜨린다). */
export function createStory(
    req: { text: string; quote: string | null; bookId: number; bgCode: string | null },
): Promise<Response> {
    return fetch('/api/stories', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify(req),
    })
}

/**
 * 좋아요를 누른다/취소한다. **POST는 멱등**이라 재전송해도 취소되지 않는다 — 그래서 토글 단일
 * 엔드포인트가 아니다. 실패(안 보이는 글·내 글·이미 취소됨 → 404)는 `null`로 수렴시키고, 화면이
 * 낙관적으로 칠했던 값을 되돌린다(기존 방어 관례 — `fetchMargin`과 같다).
 */
export async function toggleStoryLike(id: number, like: boolean): Promise<LikeState | null> {
    const res = await fetch(`/api/stories/${id}/like`, {
        method: like ? 'POST' : 'DELETE',
        credentials: 'same-origin',
        headers: { 'X-CSRF-TOKEN': getCsrfToken() },
    })
    if (!res.ok) return null
    const data = await res.json()
    return typeof data?.likeCount === 'number' ? data : null
}

/**
 * 그 글에 좋아요를 누른 사람들 — 「좋아요 N명」이 펴는 명단(최근순).
 *
 * <p>서버가 **차단 관계·핸들 없는 사람**을 이미 걸러 준다 — 화면은 받은 행을 그대로 그린다.
 * 안 보이는 글의 명단은 404이고, 실패·비정형은 `null`로 수렴시킨다(`fetchMargin`과 같은 방어 관례) —
 * 빈 배열로 뭉개면 「아직 아무도 안 눌렀다」는 거짓말이 된다.
 */
export async function fetchStoryLikers(id: number): Promise<UserRowData[] | null> {
    const res = await fetch(`/api/stories/${id}/likes`, { credentials: 'same-origin' })
    if (!res.ok) return null
    const data = await res.json()
    return Array.isArray(data) ? data : null
}

export async function deleteStory(id: number): Promise<boolean> {
    const res = await fetch(`/api/stories/${id}`, {
        method: 'DELETE',
        credentials: 'same-origin',
        headers: { 'X-CSRF-TOKEN': getCsrfToken() },
    })
    return res.ok
}
