package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠팡 검색 링크 빌더 단위테스트 — 네트워크·DB 없이 enabled 게이트와 정적 URL 조립만 본다.
 *
 * <p>알라딘({@link AladinBookSearchClient})의 "추적코드 미설정이면 비활성 + 정적 순수 URL 조립" 패턴을
 * 그대로 모방한다. 쿠팡 링크는 DB에 저장하지 않고 책 메타(ISBN/제목)와 추적코드·템플릿에서 런타임 생성한다.
 */
class CoupangLinkBuilderTest {

    private static final String TEMPLATE =
            "https://www.coupang.com/np/search?q={query}&lptag={trackingCode}";

    private static Book book(String title, String isbn13) {
        User u = User.of("e@booktimer.com", "hash", "독자", "Asia/Seoul", Role.USER);
        return Book.register(u, title, null, isbn13, null, null, null, BookStatus.WANT_TO_READ);
    }

    @Test
    @DisplayName("추적코드 미설정이면 비활성 — 링크도 null(가입 전 쿠팡 버튼 숨김)")
    void disabled_whenTrackingCodeNotConfigured() {
        assertThat(new CoupangLinkBuilder("not-configured", TEMPLATE).isEnabled()).isFalse();
        assertThat(new CoupangLinkBuilder("  ", TEMPLATE).isEnabled()).isFalse();
        assertThat(new CoupangLinkBuilder("not-configured", TEMPLATE)
                .buildSearchLink(book("클린 코드", "9788966260959"))).isNull();
    }

    @Test
    @DisplayName("추적코드가 설정되면 활성")
    void enabled_whenTrackingCodeConfigured() {
        assertThat(new CoupangLinkBuilder("AF1234567", TEMPLATE).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("검색어는 ISBN13을 우선 사용한다(동명 책 회피)")
    void query_usesIsbn13WhenPresent() {
        assertThat(CoupangLinkBuilder.queryFor(book("클린 코드", "9788966260959")))
                .isEqualTo("9788966260959");
    }

    @Test
    @DisplayName("ISBN이 없으면 제목으로 폴백한다")
    void query_fallsBackToTitleWhenNoIsbn() {
        assertThat(CoupangLinkBuilder.queryFor(book("클린 코드", null))).isEqualTo("클린 코드");
        // 공백 ISBN은 Book.register가 null로 정규화 → 제목 폴백
        assertThat(CoupangLinkBuilder.queryFor(book("클린 코드", "   "))).isEqualTo("클린 코드");
    }

    @Test
    @DisplayName("생성 URL에 추적코드가 실린다(제휴 귀속)")
    void link_includesTrackingCode() {
        String link = new CoupangLinkBuilder("AF1234567", TEMPLATE)
                .buildSearchLink(book("클린 코드", "9788966260959"));

        assertThat(link).contains("AF1234567");
        assertThat(link).contains("9788966260959");
    }

    @Test
    @DisplayName("검색어의 한글·공백·&는 퍼센트 인코딩된다(깨진 URL·쿼리 오염 방지)")
    void link_encodesQuery() {
        String link = new CoupangLinkBuilder("AF1", "https://www.coupang.com/np/search?q={query}")
                .buildSearchLink(book("클린 코드 & 리팩터링", null));

        // 원문 그대로 새지 않음
        assertThat(link).doesNotContain("클린 코드 & 리팩터링");
        // &가 쿼리 구분자로 새지 않고 인코딩됨(%26)
        assertThat(link).contains("%26");
    }
}
