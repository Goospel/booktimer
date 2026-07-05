package com.booktimer.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 교보문고 제휴 검색 링크 빌더 — 책의 ISBN/제목과 추적코드·URL 템플릿에서 구매 링크를 <b>런타임 생성</b>한다.
 *
 * <p>{@link Yes24LinkBuilder}와 동형(제휴 제공자를 대칭 유지). 알라딘({@link AladinBookSearchClient})이
 * 검색 API가 통째로 준 링크를 DB에 저장하는 것과 달리, 교보 검색 링크는 {@code f(책 제목·ISBN, 추적코드, 템플릿)}로
 * 결정적이라 저장하지 않는다 — 백필 부담 0, 추적코드/템플릿이 바뀌면 환경변수 교체만으로 즉시 반영된다.
 * (교보 상품 상세 URL은 내부ID(S000…) 기반이라 ISBN으로 조립할 수 없어 목적지는 통합검색 URL을 쓴다.)
 *
 * <p>추적코드가 {@code not-configured}(가입 전 기본값)면 {@link #isEnabled()}가 false라 화면에서 교보
 * 버튼·고지문구를 숨긴다(dark-launch). 교보도 Yes24처럼 자체 셀프서비스 제휴링크가 뚜렷하지 않아 링크프라이스 등
 * CPS 제휴 네트워크 경유가 일반적이며, 가입 후 발급되는 추적 URL 형식을 코드에 박지 않고 템플릿 환경변수로 둔다.
 * 템플릿의 {@code {query}}는 검색어(URL 인코딩됨), {@code {trackingCode}}는 추적코드로 치환된다. 운영에서
 * {@code booktimer.kyobo.search-url-template}(SSM)에 링크프라이스 래퍼(newtip.net/click.php?m=…&a={trackingCode}
 * &…&tu=&lt;교보 검색 URL&gt;{query})를 주입하면 점등된다.
 *
 * <p>모바일 UA에서 제휴 게이트가 딥링크 목적지를 버리는 문제(Yes24 T-128)가 교보에도 있는지는 점등 후 실측으로
 * 확정한다 — 대칭 복제로 {@link #buildSearchLink(Book, boolean)}의 모바일 분기·{@code mobileSearchUrlTemplate}를
 * 그대로 두되, 실측 전엔 모바일 기본 템플릿을 순수 교보 검색으로 둔다(추적 없이 검색으로 직행). 실측 후 교보 게이트가
 * 살아 있으면 SSM 모바일 템플릿에도 래퍼를 넣어 모바일 추적을 켤 수 있다.
 */
@Component
public class KyoboLinkBuilder {

    private static final String NOT_CONFIGURED = "not-configured";

    /** Yes24 자신의 기기 판별을 미러링한 모바일 UA 목록(제휴 제공자 대칭 유지). */
    private static final Pattern MOBILE_USER_AGENT = Pattern.compile(
            "Android|BlackBerry|iPhone|iPad|iPod|Opera Mini|IEMobile", Pattern.CASE_INSENSITIVE);

    private final String trackingCode;
    private final String searchUrlTemplate;
    private final String mobileSearchUrlTemplate;

    public KyoboLinkBuilder(
            @Value("${booktimer.kyobo.tracking-code:not-configured}") String trackingCode,
            @Value("${booktimer.kyobo.search-url-template:https://search.kyobobook.co.kr/search?keyword={query}}")
            String searchUrlTemplate,
            @Value("${booktimer.kyobo.mobile-search-url-template:https://search.kyobobook.co.kr/search?keyword={query}}")
            String mobileSearchUrlTemplate) {
        this.trackingCode = trackingCode;
        this.searchUrlTemplate = searchUrlTemplate;
        this.mobileSearchUrlTemplate = mobileSearchUrlTemplate;
    }

    /**
     * 교보 제휴가 실제로 추적 가능한 상태인가 — false면 버튼·고지문구를 화면에서 숨긴다.
     *
     * <p>추적코드가 설정됐고(가입) <b>동시에</b> 데스크톱 {@code searchUrlTemplate}에 {@code {trackingCode}}
     * 자리가 있어야 한다. 추적코드만 있고 템플릿이 순수 검색 URL(자리 없음)이면 코드가 링크에 실릴 곳이 없어
     * "추적 0인데 버튼 뜨는" 무성실패가 된다(T-131 계열 — 알라딘 {@code includeKey}·쿠팡 {@code lptag}·Yes24와
     * 같은 클래스). {@code buildSearchLink}의 {@code .replace}가 자리 없으면 조용히 no-op이라 이 게이트가 유일한
     * 방어다. (모바일 템플릿은 T-128 대칭으로 추적을 실측 전까지 포기하므로 이 게이트에 넣지 않는다 — 데스크톱 추적
     * 가능 여부로 노출을 결정한다.)
     */
    public boolean isEnabled() {
        return trackingCode != null && !trackingCode.isBlank() && !NOT_CONFIGURED.equals(trackingCode)
                && searchUrlTemplate != null && searchUrlTemplate.contains("{trackingCode}");
    }

    /**
     * 그 책의 교보 검색 링크를 만든다. 비활성(추적코드 미설정)이거나 책이 null이면 null.
     * 제목이 항상 있어 링크가 늘 생성되므로, null 사유는 "비활성"뿐이다.
     *
     * @param mobile true면 모바일 검색 템플릿, false면 데스크톱 래퍼 템플릿(T-128 대칭 — 모바일 게이트는 실측 후 결정).
     */
    public String buildSearchLink(Book book, boolean mobile) {
        if (!isEnabled() || book == null) {
            return null;
        }
        String template = mobile ? mobileSearchUrlTemplate : searchUrlTemplate;
        return buildSearchLink(template, queryFor(book), trackingCode);
    }

    /** 그 UA가 모바일 기기인가(Yes24 판별 기준 미러링). null/빈 문자열은 false(데스크톱 취급). */
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
     * 네트워크 없이 단위테스트할 수 있게 분리한다(Yes24LinkBuilder와 동일 정신).
     */
    static String buildSearchLink(String template, String query, String trackingCode) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return template
                .replace("{query}", encoded)
                .replace("{trackingCode}", trackingCode == null ? "" : trackingCode);
    }
}
