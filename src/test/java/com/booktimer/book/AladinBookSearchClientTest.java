package com.booktimer.book;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알라딘 어댑터의 JSON 매핑·활성화 게이트 단위테스트 — 네트워크 없이 정적 파싱과 키 판정만 본다.
 */
class AladinBookSearchClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("TTBKey 미설정이면 검색 비활성(수동 입력 폴백)")
    void disabled_whenKeyNotConfigured() {
        assertThat(new AladinBookSearchClient("not-configured").isEnabled()).isFalse();
        assertThat(new AladinBookSearchClient("  ").isEnabled()).isFalse();
        assertThat(new AladinBookSearchClient("ttbreal123").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("검색 기준에 따라 알라딘 QueryType이 Title/Author로 들어간다")
    void buildUrl_carriesQueryType() {
        String titleUrl = AladinBookSearchClient.buildSearchUrl("ttb1", "모기", BookSearchType.TITLE, 1);
        assertThat(titleUrl).contains("QueryType=Title");
        assertThat(titleUrl).doesNotContain("QueryType=Keyword");

        String authorUrl = AladinBookSearchClient.buildSearchUrl("ttb1", "모기", BookSearchType.AUTHOR, 1);
        assertThat(authorUrl).contains("QueryType=Author");
    }

    @Test
    @DisplayName("검색·조회 URL에 includeKey=1을 실어 알라딘 응답 link에 TTBKey(제휴 식별자)가 포함되게 한다 — 없으면(기본 0) 응답 link에 ttbkey가 빠져 제휴 클릭이 귀속 안 됨(무성 추적실패, 쿠팡 lptag와 동일 계열)")
    void buildUrl_carriesIncludeKey_forAffiliateTracking() {
        String searchUrl = AladinBookSearchClient.buildSearchUrl("ttb1", "모기", BookSearchType.TITLE, 1);
        assertThat(searchUrl).contains("includeKey=1");

        String lookupUrl = AladinBookSearchClient.buildLookupUrl("ttb1", "9788966260959");
        assertThat(lookupUrl).contains("includeKey=1");
    }

    @Test
    @DisplayName("ItemLookUp URL: ISBN13으로 단건 조회한다(itemIdType=ISBN13, ItemId=isbn, ttbkey) — 백필용")
    void buildLookupUrl_byIsbn() {
        String url = AladinBookSearchClient.buildLookupUrl("ttb1", "9788966260959");
        assertThat(url).contains("ItemLookUp.aspx");
        assertThat(url).contains("itemIdType=ISBN13");
        assertThat(url).contains("ItemId=9788966260959");
        assertThat(url).contains("ttbkey=ttb1");
    }

    @Test
    @DisplayName("검색·조회 엔드포인트는 https로 호출한다 — http면 알라딘 CloudFront가 301로 https로 보내는데, RestClient가 리다이렉트를 안 따라가 응답 본문이 HTML('<')이 되어 JSON 파싱이 깨지고 운영 검색이 전부 0건이 된다(회귀 가드)")
    void buildUrl_usesHttps_notHttp() {
        String searchUrl = AladinBookSearchClient.buildSearchUrl("ttb1", "모기", BookSearchType.TITLE, 1);
        assertThat(searchUrl).startsWith("https://www.aladin.co.kr");

        String lookupUrl = AladinBookSearchClient.buildLookupUrl("ttb1", "9788966260959");
        assertThat(lookupUrl).startsWith("https://www.aladin.co.kr");
    }

    @Test
    @DisplayName("lookupByIsbn: 비활성 키거나 isbn이 비면 빈 결과(외부 호출 없이 가드)")
    void lookupByIsbn_guards() {
        assertThat(new AladinBookSearchClient("not-configured").lookupByIsbn("9788966260959")).isEmpty();
        assertThat(new AladinBookSearchClient("ttbreal").lookupByIsbn("  ")).isEmpty();
        assertThat(new AladinBookSearchClient("ttbreal").lookupByIsbn(null)).isEmpty();
    }

    @Test
    @DisplayName("알라딘 ItemSearch JSON(item 배열)을 검색 결과로 매핑한다")
    void parse_mapsItems() {
        String json = """
                {
                  "version": "20131101",
                  "title": "알라딘 검색결과",
                  "item": [
                    {
                      "title": "클린 코드",
                      "author": "로버트 마틴",
                      "isbn13": "9788966260959",
                      "cover": "http://image.aladin.co.kr/clean.jpg",
                      "publisher": "인사이트",
                      "link": "http://www.aladin.co.kr/shop/buy?ItemId=1&ttbkey=x"
                    },
                    {
                      "title": "이펙티브 자바",
                      "author": "조슈아 블로크",
                      "isbn13": "9788966262281",
                      "cover": "http://image.aladin.co.kr/effective.jpg",
                      "publisher": "인사이트",
                      "link": "http://www.aladin.co.kr/shop/buy?ItemId=2&ttbkey=x"
                    }
                  ]
                }
                """;

        List<BookSearchResult> results = AladinBookSearchClient.parse(json, objectMapper);

        assertThat(results).hasSize(2);
        BookSearchResult first = results.get(0);
        assertThat(first.title()).isEqualTo("클린 코드");
        assertThat(first.author()).isEqualTo("로버트 마틴");
        assertThat(first.isbn13()).isEqualTo("9788966260959");
        assertThat(first.coverUrl()).isEqualTo("http://image.aladin.co.kr/clean.jpg");
        assertThat(first.publisher()).isEqualTo("인사이트");
        assertThat(first.purchaseLink()).contains("ttbkey=x");
    }

    @Test
    @DisplayName("link의 &amp; 를 & 로 되돌린다 — 안 되돌리면 제휴 파라미터가 amp;ttbkey 로 읽혀 추적만 조용히 죽는다")
    void parse_unescapesAmpersandInLink() {
        // 알라딘 ItemSearch가 실제로 내려주는 형태(2026-08-22 운영 실측) — link 값에만 &amp; 가 섞여 온다.
        String json = """
                {
                  "item": [
                    {
                      "title": "채식주의자",
                      "isbn13": "9788936434595",
                      "link": "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=291370219&amp;ttbkey=k&amp;partner=openAPI&amp;start=api"
                    },
                    {
                      "title": "이미 정상인 링크",
                      "isbn13": "9788900000002",
                      "link": "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=2&ttbkey=k&partner=openAPI"
                    }
                  ]
                }
                """;

        List<BookSearchResult> results = AladinBookSearchClient.parse(json, objectMapper);

        // 핵심 — 알라딘이 실제로 읽는 파라미터 이름으로 복원돼야 한다. "&amp; 가 없다"만으로는
        // 부족하다: 추적을 살리는 것은 ttbkey·partner 가 독립 파라미터로 서는 것이기 때문.
        assertThat(results.get(0).purchaseLink())
                .doesNotContain("&amp;")
                .contains("&ttbkey=k")
                .contains("&partner=openAPI");
        // 이미 정상인 링크는 건드리지 않는다(이중 디코딩으로 &를 더 먹지 않는다).
        assertThat(results.get(1).purchaseLink())
                .isEqualTo("https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=2&ttbkey=k&partner=openAPI");
    }

    @Test
    @DisplayName("책BTI 입력 — categoryName(장르)·pubDate(출간일)를 매핑한다. 없는 항목은 null")
    void parse_mapsCategoryAndPubDate() {
        String json = """
                {
                  "item": [
                    {
                      "title": "한국소설책",
                      "author": "어떤작가",
                      "isbn13": "9788900000001",
                      "categoryName": "국내도서>소설/시/희곡>한국소설",
                      "pubDate": "2020-03-15"
                    },
                    {
                      "title": "메타없는책",
                      "author": "무명",
                      "isbn13": "9788900000002"
                    }
                  ]
                }
                """;

        List<BookSearchResult> results = AladinBookSearchClient.parse(json, objectMapper);

        assertThat(results).hasSize(2);
        BookSearchResult withMeta = results.get(0);
        assertThat(withMeta.category()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(withMeta.pubDate()).isEqualTo("2020-03-15");

        // 카테고리/출간일이 없는 응답은 null로 안전 처리(적재 시 정상 — 백필·집계에서 빠짐)
        BookSearchResult noMeta = results.get(1);
        assertThat(noMeta.category()).isNull();
        assertThat(noMeta.pubDate()).isNull();
    }

    @Test
    @DisplayName("빈/오류 응답은 빈 목록으로 안전 처리한다")
    void parse_handlesEmptyOrBad() {
        assertThat(AladinBookSearchClient.parse(null, objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("", objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("not json", objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("{\"errorCode\":8}", objectMapper)).isEmpty();
    }

    @Test
    @DisplayName("totalResults를 읽어 전체 결과 수를 얻는다(없으면 0)")
    void parseTotalResults_reads() {
        assertThat(AladinBookSearchClient.parseTotalResults(
                "{\"totalResults\": 137, \"item\": []}", objectMapper)).isEqualTo(137);
        assertThat(AladinBookSearchClient.parseTotalResults("{\"item\": []}", objectMapper)).isZero();
        assertThat(AladinBookSearchClient.parseTotalResults("not json", objectMapper)).isZero();
    }
}
