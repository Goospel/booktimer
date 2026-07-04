package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yes24 검색 링크 빌더 단위테스트 — 네트워크·DB 없이 enabled 게이트와 정적 URL 조립만 본다.
 *
 * <p>쿠팡({@link CoupangLinkBuilder})의 "추적코드 미설정이면 비활성 + 정적 순수 URL 조립" 패턴을 그대로
 * 모방한다(제휴 3제공자를 대칭 유지). Yes24 링크도 DB에 저장하지 않고 책 메타(ISBN/제목)와 추적코드·템플릿에서
 * 런타임 생성한다. 실제 Yes24 제휴 URL 형식은 가입 후 확정되므로 URL을 코드에 박지 않고 템플릿 환경변수로 둔다.
 *
 * <p>모바일 UA는 Yes24 자체 제휴 게이트가 딥링크 목적지를 버리고 모바일 메인으로 치환하므로(T-128),
 * 래퍼 없이 {@code mobileSearchUrlTemplate}(모바일 검색)로 직행한다 — {@link #isMobileUserAgent(String)}가
 * 그 분기 판정을 담당한다.
 */
class Yes24LinkBuilderTest {

    private static final String TEMPLATE =
            "https://www.yes24.com/product/search?query={query}&pid={trackingCode}";
    private static final String MOBILE_TEMPLATE = "https://m.yes24.com/mobile-search?q={query}";

    private static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/125.0.0.0 Mobile Safari/537.36";
    private static final String WINDOWS_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/125.0.0.0 Safari/537.36";
    private static final String MAC_SAFARI_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.5 Safari/605.1.15";

    private static Book book(String title, String isbn13) {
        User u = User.of("e@booktimer.com", "hash", "독자", "Asia/Seoul", Role.USER);
        return Book.register(u, title, null, isbn13, null, null, null, BookStatus.WANT_TO_READ);
    }

    private static Yes24LinkBuilder builder(String trackingCode) {
        return new Yes24LinkBuilder(trackingCode, TEMPLATE, MOBILE_TEMPLATE);
    }

    @Test
    @DisplayName("추적코드 미설정이면 비활성 — 링크도 null(가입 전 Yes24 버튼 숨김, mobile 여부 무관)")
    void disabled_whenTrackingCodeNotConfigured() {
        assertThat(builder("not-configured").isEnabled()).isFalse();
        assertThat(new Yes24LinkBuilder("  ", TEMPLATE, MOBILE_TEMPLATE).isEnabled()).isFalse();
        assertThat(builder("not-configured")
                .buildSearchLink(book("클린 코드", "9788966260959"), false)).isNull();
        assertThat(builder("not-configured")
                .buildSearchLink(book("클린 코드", "9788966260959"), true)).isNull();
    }

    @Test
    @DisplayName("추적코드가 설정되면 활성")
    void enabled_whenTrackingCodeConfigured() {
        assertThat(builder("LP1234567").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("추적코드는 설정됐어도 데스크톱 템플릿에 {trackingCode} 자리가 없으면 비활성 — 추적코드가 링크에 실릴 곳이 없어 '추적 0인데 버튼 뜨는' 무성실패가 되므로 버튼·고지문을 숨긴다(운영 SSM이 순수 검색 URL로 잘못/미설정된 상황 방어, T-131 계열)")
    void disabled_whenTemplateLacksTrackingCodePlaceholder() {
        String pureTemplate = "https://www.yes24.com/product/search?query={query}"; // {trackingCode} 자리 없음
        Yes24LinkBuilder b = new Yes24LinkBuilder("LP1234567", pureTemplate, MOBILE_TEMPLATE);

        assertThat(b.isEnabled()).isFalse();
        // 비활성이면 데스크톱·모바일 모두 링크 생성 안 함(무추적 링크로 사용자를 보내지 않음)
        assertThat(b.buildSearchLink(book("클린 코드", "9788966260959"), false)).isNull();
        assertThat(b.buildSearchLink(book("클린 코드", "9788966260959"), true)).isNull();
    }

    @Test
    @DisplayName("검색어는 ISBN13을 우선 사용한다(동명 책 회피)")
    void query_usesIsbn13WhenPresent() {
        assertThat(Yes24LinkBuilder.queryFor(book("클린 코드", "9788966260959")))
                .isEqualTo("9788966260959");
    }

    @Test
    @DisplayName("ISBN이 없으면 제목으로 폴백한다")
    void query_fallsBackToTitleWhenNoIsbn() {
        assertThat(Yes24LinkBuilder.queryFor(book("클린 코드", null))).isEqualTo("클린 코드");
        // 공백 ISBN은 Book.register가 null로 정규화 → 제목 폴백
        assertThat(Yes24LinkBuilder.queryFor(book("클린 코드", "   "))).isEqualTo("클린 코드");
    }

    @Test
    @DisplayName("생성 URL에 추적코드가 실린다(제휴 귀속) — mobile=false(데스크톱)")
    void link_includesTrackingCode() {
        String link = builder("LP1234567")
                .buildSearchLink(book("클린 코드", "9788966260959"), false);

        assertThat(link).contains("LP1234567");
        assertThat(link).contains("9788966260959");
    }

    @Test
    @DisplayName("검색어의 한글·공백·&는 퍼센트 인코딩된다(깨진 URL·쿼리 오염 방지) — mobile=false")
    void link_encodesQuery() {
        String link = new Yes24LinkBuilder("LP1", "https://www.yes24.com/product/search?query={query}&pid={trackingCode}",
                MOBILE_TEMPLATE)
                .buildSearchLink(book("클린 코드 & 리팩터링", null), false);

        // 원문 그대로 새지 않음
        assertThat(link).doesNotContain("클린 코드 & 리팩터링");
        // &가 쿼리 구분자로 새지 않고 인코딩됨(%26)
        assertThat(link).contains("%26");
    }

    @Test
    @DisplayName("mobile=false면 기존 데스크톱 템플릿을 그대로 쓴다(회귀 가드)")
    void desktop_usesDesktopTemplate_whenMobileFalse() {
        String link = builder("LP1234567")
                .buildSearchLink(book("클린 코드", "9788966260959"), false);

        assertThat(link).startsWith("https://www.yes24.com/product/search");
        assertThat(link).doesNotContain("m.yes24.com");
    }

    @Test
    @DisplayName("mobile=true면 제휴 래퍼 없이 모바일 검색 템플릿으로 링크를 만든다"
            + "(Yes24 게이트가 딥링크를 버려 우회 불가 — T-128)")
    void mobile_usesMobileTemplate() {
        String link = builder("LP1234567")
                .buildSearchLink(book("클린 코드", "9788966260959"), true);

        assertThat(link).isEqualTo("https://m.yes24.com/mobile-search?q=9788966260959");
        assertThat(link).doesNotContain("LP1234567"); // 래퍼를 안 타므로 추적코드가 실리지 않음
    }

    @Test
    @DisplayName("모바일 템플릿 기본값(m.yes24.com 검색)으로 링크가 생성된다")
    void mobileTemplate_defaultValue_producesMYes24Search() {
        String defaultMobileTemplate = "https://m.yes24.com/search?query={query}";
        String link = new Yes24LinkBuilder("LP1234567", TEMPLATE, defaultMobileTemplate)
                .buildSearchLink(book("클린 코드", "9788966260959"), true);

        assertThat(link).isEqualTo("https://m.yes24.com/search?query=9788966260959");
    }

    @Test
    @DisplayName("isMobileUserAgent: Yes24 자체 기기 판별 목록(Android/BlackBerry/iPhone/iPad/iPod/"
            + "Opera Mini/IEMobile)을 미러링한 판정")
    void isMobileUserAgent_detectsKnownMobileDevices() {
        assertThat(Yes24LinkBuilder.isMobileUserAgent(IPHONE_UA)).isTrue();
        assertThat(Yes24LinkBuilder.isMobileUserAgent(ANDROID_CHROME_UA)).isTrue();
        assertThat(Yes24LinkBuilder.isMobileUserAgent(WINDOWS_CHROME_UA)).isFalse();
        assertThat(Yes24LinkBuilder.isMobileUserAgent(MAC_SAFARI_UA)).isFalse();
        assertThat(Yes24LinkBuilder.isMobileUserAgent(null)).isFalse();
        assertThat(Yes24LinkBuilder.isMobileUserAgent("")).isFalse();
    }
}
