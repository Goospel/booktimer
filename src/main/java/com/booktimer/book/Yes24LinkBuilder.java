package com.booktimer.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Yes24 제휴 검색 링크 빌더 — 책의 ISBN/제목과 추적코드·URL 템플릿에서 구매 링크를 <b>런타임 생성</b>한다.
 *
 * <p>{@link CoupangLinkBuilder}와 동형(제휴 3제공자를 대칭 유지). 알라딘({@link AladinBookSearchClient})이
 * 검색 API가 통째로 준 링크를 DB에 저장하는 것과 달리, Yes24 검색 링크는 {@code f(책 제목·ISBN, 추적코드, 템플릿)}로
 * 결정적이라 저장하지 않는다 — 백필 부담 0, 추적코드/템플릿이 바뀌면 환경변수 교체만으로 즉시 반영된다.
 *
 * <p>추적코드가 {@code not-configured}(가입 전 기본값)면 {@link #isEnabled()}가 false라 화면에서 Yes24
 * 버튼·고지문구를 숨긴다. Yes24는 알라딘·쿠팡과 달리 자체 셀프서비스 제휴링크가 뚜렷하지 않아 링크프라이스 등
 * CPS 제휴 네트워크 가입이 일반적이며, 가입 후 발급되는 추적 URL 형식을 코드에 박지 않고 템플릿 환경변수로 둔다.
 * 템플릿의 {@code {query}}는 검색어(URL 인코딩됨), {@code {trackingCode}}는 추적코드로 치환된다.
 *
 * <p>모바일 UA는 Yes24 자체 제휴 게이트({@code lpfront.aspx})가 링크프라이스 딥링크의 목적지를 버리고
 * 모바일 메인({@code m.yes24.com})으로 치환해버린다(우회 불가, 실측 확인, T-128) — 그래서 모바일이면
 * 제휴 래퍼를 아예 타지 않고 {@link #buildSearchLink(Book, boolean)}가 {@code mobileSearchUrlTemplate}
 * (모바일 검색)로 직행시킨다. 모바일 클릭의 커미션 귀속은 포기하는 트레이드오프다.
 */
@Component
public class Yes24LinkBuilder {

    private static final String NOT_CONFIGURED = "not-configured";

    /** Yes24 자신의 기기 판별(RedirectWebSiteList.min.js list_mobile_device)을 미러링한 모바일 UA 목록. */
    private static final Pattern MOBILE_USER_AGENT = Pattern.compile(
            "Android|BlackBerry|iPhone|iPad|iPod|Opera Mini|IEMobile", Pattern.CASE_INSENSITIVE);

    private final String trackingCode;
    private final String searchUrlTemplate;
    private final String mobileSearchUrlTemplate;

    public Yes24LinkBuilder(
            @Value("${booktimer.yes24.tracking-code:not-configured}") String trackingCode,
            @Value("${booktimer.yes24.search-url-template:https://www.yes24.com/product/search?query={query}}")
            String searchUrlTemplate,
            @Value("${booktimer.yes24.mobile-search-url-template:https://m.yes24.com/search?query={query}}")
            String mobileSearchUrlTemplate) {
        this.trackingCode = trackingCode;
        this.searchUrlTemplate = searchUrlTemplate;
        this.mobileSearchUrlTemplate = mobileSearchUrlTemplate;
    }

    /**
     * Yes24 제휴가 실제로 추적 가능한 상태인가 — false면 버튼·고지문구를 화면에서 숨긴다.
     *
     * <p>추적코드가 설정됐고(가입) <b>동시에</b> 데스크톱 {@code searchUrlTemplate}에 {@code {trackingCode}}
     * 자리가 있어야 한다. 추적코드만 있고 템플릿이 순수 검색 URL(자리 없음)이면 코드가 링크에 실릴 곳이 없어
     * "추적 0인데 버튼 뜨는" 무성실패가 된다(T-131 계열 — 알라딘 {@code includeKey}·쿠팡 {@code lptag}와
     * 같은 클래스). {@code buildSearchLink}의 {@code .replace}가 자리 없으면 조용히 no-op이라 이 게이트가 유일한
     * 방어다. (모바일 템플릿은 T-128대로 추적을 의도적으로 포기하므로 이 게이트에 넣지 않는다 — 데스크톱 추적
     * 가능 여부로 노출을 결정한다.)
     */
    public boolean isEnabled() {
        return trackingCode != null && !trackingCode.isBlank() && !NOT_CONFIGURED.equals(trackingCode)
                && searchUrlTemplate != null && searchUrlTemplate.contains("{trackingCode}");
    }

    /**
     * 그 책의 Yes24 검색 링크를 만든다. 비활성(추적코드 미설정)이거나 책이 null이면 null.
     * 쿠팡과 마찬가지로 제목이 항상 있어 링크가 늘 생성되므로, null 사유는 "비활성"뿐이다.
     *
     * @param mobile true면 제휴 래퍼 없이 모바일 검색 템플릿으로 직행(T-128), false면 기존 데스크톱 래퍼 템플릿.
     */
    public String buildSearchLink(Book book, boolean mobile) {
        if (!isEnabled() || book == null) {
            return null;
        }
        String template = mobile ? mobileSearchUrlTemplate : searchUrlTemplate;
        return buildSearchLink(template, queryFor(book), trackingCode);
    }

    /** 그 UA가 Yes24 자체 판별 기준으로 모바일 기기인가. null/빈 문자열은 false(데스크톱 취급). */
    public static boolean isMobileUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        return MOBILE_USER_AGENT.matcher(userAgent).find();
    }

    /** 검색어 — ISBN13 우선(동명 책 회피), 없으면 제목으로 폴백. */
    static String queryFor(Book book) {
        String isbn = book.getIsbn13();
        if (isbn != null && !isbn.isBlank()) {
            return isbn;
        }
        return book.getTitle();
    }

    /**
     * 정적·순수 — 템플릿의 {@code {query}}(URL 인코딩)·{@code {trackingCode}}를 치환한다.
     * 네트워크 없이 단위테스트할 수 있게 분리한다(CoupangLinkBuilder와 동일 정신).
     */
    static String buildSearchLink(String template, String query, String trackingCode) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return template
                .replace("{query}", encoded)
                .replace("{trackingCode}", trackingCode == null ? "" : trackingCode);
    }
}
