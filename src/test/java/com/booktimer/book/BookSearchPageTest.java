package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 페이징 계산(전체 페이지 수·이전/다음 여부) 단위테스트 — 순수 로직.
 */
class BookSearchPageTest {

    private static BookSearchResult dummy() {
        return new BookSearchResult("t", null, null, null, null, null);
    }

    private static BookSearchPage page(int page, int pageSize, int total) {
        return new BookSearchPage(List.of(dummy()), page, pageSize, total);
    }

    @Test
    @DisplayName("전체 페이지 수는 totalResults/pageSize 올림이다")
    void totalPages_ceilDivision() {
        assertThat(page(1, 10, 25).totalPages()).isEqualTo(3);
        assertThat(page(1, 10, 20).totalPages()).isEqualTo(2);
        assertThat(page(1, 10, 1).totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 페이지는 이전 없음, 결과가 더 있으면 다음 있음")
    void firstPage_prevNext() {
        BookSearchPage p = page(1, 10, 25);
        assertThat(p.hasPrev()).isFalse();
        assertThat(p.hasNext()).isTrue();
        assertThat(p.nextPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 페이지는 다음 없음, 이전 있음")
    void lastPage_prevNext() {
        BookSearchPage p = page(3, 10, 25);
        assertThat(p.hasPrev()).isTrue();
        assertThat(p.hasNext()).isFalse();
        assertThat(p.prevPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과 없으면 전체 0페이지, 이전·다음 모두 없음")
    void empty_noPages() {
        BookSearchPage p = BookSearchPage.empty(1, 10);
        assertThat(p.isEmpty()).isTrue();
        assertThat(p.totalPages()).isZero();
        assertThat(p.hasPrev()).isFalse();
        assertThat(p.hasNext()).isFalse();
    }
}
