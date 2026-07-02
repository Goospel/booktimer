import { getCsrfToken } from '../follow'
import type { StoryCard, StoryFeedResponse, StoryViewerEntry } from './storyFeed'

/**
 * 독서 스토리 API 래퍼 (sns-design §13.4) — fetch 컨벤션은 shared/follow.ts와 동일:
 * `credentials: 'same-origin'` + mutation은 `X-CSRF-TOKEN` 헤더.
 */

export async function fetchStoryFeed(): Promise<StoryFeedResponse> {
    const res = await fetch('/api/stories/feed', { credentials: 'same-origin' })
    if (!res.ok) throw new Error('story feed load failed')
    const data = await res.json()
    if (!data || !Array.isArray(data.groups)) throw new Error('story feed shape mismatch') // 비정형 응답 방어 — 스트립 숨김으로 수렴
    return data
}

/** 책방의 그 사람 활성 스토리 — 비팔로워는 서버가 빈 배열, 차단·미존재(404)도 빈 배열로 수렴(링 미표시). */
export async function fetchStoriesOf(loginId: string): Promise<StoryCard[]> {
    const res = await fetch(`/api/stories/of/${encodeURIComponent(loginId)}`, { credentials: 'same-origin' })
    if (!res.ok) return []
    const data = await res.json()
    return Array.isArray(data) ? data : [] // 비배열 응답 방어 — 링·뷰어가 렌더를 못 깨게
}

/** 작성 — 상태코드 분기(429 레이트리밋·400 상한/검증)는 작성 모달이 하므로 Response를 그대로 돌려준다. */
export function createStory(req: { text: string; bookId: number | null; bgCode: string | null }): Promise<Response> {
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

export async function deleteStory(id: number): Promise<boolean> {
    const res = await fetch(`/api/stories/${id}`, {
        method: 'DELETE',
        credentials: 'same-origin',
        headers: { 'X-CSRF-TOKEN': getCsrfToken() },
    })
    return res.ok
}

/** 열람 기록 — 서버가 멱등(uk_story_view)이라 실패는 조용히 무시(카드 진행을 막지 않는다). */
export function markStoryViewed(id: number): void {
    fetch(`/api/stories/${id}/view`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'X-CSRF-TOKEN': getCsrfToken() },
    }).catch(() => { /* 오프라인 등 — 다음 열람 때 다시 기록됨 */ })
}

/** 열람자 목록 — 작성자 본인만(서버 게이트). */
export async function fetchStoryViewers(id: number): Promise<StoryViewerEntry[]> {
    const res = await fetch(`/api/stories/${id}/viewers`, { credentials: 'same-origin' })
    if (!res.ok) return []
    return res.json()
}
