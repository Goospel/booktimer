package com.booktimer.book;

import com.booktimer.book.GoogleNewsRssClient.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게스트(로그인 전) 「일반 책 뉴스」 — <b>운영자가 정한 고정 주제</b>로 구글 뉴스를 모아 메모리에 둔다.
 *
 * <p><b>사용자 입력은 어디에도 섞이지 않는다</b>(리뷰 차단 B-1): 사용자 완독 책의 제목·ISBN으로 모은
 * {@code book_news}를 익명에게 열면 계정 하나로 게스트 화면의 문구·기사 주제를 조종할 수 있었다. 그래서 의존은
 * RSS 클라이언트와 시계뿐이고(리포지토리 없음), 검색어는 코드 상수 {@link #TOPICS}뿐이다.
 *
 * <p>저장이 메모리인 이유: 재배포가 잦아도 기동 즉시 채우므로 비는 창이 수 초이고, 컨트롤러가 DB를 아예 안 본다.
 * 한 대(EC2) 전제 — 스케일아웃하면 테이블로 옮긴다.
 */
@Component
public class GeneralBookNewsFeed {

    private static final Logger log = LoggerFactory.getLogger(GeneralBookNewsFeed.class);

    /** 운영자 고정 주제 — 라벨이 곧 응답 {@code bookTitle}. 검색어 품질은 2026-09-17 실측으로 골랐다. */
    static final List<Topic> TOPICS = List.of(
            new Topic("신간", "신간 when:7d"),
            new Topic("출판계", "출판계 when:7d"),
            new Topic("베스트셀러", "베스트셀러 도서 when:7d"));
    /**
     * 첫 화면·심사 화면이라 책 기사여도 사건 제목은 뺀다. 짧게, 명백한 것만 — 사건어 8 + 도박어 4.
     *
     * <p>「도박」·「배당」·「토토」는 <b>넣지 않는다</b> — 『도박 묵시록』·「배당주 투자」·「이웃집 토토로」에 걸린다.
     */
    static final List<String> BLOCKED_TITLE_WORDS = List.of(
            "사망", "숨져", "살인", "성폭행", "성추행", "마약", "자살", "시신",
            "카지노", "슬롯", "바카라", "먹튀");
    /** 매체가 아니라 블로그 플랫폼 — 개인 글·SEO·일일 목록이 뉴스로 온다. 스팸 이름 차단목록이 아니다(그건 사후약방문). */
    static final List<String> BLOCKED_SOURCES = List.of("브런치");
    static final int MAX_PER_TOPIC = 10;
    static final int MAX_TOTAL = 30;
    /** {@code when:7d}는 비공식 연산자라 조용히 죽을 수 있다 — 서버가 한 번 더 막는다. */
    static final Duration MAX_AGE = Duration.ofDays(14);
    /** 채운 스냅샷을 다시 수집하는 주기. 아직 못 채웠으면 점검 주기(5분)마다 재시도한다. */
    static final Duration REFRESH_INTERVAL = Duration.ofHours(6);
    /** 구글 리다이렉트 링크만 — 응답이 익명 공개고 클라이언트가 {@code window.open} 폴백을 쓴다. */
    static final String LINK_PREFIX = "https://news.google.com/";

    private final GoogleNewsRssClient newsClient;
    private final Clock clock;
    private final AtomicReference<List<Item>> snapshot = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    public GeneralBookNewsFeed(GoogleNewsRssClient newsClient, Clock clock) {
        this.newsClient = newsClient;
        this.clock = clock;
    }

    public record Topic(String label, String query) {
    }

    public record Item(String topic, NewsArticle article) {
    }

    /**
     * 기동 직후부터 5분마다 점검하고 {@link #isDue()}일 때만 수집한다 — 채운 뒤로는 6시간마다, 아직 못 채웠으면(기동 시
     * 첫 수집 실패 포함) 5분마다 재시도한다. 테스트 컨텍스트는 initial-delay를 하루로 밀어 실호출을 막는다.
     */
    @Scheduled(initialDelayString = "${booktimer.news.general.initial-delay-ms:0}",
            fixedDelayString = "${booktimer.news.general.check-ms:300000}")
    public void refresh() {
        if (!isDue()) {
            return;
        }
        long started = System.currentTimeMillis();
        int saved = collect();
        log.info("일반 책 뉴스 새로고침 — 저장 {}건 ({} ms)", saved, System.currentTimeMillis() - started);
    }

    /** 새로고침 1회분 — 교체한 건수. 0이면 옛 스냅샷 유지(장애 격리), 킬스위치가 꺼졌으면 비운다. */
    public int collect() {
        if (!newsClient.isEnabled()) {
            snapshot.set(List.of());
            return 0;
        }
        Map<String, List<NewsArticle>> byTopic = new LinkedHashMap<>();
        for (Topic topic : TOPICS) {
            byTopic.put(topic.label(), newsClient.search(topic.query()));
        }
        List<Item> merged = merge(byTopic, clock.instant());
        if (merged.isEmpty()) {
            return 0;
        }
        snapshot.set(merged);
        lastSuccess.set(clock.instant());
        return merged.size();
    }

    /** 필터(https·발행일·제외어·출처) → 주제별 상한 → 합쳐 최신순 → link·제목 키 중복 제거 → 전체 상한. */
    static List<Item> merge(Map<String, List<NewsArticle>> byTopic, Instant now) {
        Comparator<Item> newestFirst = Comparator.comparing((Item i) -> i.article().publishedAt()).reversed();
        Instant oldest = now.minus(MAX_AGE);
        List<Item> all = new ArrayList<>();
        byTopic.forEach((label, articles) -> articles.stream()
                .filter(a -> a.link() != null && a.link().startsWith(LINK_PREFIX))
                .filter(a -> a.publishedAt() != null && !a.publishedAt().isBefore(oldest))
                .filter(a -> !blocked(a.title()))
                .filter(a -> trustedSource(a.source()))
                .map(a -> new Item(label, a))
                .sorted(newestFirst)
                .limit(MAX_PER_TOPIC)
                .forEach(all::add));
        all.sort(newestFirst);
        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        return all.stream()
                .filter(i -> seenLinks.add(i.article().link()) && seenTitles.add(BookNewsMatcher.key(i.article().title())))
                .limit(MAX_TOTAL)
                .toList();
    }

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern ACRONYM = Pattern.compile("[A-Z0-9]{2,5}");
    private static final Pattern HOST_LIKE = Pattern.compile("[\\w-]+(?:\\.[\\w-]+)*\\.([A-Za-z]{2,})");

    /**
     * 국내 매체인가 — 구글 RSS {@code <source>}는 발행사가 등록한 표시명이 있으면 그것을, 없으면 <b>호스트명</b>을 준다.
     * 국내 중소 매체는 호스트명({@code youthassembly.kr})으로, 해외 스팸 농장은 등록 표시명(「Histoire pour tous」)으로 온다.
     *
     * <p>그래서 한글 표시명·약어(YTN)·{@code .kr}/일반도메인 호스트는 통과시키고, 설명형 라틴 표시명과 외국
     * 국가도메인({@code .fr}·{@code .vn})은 뺀다. 2026-09-21 실측 스팸 11/11 탈락 · 라틴 표기 국내 매체 0 탈락.
     * 이름 차단목록이 주 방어가 아닌 이유: 한 주에 스팸 출처 이름이 셋 갈린다(늘 한 발 늦는다).
     *
     * <p>null·빈 값은 <b>통과</b> — RSS 형식이 드리프트해도 스냅샷이 통째로 0건이 되지 않게.
     * 알려진 오탐 계급: 한글 없는 다단어 국내 매체 표시명(「SBS Biz」류). 실측 110건에 0회 — 배포 후 로그로 본다.
     */
    static boolean trustedSource(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        String s = source.strip();
        if (BLOCKED_SOURCES.contains(s)) {
            return false;
        }
        if (HANGUL.matcher(s).find() || ACRONYM.matcher(s).matches()) {
            return true;
        }
        Matcher host = HOST_LIKE.matcher(s);
        if (!host.matches()) {
            return false;
        }
        String tld = host.group(1).toLowerCase(Locale.ROOT);
        return tld.equals("kr") || tld.length() >= 3;
    }

    private static boolean blocked(String title) {
        String cleaned = BookNewsMatcher.clean(title);
        return cleaned == null || BLOCKED_TITLE_WORDS.stream().anyMatch(cleaned::contains);
    }

    /**
     * 수집할 차례인가 — 아직 한 번도 채우지 못했으면(기동 직후·첫 수집 실패) 매 점검(5분)마다,
     * 채운 뒤로는 마지막 성공 후 {@link #REFRESH_INTERVAL}마다. 스냅샷은 성공 뒤 비는 일이 없다(킬스위치 OFF는 수집이 호출 없이 0건).
     */
    boolean isDue() {
        Instant success = lastSuccess.get();
        return success == null || !clock.instant().isBefore(success.plus(REFRESH_INTERVAL));
    }

    public List<Item> snapshot() {
        return snapshot.get();
    }

    /**
     * 테스트 seam — {@code RateLimitService.clearForTest} 관례.
     *
     * <p>⚠️ <b>운영 코드에서 호출 금지.</b> 사용자 입력이 여기로 들어가면 B-1(사용자 입력이 익명 게스트 화면에 뜸)의
     * 새 문이 된다 — 스냅샷을 채우는 운영 경로는 {@link #collect()} 하나뿐이어야 한다.
     */
    public void replaceForTest(List<Item> items) {
        snapshot.set(List.copyOf(items));
    }
}
