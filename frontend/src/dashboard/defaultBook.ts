import type { BookOption } from './types'
import type { StudyBookRow } from '../study/api'

/**
 * 홈의 「지금 그 책」 — 최근 읽은 책(이어 읽기), 없으면 첫 책.
 *
 * <p><b>호출부는 읽는 중 책만 넘긴다</b>(R2 결정 5, 2026-09-24). 다 읽은 책이 기본 칩에 서면 「측정 시작」
 * 한 번에 끝난 책으로 시간이 쌓인다 — 그래서 recentBookId가 다 읽은 책이면 목록에 없어 읽는 중 첫 책으로
 * 떨어지고, 읽는 중이 0권이면 null(빈 상태가 「책 고르기」로 답한다). 미니앱 캐러셀과 같은 규칙이다.
 *
 * <p>타이머 칩과 여백 카드가 <b>같은 함수</b>를 봐야 한다. 각자 계산하면 recentBookId가 지워진 책을
 * 가리키는 날 「칩엔 A, 여백엔 B」로 갈린다 — 그래서 없는 id는 null이 아니라 첫 책으로 떨어뜨린다.
 */
export function defaultBookOf(books: BookOption[], recentBookId: number | null): BookOption | null {
    return books.find(b => b.id === recentBookId) ?? books[0] ?? null
}

/**
 * 공부 칩·홈 필기가 같이 보는 「지금 그 책」 — 고른 책(서버 최신 행으로) → 최근 걸고 잰 책 → 첫 책.
 *
 * <p>고른 책은 **서버 최신 행으로** 다시 찾는다 — 시트에서 붙잡은 사본은 회당 시간을 저장해도 안 바뀐다.
 */
export function defaultStudyBookOf(
    books: StudyBookRow[], recentBookId: number | null, picked: StudyBookRow | null,
): StudyBookRow | null {
    return (picked && (books.find(b => b.id === picked.id) ?? picked))
        ?? books.find(b => b.id === recentBookId) ?? books[0] ?? null
}
