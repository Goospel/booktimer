package com.booktimer.story;

import com.booktimer.book.Book;

/**
 * 책축 여백 화면의 헤더 라벨 — 표지·제목·저자.
 *
 * <p><b>주인 이름이 없다</b>: 이 화면의 주인공은 사람이 아니라 책이다(사람축 {@code MarginResponse}의
 * {@code ownerNickname}과 대비). 그래서 라벨의 출처가 누구의 책 행이든 화면은 달라지지 않는다 —
 * 서버는 viewer 본인 책 → 첫 공유 글의 책 순으로 아무거나 하나를 골라 채운다.
 *
 * <p>{@code isPublic}도 없다: 이 목록에 실린 글은 이미 전부 PUBLIC 책의 것이라 물어볼 것이 없다.
 *
 * <p>§3.4 화이트리스트: 노출 필드는 여기 정의된 것뿐.
 */
public record BookMarginLabel(String isbn13, String title, String author, String coverUrl) {

    public static BookMarginLabel of(Book book) {
        return new BookMarginLabel(book.getIsbn13(), book.getTitle(), book.getAuthor(),
                book.getCoverUrl());
    }
}
