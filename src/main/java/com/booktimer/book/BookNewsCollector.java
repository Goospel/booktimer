package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 완독한 책의 관련 뉴스를 새벽에 1회 모아 캐시({@link BookNews})에 채우는 배치.
 *
 * <p><b>왜 배치·캐시인가</b>: 홈 진입마다 외부 API를 부르면 피드 응답이 구글에 인질로 잡힌다. 뉴스는
 * 신선도 요구가 낮아 하루 1회면 충분하다 — 대신 새로 완독한 책의 기사는 다음 새벽부터 뜬다(의도된 지연).
 *
 * <p><b>가드</b>: {@code @ConditionalOnProperty}가 아니라 메서드 첫 줄의 {@code isEnabled()} 체크다
 * (킬스위치 {@code booktimer.news.enabled}). 빈이 항상 등록되므로 테스트에서 끄고 켜기가 쉽다.
 *
 * <p>{@code @Transactional}로 감싸지 않는다 — 후보마다 외부 HTTP를 부르는데 트랜잭션을 연 채 네트워크를
 * 기다리면 DB 커넥션을 오래 점유한다({@link BookCatalogBackfillService}와 동일 사유). 쓰기는 짧은
 * 리포지토리 호출로만 한다.
 */
@Component
public class BookNewsCollector {

    private static final Logger log = LoggerFactory.getLogger(BookNewsCollector.class);

    /** 책 한 권당 보관할 기사 수 — 홈에서 미리보기로 몇 줄만 보이므로 3건이면 충분하다. */
    private static final int MAX_PER_ISBN = 3;

    private final BookRepository bookRepository;
    private final BookNewsRepository bookNewsRepository;
    private final GoogleNewsRssClient newsClient;

    public BookNewsCollector(BookRepository bookRepository,
                             BookNewsRepository bookNewsRepository,
                             GoogleNewsRssClient newsClient) {
        this.bookRepository = bookRepository;
        this.bookNewsRepository = bookNewsRepository;
        this.newsClient = newsClient;
    }

    /** 매일 KST 04:30 — 트래픽이 가장 낮은 시간대(초 분 시 일 월 요일). 타임존 명시는 넛지 배치 선례와 동일. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void runNightlyCollection() {
        collect();
    }

    /**
     * 수집 1회분. 대상은 <b>완독 + isbn13·저자가 모두 있는 책의 isbn 합집합</b>이다 —
     * 저자가 없으면 매칭 AND를 만들 수 없어 오탐을 못 막고, isbn이 없으면 캐시 키가 없다.
     * PRIVATE 완독 책도 대상이다(뉴스는 책 자체에 대한 공개 정보고, 노출은 그 책을 완독한 본인에게만 된다).
     */
    public void collect() {
        if (!newsClient.isEnabled()) {
            return;
        }
        List<BookNewsTarget> targets = bookRepository.findNewsCollectionTargets();
        int saved = 0;
        for (BookNewsTarget target : targets) {
            try {
                saved += collectOne(target);
            } catch (Exception e) {
                // 한 책의 실패(응답 이상·길이 초과 등)가 나머지 수집을 멈추지 않게 격리한다.
                log.warn("책 뉴스 수집 실패 — isbn13={}: {}", target.getIsbn13(), e.toString());
            }
        }
        log.info("책 뉴스 수집 — 대상 {}종, 저장 {}건", targets.size(), saved);
    }

    /**
     * isbn 하나: API 1회 → 매처 필터 → 상위 {@value #MAX_PER_ISBN}건을 delete-then-insert로 교체.
     * 교체라서 무한 증식이 없고 갱신이 한 동작이다.
     *
     * <p>ponytail: 구글이 일시 장애면 {@code search}가 빈 목록을 주고 그날의 캐시가 비워진다(다음
     * 새벽에 복구). 장애와 "기사 없음"을 구분하려면 반환 타입을 Optional로 바꿔야 하는데, 하루치
     * 캐시 공백에 그 값어치가 없다 — 오래 비어 보이면 그때 구분한다.
     */
    private int collectOne(BookNewsTarget target) {
        List<GoogleNewsRssClient.NewsArticle> matched =
                newsClient.search(GoogleNewsRssClient.buildQuery(target.getTitle(), target.getAuthor()))
                        .stream()
                        .filter(a -> BookNewsMatcher.matches(a.title(),
                                target.getTitle(), target.getAuthor()))
                        .limit(MAX_PER_ISBN)
                        .toList();

        bookNewsRepository.deleteByIsbn13(target.getIsbn13());
        if (matched.isEmpty()) {
            return 0;
        }
        bookNewsRepository.saveAll(matched.stream()
                .map(a -> BookNews.of(target.getIsbn13(), a.title(), a.link(), a.publishedAt(), a.source()))
                .toList());
        return matched.size();
    }
}
