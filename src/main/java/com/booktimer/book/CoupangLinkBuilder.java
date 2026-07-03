package com.booktimer.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 쿠팡 파트너스 검색 링크 빌더 — 책의 ISBN/제목과 URL 템플릿에서 검색 링크를 <b>런타임 생성</b>한다.
 *
 * <p>알라딘({@link AladinBookSearchClient})이 검색 API가 통째로 준 링크를 DB에 저장하는 것과 달리,
 * 쿠팡 검색 링크는 {@code f(책 제목·ISBN, 템플릿)}로 결정적이라 저장하지 않는다 — 백필 부담 0.
 *
 * <p>이 링크는 그 자체로 추적되지 않는다({@code lptag}만 붙인 자작 URL은 파트너스 리포트에 잡히지 않음 —
 * N-146/T-129). {@link CoupangDeeplinkClient}가 딥링크 API로 이 raw URL을 정식 추적링크(shortenUrl)로
 * 변환하는 **입력값**일 뿐이다.
 *
 * <p><b>활성 게이트는 딥링크 API 키가 아니라 "파트너 등록 여부"({@link #isEnabled()})다.</b> 버튼·고지문구
 * 노출은 "쿠팡 파트너스에 가입했는가"의 문제고, 딥링크 API 키(추가 승인 게이트, {@code CoupangDeeplinkProperties})
 * 보유는 "정식 추적링크로 변환 가능한가"라는 별개 문제 — {@link CoupangDeeplinkClient}가 독립적으로 게이트한다.
 * 두 게이트를 하나로 묶으면 이미 파트너 등록은 됐지만 딥링크 키(누적 판매 15만원 게이트)만 없는 기간 동안
 * 버튼 자체가 운영에서 사라지는 회귀가 생긴다 — 딥링크 API가 실패해도 raw 검색 링크는 늘 유효한 구매 경로다.
 */
@Component
public class CoupangLinkBuilder {

    private final String searchUrlTemplate;
    private final boolean partnerEnabled;

    public CoupangLinkBuilder(
            @Value("${booktimer.coupang.search-url-template:https://www.coupang.com/np/search?q={query}}")
            String searchUrlTemplate,
            @Value("${booktimer.coupang.partner-enabled:false}") boolean partnerEnabled) {
        this.searchUrlTemplate = searchUrlTemplate;
        this.partnerEnabled = partnerEnabled;
    }

    /** 쿠팡 파트너스 가입이 완료됐는가 — false면 쿠팡 버튼·고지문구를 화면에서 숨긴다. */
    public boolean isEnabled() {
        return partnerEnabled;
    }

    /**
     * 그 책의 쿠팡 검색 링크(raw, 딥링크 API 입력용이자 그 자체로도 유효한 구매 경로)를 만든다.
     * 비활성(파트너 미등록)이거나 책이 null이면 null. 알라딘과 달리 쿠팡은 제목이 항상 있어 링크가 늘
     * 생성되므로, null 사유는 "비활성"뿐이다.
     */
    public String buildSearchLink(Book book) {
        if (!isEnabled() || book == null) {
            return null;
        }
        return buildSearchLink(searchUrlTemplate, queryFor(book));
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
     * 정적·순수 — 템플릿의 {@code {query}}(URL 인코딩)를 치환한다. {@code {trackingCode}}는 더 이상 쓰이지
     * 않지만(추적은 딥링크 API의 shortenUrl이 담당), 옛 SSM 템플릿 값에 그 플레이스홀더가 남아 있을 경우
     * 딥링크 API에 깨진 URL을 보내지 않도록 방어적으로 제거한다. 네트워크 없이 단위테스트할 수 있게 분리한다.
     */
    static String buildSearchLink(String template, String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return template
                .replace("{query}", encoded)
                .replace("{trackingCode}", "");
    }
}
