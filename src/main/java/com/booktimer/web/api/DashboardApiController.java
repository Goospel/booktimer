package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.garden.GardenService;
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
 * 404 = bookId가 '있으나' 소유 아님·미존재(IDOR 마스킹) — bookId를 아예 안 주면 책 미지정 시작 허용(발견 1).
 * 409 = 중복 start / 무세션 stop. 403 = CSRF 누락.
 */
@RestController
public class DashboardApiController {

    /** 대시보드 슬롯머신 로테이션에 실어 보낼 격언 최대 개수(셔플 후 상한). */
    private static final int QUOTE_ROTATION_MAX = 10;

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
        List<QuoteDto> quotes = quoteService.randomList(QUOTE_ROTATION_MAX).stream()
                .map(q -> new QuoteDto(q.getText(), q.getAuthor()))
                .toList();

        return new DashboardResponse(
                live.nickname(), live.loginId(),
                user.getProfileCharacterCode(),
                live.remainingSeconds(), live.carriedDebtSeconds(),
                live.todayGoalSeconds(), live.carryover(),
                live.hasActiveSession(), live.activeStartedAt(),
                live.activeBookTitle(), live.activeBookTotalSeconds(),
                toOptions(live.readingBooks()), toOptions(live.finishedBooks()),
                live.recentBookId(),
                toGraphDto(graph),
                garden,
                quotes,
                user.isEmailVerified());
    }

    @PostMapping("/api/sessions/start")
    public ResponseEntity<TimerState> start(@RequestBody StartSessionRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        // bookId를 아예 안 주면 책 미지정 세션(발견 1 — 시작을 책 선택으로 가로막지 않음).
        // bookId가 '있는데' 소유 아님/미존재면 404(IDOR 마스킹) — 이 경계는 그대로 유지.
        Book book = null;
        if (req.bookId() != null) {
            book = bookRepository.findByIdAndUser(req.bookId(), user)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
        }
        try {
            sessionService.start(user, clock.instant(), book);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 측정이 있습니다");
        }
        return ResponseEntity.ok(buildTimerState(user));
    }

    @PostMapping("/api/sessions/stop")
    public ResponseEntity<StopResponse> stop(Principal principal) {
        User user = currentUserService.resolve(principal);
        try {
            sessionService.stop(user, clock.instant());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        // 측정 종료는 잔디가 변하는 바로 그 순간 — 방금 확정된 세션이 반영된 graph를 동봉해
        // 클라이언트가 새로고침 없이 잔디·연속일을 즉시 갱신하게 한다(start는 잔디 불변이라 TimerState 그대로).
        TimerState timer = buildTimerState(user);
        ContributionGraphDto graph = toGraphDto(contributionService.contributionGraph(user));
        return ResponseEntity.ok(new StopResponse(timer, graph));
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
            String profileCharacterCode,
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
            List<QuoteDto> quotes,
            boolean emailVerified
    ) {}

    /** stop 응답 — 타이머 + 방금 확정된 세션이 반영된 잔디(측정 종료 즉시 잔디 갱신용). */
    public record StopResponse(TimerState timer, ContributionGraphDto graph) {}

    /** start 응답 — 라이브 부분집합(graph/garden/quote/emailVerified 제외). 잔디는 stop 때만 변함. */
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
