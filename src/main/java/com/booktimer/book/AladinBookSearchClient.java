package com.booktimer.book;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

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
    private static final String ENDPOINT = "http://www.aladin.co.kr/ttb/api/ItemSearch.aspx";
    private static final String NOT_CONFIGURED = "not-configured";

    private final String ttbKey;
    private final RestClient restClient;
    // SSR 앱이라 Jackson ObjectMapper 빈이 자동 등록되지 않는다(Boot 4 모듈러 autoconfig).
    // 주입 대신 자체 인스턴스를 둔다 — 외부 빈 의존 없이 격리(스레드 안전, 재사용).
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public List<BookSearchResult> search(String query) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("ttbkey", ttbKey)
                .queryParam("Query", query.strip())
                .queryParam("QueryType", "Keyword")
                .queryParam("MaxResults", 10)
                .queryParam("start", 1)
                .queryParam("SearchTarget", "Book")
                .queryParam("Cover", "MidBig")
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .build()
                .toUriString();
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            return parse(body, objectMapper);
        } catch (Exception e) {
            // 외부 API 장애가 페이지 전체를 깨지 않도록 빈 결과로 격리(로그만 남김).
            log.warn("알라딘 도서 검색 실패 — query='{}': {}", query, e.toString());
            return List.of();
        }
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
                            text(item, "link")));
                }
            }
        } catch (Exception e) {
            log.warn("알라딘 응답 파싱 실패: {}", e.toString());
        }
        return results;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
