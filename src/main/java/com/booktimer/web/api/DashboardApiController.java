package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.garden.GardenService;
import com.booktimer.quote.QuoteService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ContributionDay;
import com.booktimer.session.ContributionGraph;
import com.booktimer.session.GoalWaiverService;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import com.booktimer.web.DashboardModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
 * <p>GET /api/dashboard — 페이지 최초 로드 시 필요한 모든 상태(타이머·잔디·서재·격언·이메일인증)를 단일 응답으로.
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
    private final ReadingSessionRepository sessionRepository;
    private final BookRepository bookRepository;
    private final GoalWaiverService goalWaiverService;
    private final Clock clock;

    public DashboardApiController(CurrentUserService currentUserService,
                                  DashboardModel dashboardModel,
                                  ReadingContributionService contributionService,
                                  GardenService gardenService,
                                  QuoteService quoteService,
                                  ReadingSessionService sessionService,
                                  ReadingSessionRepository sessionRepository,
                                  BookRepository bookRepository,
                                  GoalWaiverService goalWaiverService,
                                  Clock clock) {
        this.currentUserService = currentUserService;
        this.dashboardModel = dashboardModel;
        this.contributionService = contributionService;
        this.gardenService = gardenService;
        this.quoteService = quoteService;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.bookRepository = bookRepository;
        this.goalWaiverService = goalWaiverService;
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
                toOptions(live.wantToReadBooks()),
                live.recentBookId(),
                toGraphDto(graph),
                garden,
                quotes,
                user.isEmailVerified(),
                goalWaiverService.availableFor(user));
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
        ReadingSession stopped;
        try {
            stopped = sessionService.stop(user, clock.instant());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        // 측정 종료는 잔디가 변하는 바로 그 순간 — 방금 확정된 세션이 반영된 graph를 동봉해
        // 클라이언트가 새로고침 없이 잔디·연속일을 즉시 갱신하게 한다(start는 잔디 불변이라 TimerState 그대로).
        TimerState timer = buildTimerState(user);
        ContributionGraphDto graph = toGraphDto(contributionService.contributionGraph(user));
        // sessionId + untagged: 책 없이 시작한 세션(book==null)이면 종료 후 "무슨 책?" 태깅 시트를 띄운다(발견 1).
        // getBook()==null은 lazy 프록시를 초기화하지 않는 참조 비교라 트랜잭션 밖에서도 안전(null 연관=실제 null).
        boolean untagged = stopped.getBook() == null;
        // 첫 완료 축하 — 방금 종료로 완료 세션 수가 '정확히 1'이 된 순간에만 참(2번째부터는 거짓).
        // 소유자 스코프 count라 남의 기록은 섞이지 않고, 수동 기록이 선행돼 있으면 자연히 2 이상이 된다.
        boolean firstCompletedSession = sessionRepository.countByUserAndEndedAtIsNotNull(user) == 1;
        return ResponseEntity.ok(new StopResponse(stopped.getId(), untagged, firstCompletedSession, timer, graph));
    }

    /**
     * 종료 후 태깅 — 책 없이 측정한 세션에 나중에 책을 연결한다(발견 1).
     *
     * <p>IDOR 이중 방어: 책은 {@code findByIdAndUser}로 소유 검증(남의 책이면 404), 세션은 서비스가
     * {@code findByIdAndUser}로 소유 검증(남의 세션이면 404 마스킹). 이미 책이 지정된 세션 재태깅은 409.
     */
    @PostMapping("/api/sessions/{id}/tag-book")
    public ResponseEntity<TagBookResponse> tagBook(@PathVariable("id") Long id,
                                                   @RequestBody TagBookRequest req,
                                                   Principal principal) {
        User user = currentUserService.resolve(principal);
        Book book = bookRepository.findByIdAndUser(req.bookId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다")); // 책 IDOR
        try {
            ReadingSession tagged = sessionService.tagBook(user, id, book);
            return ResponseEntity.ok(new TagBookResponse(tagged.getId(), book.getTitle()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "측정을 찾을 수 없습니다"); // 세션 IDOR 마스킹
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 책이 지정된 측정입니다");
        }
    }

    private TimerState buildTimerState(User user) {
        return TimerState.of(dashboardModel.computeLive(user), goalWaiverService.availableFor(user));
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
            List<BookOption> wantToReadBooks,
            Long recentBookId,
            ContributionGraphDto graph,
            GardenApiResponse.CatalogDto garden,
            List<QuoteDto> quotes,
            boolean emailVerified,
            /** 리워드 광고로 밀린 하루를 지울 수 있는지 — 미니앱 홈 버튼 노출 조건(웹에는 버튼이 없다). */
            boolean debtWaiverAvailable
    ) {}

    /**
     * stop 응답 — 방금 종료된 세션의 id·미태깅 여부 + 타이머 + 잔디(측정 종료 즉시 잔디 갱신용).
     * {@code untagged}이면 클라이언트가 "무슨 책?" 태깅 시트를 띄우고 {@code sessionId}로 태깅 요청한다(발견 1).
     *
     * @param firstCompletedSession 이번 종료가 이 사용자의 <b>첫 완료 기록</b>인지 — 미니앱이 축하 배너와
     *                              잔디 하이라이트를 띄우는 스위치다. 잔디는 1초만 읽어도 점등되는데
     *                              미리보기가 폴드 아래라 첫 보상을 아무도 보지 못했다(운영 실측 2026-08-13)
     */
    public record StopResponse(Long sessionId, boolean untagged, boolean firstCompletedSession,
                               TimerState timer, ContributionGraphDto graph) {}

    public record TagBookRequest(Long bookId) {}

    /** tag-book 응답 — 어느 세션에 어떤 책을 붙였는지 확인용. */
    public record TagBookResponse(Long sessionId, String bookTitle) {}

    /**
     * start 응답 — 라이브 부분집합(graph/garden/quote/emailVerified 제외). 잔디는 stop 때만 변함.
     *
     * @param debtWaiverAvailable 리워드 광고로 밀린 하루를 지울 수 있는지(미니앱 버튼 노출 조건).
     *                            start/stop/waive 응답에 함께 실려 버튼 노출·숨김이 재조회 없이 갱신된다
     */
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
            Long recentBookId,
            boolean debtWaiverAvailable
    ) {
        /** 라이브 상태 → DTO. 용서권 가용 여부만 따로 받는다(부채 계산과 다른 출처라 합칠 수 없다). */
        public static TimerState of(DashboardModel.LiveState live, boolean debtWaiverAvailable) {
            return new TimerState(
                    live.remainingSeconds(), live.carriedDebtSeconds(),
                    live.todayGoalSeconds(), live.carryover(),
                    live.hasActiveSession(), live.activeStartedAt(),
                    live.activeBookTitle(), live.activeBookTotalSeconds(),
                    toOptions(live.readingBooks()), toOptions(live.finishedBooks()),
                    live.recentBookId(), debtWaiverAvailable);
        }
    }

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
