package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구글 뉴스 RSS 어댑터의 XML 매핑·게이트 단위테스트 — 네트워크 없이 정적 파싱만 본다
 * ({@link AladinBookSearchClientTest}와 동일 정신).
 *
 * <p>픽스처는 <b>실제 응답에서 잘라온 item 3건</b>이다(질의 {@code "미움받을 용기" 기시미 이치로},
 * 2026-08-14 실측). 공개 문서 기준의 추측 픽스처를 쓰지 않는 이유: 직전 네이버 전환에서 미검증 가정이
 * 그대로 남았던 것이 이번 전환의 교훈이다.
 */
class GoogleNewsRssClientTest {

    /** 실측 응답에서 잘라온 item 3건(guid·description 포함 원문 그대로) + channel 껍데기. */
    private static final String FIXTURE = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><generator>NFE/5.0</generator><title>""미움받을 용기" 기시미 이치로" - Google 뉴스</title><language>ko</language>
            <item><title>미움받을 용기 기시미 이치로의 인생철학 - 불교신문</title><link>https://news.google.com/rss/articles/CBMiaEFVX3lxTE8yTzRiWjIxLXVRZmNjRHFmUU1YdEV6S0V2RG1vMHpPQ2QxdzJVaHZtYTJXa3lpRVNDU192NjgwT1RsVkE0UGVBcHk4SVBSX2FTMVh4YjY2YUNvdXNwQ19FMmZlMG51bERt?oc=5</link><guid isPermaLink="false">CBMiaEFVX3lxTE8yTzRiWjIxLXVRZmNjRHFmUU1YdEV6S0V2RG1vMHpPQ2QxdzJVaHZtYTJXa3lpRVNDU192NjgwT1RsVkE0UGVBcHk4SVBSX2FTMVh4YjY2YUNvdXNwQ19FMmZlMG51bERt</guid><pubDate>Thu, 02 Nov 2023 07:00:00 GMT</pubDate><description>&lt;a href="https://news.google.com/rss/articles/CBMiaEFVX3lxTE8yTzRiWjIx?oc=5" target="_blank"&gt;미움받을 용기 기시미 이치로의 인생철학&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;불교신문&lt;/font&gt;</description><source url="http://www.ibulgyo.com">불교신문</source></item>
            <item><title>미움받을 용기 - 기시미 이치로, 고가 후미타케 - 브런치</title><link>https://news.google.com/rss/articles/CBMiSkFVX3lxTE5sN1NXaFZWclYzOUp6aDNienBhUGNVcEdNcDBGNXhURlhycWVFZEVraHVNZldWNHJ0NUMyZDIyd2lfTlVqck5Gb2ZR?oc=5</link><guid isPermaLink="false">CBMiSkFVX3lxTE5sN1NXaFZWclYzOUp6aDNienBhUGNVcEdNcDBGNXhURlhycWVFZEVraHVNZldWNHJ0NUMyZDIyd2lfTlVqck5Gb2ZR</guid><pubDate>Thu, 22 Feb 2024 10:43:20 GMT</pubDate><description>&lt;a href="https://news.google.com/rss/articles/CBMiSkFVX3lxTE5sN1NX?oc=5" target="_blank"&gt;미움받을 용기 - 기시미 이치로, 고가 후미타케&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;브런치&lt;/font&gt;</description><source url="https://brunch.co.kr">브런치</source></item>
            <item><title>기시미 이치로 책은 월간지?…18개월 새 17권 출간 - 연합뉴스</title><link>https://news.google.com/rss/articles/CBMiW0FVX3lxTE1tVjR1Y0RLMUdobUJJOF9fSFJpNDVmLUtzXzYxUW5iSHQ4M09hUnZGeUJQRjB4V2ZmSlNLQXg2SGYtMWh6WUJvRktTSkM4dmVDVkhEUHJ5cXZDVW8?oc=5</link><guid isPermaLink="false">CBMiW0FVX3lxTE1tVjR1Y0RLMUdobUJJOF9fSFJpNDVmLUtzXzYxUW5iSHQ4M09hUnZGeUJQRjB4V2ZmSlNLQXg2SGYtMWh6WUJvRktTSkM4dmVDVkhEUHJ5cXZDVW8</guid><pubDate>Fri, 06 May 2016 07:00:00 GMT</pubDate><description>&lt;a href="https://news.google.com/rss/articles/CBMiW0FVX3lxTE1tVjR1?oc=5" target="_blank"&gt;기시미 이치로 책은 월간지?…18개월 새 17권 출간&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;연합뉴스&lt;/font&gt;</description><source url="https://www.yna.co.kr">연합뉴스</source></item>
            </channel></rss>
            """;

    @Test
    @DisplayName("실측 RSS를 기사 목록으로 매핑한다 — 제목·링크·발행일(RFC-822)·출처")
    void parse_mapsRealItems() {
        List<GoogleNewsRssClient.NewsArticle> articles = GoogleNewsRssClient.parse(FIXTURE);

        assertThat(articles).hasSize(3);
        GoogleNewsRssClient.NewsArticle first = articles.get(0);
        assertThat(first.title()).isEqualTo("미움받을 용기 기시미 이치로의 인생철학");
        assertThat(first.source()).isEqualTo("불교신문");
        assertThat(first.link()).startsWith("https://news.google.com/rss/articles/CBMiaEFVX3lxTE8y");
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2023-11-02T07:00:00Z"));
        assertThat(articles.get(2).publishedAt()).isEqualTo(Instant.parse("2016-05-06T07:00:00Z"));
    }

    @Test
    @DisplayName("제목 끝의 ' - 출처'만 잘라낸다 — 제목 안의 하이픈은 살린다(실측 항목)")
    void parse_stripsOnlyTrailingSourceSuffix() {
        List<GoogleNewsRssClient.NewsArticle> articles = GoogleNewsRssClient.parse(FIXTURE);

        assertThat(articles.get(1).title()).isEqualTo("미움받을 용기 - 기시미 이치로, 고가 후미타케");
        assertThat(articles.get(1).source()).isEqualTo("브런치");
    }

    @Test
    @DisplayName("접미사가 없거나 출처가 없으면 제목을 그대로 둔다")
    void stripSourceSuffix_leavesTitleWhenNoSuffix() {
        assertThat(GoogleNewsRssClient.stripSourceSuffix("미움받을 용기 다시 읽기", "불교신문"))
                .isEqualTo("미움받을 용기 다시 읽기");
        assertThat(GoogleNewsRssClient.stripSourceSuffix("제목 - 불교신문", null)).isEqualTo("제목 - 불교신문");
        assertThat(GoogleNewsRssClient.stripSourceSuffix("제목 - 불교신문", "  ")).isEqualTo("제목 - 불교신문");
    }

    @Test
    @DisplayName("빈 응답·깨진 XML·null은 빈 목록 — 외부 장애가 배치를 깨지 않게 격리(알라딘 패턴)")
    void parse_isolatesFailures() {
        assertThat(GoogleNewsRssClient.parse("<rss><channel></channel></rss>")).isEmpty();
        assertThat(GoogleNewsRssClient.parse("<rss><channel><item><title>안 닫힌")).isEmpty();
        assertThat(GoogleNewsRssClient.parse("이건 XML이 아니다")).isEmpty();
        assertThat(GoogleNewsRssClient.parse("")).isEmpty();
        assertThat(GoogleNewsRssClient.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("제목·링크가 없는 item은 건너뛰고, pubDate를 못 읽으면 그 기사만 publishedAt=null")
    void parse_skipsIncompleteItemsAndToleratesBadPubDate() {
        String xml = """
                <rss><channel>
                <item><title>제목만 있고 링크 없음</title></item>
                <item><link>https://news.google.com/rss/articles/only-link</link></item>
                <item><title>날짜가 깨진 기사 - 어떤신문</title><link>https://news.google.com/rss/articles/bad-date</link><pubDate>잘못된 날짜</pubDate><source url="https://x.example">어떤신문</source></item>
                </channel></rss>
                """;

        List<GoogleNewsRssClient.NewsArticle> articles = GoogleNewsRssClient.parse(xml);

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).title()).isEqualTo("날짜가 깨진 기사");
        assertThat(articles.get(0).publishedAt()).isNull();
    }

    @Test
    @DisplayName("XXE 차단 — DOCTYPE·외부 엔티티가 있는 XML은 파일을 읽지 않고 빈 목록으로 떨어진다")
    void parse_blocksExternalEntities(@TempDir Path tempDir) throws Exception {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-CONTENT");
        String malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "%s">]>
                <rss><channel><item><title>&xxe;</title><link>https://news.google.com/rss/articles/x</link></item></channel></rss>
                """.formatted(secret.toUri());

