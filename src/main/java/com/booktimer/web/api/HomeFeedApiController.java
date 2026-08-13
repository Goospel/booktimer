package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * <p>「책 뉴스」는 아직 꺼져 있다 — 수집기(네이버 뉴스 API)는 후속 PR이고, 그때까지
 * {@code newsEnabled=false}·{@code news=[]}로 미니앱이 탭 자체를 안 그린다(죽은 탭 금지).
 * SecurityConfig default-deny로 자동 인증.
 */
@RestController
public class HomeFeedApiController {

    /** 피드 창 — 7일이면 소셜 그래프가 작은 초기 사용자에게 빈 피드가 너무 잦다. */
    private static final Duration WINDOW = Duration.ofDays(14);

    /** 응답 상한. 미니앱은 미리보기 3줄 + 「더 보기」로 이 안에서 펼친다(페이지네이션 없음). */
    private static final int MAX_EVENTS = 30;

    private final CurrentUserService currentUserService;
    private final BookRepository bookRepository;
    private final Clock clock;

    public HomeFeedApiController(CurrentUserService currentUserService,
                                 BookRepository bookRepository,
                                 Clock clock) {
        this.currentUserService = currentUserService;
        this.bookRepository = bookRepository;
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
                false, List.of());
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

    /** @param bookTitle 내 어느 책의 기사인지 보여주는 라벨. 뉴스 점등 전까지 이 목록은 항상 빈다. */
    public record NewsItem(String title, String link, Instant publishedAt, String bookTitle) {
    }
}
