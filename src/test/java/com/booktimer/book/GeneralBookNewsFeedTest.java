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
import java.util.Set;

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
        return GeneralBookNewsFeed.QUERIES.get(i);
    }

    @Test
    @DisplayName("검색어는 운영자 고정 주제 3종 그대로다 — 순서·when:7d까지 정확히")
    void queriesAreFixedTopicsOnly() {
        StubClient client = new StubClient(true);
        client.byQuery.put("신간 when:7d", List.of(article("아무 기사", "x", daysAgo(1))));

        new GeneralBookNewsFeed(client, CLOCK).collect();

        assertThat(client.queries).containsExactly("신간 when:7d", "출판계 when:7d", "베스트셀러 도서 when:7d");
    }

    @Test
    @DisplayName("질의를 합쳐 최신순 — 배지는 질의가 아니라 제목이 정한다(태그 없는 합성 제목은 전부 출판계)")
    void mergesNewestFirstAcrossTopics() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(article("A 옛 기사", "a-old", daysAgo(7)), article("A 새 기사", "a-new", daysAgo(1))));
        client.byQuery.put(q(1), List.of(article("B 기사", "b", daysAgo(2))));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        assertThat(feed.collect()).isEqualTo(3);

        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .containsExactly("A 새 기사", "B 기사", "A 옛 기사");
        assertThat(feed.snapshot()).extracting(GeneralBookNewsFeed.Item::topic)
                .containsExactly("출판계", "출판계", "출판계");
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
    @DisplayName("2026-09-21 운영 스냅샷 25건 재생 — 스팸 2건은 출처 규칙이, 중복 사건 3건은 사건 묶기가 걷어 20건")
    void snapshotReplay_2026_09_21_dropsOnlySpam() throws Exception {
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(snapshotClient(), SNAPSHOT_CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).hasSize(20);
        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .doesNotContain(
                        "결과 표시가 여러 번 나오는 이유는? 메랜 슬롯 교환 진행 설명",
                        "배당표 예시와 실제 조합을 구분하는 왕좌의 게임 아트북")
                // 라틴 표기 국내 매체가 살아남는지 — chosun.com·v.daum.net.
                // (youthassembly.kr의 「133억 들인 출판전산망…」은 스팸이 아니라 사건 묶기가 걷는다 —
                //  `.kr` 호스트꼴이 출처 규칙을 통과한다는 단언은 dropsUntrustedSources가 직접 한다.)
                .contains(
                        "[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등",
                        "[카페 2030] 문학이라는 동네의 가격",
                        "[신간] 인생 후반기에 중요한 것…'딱 알맞은 고독'",
                        "취미와 접목하고, 편의점 진출…일상 파고드는 서점·출판가");
    }

    // ────────────────────────────────────────────────────────────────────
    // 2026-09-21 — ② 같은 사건 묶기 · ③ 제목 기반 라벨
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 사건은 최신 1건만 — 출판전산망 5건이 대표로 접힌다")
    void mergesSameEventNewestWins() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("134억 들인 ‘책 전산망’…지난해 신간 4권 중 3권은 없었다", "s02",
                        Instant.parse("2026-09-21T02:40:48Z"), "더퍼블릭"),
                article("진선미 “134억 들인 출판유통통합전산망, 지난해 베스트셀러 50종 중 23종만 등록…‘반쪽짜리’”", "s11",
                        Instant.parse("2026-09-20T23:50:32Z"), "뉴스데일리"),
                article("133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락", "s12",
                        Instant.parse("2026-09-20T22:56:59Z"), "youthassembly.kr"),
                article("130억원 쏟은 출판전산망…신간 등록률, 4권 중 1권뿐", "s14",
                        Instant.parse("2026-09-20T11:26:00Z"), "경향신문"),
                article("[단독]130억 들인 출판유통전산망, 신간 4권 중 3권 외면·베스트셀러 절반 이상 미등록", "s16",
                        Instant.parse("2026-09-20T06:52:00Z"), "경향신문")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, SNAPSHOT_CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .contains("134억 들인 ‘책 전산망’…지난해 신간 4권 중 3권은 없었다")
                .doesNotContain(
                        "진선미 “134억 들인 출판유통통합전산망, 지난해 베스트셀러 50종 중 23종만 등록…‘반쪽짜리’”",
                        "133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락",
                        "[단독]130억 들인 출판유통전산망, 신간 4권 중 3권 외면·베스트셀러 절반 이상 미등록");
        // 「130억원 쏟은…」은 대표와 자카드 0.16이라 남는다 — 알려진 천장(설계 §4).
        assertThat(feed.snapshot()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("3자리+ 숫자·단위를 공유하면 같은 사건 — 「645억」 예산 기사 6건이 1건으로")
    void mergesByBigNumberKey() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(1), List.of(
                article("문체부, 출판예산 645억원 편성…책 K콘텐츠로 ‘출판 IP' 산업 키운다", "b1", daysAgo(1), "연합뉴스"),
                article("문체부, 내년 출판 예산 645억 원 편성", "b2", daysAgo(2), "연합뉴스"),
                article("출판 분야 내년 예산 645억원…지역서점·수출·출판 IP에 투입", "b3", daysAgo(3), "연합뉴스"),
                article("내년 출판 진흥 예산 645억원…역대급 편성에 출판계 ‘반색’ · 비결은?", "b4", daysAgo(4), "연합뉴스"),
                article("문화체육관광부, 출판 분야 '27년 정부안 645억 원 편성, 출판계와 지원 방향 논의", "b5", daysAgo(5), "연합뉴스"),
                article("출판예산 645억원으로 18% 확대…지역서점 지원 84% 늘린다", "b6", daysAgo(6), "연합뉴스")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .containsExactly("문체부, 출판예산 645억원 편성…책 K콘텐츠로 ‘출판 IP' 산업 키운다");
    }

    @Test
    @DisplayName("음성 대조군 — 다른 사건은 묶이지 않는다(태그·작은 숫자·질의어·얕은 자카드만 공유하는 17건 전부 생존)")
    void keepsDistinctStories() {
        StubClient client = new StubClient(true);
        String[] titles = {
                "[신간] ‘마음 가는 대로 가라’…삶의 길목에서 건넨 지혜",
                "[신간] 『AI 나라의 홍콩할머니』",
                "[신간] 인생 후반기에 중요한 것…'딱 알맞은 고독'",
                "[신간] 『결국, 태도가 삶의 품격을 만든다』",
                "[청소년신간] 숨죽인 고통에 건네는 위로…김선미 '요절 금지'",
                "[신간]시집 ‘백록담의 바람’",
                // 같은 연재 태그를 단 다른 두 책 소개 — 앞머리 태그를 안 벗기면 「새로 나온 책」 4개 2-gram만으로
                // 자카드가 0.20에 닿아 묶인다(태그를 벗기면 0.00). 태그가 질의어(신간·베스트셀러)면 어차피
                // 질의어 제거가 지워 버려 이 함정이 안 보이므로, 질의어가 아닌 태그로 계측한다.
                "[새로 나온 책] 소설가의 밤 외 5권",
                "[신간] \"언론의 민낯, 픽션으로 고발하다\"… 오효석 기자, 네 번째 저서 '내 얼굴에 침뱉기’",
                "[새로 나온 책] 우리가 사랑한 문장들",
                // 1~2자리 숫자·단위(「30권」)만 공유하는 다른 사건 — 숫자키를 `\d+`로 넓히면 이 둘이 묶인다.
                // (3자리+ 제한이 없으면 모든 「4권 중 3권」·「30권」 기사가 한 사건이 된다.)
                "[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등",
                "문학동네 세계문학전집 30권 돌파 기념 특별전을 연다",
                // 자카드 0.133(교집합 6)인 다른 사건 — 임계값을 0.1로 낮추면 이 둘이 묶인다.
                "한국문학번역원이 번역가 양성 과정을 올해부터 크게 늘리기로 했다",
                "한국문학번역원 지원작이 해외 문학상 최종 후보에 올랐다고 한다",
                "이번 주 종합 베스트셀러 순위 [인포그래픽]",
                "베스트셀러 작가도 책 사재기?…\"오프라인 제도 미흡\"",
                "[베스트셀러] '세네카, 오늘을 빼앗기고 있는 당신에게' 1위",
                "성시경, 일본 아마존서 뜻밖의 1위…"};
        // 질의 둘로 나눠 넣는다 — 질의별 상한(10건)에 걸리지 않게 하고, 덤으로 질의를 가로지르는 묶기까지 본다.
        List<NewsArticle> first = new ArrayList<>();
        List<NewsArticle> second = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            (i < 9 ? first : second).add(article(titles[i], "d" + i, NOW.minus(Duration.ofHours(i + 1)), "연합뉴스"));
        }
        client.byQuery.put(q(0), first);
        client.byQuery.put(q(1), second);
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly(titles);
    }

    @Test
    @DisplayName("겹침이 얕으면 비율이 임계값에 닿아도 안 묶는다 — 조사·어미만 겹친 다른 책 두 권(교집합 3, 자카드 0.200)")
    void doesNotMergeOnThinOverlap() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                // 서로 다른 책인데 「다는」·「는착」·「착각」 3개만 겹치고 양쪽 2-gram이 9개씩이라 자카드가 정확히 0.200이다.
                article("[신간] 과학적으로 옳다는 착각", "thin1", daysAgo(1), "연합뉴스"),
                article("[신간] 아무렇지도 않다는 착각", "thin2", daysAgo(2), "연합뉴스"),
                // 양성 대조군 — 진짜 같은 사건은 교집합이 6개라 하한을 넘어 그대로 묶인다(「다 안 묶는다」 구현을 배제한다).
                article("134억 들인 ‘책 전산망’…지난해 신간 4권 중 3권은 없었다", "real1", daysAgo(3), "더퍼블릭"),
                article("133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락", "real2", daysAgo(4), "youthassembly.kr")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly(
                "[신간] 과학적으로 옳다는 착각",
                "[신간] 아무렇지도 않다는 착각",
                "134억 들인 ‘책 전산망’…지난해 신간 4권 중 3권은 없었다");
    }

    @Test
    @DisplayName("짧은 제목은 자카드로 묶지 않는다 — 2-gram이 6개 미만이면 숫자키·완전일치만")
    void shortTitlesNeverMergeByJaccard() {
        StubClient client = new StubClient(true);
        client.byQuery.put(q(0), List.of(
                article("A 새 기사", "t1", daysAgo(1), "연합뉴스"),
                article("A 옛 기사", "t2", daysAgo(2), "연합뉴스"),
                article("2026-09-16 일일 베스트셀러", "t3", daysAgo(3), "연합뉴스"),
                article("2026-09-21 봄봄스쿨 일일 베스트셀러", "t4", daysAgo(4), "연합뉴스")));
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .containsExactly("A 새 기사", "A 옛 기사", "2026-09-16 일일 베스트셀러", "2026-09-21 봄봄스쿨 일일 베스트셀러");
    }

    @Test
    @DisplayName("중복 제거가 상한보다 먼저 — 한 질의의 최신 10건이 한 사건이어도 그 뒤 별개 기사가 살아남는다")
    void dedupesBeforeCapping() {
        StubClient client = new StubClient(true);
        List<NewsArticle> oneEventThenOther = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            oneEventThenOther.add(article("문체부, 내년 출판 진흥 예산 645억원 편성 브리핑 " + i + "차 보도",
                    "e" + i, NOW.minus(Duration.ofHours(i + 1)), "연합뉴스"));
        }
        oneEventThenOther.add(article("[카페 2030] 문학이라는 동네의 가격", "other", NOW.minus(Duration.ofHours(20)), "chosun.com"));
        client.byQuery.put(q(0), oneEventThenOther);
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(client, CLOCK);

        feed.collect();

        // 옛 순서(상한 → 중복 제거)라면 최신 10건이 전부 한 사건이라 별개 기사가 상한에서 잘리고 1건만 남는다.
        assertThat(feed.snapshot()).extracting(i -> i.article().title()).containsExactly(
                "문체부, 내년 출판 진흥 예산 645억원 편성 브리핑 0차 보도",
                "[카페 2030] 문학이라는 동네의 가격");
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("배지는 물어온 질의가 아니라 제목이 정한다 — 대괄호 태그 · 「베스트셀러」+순위어 · 나머지는 출판계")
    @CsvSource(delimiter = '|', value = {
            "[신간] 『AI 나라의 홍콩할머니』 | 신간",
            "[새로 나온 책] ‘가장 지혜로운 책’ 창비 한국사상선 30권 완간 등 | 신간",
            "[청소년신간] 숨죽인 고통에 건네는 위로…김선미 '요절 금지' | 신간",
            "[신간]시집 ‘백록담의 바람’ | 신간",
            "이번 주 종합 베스트셀러 순위 [인포그래픽] | 베스트셀러",
            "[베스트셀러] '세네카, 오늘을 빼앗기고 있는 당신에게' 1위 | 베스트셀러",
            "[주간 베스트셀러] 교사들의 마음을 붙잡은 책 | 베스트셀러",
            "베스트셀러 작가도 책 사재기?…\"오프라인 제도 미흡\" | 출판계",
            "130억원 쏟은 출판전산망…신간 등록률, 4권 중 1권뿐 | 출판계",
            "진선미 “134억 들인 출판유통통합전산망, 지난해 베스트셀러 50종 중 23종만 등록…‘반쪽짜리’” | 출판계",
            "신간 소개하는 송길영 작가 | 출판계"})
    void labelsByTitle(String title, String label) {
        assertThat(GeneralBookNewsFeed.labelOf(title)).isEqualTo(label);
    }

    @Test
    @DisplayName("2026-09-21 운영 스냅샷 재생 — 사건 묶기로 23 → 20건, 배지는 신간 8 · 출판계 11 · 베스트셀러 1")
    void snapshotReplay_2026_09_21() throws Exception {
        // 스팸 2건(s21·s22)은 ① 출처 규칙이 지운다(별도 PR) — 여기서는 ②③만 겨눈다.
        GeneralBookNewsFeed feed = new GeneralBookNewsFeed(snapshotClient("s21", "s22"), SNAPSHOT_CLOCK);

        feed.collect();

        assertThat(feed.snapshot()).hasSize(20);
        assertThat(feed.snapshot()).extracting(GeneralBookNewsFeed.Item::topic)
                .filteredOn("신간"::equals).hasSize(8);
        assertThat(feed.snapshot()).extracting(GeneralBookNewsFeed.Item::topic)
                .filteredOn("출판계"::equals).hasSize(11);
        assertThat(feed.snapshot()).extracting(GeneralBookNewsFeed.Item::topic)
                .filteredOn("베스트셀러"::equals).hasSize(1);
        // s01(KBS 「[새로 나온 책]」)은 「베스트셀러」 질의가 물어왔지만 배지는 신간이고,
        // s14(경향 130억)는 「신간」 질의가 물어왔지만 배지는 출판계다.
        assertThat(feed.snapshot()).filteredOn(i -> i.article().title().startsWith("[새로 나온 책]"))
                .singleElement().extracting(GeneralBookNewsFeed.Item::topic).isEqualTo("신간");
        assertThat(feed.snapshot()).filteredOn(i -> i.article().title().startsWith("130억원 쏟은"))
                .singleElement().extracting(GeneralBookNewsFeed.Item::topic).isEqualTo("출판계");
        assertThat(feed.snapshot()).extracting(i -> i.article().title())
                .doesNotContain(
                        "진선미 “134억 들인 출판유통통합전산망, 지난해 베스트셀러 50종 중 23종만 등록…‘반쪽짜리’”",
                        "133억 들인 출판전산망 ‘구멍’…신간 4권 중 3권 누락",
                        "[단독]130억 들인 출판유통전산망, 신간 4권 중 3권 외면·베스트셀러 절반 이상 미등록");
    }

    /**
     * 2026-09-21 14:07 운영 {@code /api/public/news} 25건을 그날의 질의 슬롯에 그대로 넣은 스텁.
     * 외부 RSS는 부르지 않는다 — 픽스처는 {@code src/test/resources/news/guest-news-2026-09-21.json}.
     * {@code excludedIds}를 주면 그 행을 빼고 넣는다(다른 단계가 이미 지운 행을 그 테스트의 관심에서 뺄 때).
     */
    private static StubClient snapshotClient(String... excludedIds) throws Exception {
        Map<String, Integer> slot = Map.of("신간", 0, "출판계", 1, "베스트셀러", 2);
        Set<String> excluded = Set.of(excludedIds);
        StubClient client = new StubClient(true);
        for (int i = 0; i < 3; i++) {
            client.byQuery.put(q(i), new ArrayList<>());
        }
        for (JsonNode row : snapshotFixture()) {
            if (excluded.contains(row.get("id").asText())) {
                continue;
            }
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
