package com.booktimer.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알라딘 어댑터의 JSON 매핑·활성화 게이트 단위테스트 — 네트워크 없이 정적 파싱과 키 판정만 본다.
 */
class AladinBookSearchClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("TTBKey 미설정이면 검색 비활성(수동 입력 폴백)")
    void disabled_whenKeyNotConfigured() {
        assertThat(new AladinBookSearchClient("not-configured").isEnabled()).isFalse();
        assertThat(new AladinBookSearchClient("  ").isEnabled()).isFalse();
        assertThat(new AladinBookSearchClient("ttbreal123").isEnabled()).isTrue();
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
    @DisplayName("빈/오류 응답은 빈 목록으로 안전 처리한다")
    void parse_handlesEmptyOrBad() {
        assertThat(AladinBookSearchClient.parse(null, objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("", objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("not json", objectMapper)).isEmpty();
        assertThat(AladinBookSearchClient.parse("{\"errorCode\":8}", objectMapper)).isEmpty();
    }
}
