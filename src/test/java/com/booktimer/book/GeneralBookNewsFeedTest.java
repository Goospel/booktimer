package com.booktimer.book;

import com.booktimer.book.GoogleNewsRssClient.NewsArticle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
