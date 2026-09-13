import type { BookOption } from './types'

/**
 * 홈이 아는 내 책 전부 — 읽는 중 → 다 읽음 → 읽고싶음 순. 이 순서가 곧 「첫 책」의 정의라
 * 폴백(아래)이 무엇을 고르는지를 결정한다.
 */
export function allBooksOf(reading: BookOption[], finished: BookOption[], wantToRead: BookOption[]): BookOption[] {
    return [...reading, ...finished, ...wantToRead]
}

/**
 * 홈의 「지금 그 책」 — 최근 읽은 책(이어 읽기), 없으면 첫 책.
 *
 * <p>타이머 칩과 여백 카드가 <b>같은 함수</b>를 봐야 한다. 각자 계산하면 recentBookId가 지워진 책을
 * 가리키는 날 「칩엔 A, 여백엔 B」로 갈린다 — 그래서 없는 id는 null이 아니라 첫 책으로 떨어뜨린다.
 */
export function defaultBookOf(books: BookOption[], recentBookId: number | null): BookOption | null {
    return books.find(b => b.id === recentBookId) ?? books[0] ?? null
}
