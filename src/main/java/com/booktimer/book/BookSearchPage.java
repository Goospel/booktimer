package com.booktimer.book;

import java.util.List;

/**
 * 도서 검색 결과 한 페이지 + 페이징 메타데이터.
 *
 * @param results      이 페이지의 결과
 * @param page         현재 페이지(1-based)
 * @param pageSize     페이지당 결과 수
 * @param totalResults 전체 검색 결과 수(제공자가 알려준 값)
 */
public record BookSearchPage(List<BookSearchResult> results, int page, int pageSize, int totalResults) {

    /** 결과 없는 빈 페이지. */
    public static BookSearchPage empty(int page, int pageSize) {
        return new BookSearchPage(List.of(), page, pageSize, 0);
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    /** 전체 페이지 수(올림). 결과가 없으면 0. */
    public int totalPages() {
        if (totalResults <= 0 || pageSize <= 0) {
            return results.isEmpty() ? 0 : 1;
        }
        return (totalResults + pageSize - 1) / pageSize;
    }

    public boolean hasPrev() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }
}
