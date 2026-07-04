package com.booktimer.book;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 알라딘 OpenAPI(ItemSearch) 기반 도서 검색 어댑터.
 *
 * <p>검색 결과의 구매 링크에 TTBKey(=제휴 식별자)가 실려 추후 제휴 수익의 토대가 된다.
 * TTBKey가 설정되지 않으면 {@link #isEnabled()}가 false라 화면은 수동 입력으로 폴백한다.
 *
 * <p>HTTP 호출과 JSON 매핑을 분리해, 매핑은 {@link #parse(String, ObjectMapper)} 정적 메서드로
 * 네트워크 없이 단위테스트한다.
 */
@Component
public class AladinBookSearchClient implements BookSearchClient {

    private static final Logger log = LoggerFactory.getLogger(AladinBookSearchClient.class);
    private static final String ENDPOINT = "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx";
    private static final String LOOKUP_ENDPOINT = "https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx";
    private static final String NOT_CONFIGURED = "not-configured";

    private final String ttbKey;
    private final RestClient restClient;
    // SSR 앱이라 Jackson ObjectMapper 빈이 자동 등록되지 않는다(Boot 4 모듈러 autoconfig).
    // 주입 대신 자체 인스턴스를 둔다 — 외부 빈 의존 없이 격리(스레드 안전, 재사용).
    // Jackson 3은 빌더로 만든다(ObjectMapper 직접 생성 폐지 — JsonMapper가 ObjectMapper를 상속).
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public AladinBookSearchClient(
            @Value("${booktimer.aladin.ttb-key:not-configured}") String ttbKey) {
        this.ttbKey = ttbKey;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isEnabled() {
        return ttbKey != null && !ttbKey.isBlank() && !NOT_CONFIGURED.equals(ttbKey);
    }

    @Override
    public BookSearchPage search(String query, BookSearchType type, int page) {
        int safePage = Math.max(1, page);
        if (!isEnabled() || query == null || query.isBlank()) {
            return BookSearchPage.empty(safePage, PAGE_SIZE);
        }
        String url = buildSearchUrl(ttbKey, query, type, safePage);
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            List<BookSearchResult> items = parse(body, objectMapper);
            int total = parseTotalResults(body, objectMapper);
            return new BookSearchPage(items, safePage, PAGE_SIZE, total);
        } catch (Exception e) {
            // 외부 API 장애가 페이지 전체를 깨지 않도록 빈 결과로 격리(로그만 남김).
            log.warn("알라딘 도서 검색 실패 — query='{}' page={}: {}", query, safePage, e.toString());
            return BookSearchPage.empty(safePage, PAGE_SIZE);
        }
    }

    @Override
    public Optional<BookSearchResult> lookupByIsbn(String isbn13) {
        if (!isEnabled() || isbn13 == null || isbn13.isBlank()) {
            return Optional.empty();
        }
        String url = buildLookupUrl(ttbKey, isbn13.strip());
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            // ItemLookUp 응답도 ItemSearch와 같은 item[] 구조라 parse()를 그대로 재사용한다(매핑 단일 소스).
            List<BookSearchResult> items = parse(body, objectMapper);
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
        } catch (Exception e) {
            // 외부 API 장애가 백필 전체를 깨지 않도록 한 건은 빈 결과로 격리(로그만).
            log.warn("알라딘 ItemLookUp 실패 — isbn13='{}': {}", isbn13, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 알라딘 ItemSearch 호출 URL을 만든다. 검색 기준(제목/저자)이 {@code QueryType}으로 들어간다.
     * 네트워크 없이 단위테스트할 수 있게 정적·순수 함수로 분리한다.
     */
    static String buildSearchUrl(String ttbKey, String query, BookSearchType type, int page) {
        BookSearchType safeType = (type == null) ? BookSearchType.TITLE : type;
        return UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("ttbkey", ttbKey)
                .queryParam("Query", query.strip())
                .queryParam("QueryType", safeType.getAladinQueryType())
                .queryParam("MaxResults", PAGE_SIZE)
                .queryParam("start", Math.max(1, page))   // 알라딘 start = 시작 페이지(1-based)
                .queryParam("SearchTarget", "Book")
                .queryParam("Cover", "MidBig")
                // includeKey=1이라야 응답 link에 TTBKey(제휴 식별자)가 실린다(기본 0=미포함). 없으면 저장·재생되는
                // 구매링크에 제휴키가 빠져 클릭이 귀속되지 않아 제휴 수익 0(무성 추적실패 — 운영 실측으로 확인, 쿠팡 lptag와 동일 계열).
                .queryParam("includeKey", 1)
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .build()
                .toUriString();
    }

    /**
     * 알라딘 ItemLookUp 호출 URL을 만든다 — ISBN-13으로 단건 조회(itemIdType=ISBN13). 백필용.
     * 네트워크 없이 단위테스트할 수 있게 정적·순수 함수로 분리한다(buildSearchUrl과 동일 정신).
     */
    static String buildLookupUrl(String ttbKey, String isbn13) {
        return UriComponentsBuilder.fromUriString(LOOKUP_ENDPOINT)
                .queryParam("ttbkey", ttbKey)
                .queryParam("itemIdType", "ISBN13")
                .queryParam("ItemId", isbn13)
                .queryParam("Cover", "MidBig")
                // 백필로 갱신되는 link에도 TTBKey가 실리게 한다(buildSearchUrl과 동일 이유). 없으면 백필해도 무추적.
                .queryParam("includeKey", 1)
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .build()
                .toUriString();
    }

    /**
     * 알라딘 ItemSearch JSON(output=js)을 검색 결과 목록으로 매핑한다. 네트워크 없이 테스트 가능.
     */
    static List<BookSearchResult> parse(String json, ObjectMapper objectMapper) {
        List<BookSearchResult> results = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return results;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("item");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    results.add(new BookSearchResult(
                            text(item, "title"),
                            text(item, "author"),
                            text(item, "isbn13"),
                            text(item, "cover"),
                            text(item, "publisher"),
                            text(item, "link"),
                            // 책BTI 입력 — 장르/출간일. 없으면 null(text(): 누락 필드 → null)
                            text(item, "categoryName"),
                            text(item, "pubDate")));
                }
            }
        } catch (Exception e) {
            log.warn("알라딘 응답 파싱 실패: {}", e.toString());
        }
        return results;
    }

    /** 알라딘 응답의 전체 결과 수(totalResults). 없거나 파싱 실패 시 0. */
    static int parseTotalResults(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readTree(json).path("totalResults").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
