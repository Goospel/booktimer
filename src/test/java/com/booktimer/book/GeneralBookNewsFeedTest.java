package com.booktimer.book;

import com.booktimer.book.GoogleNewsRssClient.NewsArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게스트 「일반 책 뉴스」 수집·병합 — 스프링·DB 없이 돈다.
 *
 * <p>검색어가 운영자 고정 주제뿐이라는 것은 생성자에 리포지토리가 없다는 <b>구조</b>로 보장되고, 여기서는
 * 검색어 문자열 자체와 병합 규칙(https·제외어·발행일·상한·중복 제거·장애 격리·킬스위치)을 못 박는다.
 * 외부 RSS는 부르지 않는다({@link StubClient}).
 */
class GeneralBookNewsFeedTest {

    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** 검색어를 기록하고 검색어별로 미리 정한 기사를 돌려준다. 없는 검색어는 빈 목록. */
    private static final class StubClient extends GoogleNewsRssClient {
        final List<String> queries = new ArrayList<>();
        final Map<String, List<NewsArticle>> byQuery = new HashMap<>();

        StubClient(boolean enabled) {
            super(enabled);
        }

        @Override
        public List<NewsArticle> search(String query) {
            queries.add(query);
            return byQuery.getOrDefault(query, List.of());
        }
    }

    private static NewsArticle article(String title, String linkId, Instant publishedAt) {
        return new NewsArticle(title, "https://news.google.com/rss/articles/" + linkId, publishedAt, "연합뉴스");
    }

    private static Instant daysAgo(long days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static String q(int i) {
        return GeneralBookNewsFeed.TOPICS.get(i).query();
    }

    private static String label(int i) {
        return GeneralBookNewsFeed.TOPICS.get(i).label();
    }

    @Test
    @DisplayName("검색어는 운영자 고정 주제 3종 그대로다 — 순서·when:7d까지 정확히")
    void queriesAreFixedTopicsOnly() {
        StubClient client = new StubClient(true);
        client.byQuery.put("신간 when:7d", List.of(article("아무 기사", "x", daysAgo(1))));

        new GeneralBookNewsFeed(client, CLOCK).collect();

        assertThat(client.queries).containsExactly("신간 when:7d", "출판계 when:7d", "베스트셀러 도서 when:7d");
        assertThat(GeneralBookNewsFeed.TOPICS).extracting(GeneralBookNewsFeed.Topic::label)
                .containsExactly("신간", "출판계", "베스트셀러");
    }

    @Test
    @DisplayName("주제를 합쳐 최신순 — 각 항목의 라벨은 자기 주제")
    void mergesNewestFirstAcrossTopics() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(article("A 옛 기사", "a-old", daysAgo(7)), article("A 새 기사", "a-new", daysAgo(1))));
        client.byQuery.put(q(1), List.of(article("B 기사", "b", daysAgo(2))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        assertThat(feed.collect()).isEqualTo(3);

        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .containsExactly("A 새 기사", "B 기사", "A 옛 기사");
        assertThat(feed.snapshot()).extracting(GeneralBookNewsFeed.Item::topic)
                .containsExactly(label(0), label(1), label(0));
    }

    @Test
    @DisplayName("주제별 10건, 전체 30건까지")
    void capsPerTopicThenTotal() {
        StubClient one = new StubClient(true);
        one.byQuery.put(q(0), many("신간", 12));
        GeneralBookNewsFeed single = new GeneralBookNewsFeed(one, CLOCK);
        single.collect();
        assertThat(single.snapshot()).hasSize(10);

        StubClient all = new StubClient(true);
        for (int t = 0; t < 3; t++) {
            all.byQuery.put(q(t), many("주제" + t, 12));
        }
        GeneralBookNewsFeed full = new GeneralBookNewsFeed(all, CLOCK);
        full.collect();
        assertThat(full.snapshot()).hasSize(30);
    }

    private static List<NewsArticle> many(String prefix, int n) {
        List<NewsArticle> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(article(prefix + " 기사 " + i, prefix + "-" + i, NOW.minus(Duration.ofHours(i + 1))));
        }
        return list;
    }

