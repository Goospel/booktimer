package com.booktimer.book;

/**
 * 도서 검색 결과 한 건 (읽기 전용). 검색 제공자(알라딘 등)에서 받아 화면에 보여주고,
 * 사용자가 "책장에 추가"하면 {@link Book}으로 영속된다.
 *
 * @param title        제목
 * @param author       저자
 * @param isbn13       ISBN-13
 * @param coverUrl     표지 이미지 URL
 * @param publisher    출판사
 * @param purchaseLink 구매 링크(제휴 태그 포함 가능)
 */
public record BookSearchResult(
        String title,
        String author,
        String isbn13,
        String coverUrl,
        String publisher,
        String purchaseLink) {
}
