package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠팡 검색 링크 빌더 단위테스트 — 네트워크·DB 없이 enabled 게이트와 정적 URL 조립만 본다.
 *
 * <p>게이트는 딥링크 API 키가 아니라 <b>파트너 등록 여부</b>({@code partnerEnabled})로 결정한다 — 버튼·고지문구
 * 노출은 "쿠팡 파트너스에 가입돼 있는가"의 문제이고, 딥링크 API 키 보유는 그중 "정식 추적링크로 변환 가능한가"라는
 * 별개 문제({@link CoupangDeeplinkClient}가 독립적으로 게이트)다. 두 게이트를 하나로 묶으면 아직 딥링크 API
 * 키가 없는(승인 대기) 기존 파트너의 버튼이 운영에서 사라지는 회귀가 생긴다(가입은 이미 완료·추적코드 발급 이력 있음).
 */
class CoupangLinkBuilderTest {

    private static final String TEMPLATE = "https://www.coupang.com/np/search?q={query}";

    private static Book book(String title, String isbn13) {
        User u = User.of("e@booktimer.com", "hash", "독자", "Asia/Seoul", Role.USER);
        return Book.register(u, title, null, isbn13, null, null, null, BookStatus.WANT_TO_READ);
    }

    @Test
    @DisplayName("파트너 미등록(partnerEnabled=false)이면 비활성 — 링크도 null(버튼 숨김)")
    void disabled_whenPartnerNotEnabled() {
        assertThat(new CoupangLinkBuilder(TEMPLATE, false).isEnabled()).isFalse();
        assertThat(new CoupangLinkBuilder(TEMPLATE, false)
                .buildSearchLink(book("클린 코드", "9788966260959"))).isNull();
    }

    @Test
    @DisplayName("파트너 등록(partnerEnabled=true)이면 활성 — 딥링크 API 키 유무와 무관")
    void enabled_whenPartnerEnabled() {
        assertThat(new CoupangLinkBuilder(TEMPLATE, true).isEnabled()).isTrue();
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
    @DisplayName("생성된 검색 URL은 딥링크 API 입력용 raw URL이라 추적코드를 싣지 않는다")
    void link_doesNotCarryTrackingCode() {
        String link = new CoupangLinkBuilder(TEMPLATE, true)
                .buildSearchLink(book("클린 코드", "9788966260959"));

        assertThat(link).isEqualTo("https://www.coupang.com/np/search?q=9788966260959");
    }

    @Test
    @DisplayName("검색어의 한글·공백·&는 퍼센트 인코딩된다(깨진 URL·쿼리 오염 방지)")
    void link_encodesQuery() {
        String link = new CoupangLinkBuilder(TEMPLATE, true)
                .buildSearchLink(book("클린 코드 & 리팩터링", null));

        // 원문 그대로 새지 않음
        assertThat(link).doesNotContain("클린 코드 & 리팩터링");
        // &가 쿼리 구분자로 새지 않고 인코딩됨(%26)
        assertThat(link).contains("%26");
    }

    @Test
    @DisplayName("운영 SSM에 남아있을 수 있는 옛 {trackingCode} 플레이스홀더는 방어적으로 제거된다(딥링크 API에 깨진 URL 전달 방지)")
    void link_stripsStaleTrackingCodePlaceholder() {
        String legacyTemplate = "https://www.coupang.com/np/search?q={query}&channel=user&lptag={trackingCode}";
        String link = new CoupangLinkBuilder(legacyTemplate, true)
                .buildSearchLink(book("클린 코드", "9788966260959"));

        assertThat(link).doesNotContain("{trackingCode}");
        assertThat(link).isEqualTo("https://www.coupang.com/np/search?q=9788966260959&channel=user&lptag=");
    }
}
