package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.garden.GardenService;
import com.booktimer.quote.Quote;
import com.booktimer.quote.QuoteService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ContributionDay;
import com.booktimer.session.ContributionGraph;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import com.booktimer.web.DashboardModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 대시보드 Vue 섬용 JSON API.
 *
 * <p>GET /api/dashboard — 페이지 최초 로드 시 필요한 모든 상태(타이머·잔디·정원·격언·이메일인증)를 단일 응답으로.
 * POST /api/sessions/start|stop — start/stop 뮤테이션. 응답은 라이브 부분집합({@link TimerState})만.
 *
 * <p>에러 계약(상태코드만 — {@code GlobalExceptionHandler}가 ResponseStatusException을 잡아 코드 보존):
 * 404 = 책 미선택·IDOR·null bookId(IDOR 마스킹). 409 = 중복 start / 무세션 stop. 403 = CSRF 누락.
 */
@RestController
public class DashboardApiController {

    private final CurrentUserService currentUserService;
    private final DashboardModel dashboardModel;
    private final ReadingContributionService contributionService;
    private final GardenService gardenService;
    private final QuoteService quoteService;
    private final ReadingSessionService sessionService;
    private final BookRepository bookRepository;
    private final Clock clock;

    public DashboardApiController(CurrentUserService currentUserService,
                                  DashboardModel dashboardModel,
                                  ReadingContributionService contributionService,
                                  GardenService gardenService,
                                  QuoteService quoteService,
                                  ReadingSessionService sessionService,
                                  BookRepository bookRepository,
                                  Clock clock) {
        this.currentUserService = currentUserService;
        this.dashboardModel = dashboardModel;
        this.contributionService = contributionService;
        this.gardenService = gardenService;
        this.quoteService = quoteService;
        this.sessionService = sessionService;
        this.bookRepository = bookRepository;
        this.clock = clock;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse get(Principal principal) {
        User user = currentUserService.resolve(principal);
        DashboardModel.LiveState live = dashboardModel.computeLive(user);
        ContributionGraph graph = contributionService.contributionGraph(user);
        GardenApiResponse.CatalogDto garden = GardenApiResponse.catalogOf(gardenService.view(user));
        Quote quote = quoteService.random();

        return new DashboardResponse(
                live.nickname(), live.loginId(),
                live.remainingSeconds(), live.carriedDebtSeconds(),
                live.todayGoalSeconds(), live.carryover(),
                live.hasActiveSession(), live.activeStartedAt(),
                live.activeBookTitle(), live.activeBookTotalSeconds(),
                toOptions(live.readingBooks()), toOptions(live.finishedBooks()),
                live.recentBookId(),
                toGraphDto(graph),
                garden,
                new QuoteDto(quote.getText(), quote.getAuthor()),
                user.isEmailVerified());
    }

    @PostMapping("/api/sessions/start")
    public ResponseEntity<TimerState> start(@RequestBody StartSessionRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        Book book = (req.bookId() == null)
                ? null
                : bookRepository.findByIdAndUser(req.bookId(), user).orElse(null);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다");
        }
        try {
            sessionService.start(user, clock.instant(), book);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 측정이 있습니다");
        }
        return ResponseEntity.ok(buildTimerState(user));
    }

    @PostMapping("/api/sessions/stop")
    public ResponseEntity<TimerState> stop(Principal principal) {
        User user = currentUserService.resolve(principal);
        try {
            sessionService.stop(user, clock.instant());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        return ResponseEntity.ok(buildTimerState(user));
    }

    private TimerState buildTimerState(User user) {
        DashboardModel.LiveState live = dashboardModel.computeLive(user);
        return new TimerState(
                live.remainingSeconds(), live.carriedDebtSeconds(),
                live.todayGoalSeconds(), live.carryover(),
                live.hasActiveSession(), live.activeStartedAt(),
                live.activeBookTitle(), live.activeBookTotalSeconds(),
                toOptions(live.readingBooks()), toOptions(live.finishedBooks()),
                live.recentBookId());
    }

    private static List<BookOption> toOptions(List<Book> books) {
        return books.stream().map(b -> new BookOption(b.getId(), b.getTitle())).toList();
    }

    private static ContributionGraphDto toGraphDto(ContributionGraph g) {
        return new ContributionGraphDto(
                g.weeks(), g.monthLabels(),
                g.totalSeconds(), g.activeDays(), g.currentStreak(),
                g.growthStage().name(), g.growthStage().emoji(), g.growthStage().label());
    }

    // ── DTO records ──────────────────────────────────────────────────────────

    public record DashboardResponse(
            String nickname,
            String loginId,
            long remainingSeconds,
            long carriedDebtSeconds,
            long todayGoalSeconds,
            boolean carryover,
            boolean hasActiveSession,
            Instant activeStartedAt,
            String activeBookTitle,
            long activeBookTotalSeconds,
            List<BookOption> readingBooks,
            List<BookOption> finishedBooks,
            Long recentBookId,
            ContributionGraphDto graph,
            GardenApiResponse.CatalogDto garden,
            QuoteDto quote,
            boolean emailVerified
    ) {}

    /** start/stop 응답 — 라이브 부분집합(graph/garden/quote/emailVerified 제외). */
    public record TimerState(
            long remainingSeconds,
            long carriedDebtSeconds,
            long todayGoalSeconds,
            boolean carryover,
            boolean hasActiveSession,
            Instant activeStartedAt,
            String activeBookTitle,
            long activeBookTotalSeconds,
            List<BookOption> readingBooks,
            List<BookOption> finishedBooks,
            Long recentBookId
    ) {}

    public record BookOption(Long id, String title) {}

    public record QuoteDto(String text, String author) {}

    public record StartSessionRequest(Long bookId) {}

    /** ContributionGraph 래퍼 — growthStage를 name+emoji+label 삼중화해 DTO-as-contract를 보장. */
    public record ContributionGraphDto(
            List<List<ContributionDay>> weeks,
            List<ContributionGraph.MonthLabel> monthLabels,
            long totalSeconds,
            int activeDays,
            int currentStreak,
            String growthStageName,
            String growthStageEmoji,
            String growthStageLabel
    ) {}
}
