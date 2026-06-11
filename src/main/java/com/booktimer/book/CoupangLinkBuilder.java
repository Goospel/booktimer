package com.booktimer.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 쿠팡 파트너스 검색 링크 빌더 — 책의 ISBN/제목과 추적코드·URL 템플릿에서 구매 링크를 <b>런타임 생성</b>한다.
 *
 * <p>알라딘({@link AladinBookSearchClient})이 검색 API가 통째로 준 링크를 DB에 저장하는 것과 달리,
 * 쿠팡 검색 링크는 {@code f(책 제목·ISBN, 추적코드, 템플릿)}로 결정적이라 저장하지 않는다 — 백필 부담 0,
 * 추적코드/템플릿이 바뀌면 환경변수 교체만으로 즉시 반영된다.
 *
 * <p>추적코드가 {@code not-configured}(가입 전 기본값)면 {@link #isEnabled()}가 false라 화면에서
 * 쿠팡 버튼·고지문구를 숨긴다. 쿠팡 파트너스 가입 후 환경변수 주입만으로 점진 활성화한다(코드 변경 없음).
 *
 * <p>실제 추적 URL 형식은 가입 후 공식 가이드로 확정되므로, URL을 코드에 박지 않고 템플릿 환경변수로 둔다.
 * 템플릿의 {@code {query}}는 검색어(URL 인코딩됨), {@code {trackingCode}}는 추적코드로 치환된다.
 */
@Component
public class CoupangLinkBuilder {

    private static final String NOT_CONFIGURED = "not-configured";

    private final String trackingCode;
    private final String searchUrlTemplate;

    public CoupangLinkBuilder(
            @Value("${booktimer.coupang.tracking-code:not-configured}") String trackingCode,
            @Value("${booktimer.coupang.search-url-template:https://www.coupang.com/np/search?q={query}}")
            String searchUrlTemplate) {
        this.trackingCode = trackingCode;
        this.searchUrlTemplate = searchUrlTemplate;
    }

    /** 추적코드가 실제 값으로 설정됐는가 — false면 쿠팡 버튼·고지문구를 화면에서 숨긴다. */
    public boolean isEnabled() {
        return trackingCode != null && !trackingCode.isBlank() && !NOT_CONFIGURED.equals(trackingCode);
    }

    /**
     * 그 책의 쿠팡 검색 링크를 만든다. 비활성(추적코드 미설정)이거나 책이 null이면 null.
     * 알라딘과 달리 쿠팡은 제목이 항상 있어 링크가 늘 생성되므로, null 사유는 "비활성"뿐이다.
     */
    public String buildSearchLink(Book book) {
        if (!isEnabled() || book == null) {
            return null;
        }
        return buildSearchLink(searchUrlTemplate, queryFor(book), trackingCode);
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
     * 네트워크 없이 단위테스트할 수 있게 분리한다(buildSearchUrl과 동일 정신).
     */
    static String buildSearchLink(String template, String query, String trackingCode) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return template
                .replace("{query}", encoded)
                .replace("{trackingCode}", trackingCode == null ? "" : trackingCode);
    }
}
