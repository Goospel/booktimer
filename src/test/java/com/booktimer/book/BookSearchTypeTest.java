package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 기준(제목/저자) 파싱·매핑 단위테스트.
 *
 * <p>사용자가 고른 검색 기준을 알라딘 QueryType으로 옮기는 규칙을 고정한다 — 기본은 제목(TITLE),
 * 잘못된/빈 값도 제목으로 폴백한다(검색이 깨지지 않게).
 */
class BookSearchTypeTest {

    @Test
    @DisplayName("기본값은 제목 — null/빈값/잘못된 값은 모두 TITLE로 폴백")
    void from_defaultsToTitle() {
        assertThat(BookSearchType.from(null)).isEqualTo(BookSearchType.TITLE);
        assertThat(BookSearchType.from("")).isEqualTo(BookSearchType.TITLE);
        assertThat(BookSearchType.from("  ")).isEqualTo(BookSearchType.TITLE);
        assertThat(BookSearchType.from("garbage")).isEqualTo(BookSearchType.TITLE);
    }

    @Test
    @DisplayName("이름으로 파싱(대소문자 무시)")
    void from_parsesByName() {
        assertThat(BookSearchType.from("TITLE")).isEqualTo(BookSearchType.TITLE);
        assertThat(BookSearchType.from("AUTHOR")).isEqualTo(BookSearchType.AUTHOR);
        assertThat(BookSearchType.from("author")).isEqualTo(BookSearchType.AUTHOR);
        assertThat(BookSearchType.from("PUBLISHER")).isEqualTo(BookSearchType.PUBLISHER);
        assertThat(BookSearchType.from("publisher")).isEqualTo(BookSearchType.PUBLISHER);
    }

    @Test
    @DisplayName("알라딘 QueryType으로 매핑된다")
    void mapsToAladinQueryType() {
        assertThat(BookSearchType.TITLE.getAladinQueryType()).isEqualTo("Title");
        assertThat(BookSearchType.AUTHOR.getAladinQueryType()).isEqualTo("Author");
        assertThat(BookSearchType.PUBLISHER.getAladinQueryType()).isEqualTo("Publisher");
    }
}