    @Test
    @DisplayName("같은 link는 한 줄 · link가 달라도 정규화 제목이 같으면 한 줄(최신 쪽이 남는다)")
    void dedupesByLinkAndByTitleKey() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("같은 링크 기사", "same", daysAgo(1)),
                article("신간 소개하는 송길영 작가", "daum-repost", daysAgo(3)),
                article("신간  소개하는 송길영 작가", "yna-original", daysAgo(2))));
        client.byQuery.put(q(1), List.of(article("같은 링크 기사", "same", daysAgo(4))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().link())
                .containsExactly("https://news.google.com/rss/articles/same", "https://news.google.com/rss/articles/yna-original");
    }

    @Test
    @DisplayName("구글 뉴스 https 링크만 남긴다 — javascript:·http://·다른 https 도메인은 뺀다")
    void dropsNonHttpsLinks() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                new NewsArticle("스크립트 링크", "javascript:alert(1)", daysAgo(1), "x"),
                new NewsArticle("평문 링크", "http://news.example/a", daysAgo(1), "x"),
                new NewsArticle("다른 도메인", "https://evil.example/news.google.com/a", daysAgo(1), "x"),
                new NewsArticle("접두 흉내", "https://news.google.com.evil.example/a", daysAgo(1), "x"),
                article("정상 링크", "ok", daysAgo(1))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly("정상 링크");
    }

    @Test
    @DisplayName("제목에 제외어가 있으면 뺀다 — 사건 기사가 첫 화면에 오지 않게")
    void dropsBlockedTitles() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(2), List.of(
                article("서점 앞 사고로 1명 사망", "blocked", daysAgo(1)),
                article("[베스트셀러] 세네카 1위 탈환", "kept", daysAgo(1))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly("[베스트셀러] 세네카 1위 탈환");
    }

    @Test
    @DisplayName("발행일이 없거나 14일보다 오래되면 뺀다(13일 전은 남는다)")
    void dropsStaleOrUndated() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("날짜 없음", "null", null),
                article("15일 전", "old", daysAgo(15)),
                article("13일 전", "recent", daysAgo(13))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly("13일 전");
    }

    @Test
    @DisplayName("새로 가져온 것이 0건이면 옛 목록을 유지한다(구글 장애 격리)")
    void keepsOldSnapshotWhenFetchEmpty() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(article("1회차 A", "a", daysAgo(1)), article("1회차 B", "b", daysAgo(2))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);
        assertThat(feed.collect()).isEqualTo(2);

        client.byQuery.clear();

        assertThat(feed.collect()).isZero();
        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly("1회차 A", "1회차 B");
    }

    /** 테스트가 시각을 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        Instant now = NOW;

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("스냅샷이 비어 있으면 매 점검마다 수집할 차례다 — 기동 시 첫 수집이 실패해도 6시간을 기다리지 않는다")
    void dueWhileEmpty() {
        MovableClock clock = new MovableClock();
        StubClient client = new StubClient(true);
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, clock);

        assertThat(feed.isDue()).isTrue();
        assertThat(feed.collect()).isZero(); // 구글 장애 — 0건
        clock.now = NOW.plus(Duration.ofMinutes(5));
        assertThat(feed.isDue()).isTrue();
    }

    @Test
    @DisplayName("수집에 성공해 차 있으면 6시간 뒤에야 다시 수집할 차례다")
    void notDueUntilSixHoursAfterSuccess() {
        MovableClock clock = new MovableClock();
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(article("기사", "a", daysAgo(1))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, clock);
        assertThat(feed.collect()).isEqualTo(1);

        clock.now = NOW.plus(Duration.ofHours(6)).minusSeconds(1);
        assertThat(feed.isDue()).isFalse();
        clock.now = NOW.plus(Duration.ofHours(6));
        assertThat(feed.isDue()).isTrue();
    }

    @Test
    @DisplayName("스케줄 점검(refresh)은 차례가 아니면 구글을 부르지 않고, 차례면 부른다")
    void refreshHonorsDue() {
        MovableClock clock = new MovableClock();
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(article("기사", "a", daysAgo(1))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, clock);
        feed.refresh();
        assertThat(client.queries).hasSize(3);

        clock.now = NOW.plus(Duration.ofMinutes(5));
        feed.refresh();
        assertThat(client.queries).hasSize(3); // 5분 점검 — 채워져 있어 건너뛴다

        clock.now = NOW.plus(Duration.ofHours(6));
        feed.refresh();
        assertThat(client.queries).hasSize(6);
    }

    @Test
    @DisplayName("킬스위치가 꺼지면 스냅샷을 비운다 — 꺼짐이 진짜 꺼짐")
    void disabledClearsSnapshot() {
        StubClient client = new StubClient(false);
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);
        feed.replaceForTest(List.of(
                new GeneralBookNewsFeed.Item("신간", article("남은 A", "a", daysAgo(1))),
                new GeneralBookNewsFeed.Item("신간", article("남은 B", "b", daysAgo(1)))));

        assertThat(feed.collect()).isZero();
        assertThat(feed.snapshot()).isEmpty();
        assertThat(client.queries).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────
    // 2026-09-21 — 스팸 출처 차단(매체명 표기 규칙 + 브런치 + 도박어)
    // ────────────────────────────────────────────────────────────────────

    /** 스냅샷 픽스처 전용 시계 — `guest-news-2026-09-21.json`의 발행일이 전부 이 시각의 14일 안. */
    private static final Clock SNAPSHOT_CLOCK = Clock.fixed(Instant.parse("2026-09-21T14:07:00Z"), ZoneOffset.UTC);

    private static NewsArticle article(String title, String linkId, Instant publishedAt, String source) {
        return new NewsArticle(title, "https://news.google.com/rss/articles/" + linkId, publishedAt, source);
    }

    @Test
    @DisplayName("스팸 출처는 빠지고 국내 매체는 남는다 — 라틴 표기 국내 매체(v.daum.net·chosun.com)도 생존")
    void dropsUntrustedSources() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("결과 표시가 여러 번 나오는 이유는? 메랜 슬롯 교환 진행 설명", "sp1", daysAgo(1), "Calgary Roughnecks"),
                article("배당표 예시와 실제 조합을 구분하는 왕좌의 게임 아트북", "sp2", daysAgo(1), "Histoire pour tous"),
                article("전 세계 출판 업계가 주목하는 새로운 흐름", "sp3", daysAgo(1), "Vietnam.vn"),
                article("[신간] 드래곤 마스터 20", "sp4", daysAgo(1), "NANOOM ENERGY"),
                article("달콤씁쓸했던 서점 나들이", "sp5", daysAgo(1), "브런치")));
        client.byQuery.put(q(1), List.of(
                article("[신간] 인생 후반기에 중요한 것…'딱 알맞은 고독'", "ok1", daysAgo(2), "v.daum.net"),
                article("[카페 2030] 문학이라는 동네의 가격", "ok2", daysAgo(2), "chosun.com"),
                article("133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락", "ok3", daysAgo(2), "youthassembly.kr"),
                article("[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등", "ok4", daysAgo(2), "KBS 뉴스"),
                article("[신간] 『AI 나라의 홍콩할머니』", "ok5", daysAgo(2), "독서신문"),
                article("임의 기사", "ok6", daysAgo(2), "YTN"),
                article("임의 기사 2", "ok7", daysAgo(2), "nc.press")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactlyInAnyOrder(
                "[신간] 인생 후반기에 중요한 것…'딱 알맞은 고독'",
                "[카페 2030] 문학이라는 동네의 가격",
                "133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락",
                "[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등",
                "[신간] 『AI 나라의 홍콩할머니』",
                "임의 기사",
                "임의 기사 2");
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("매체명 표기 규칙 — 한글·약어·.kr/일반도메인은 통과, 설명형 라틴 표시명·외국 국가도메인·플랫폼은 탈락")
    @CsvSource(nullValues = "NULL", value = {
            "NULL, true",
            "'', true",
            "독서신문, true",
            "v.daum.net, true",
            "YTN, true",
            "JTBC, true",
            "nc.press, true",
            "Vietnam.vn, false",
            "Histoire pour tous, false",
            "NANOOM ENERGY, false",
            "브런치, false",
            "histoire-pour-tous.fr, false"})
    void trustedSourceTable(String source, boolean trusted) {
        assertThat(GeneralBookNewsFeed.trustedSource(source)).isEqualTo(trusted);
    }

    @Test
    @DisplayName("도박어는 국내 매체가 실어도 뺀다 — 단 배당·토토는 제외어가 아니다(책 제목에 걸린다)")
    void dropsGamblingTitlesEvenFromKoreanSource() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("결과 표시가 여러 번 나오는 이유는? 메랜 슬롯 교환 진행 설명", "g1", daysAgo(1), "연합뉴스"),
                article("작은 화면에서 읽기 쉬운가? 카지노가입 모바일 점검", "g2", daysAgo(1), "연합뉴스"),
                article("바카라 규칙을 다룬 신간이라는 광고", "g3", daysAgo(1), "연합뉴스"),
                article("먹튀 없는 곳을 고르는 법이라는 홍보 글", "g4", daysAgo(1), "연합뉴스")));
        client.byQuery.put(q(1), List.of(
                article("배당 투자로 월급 만들기 출간", "k1", daysAgo(2), "연합뉴스"),
                article("이웃집 토토로 그림책 재출간", "k2", daysAgo(2), "연합뉴스"),
                article("[신간] 『AI 나라의 홍콩할머니』", "k3", daysAgo(2), "독서신문")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactlyInAnyOrder(
                "배당 투자로 월급 만들기 출간",
                "이웃집 토토로 그림책 재출간",
                "[신간] 『AI 나라의 홍콩할머니』");
    }

    @Test
    @DisplayName("2026-09-21 운영 스냅샷 25건 재생 — 스팸 2건만 빠지고 정상 23건은 전부 남는다")
    void snapshotReplay_2026_09_21_dropsOnlySpam() throws Exception {
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(snapshotClient(), SNAPSHOT_CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).hasSize(23);
        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .doesNotContain(
                        "결과 표시가 여러 번 나오는 이유는? 메랜 슬롯 교환 진행 설명",
                        "배당표 예시와 실제 조합을 구분하는 왕좌의 게임 아트북")
                .contains(
                        "[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등",
                        "[카페 2030] 문학이라는 동네의 가격",
                        "133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락",
                        "취미와 접목하고, 편의점 진출…일상 파고드는 서점·출판가");
    }

    /**
     * 2026-09-21 14:07 운영 {@code /api/public/news} 25건을 그날의 질의 슬롯에 그대로 넣은 스텁.
     * 외부 RSS는 부르지 않는다 — 픽스처는 {@code src/test/resources/news/guest-news-2026-09-21.json}.
     */
    private static StubClient snapshotClient() throws Exception {
        Map<String, Integer> slot = Map.of("신간", 0, "출판계", 1, "베스트셀러", 2);
        StubClient client = new StubClient(true);
        for (int i = 0; i < 3; i++) {
            client.byQuery.put(q(i), new ArrayList<>());
        }
        for (JsonNode row : snapshotFixture()) {
            client.byQuery.get(q(slot.get(row.get("fetchedBy").asText()))).add(new NewsArticle(
                    row.get("title").asText(),
                    "https://news.google.com/rss/articles/" + row.get("id").asText() + "?oc=5",
                    Instant.parse(row.get("publishedAt").asText()),
                    row.get("source").asText()));
        }
        return client;
    }

    private static JsonNode snapshotFixture() throws Exception {
        try (InputStream in = GeneralBookNewsFeedTest.class.getResourceAsStream("/news/guest-news-2026-09-21.json")) {
            return new ObjectMapper().readTree(in);
        }
    }
}
