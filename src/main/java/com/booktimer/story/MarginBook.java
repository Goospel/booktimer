package com.booktimer.story;

import com.booktimer.book.Book;

/**
 * 여백 헤더에 그릴 책 라벨 — 게이트를 통과한 책만 여기 담긴다.
 *
 * <p>{@code isPublic}이 {@code false}인 응답은 <b>소유자에게만</b> 나간다(2026-08-16 결정 2 —
 * 비공개 책 여백 = 나만 보는 메모). 클라이언트는 이 필드로 가시성 안내 문구를 가른다:
 * 「나만 봐요」 대 「팔로워에게 보여요」.
 *
 * <p>{@code isbn13}은 <b>「모두」 탭의 존재 조건</b>이다(2026-08-22 책축 개방) — {@code null}이면 그 책은
 * 책축 좌표가 없으므로(수동 등록 등) 화면이 탭 없이 예전 그대로 그려진다.
 */
public record MarginBook(Long id, String title, String author, String coverUrl, boolean isPublic,
                         String isbn13) {

    public static MarginBook of(Book book) {
        return new MarginBook(book.getId(), book.getTitle(), book.getAuthor(), book.getCoverUrl(),
                book.isPublic(), book.getIsbn13());
    }
}
