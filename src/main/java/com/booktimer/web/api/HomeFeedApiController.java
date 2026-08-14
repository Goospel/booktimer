package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookNews;
import com.booktimer.book.BookNewsRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.GoogleNewsRssClient;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 미니앱 홈 피드 박스용 JSON API — GET /api/home-feed.
 *
 * <p>대시보드({@code /api/dashboard})에 동봉하지 않고 따로 둔다: 히어로(타이머) 렌더가 피드 쿼리에
 * 인질로 잡히지 않고, 계약이 독립이라 서버·미니앱 PR이 묶이지 않는다.
 *
 * <p>「소식」 = 팔로우한 사람의 PUBLIC 책 활동(읽기 시작·완독). 노출 게이트는 전부 쿼리에 있고
 * ({@link BookRepository#feedFinished}·{@link BookRepository#feedStarted}) 여기선 두 목록을 최신순으로
 * 합쳐 상한까지 자르기만 한다 — JPQL에 union이 없어 쿼리 2개 + 인메모리 병합이 가장 단순하고
 * 충분하다(창 14일 · 팔로우 규모 소형).
 *
 * <p>「책 뉴스」 = 내가 완독한 책(isbn13)의 기사. 실제 수집은 새벽 배치({@code BookNewsCollector})가 하고
 * 여기선 캐시를 읽기만 한다 — 요청 경로에서 외부 API를 부르지 않는다. 노출 게이트는 <b>서버의
 * {@code newsEnabled}</b>(킬스위치 {@code booktimer.news.enabled}, 기본 켜짐): 끄면 false + 빈 목록이라
 * 미니앱이 탭 자체를 안 그린다(죽은 탭 금지) — 구글 RSS 형식이 바뀌어도 미니앱 재배포 없이 끌 수 있다.
 * SecurityConfig default-deny로 자동 인증.
 */
@RestController
public class HomeFeedApiController {

    /** 피드 창 — 7일이면 소셜 그래프가 작은 초기 사용자에게 빈 피드가 너무 잦다. */
    private static final Duration WINDOW = Duration.ofDays(14);

    /** 응답 상한. 미니앱은 미리보기 3줄 + 「더 보기」로 이 안에서 펼친다(페이지네이션 없음). */
    private static final int MAX_EVENTS = 30;

    /** 뉴스 응답 상한. 소식과 같은 이유(미리보기 + 「더 보기」로 이 안에서 펼친다). */
    private static final int MAX_NEWS = 30;

    private final CurrentUserService currentUserService;
    private final BookRepository bookRepository;
    private final BookNewsRepository bookNewsRepository;
    private final GoogleNewsRssClient newsClient;
    private final Clock clock;

    public HomeFeedApiController(CurrentUserService currentUserService,
                                 BookRepository bookRepository,
                                 BookNewsRepository bookNewsRepository,
                                 GoogleNewsRssClient newsClient,
                                 Clock clock) {
        this.currentUserService = currentUserService;
        this.bookRepository = bookRepository;
        this.bookNewsRepository = bookNewsRepository;
        this.newsClient = newsClient;
        this.clock = clock;
    }

    @GetMapping("/api/home-feed")
    public HomeFeedResponse homeFeed(Principal principal) {
        User viewer = currentUserService.resolve(principal);
        Instant cutoff = clock.instant().minus(WINDOW);

        List<SocialEvent> events = new ArrayList<>();
        for (Book book : bookRepository.feedFinished(viewer, cutoff)) {
            events.add(event(book, "FINISHED", book.getFinishedAt()));
        }
        for (Book book : bookRepository.feedStarted(viewer, cutoff)) {
            events.add(event(book, "STARTED", book.getStartedReadingAt()));
        }
        events.sort(Comparator.comparing(SocialEvent::occurredAt).reversed());

        return new HomeFeedResponse(
                events.size() > MAX_EVENTS ? List.copyOf(events.subList(0, MAX_EVENTS)) : events,
                newsClient.isEnabled(), newsFor(viewer));
    }

    /**
     * 내 완독 책(isbn13)의 캐시된 기사 — 발행 최신순, 상한 {@value #MAX_NEWS}건.
     *
     * <p>게이트가 꺼져 있거나 완독 책이 없으면 쿼리 없이 빈 목록. isbn → 내 책 제목 맵이
     * {@code bookTitle} 라벨("내 어느 책의 기사인가")을 만든다 — 같은 isbn을 여러 번 완독 등록했으면
     * 먼저 만난 제목 하나를 쓴다(라벨일 뿐이라 어느 쪽이든 같은 책이다).
     */
    private List<NewsItem> newsFor(User viewer) {
        if (!newsClient.isEnabled()) {
            return List.of();
        }
        Map<String, String> titleByIsbn = new HashMap<>();
        for (Book book : bookRepository.findFinishedWithIsbn(viewer)) {
            titleByIsbn.putIfAbsent(book.getIsbn13(), book.getTitle());
        }
        if (titleByIsbn.isEmpty()) {
            return List.of();
        }
        List<BookNews> cached = bookNewsRepository.findByIsbn13InOrderByPublishedAtDesc(
                titleByIsbn.keySet(), PageRequest.of(0, MAX_NEWS));
        return cached.stream()
                .map(n -> new NewsItem(n.getTitle(), n.getLink(), n.getPublishedAt(),
                        titleByIsbn.get(n.getIsbn13()), n.getSource()))
                .toList();
    }

    private static SocialEvent event(Book book, String type, Instant occurredAt) {
        return new SocialEvent(book.getUser().getLoginId(), book.getUser().getNickname(),
                book.getTitle(), type, occurredAt);
    }

    public record HomeFeedResponse(List<SocialEvent> social, boolean newsEnabled, List<NewsItem> news) {
    }

    /** @param type "STARTED" | "FINISHED" — 미니앱이 문구("읽기 시작했어요"/"완독했어요")로 옮긴다. */
    public record SocialEvent(String loginId, String nickname, String bookTitle,
                              String type, Instant occurredAt) {
    }

    /**
     * @param bookTitle 내 어느 책의 기사인지 보여주는 라벨
     * @param source    매체명(구글 뉴스 {@code <source>}). 응답에 없었으면 null — 미니앱이 라벨을 생략한다
     */
    public record NewsItem(String title, String link, Instant publishedAt, String bookTitle,
                           String source) {
    }
}
