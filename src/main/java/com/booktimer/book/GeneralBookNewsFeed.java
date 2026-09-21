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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게스트(로그인 전) 「일반 책 뉴스」 — <b>운영자가 정한 고정 주제</b>로 구글 뉴스를 모아 메모리에 둔다.
 *
 * <p><b>사용자 입력은 어디에도 섞이지 않는다</b>(리뷰 차단 B-1): 사용자 완독 책의 제목·ISBN으로 모은
 * {@code book_news}를 익명에게 열면 계정 하나로 게스트 화면의 문구·기사 주제를 조종할 수 있었다. 그래서 의존은
 * RSS 클라이언트와 시계뿐이고(리포지토리 없음), 검색어는 코드 상수 {@link #QUERIES}뿐이다.
 *
 * <p>저장이 메모리인 이유: 재배포가 잦아도 기동 즉시 채우므로 비는 창이 수 초이고, 컨트롤러가 DB를 아예 안 본다.
 * 한 대(EC2) 전제 — 스케일아웃하면 테이블로 옮긴다.
 */
@Component
public class GeneralBookNewsFeed {

    private static final Logger log = LoggerFactory.getLogger(GeneralBookNewsFeed.class);

    /**
     * 응답 {@code bookTitle}로 나가는 배지 3종 — <b>미니앱과의 계약</b>이라 값 집합을 바꾸면 번들·심사가 붙는다.
     * 어느 배지를 다는지는 물어온 질의가 아니라 제목이 정한다({@link #labelOf}).
     */
    static final String LABEL_NEW = "신간";
    static final String LABEL_BEST = "베스트셀러";
    static final String LABEL_INDUSTRY = "출판계";
    /** 운영자 고정 검색어 — 라벨은 여기 없다. 검색어 품질은 2026-09-17 실측으로 골랐다. */
    static final List<String> QUERIES = List.of("신간 when:7d", "출판계 when:7d", "베스트셀러 도서 when:7d");
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
    /** ponytail: 2026-09-21 하루치(운영 25 + RSS 원본 85)로 맞춘 튜닝값 — 배포 후 묶임 로그로 재조정한다. */
    static final double SAME_EVENT_JACCARD = 0.2;
    /** 짧은 제목은 자카드로 묶지 않는다 — 2-gram 1개를 공유한 다른 사건이 0.2를 넘긴 실례가 있다. */
    static final int MIN_BIGRAMS = 6;
    /**
     * 자카드에 AND로 얹는 <b>절대 교집합 하한</b> — 비율만으로는 짧은 제목 둘이 조사·어미만 겹쳐도 임계값에 닿는다.
     *
     * <p>실제 반례: 「[신간] 과학적으로 옳다는 착각」 ↔ 「[신간] 아무렇지도 않다는 착각」 — 서로 다른 책인데
     * {@code 다는}·{@code 는착}·{@code 착각} <b>3개</b>만 겹치고 양쪽 2-gram이 9개씩이라 자카드가 정확히 0.200이다.
     * 정상 묶임은 훨씬 두껍다(2026-09-21 픽스처의 자카드 묶임 2쌍 모두 6개, 09-22 RSS 184건의 자카드 묶임 11쌍 최소 6개).
     * {@link #MIN_BIGRAMS}를 올리는 대안은 기각했다 — 「신간 소개하는 송길영 작가」(2-gram 8개) 같은 정상 묶임이 죽는다.
     */
    static final int MIN_SHARED_BIGRAMS = 4;
    /** 이 밑이면 WARN만 남기고 교체는 한다 — 얇지만 현재인 목록이 낡은 두꺼운 목록보다 정직하다. */
    static final int MIN_HEALTHY = 10;

    private final GoogleNewsRssClient newsClient;
    private final Clock clock;
    private final AtomicReference<List<Item>> snapshot = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    public GeneralBookNewsFeed(GoogleNewsRssClient newsClient, Clock clock) {
        this.newsClient = newsClient;
        this.clock = clock;
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

    /**
     * 새로고침 1회분 — 교체한 건수. 0이면 옛 스냅샷 유지(장애 격리), 킬스위치가 꺼졌으면 비운다.
     *
     * <p>{@link #MIN_HEALTHY} 미만이면 <b>WARN만 남기고 교체는 한다</b> — 얇아지는 원인은 대개 필터가 아니라
     * 질의 실패(읽기 5초 타임아웃)라, 필터를 풀어도 실패한 질의 몫은 돌아오지 않고 중복만 되살아난다.
     */
    public int collect() {
        if (!newsClient.isEnabled()) {
            snapshot.set(List.of());
            return 0;
        }
        List<List<NewsArticle>> byQuery = new ArrayList<>();
        for (String query : QUERIES) {
            byQuery.add(newsClient.search(query));
        }
        List<Item> merged = merge(byQuery, clock.instant());
        if (merged.isEmpty()) {
            return 0;
        }
        if (merged.size() < MIN_HEALTHY) {
            log.warn("일반 책 뉴스가 얇다 — {}건(하한 {}건). 질의 실패를 의심한다.", merged.size(), MIN_HEALTHY);
        }
        snapshot.set(merged);
        lastSuccess.set(clock.instant());
        return merged.size();
    }

    /**
     * 필터(https·발행일·제외어·<b>출처</b>) → 최신순 → link·제목 키 완전일치 제거 → <b>사건 묶기</b>(최신이 대표)
     * → 질의별 상한 → 전체 상한 → 제목으로 배지.
     *
     * <p><b>순서가 요점이다.</b> 예전에는 「질의별 상한 → 중복 제거」라, 한 질의의 최신 10건이 모두 한 사건이면
     * 그 질의 몫이 통째로 1건으로 쪼그라들고 그 뒤의 별개 기사는 상한에서 이미 잘려 있었다(645억 13건 실측).
     */
    static List<Item> merge(List<List<NewsArticle>> byQuery, Instant now) {
        record Cand(int query, NewsArticle article, EventKey key) {
        }
        Instant oldest = now.minus(MAX_AGE);
        int candidates = 0;
        List<String> droppedSources = new ArrayList<>();
        List<Cand> pool = new ArrayList<>();
        for (int q = 0; q < byQuery.size(); q++) {
            for (NewsArticle a : byQuery.get(q)) {
                candidates++;
                if (a.link() != null && a.link().startsWith(LINK_PREFIX)
                        && a.publishedAt() != null && !a.publishedAt().isBefore(oldest)
                        && !blocked(a.title())
                        && keepTrusted(a.source(), droppedSources)) {
                    pool.add(new Cand(q, a, EventKey.of(a.title())));
                }
            }
        }
        pool.sort(Comparator.comparing((Cand c) -> c.article().publishedAt()).reversed());

        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<Cand> kept = new ArrayList<>();
        List<String> merges = new ArrayList<>();
        for (Cand c : pool) {
            if (!seenLinks.add(c.article().link()) || !seenTitles.add(BookNewsMatcher.key(c.article().title()))) {
                continue;
            }
            // ponytail: O(n²)이지만 n ≤ 300이고 6시간에 한 번 돈다 — 색인을 만들 이유가 없다.
            Cand representative = kept.stream().filter(k -> k.key().sameEvent(c.key())).findFirst().orElse(null);
            if (representative != null) {
                merges.add(excerpt(representative.article().title()) + " ← " + excerpt(c.article().title()));
                continue;
            }
            kept.add(c);
        }

        int[] perQuery = new int[byQuery.size()];
        List<Item> merged = kept.stream()
                .filter(c -> ++perQuery[c.query()] <= MAX_PER_TOPIC)
                .limit(MAX_TOTAL)
                .map(c -> new Item(labelOf(c.article().title()), c.article()))
                .toList();
        // 배포 후 감시용 계측기 — 탈락 목록에 한글 매체나 국내 방송(「SBS Biz」류)이 보이면 출처 규칙이 과하고,
        // 묶임 쌍의 두 제목이 눈으로 다른 사건이면 자카드 임계값이 낮다. 둘 다 응답만 봐서는 알 수 없다(잘린 건 안 나온다).
        log.info("일반 책 뉴스 병합 — 후보 {} · 필터 통과 {} · 출처 탈락 {} {} · 사건 묶임 {} {} · 최종 {}",
                candidates, pool.size(), droppedSources.size(), droppedSources, merges.size(), merges, merged.size());
        return merged;
    }

    /** {@link #trustedSource}와 같은 판정인데, 탈락한 매체명을 모아 로그에 남긴다. */
    private static boolean keepTrusted(String source, List<String> droppedSources) {
        if (trustedSource(source)) {
            return true;
        }
        droppedSources.add(source);
        return false;
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

    private static String excerpt(String title) {
        return title.length() <= 20 ? title : title.substring(0, 20) + "…";
    }

    private static final Pattern LEADING_TAGS = Pattern.compile("^(\\[[^\\]]*\\]\\s*)+");
    private static final Pattern NOT_LETTER = Pattern.compile("[^가-힣A-Za-z]+");
    /** 3자리 이상 + 단위. {@code 1위}·{@code 4권}은 모든 순위 기사가 공유하고, 년·월·일은 연도가 다 걸려 뺀다. */
    private static final Pattern BIG_NUMBER = Pattern.compile("(\\d{3,})\\s?(억|만|명|권|종|부)");
    /** 어느 질의로 물어왔든 제목에 섞이는 말 — 남겨 두면 「베스트셀러」만 공유한 다른 사건이 묶인다. */
    private static final List<String> QUERY_WORDS = List.of("베스트셀러", "출판계", "신간", "도서", "출판");

    /**
     * 사건 키 — 문자 2-gram(앞머리 대괄호 태그·숫자·부호·질의어를 뗀 뒤)과 3자리+ 숫자·단위 토큰.
     *
     * <p>어절 토큰이 아니라 문자 2-gram인 이유: 조사와 복합명사(출판전산망 / 출판유통전산망 / ‘책 전산망’)가
     * 어절 단위로는 안 갈라진다. 숫자키를 OR로 얹는 이유: 예산·수치 사건은 제목 변형이 커서 자카드만으로는
     * 한 임계값에 다 안 걸리는데, 「645억」 공유 하나로 13건이 한 번에 묶인다(2026-09-21 실측).
     */
    record EventKey(Set<String> bigrams, Set<String> numbers) {

        static EventKey of(String title) {
            String s = LEADING_TAGS.matcher(BookNewsMatcher.clean(title)).replaceFirst("");
            s = NOT_LETTER.matcher(s).replaceAll("");
            for (String word : QUERY_WORDS) {
                s = s.replace(word, "");
            }
            s = s.toLowerCase(Locale.ROOT);
            Set<String> grams = new HashSet<>();
            for (int i = 0; i + 1 < s.length(); i++) {
                grams.add(s.substring(i, i + 2));
            }
            Set<String> numbers = new HashSet<>();
            Matcher m = BIG_NUMBER.matcher(title);
            while (m.find()) {
                numbers.add(m.group(1) + m.group(2));
            }
            return new EventKey(grams, numbers);
        }

        boolean sameEvent(EventKey other) {
            if (!Collections.disjoint(numbers, other.numbers)) {
                return true;
            }
            if (bigrams.size() < MIN_BIGRAMS || other.bigrams.size() < MIN_BIGRAMS) {
                return false;
            }
            long intersection = bigrams.stream().filter(other.bigrams::contains).count();
            if (intersection < MIN_SHARED_BIGRAMS) {
                return false;
            }
            double union = bigrams.size() + other.bigrams.size() - intersection;
            return intersection / union >= SAME_EVENT_JACCARD;
        }
    }

    private static final Pattern NEW_BOOK_TAG = Pattern.compile("\\[[^\\]]*(신간|새로 나온 책|새 책)[^\\]]*\\]");
    private static final Pattern BEST_TAG = Pattern.compile("\\[[^\\]]*베스트셀러[^\\]]*\\]");
    private static final Pattern RANKING = Pattern.compile("\\d+위|순위|주간|월간|연간");

    /**
     * 배지는 제목 내용이 정한다 — 물어온 질의가 아니라.
     *
     * <p>질의 라벨을 쓰면 세 질의에 다 걸린 기사가 아무 배지나 달고 나간다(출판전산망 기사 4건이
     * 「베스트셀러」로 나간 실측). 언론 관행인 <b>대괄호 태그</b>를 1순위로 보고, 태그가 없는 「신간」 낱말은
     * 신간으로 치지 않는다 — 「신간 등록률」·「신간 소개하는…」이 바로 그 오라벨의 원인이었다.
     * 「출판계」는 참인 포괄 라벨이라 나머지가 전부 여기로 간다(책·출판 업계 기사는 다 여기 속한다).
     */
    static String labelOf(String title) {
        if (NEW_BOOK_TAG.matcher(title).find()) {
            return LABEL_NEW;
        }
        if (BEST_TAG.matcher(title).find() || (title.contains(LABEL_BEST) && RANKING.matcher(title).find())) {
            return LABEL_BEST;
        }
        return LABEL_INDUSTRY;
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
