import { avatarInitial } from '../dashboard/timerProgress'

/**
 * 책방(/u/{loginId}) 표시용 순수 포맷터 — vitest(node 환경)에서 컴포넌트 mount 없이 테스트.
 * 로직은 ProfileApp 현행 동작을 추출만 한다(의미 변경 금지).
 */

/**
 * 누적 독서시간 → "N시간 M분". 현행 ProfileApp의 분해식(Math.floor)을 그대로 추출.
 * 60초 미만(0 포함)은 빈 문자열 — 호출부는 빈값이면 시간 메타를 렌더하지 않는다.
 */
export function formatReadingTime(sec: number): string {
    if (sec < 60) return ''
    const h = Math.floor(sec / 3600)
    const m = Math.floor((sec % 3600) / 60)
    return `${h}시간 ${m}분`
}

/**
 * 서버 한글 상태 라벨 → .shop-badge 3색 클래스.
 * 미지의/빈 라벨은 안전 폴백(reading) — 영문 enum이 새어들어도 깨지지 않게.
 */
export function statusBadgeClass(label: string): string {
    switch (label) {
        case '완독':     return 'shop-badge-finished'
        case '읽고 싶음': return 'shop-badge-want'
        case '읽는 중':   return 'shop-badge-reading'
        default:         return 'shop-badge-reading'
    }
}

/** 표지 폴백 글자 — 트림 후 첫 글자, 빈 값이면 '?'. avatarInitial과 동일 로직 재사용(단일 출처). */
export function coverInitial(title: string): string {
    return avatarInitial(title)
}

/** coverUrl이 실제 표지 URL인지 — null/빈문자열/공백만은 false. */
export function hasCover(coverUrl: string | null): boolean {
    return coverUrl != null && coverUrl.trim() !== ''
}