        List<GoogleNewsRssClient.NewsArticle> articles = GoogleNewsRssClient.parse(malicious);

        // DOCTYPE 자체를 거부하므로 파싱이 실패해 빈 목록 — 엔티티가 해제될 여지가 없다.
        assertThat(articles).isEmpty();
    }

    @Test
    @DisplayName("킬스위치가 꺼지면 비활성 — 수집·노출이 통째로 꺼진다 (기본값은 켜짐)")
    void killSwitch() {
        assertThat(new GoogleNewsRssClient(true).isEnabled()).isTrue();
        assertThat(new GoogleNewsRssClient(false).isEnabled()).isFalse();
        assertThat(new GoogleNewsRssClient(false).search("미움받을 용기 기시미 이치로")).isEmpty();
    }

    @Test
    @DisplayName("검색 쿼리는 제목 + 저자 — 제목만 던지면 오탐이 급증한다(실측)")
    void buildQuery_titleAndAuthor() {
        assertThat(GoogleNewsRssClient.buildQuery("미움받을 용기", "기시미 이치로"))
                .isEqualTo("미움받을 용기 기시미 이치로");
    }

    @Test
    @DisplayName("검색 URL은 한글 질의를 퍼센트 인코딩하고 한국어 로케일 파라미터를 붙인다")
    void searchUrl_encodesQuery() {
        String url = GoogleNewsRssClient.searchUrl("데미안 헤르만 헤세").toString();

        assertThat(url).startsWith("https://news.google.com/rss/search?q=");
        assertThat(url).contains("%EB%8D%B0%EB%AF%B8%EC%95%88"); // "데미안"
        assertThat(url).doesNotContain("데미안");
        assertThat(url).contains("hl=ko").contains("gl=KR").contains("ceid=KR:ko");
    }
}
