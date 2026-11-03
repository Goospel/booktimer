package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 카카오(다음) 책 검색 어댑터 — {@code GET https://dapi.kakao.com/v3/search/book}.
 *
 * <p>2026-11-03 알라딘 OpenAPI 종료(10-30)로 교체했다(T-278). 응답 모양({@link BookSearchResult})은 그대로라
 * 앱 · 웹은 바뀌지 않는다. 카카오는 장르 · 구매 링크 · 베스트셀러를 주지 않아 {@code category} ·
 * {@code purchaseLink}는 null이고 {@link #bestsellers()}는 늘 비어 있다.
 *
 * <p><b>장애는 0건이 아니다</b>(REQ-383) — 키 없음 · HTTP 오류 · 시간 초과 · {@code documents} 배열이 없는 본문은
 * {@link BookSearchUnavailableException}. 알라딘 어댑터는 이것들을 빈 목록으로 삼켜 종료가 4일간 「찾은 책이 없어요」로만
 * 보였다. 그 판정은 {@link #call(URI)} 한 곳에서만 한다.
 */
@Component
public class KakaoBookSearchClient implements BookSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoBookSearchClient.class);
    private static final String ENDPOINT = "https://dapi.kakao.com/v3/search/book";
    private static final String NOT_CONFIGURED = "not-configured";
    /** 카카오 page 상한 — 넘기면 400이 오는데, 그건 장애가 아니라 우리 쪽 값이다(E2). */
    private static final int MAX_PAGE = 50;

    private final String restApiKey;
    private final RestClient restClient;
    // 앱에 Jackson ObjectMapper 빈이 자동 등록되지 않아(Boot 4 모듈러 autoconfig) 자체 인스턴스를 둔다.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    public KakaoBookSearchClient(@Value("${booktimer.kakao.rest-api-key:not-configured}") String restApiKey) {
        this(restApiKey, defaultRestClient());
    }

    /** 테스트 전용 — {@code MockRestServiceServer}가 붙은 RestClient로 실제 아웃바운드 URL · 헤더를 검사한다. */
    KakaoBookSearchClient(String restApiKey, RestClient restClient) {
        this.restApiKey = restApiKey;
        this.restClient = restClient;
    }

    /** 연결 3초 · 읽기 5초 — 앱의 「찾는 중이에요」가 무한히 돌지 않게(E21, CoupangDeeplinkClient와 같은 꼴). */
    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isEnabled() {
        return restApiKey != null && !restApiKey.isBlank() && !NOT_CONFIGURED.equals(restApiKey);
    }

    @Override
    public BookSearchPage search(String query, BookSearchType type, int page) {
        int safePage = Math.min(Math.max(1, page), MAX_PAGE);
        if (query == null || query.isBlank()) {
            return BookSearchPage.empty(safePage, PAGE_SIZE);
        }
        JsonNode root = call(buildSearchUrl(query, type, safePage));
        return new BookSearchPage(parse(root), safePage, PAGE_SIZE, root.path("meta").path("total_count").asInt(0));
    }

    @Override
    public List<BookSearchResult> bestsellers() {
        // 카카오엔 베스트셀러 목록이 없다 — 추천 콜드스타트는 정직하게 비운다(REQ-385).
        return List.of();
    }

    @Override
    public Optional<BookSearchResult> lookupByIsbn(String isbn13) {
        if (isbn13 == null || isbn13.isBlank()) {
            return Optional.empty();
        }
        String wanted = isbn13.strip();
        // target=isbn도 다른 ISBN의 책을 섞어 줄 수 있다 — 엉뚱한 책을 「바코드로 찾은 책」으로 보이지 않는다(REQ-382).
        return parse(call(buildIsbnUrl(wanted))).stream()
                .filter(r -> wanted.equals(r.isbn13()))
                .findFirst();
    }

    /** 장애를 0건과 가르는 단 한 곳. 검색어 · 키 · 헤더는 로그에 남기지 않는다. */
    private JsonNode call(URI url) {
        if (!isEnabled()) {
            throw new BookSearchUnavailableException("카카오 REST 키가 없다");
        }
        try {
            String body = restClient.get().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve().body(String.class);
            JsonNode root = body == null ? null : objectMapper.readTree(body);
            if (root == null || !root.path("documents").isArray()) {
                // 알라딘 종료 응답이 이 꼴이었다 — HTTP 200 + {"errorCode":999,...}(T-278)
                String head = body == null ? "" : body.substring(0, Math.min(120, body.length()));
                log.warn("카카오 책 검색 응답에 documents가 없다 — body={}", head);
                throw new BookSearchUnavailableException("카카오 책 검색 응답에 documents가 없다: " + head);
            }
            return root;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("카카오 책 검색 실패 — http={} body={}", e.getStatusCode().value(),
                    body.substring(0, Math.min(200, body.length())));
            throw new BookSearchUnavailableException("카카오 책 검색 HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException | JacksonException e) {
            log.warn("카카오 책 검색 실패 — {}", e.toString());
            throw new BookSearchUnavailableException("카카오 책 검색 실패", e);
        }
    }

    /**
     * 검색 URL. <b>{@code String}이 아니라 {@link URI}를 돌려주고 호출부는 {@code uri(URI)} 오버로드로 넘긴다</b> —
     * 문자열이면 URI 템플릿 확장이 한 번 더 인코딩해 {@code %26}이 {@code %2526}이 된다(검색어가 조용히 깨진다).
     * {@code encode()}가 없으면 검색어의 {@code &}·{@code =}·{@code #}가 파라미터를 오염시킨다.
     */
    static URI buildSearchUrl(String query, BookSearchType type, int page) {
        BookSearchType safeType = (type == null) ? BookSearchType.TITLE : type;
        return UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("query", query.strip())
                .queryParam("target", safeType.getKakaoTarget())
                .queryParam("size", PAGE_SIZE)
                .queryParam("page", page)
                .build()
                .encode()
                .toUri();
    }

    /** ISBN 한 건 조회 URL — isbn13도 저장된 사용자 입력일 수 있어 같은 규율로 인코딩한다. */
    static URI buildIsbnUrl(String isbn13) {
        return UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("query", isbn13)
                .queryParam("target", "isbn")
                .build()
                .encode()
                .toUri();
    }

    /** 문서 → 검색 결과. 같은 ISBN-13은 앞 것만(앱 행 키가 isbn13이다). ISBN 없는 문서는 그대로 둔다. */
    static List<BookSearchResult> parse(JsonNode root) {
        List<BookSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode doc : root.path("documents")) {
            String isbn13 = isbn13Of(text(doc, "isbn"));
            if (isbn13 != null && !seen.add(isbn13)) {
                continue;
            }
            results.add(new BookSearchResult(
                    text(doc, "title"),
                    authorOf(doc),
                    isbn13,
                    blankToNull(text(doc, "thumbnail")),
                    text(doc, "publisher"),
                    null,
                    null,
                    pubDateOf(text(doc, "datetime"))));
        }
        return results;
    }

    /**
     * 저자들 + 옮긴이마다 「 (옮긴이)」를 붙여 「, 」로 잇는다. 둘 다 비면 null.
     * {@code WriterName}이 「(옮긴이)」를 비저자로 다뤄 목록 표시 · 책BTI 저자 집계가 알라딘 때와 같게 돈다.
     */
    static String authorOf(JsonNode doc) {
        List<String> names = new ArrayList<>();
        for (JsonNode a : doc.path("authors")) {
            if (!a.asText().isBlank()) {
                names.add(a.asText().strip());
            }
        }
        for (JsonNode t : doc.path("translators")) {
            if (!t.asText().isBlank()) {
                names.add(t.asText().strip() + " (옮긴이)");
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** 공백으로 나뉜 ISBN 토큰 중 13자리, 없으면 10자리를 978 ISBN-13으로, 둘 다 없으면 null. */
    static String isbn13Of(String raw) {
        if (raw == null) {
            return null;
        }
        String isbn10 = null;
        for (String token : raw.strip().split("\\s+")) {
            String t = Isbn.normalize(token);
            if (t == null) {
                continue;
            }
            if (t.matches("\\d{13}")) {
                return t;
            }
            if (isbn10 == null && t.matches("\\d{9}[\\dXx]")) {
                isbn10 = t;
            }
        }
        return Isbn.toIsbn13(isbn10);
    }

    /** ISO 8601 앞 10자({@code yyyy-MM-dd}). 빈 값 · 꼴이 다르면 null. */
    static String pubDateOf(String datetime) {
        return datetime != null && datetime.matches("\\d{4}-\\d{2}-\\d{2}.*") ? datetime.substring(0, 10) : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
