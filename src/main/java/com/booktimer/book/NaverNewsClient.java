package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 뉴스 검색 API 어댑터 — 완독한 책의 관련 기사를 가져온다({@link BookNewsCollector}가 쓴다).
 *
 * <p><b>config-gate</b>: client-id/secret이 비어 있으면 {@link #isEnabled()}가 false라 호출을 아예 하지 않는다
 * ({@link AladinBookSearchClient} 패턴). 키 없이 배포해도 수집기는 no-op이고 홈의 「책 뉴스」 탭도 꺼지며,
 * 키가 SSM으로 들어오면 코드 변경 없이 점등된다.
 *
 * <p>HTTP 호출과 JSON 매핑을 분리해, 매핑은 {@link #parse(String, ObjectMapper)} 정적 메서드로 네트워크
 * 없이 단위테스트한다. 응답의 {@code <b>} 강조 태그·HTML 엔티티는 parse에서 벗긴다.
 */
@Component
public class NaverNewsClient {

    private static final Logger log = LoggerFactory.getLogger(NaverNewsClient.class);
    private static final String ENDPOINT = "https://openapi.naver.com/v1/search/news.json";

    /** 한 책당 받아오는 기사 수. 매처가 걸러낸 뒤 상위 몇 건만 남으므로 넉넉히 받는다. */
    private static final int DISPLAY = 10;

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    // SSR 앱이라 Jackson ObjectMapper 빈이 자동 등록되지 않는다(Boot 4 모듈러 autoconfig) — 알라딘 어댑터와 동일.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public NaverNewsClient(@Value("${booktimer.naver.client-id:}") String clientId,
                           @Value("${booktimer.naver.client-secret:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
    }

    /** 키(id·secret)가 <b>둘 다</b> 설정됐을 때만 활성. 하나만 있으면 호출이 401이므로 꺼진 것으로 본다. */
    public boolean isEnabled() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    /**
     * 뉴스를 최신순(sort=date)으로 검색한다. 비활성이거나 쿼리가 비면 호출 없이 빈 목록.
     * 외부 장애는 빈 목록으로 격리한다(알라딘 패턴) — 한 책의 실패가 배치 전체를 깨지 않는다.
     */
    public List<NewsArticle> search(String query) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("query", query)
                .queryParam("display", DISPLAY)
                .queryParam("sort", "date")
                .build()
                .toUriString();
        try {
            String body = restClient.get().uri(url)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .body(String.class);
            return parse(body, objectMapper);
        } catch (Exception e) {
            log.warn("네이버 뉴스 검색 실패 — query='{}': {}", query, e.toString());
            return List.of();
        }
    }

    /**
     * 검색 쿼리를 만든다 — 제목은 따옴표 구절 검색, 저자는 그냥 덧붙인다.
     * 정밀도의 1차 방어(쿼리)이고, 2차 방어(사후 필터)는 {@link BookNewsMatcher}가 한다.
     */
    static String buildQuery(String title, String author) {
        return "\"" + title.strip() + "\" " + author.strip();
    }

    /**
     * 네이버 뉴스 검색 응답(JSON)을 기사 목록으로 매핑한다. 네트워크 없이 테스트 가능.
     *
     * <p>제목·본문은 {@link BookNewsMatcher#clean}으로 태그·엔티티를 벗겨 담는다(저장·비교 단일 표기).
     * {@code pubDate}(RFC-822)를 못 읽으면 그 기사만 {@code publishedAt=null}로 두고 줄은 살린다.
     * 깨진 JSON은 빈 목록 — 외부 장애 격리.
     */
    static List<NewsArticle> parse(String json, ObjectMapper objectMapper) {
        List<NewsArticle> results = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return results;
        }
        try {
            JsonNode items = objectMapper.readTree(json).path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    results.add(new NewsArticle(
                            BookNewsMatcher.clean(text(item, "title")),
                            BookNewsMatcher.clean(text(item, "description")),
                            text(item, "link"),
                            parsePubDate(text(item, "pubDate"))));
                }
            }
        } catch (Exception e) {
            log.warn("네이버 뉴스 응답 파싱 실패: {}", e.toString());
        }
        return results;
    }

    /** RFC-822 발행 시각(예 "Tue, 11 Aug 2026 09:00:00 +0900") → Instant. 못 읽으면 null. */
    private static Instant parsePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 네이버가 준 기사 한 건 — 제목·본문은 태그/엔티티가 벗겨진 상태다. */
    public record NewsArticle(String title, String description, String link, Instant publishedAt) {
    }
}
