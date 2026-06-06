package com.booktimer.book;

/**
 * 도서 검색 결과 한 건 (읽기 전용). 검색 제공자(알라딘 등)에서 받아 화면에 보여주고,
 * 사용자가 "책장에 추가"하면 {@link Book}으로 영속된다.
 *
 * <p>{@code category}·{@code pubDate}는 책BTI(독서 성향 분석)의 입력이 되는 카탈로그 메타데이터다 —
 * 장르 편향·신/구 분포를 집계하려면 등록 시 함께 저장해야 한다(설계: reading-personality-design.md §3).
 * 검색이 아닌 수동 입력 경로엔 이 메타가 없으므로 6-인자 생성자로 null을 둔다.
 *
 * @param title        제목
 * @param author       저자
 * @param isbn13       ISBN-13
 * @param coverUrl     표지 이미지 URL
 * @param publisher    출판사
 * @param purchaseLink 구매 링크(제휴 태그 포함 가능)
 * @param category     장르/카테고리(알라딘 categoryName, 예 "국내도서&gt;소설/시/희곡&gt;한국소설")
 * @param pubDate      출간일(알라딘 pubDate, 예 "2020-03-15")
 */
public record BookSearchResult(
        String title,
        String author,
        String isbn13,
        String coverUrl,
        String publisher,
        String purchaseLink,
        String category,
        String pubDate) {

    /** 카탈로그 메타(장르·출간일) 없이 생성 — 수동 입력 등 기존 호출 경로를 그대로 보존한다. */
    public BookSearchResult(String title, String author, String isbn13,
                            String coverUrl, String publisher, String purchaseLink) {
        this(title, author, isbn13, coverUrl, publisher, purchaseLink, null, null);
    }
}
