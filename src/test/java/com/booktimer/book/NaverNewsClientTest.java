package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 뉴스 어댑터의 JSON 매핑·활성화 게이트 단위테스트 — 네트워크 없이 정적 파싱과 키 판정만 본다
 * ({@link AladinBookSearchClientTest}와 동일 정신).
 */
class NaverNewsClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final String FIXTURE = """
            {
              "lastBuildDate": "Tue, 11 Aug 2026 12:00:00 +0900",
              "total": 2,
              "start": 1,
              "display": 2,
              "items": [
                {
                  "title": "<b>총, 균, 쇠</b> 개정판 출간",
                  "originallink": "https://example.com/a",
                  "link": "https://n.news.naver.com/a",
                  "description": "&quot;재레드 다이아몬드&quot;의 대표작",
                  "pubDate": "Tue, 11 Aug 2026 09:00:00 +0900"
                },
                {
                  "title": "두 번째 기사",
                  "originallink": "https://example.com/b",
                  "link": "https://n.news.naver.com/b",
                  "description": "본문",
                  "pubDate": "잘못된 날짜"
                }
              ]
            }
            """;

    @Test
    @DisplayName("키가 비어 있으면 비활성 — 키 없이 배포해도 수집·노출이 통째로 꺼진다")
    void disabled_whenKeysBlank() {
        assertThat(new NaverNewsClient("", "").isEnabled()).isFalse();
        assertThat(new NaverNewsClient("id", "  ").isEnabled()).isFalse();
        assertThat(new NaverNewsClient("  ", "secret").isEnabled()).isFalse();
        assertThat(new NaverNewsClient(null, null).isEnabled()).isFalse();
        assertThat(new NaverNewsClient("id", "secret").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("응답 JSON을 기사 목록으로 매핑한다 — 태그·엔티티를 벗기고 pubDate(RFC-822)를 Instant로")
    void parse_mapsItems() {
        List<NaverNewsClient.NewsArticle> articles = NaverNewsClient.parse(FIXTURE, objectMapper);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(0).title()).isEqualTo("총, 균, 쇠 개정판 출간");
        assertThat(articles.get(0).description()).isEqualTo("\"재레드 다이아몬드\"의 대표작");
        assertThat(articles.get(0).link()).isEqualTo("https://n.news.naver.com/a");
        assertThat(articles.get(0).publishedAt()).isEqualTo(Instant.parse("2026-08-11T00:00:00Z"));
    }

    @Test
    @DisplayName("pubDate를 못 읽으면 그 기사만 publishedAt=null — 줄 자체는 살린다")
    void parse_unparsablePubDateBecomesNull() {
        List<NaverNewsClient.NewsArticle> articles = NaverNewsClient.parse(FIXTURE, objectMapper);

        assertThat(articles.get(1).publishedAt()).isNull();
        assertThat(articles.get(1).title()).isEqualTo("두 번째 기사");
    }

    @Test
    @DisplayName("빈 응답·깨진 JSON·null은 빈 목록 — 외부 장애가 배치를 깨지 않게 격리(알라딘 패턴)")
    void parse_isolatesFailures() {
        assertThat(NaverNewsClient.parse("{\"items\":[]}", objectMapper)).isEmpty();
        assertThat(NaverNewsClient.parse("{ 이건 JSON이 아니다", objectMapper)).isEmpty();
        assertThat(NaverNewsClient.parse("", objectMapper)).isEmpty();
        assertThat(NaverNewsClient.parse(null, objectMapper)).isEmpty();
    }

    @Test
    @DisplayName("비활성(키 없음) 상태에서 search()는 외부 호출 없이 빈 목록을 준다")
    void search_returnsEmptyWhenDisabled() {
        assertThat(new NaverNewsClient("", "").search("\"총, 균, 쇠\" 재레드 다이아몬드")).isEmpty();
    }

    @Test
    @DisplayName("검색 쿼리는 제목을 따옴표 구절로 감싸고 저자를 붙인다")
    void buildQuery_quotesTitle() {
        assertThat(NaverNewsClient.buildQuery("총, 균, 쇠", "재레드 다이아몬드"))
                .isEqualTo("\"총, 균, 쇠\" 재레드 다이아몬드");
    }
}
