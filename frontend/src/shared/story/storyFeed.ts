/**
 * 여백 순수 로직 — 발광 판정·손잡이 노출·작성 검증·신고 발췌 (2026-08-16 책 귀속 재설계).
 *
 * 여백은 <b>책에 딸린 자리</b>고, 거기 쌓이는 것이 <b>글</b>이다. 인스타 문법(스트립·뷰어·진행바·열람)은
 * 폐기됐고 진입로는 「책방 리스트의 책 → 그 책의 여백」 하나뿐이다. 파일·타입 이름의 `story`는 서버
 * 경로(`/api/stories`)에 맞춘 내부 이름으로 남는다(#814 결정).
 *
 * DOM 무관 순수 함수만 둔다(vitest node 환경 대상). fetch는 storyApi.ts, 렌더는 MarginPanel/Composer 담당.
 */

/** 배경 팔레트 닫힌 코드 — 백엔드 Story.BG_CODES·app.css .story-bg-* 스와치와 코드 일치 계약. */
export const BG_CODES = ['paper', 'night', 'forest', 'sunset', 'sea', 'plum'] as const
export type BgCode = typeof BG_CODES[number]

const MAX_TEXT = 500
const REPORT_EXCERPT_MAX = 200
const REPORT_DETAIL_MAX = 500

/** 발광이 지속되는 창 — 하루. 서버는 시각만 주고 판정은 클라가 한다(설계 §D3ⓐ). */
export const FRESH_WINDOW_MS = 86_400_000

/** 책 한 권의 여백에 남긴 글 한 장. */
export interface MarginEntry {
    id: number
    text: string
    bgCode: string | null
    createdAt: string
}

/** 여백이 딸린 책 — 응답이 자기완결이라 패널이 다른 요청 없이 헤더를 그린다. */
export interface MarginBook {
    id: number
    title: string
    author: string | null
    coverUrl: string | null
}

export interface MarginResponse {
    book: MarginBook
    ownerNickname: string
    self: boolean
    /** 팔로우 관계 — self일 때 서버는 항상 false를 준다. */
    following: boolean
    /** 비팔로워에겐 빈 배열(글 유무도 안 샌다). */
    entries: MarginEntry[]
}

/**
 * 24시간 이내 새 글이 달린 책인가 — 책방 리스트 표지 발광의 유일한 근거.
 *
 * 경계는 <b>미만(&lt;)</b>이다: 정각 24시간은 이미 창 밖. null(글이 없거나 비팔로워라 서버가 가린 경우)은
 * false — 발광은 "새 글이 있다"는 단언이라 모르는 상태를 참으로 올리지 않는다.
 */
export function hasFreshStory(lastStoryAt: string | null, now: number): boolean {
    return lastStoryAt !== null && now - Date.parse(lastStoryAt) < FRESH_WINDOW_MS
}

/**
 * 리스트 행에 「여백」 손잡이를 세울지 — 프라이버시 게이트는 서버가 이미 했다(비팔로워는 lastStoryAt 전부 null).
 * 본인은 글이 없어도 노출(작성 진입), 남의 책은 글이 있을 때만(눌러도 빈 버튼을 안 만든다 — 이 집 관례).
 */
export function showMarginHandle(self: boolean, lastStoryAt: string | null): boolean {
    return self || lastStoryAt !== null
}

/** 신고 detail 접두 — `[글#id] 원문 발췌(≤200자)` (§13.5 — 운영자가 id로 원문 대조). */
export function excerptForReport(storyId: number, text: string): string {
    return `[글#${storyId}] ${text.slice(0, REPORT_EXCERPT_MAX)}`
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

/**
 * 작성 시각 라벨 — 글은 이제 사라지지 않으므로 하루가 넘으면 날짜로 떨어진다.
 * ("300시간 전"은 아무도 못 읽는다. 24시간 만료 시절엔 분·시간이면 충분했다.)
 */
export function formatStoryAge(createdAt: string, nowMs: number): string {
    const at = Date.parse(createdAt)
    const minutes = Math.floor((nowMs - at) / 60_000)
    if (minutes < 1) return '방금 전'
    if (minutes < 60) return `${minutes}분 전`
    if (minutes < 1440) return `${Math.floor(minutes / 60)}시간 전`
    const d = new Date(at) // 읽는 사람의 시간대 기준 날짜(서버 Instant는 UTC)
    return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`
}
