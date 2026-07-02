/**
 * 독서 스토리 피드 순수 로직 — 스트립 링 구성·뷰어 진행·작성 검증·신고 발췌 (sns-design §13.7).
 * DOM 무관 순수 함수만 둔다(vitest node 환경 대상). fetch는 storyApi.ts, 렌더는 Story*.vue 담당.
 */

/** 장당 자동 넘김 시간 (§13.7 — 인스타 문법 ~5초). */
export const STORY_DURATION_MS = 5000

/** 배경 팔레트 닫힌 코드 — 백엔드 Story.BG_CODES·app.css .story-bg-* 스와치와 코드 일치 계약. */
export const BG_CODES = ['paper', 'night', 'forest', 'sunset', 'sea', 'plum'] as const
export type BgCode = typeof BG_CODES[number]

const MAX_TEXT = 500
const REPORT_EXCERPT_MAX = 200
const REPORT_DETAIL_MAX = 500

export interface StoryCard {
    id: number
    text: string
    bgCode: string | null
    bookTitle: string | null
    bookCoverUrl: string | null
    createdAt: string
    viewed: boolean
}

export interface AuthorStories {
    loginId: string
    nickname: string
    profileCharacterCode: string | null
    allViewed: boolean
    stories: StoryCard[]
}

export interface StoryFeedResponse {
    mine: AuthorStories | null
    groups: AuthorStories[]
}

export interface StoryViewerEntry {
    loginId: string
    nickname: string
    profileCharacterCode: string | null
    viewedAt: string
}

/** 스트립 아바타 링 하나 — 내 링은 스토리가 없어도 표시(작성 진입). */
export interface StripRing {
    loginId: string
    nickname: string
    profileCharacterCode: string | null
    mine: boolean
    hasStories: boolean
    /** 미열람 강조 — 내 링은 항상 false(본인 열람은 기록되지 않음). */
    unviewed: boolean
    /** viewerGroups() 배열에서의 위치. 내 링인데 스토리가 없으면 -1(뷰어 대신 작성 진입). */
    groupIndex: number
}

/** 뷰어가 순차 재생할 그룹 배열 — 내 스토리가 있으면 맨 앞(스트립 순서 그대로). */
export function viewerGroups(feed: StoryFeedResponse): AuthorStories[] {
    return feed.mine ? [feed.mine, ...feed.groups] : feed.groups
}

/**
 * 스트립 링 목록 — 내 링 맨 앞 고정, 팔로잉 그룹은 서버 정렬(미열람 우선) 그대로.
 * 내 스토리가 없으면 me 폴백 정보로 링만 그린다(탭 = 작성 진입).
 */
export function stripRings(
    feed: StoryFeedResponse,
    me: { loginId: string; nickname: string; profileCharacterCode: string | null },
): StripRing[] {
    const offset = feed.mine ? 1 : 0
    const mineRing: StripRing = feed.mine
        ? {
            loginId: feed.mine.loginId, nickname: feed.mine.nickname,
            profileCharacterCode: feed.mine.profileCharacterCode,
            mine: true, hasStories: true, unviewed: false, groupIndex: 0,
        }
        : {
            loginId: me.loginId, nickname: me.nickname, profileCharacterCode: me.profileCharacterCode,
            mine: true, hasStories: false, unviewed: false, groupIndex: -1,
        }
    const groupRings = feed.groups.map((group, idx): StripRing => ({
        loginId: group.loginId, nickname: group.nickname,
        profileCharacterCode: group.profileCharacterCode,
        mine: false, hasStories: true, unviewed: !group.allViewed, groupIndex: idx + offset,
    }))
    return [mineRing, ...groupRings]
}

/** 뷰어 시작 카드 — 첫 미열람 위치, 전부 열람이면 처음부터(인스타 동일). */
export function firstUnviewedIndex(stories: StoryCard[]): number {
    const idx = stories.findIndex(story => !story.viewed)
    return idx < 0 ? 0 : idx
}

/** 진행바 세그먼트 상태 — 지난 카드 done / 현재 active / 남은 카드 todo. */
export function progressStates(count: number, current: number): Array<'done' | 'active' | 'todo'> {
    return Array.from({ length: count }, (_, i) => (i < current ? 'done' : i === current ? 'active' : 'todo'))
}

/** 신고 detail 접두 — `[스토리#id] 원문 발췌(≤200자)` (§13.5 — 운영자가 id로 원문 대조). */
export function excerptForReport(storyId: number, text: string): string {
    return `[스토리#${storyId}] ${text.slice(0, REPORT_EXCERPT_MAX)}`
}

/** 접두 + 사용자 상세 결합, 전체 500자 절삭(기존 신고 detail 상한과 동일 — §13.5). */
export function composeReportDetail(prefix: string, detail: string): string {
    const joined = detail ? `${prefix}\n${detail}` : prefix
    return joined.slice(0, REPORT_DETAIL_MAX)
}

/** 작성 가능 여부 — 백엔드 Story.of와 동일 경계(1~500자 비공백). */
export function canSubmit(text: string): boolean {
    const stripped = text.trim()
    return stripped.length > 0 && text.length <= MAX_TEXT
}

export function remainingChars(text: string): number {
    return MAX_TEXT - text.length
}

/** 작성 시각 라벨 — 활성 스토리는 24h 이내라 분·시간 단위면 충분. */
export function formatStoryAge(createdAt: string, nowMs: number): string {
    const ageMs = nowMs - Date.parse(createdAt)
    const minutes = Math.floor(ageMs / 60_000)
    if (minutes < 1) return '방금 전'
    if (minutes < 60) return `${minutes}분 전`
    return `${Math.floor(minutes / 60)}시간 전`
}
