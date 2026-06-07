package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 측정 세션 시작/종료 — 대시보드의 버튼이 호출하는 경로.
 *
 * <p>인증 식별자(email)로 도메인 {@link User}를 찾아(N-012) {@link ReadingSessionService}에
 * 위임한다. 잘못된 상태(이미 진행 중인데 start, 진행 없는데 stop)는 서비스가
 * {@link IllegalStateException}으로 알리며, 여기선 이를 사용자에게 부드럽게 안내한다.
 *
 * <p><b>두 가지 응답 모드</b>:
 * <ul>
 *   <li>일반 폼 전송 — {@code redirect:/}로 대시보드 재요청(플래시 메시지로 에러 전달). JS 없이도 동작(점진적 향상).</li>
 *   <li>htmx 무리로드(요청에 {@code HX-Request} 헤더) — 전체 페이지 리로드 없이
 *       대시보드 <b>라이브 영역 프래그먼트</b>({@code dashboard :: live})만 200으로 반환해 swap.
 *       에러는 플래시 대신 모델에 실어 프래그먼트 안에서 표시한다.</li>
 * </ul>
 *
 * <p>시각은 주입된 {@link Clock}으로 격리(N-010) — start/stop 모두 "지금"을 시계에서 읽는다.
 */
@Controller
@RequestMapping("/sessions")
public class ReadingSessionController {

    private final CurrentUserService currentUserService;
    private final ReadingSessionService sessionService;
    private final DashboardModel dashboardModel;
    private final BookRepository bookRepository;
    private final Clock clock;

    public ReadingSessionController(CurrentUserService currentUserService,
                                    ReadingSessionService sessionService,
                                    DashboardModel dashboardModel,
                                    BookRepository bookRepository,
                                    Clock clock) {
        this.currentUserService = currentUserService;
        this.sessionService = sessionService;
        this.dashboardModel = dashboardModel;
        this.bookRepository = bookRepository;
        this.clock = clock;
    }

    @PostMapping("/start")
    public String start(Principal principal,
                        @RequestParam(value = "bookId", required = false) Long bookId,
                        @RequestHeader(value = "HX-Request", required = false, defaultValue = "false") boolean htmx,
                        Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        // 측정은 반드시 "내 책"을 대상으로 한다(책 미지정 측정 금지). bookId가 없거나 내 책이 아니면(IDOR 포함)
        // 책 선택을 안내하고 세션을 만들지 않는다.
        Book book = (bookId == null) ? null : bookRepository.findByIdAndUser(bookId, user).orElse(null);
        String error = null;
        if (book == null) {
            error = "측정할 책을 선택하세요.";
        } else {
            try {
                sessionService.start(user, clock.instant(), book);
            } catch (IllegalStateException e) {
                error = "이미 진행 중인 측정이 있습니다.";
            }
        }
        return respond(htmx, user, error, model, redirectAttributes);
    }

    @PostMapping("/stop")
    public String stop(Principal principal,
                       @RequestHeader(value = "HX-Request", required = false, defaultValue = "false") boolean htmx,
                       Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        String error = null;
        try {
            sessionService.stop(user, clock.instant());
        } catch (IllegalStateException e) {
            error = "진행 중인 측정이 없습니다.";
        }
        return respond(htmx, user, error, model, redirectAttributes);
    }

    /** 하루를 넘는 단일 수동 기록은 오입력으로 보고 거부한다(살찐 손가락 방지). */
    private static final long MAX_MANUAL_SECONDS = 24 * 3600L;

    /**
     * 사후 수동 입력 폼 — 측정 시작을 깜빡한 독서를 직접 적는 화면. 책 미지정 기록은 금지라
     * 고를 책 목록(전체)을 싣고, 날짜 기본값/상한으로 쓸 "오늘"(유저 타임존)도 함께 싣는다.
     */
    @GetMapping("/manual")
    public String manualForm(Principal principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("books", bookRepository.findByUserOrderByCreatedAtDesc(user));
        model.addAttribute("today",
                LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone())).toString());
        return "manual-session";
    }

    /**
     * 사후 수동 입력 제출 — 책+날짜+시간으로 완료 세션 1건을 만든다.
     *
     * <p>검증(거부 시 폼으로 플래시 에러): 책 미선택·남의 책(IDOR), 날짜 형식 오류·미래 날짜, 0 이하·24시간 초과 시간.
     * 잔디·일자별 기록은 {@code startedAt}을 유저 타임존으로 묶으므로(N-010), 고른 날짜에 안착하도록 시각을 잡는다 —
     * 오늘이면 "지금" 끝난 것으로(미래 시각 회피), 과거면 그 날 정오를 종료로 삼아 {@code startedAt}이 같은 날에 남게 한다.
     */
    @PostMapping("/manual")
    public String manualSubmit(Principal principal,
                               @RequestParam(value = "bookId", required = false) Long bookId,
                               @RequestParam(value = "date", required = false) String date,
                               @RequestParam(value = "hours", required = false, defaultValue = "0") int hours,
                               @RequestParam(value = "minutes", required = false, defaultValue = "0") int minutes,
                               RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        Book book = (bookId == null) ? null : bookRepository.findByIdAndUser(bookId, user).orElse(null);
        LocalDate readDate = parseDate(date);
        long durationSeconds = hours * 3600L + minutes * 60L;

        String error = validateManual(book, readDate, durationSeconds, today);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/sessions/manual";
        }

        Instant endedAt = readDate.equals(today)
                ? clock.instant()
                : readDate.atTime(LocalTime.NOON).atZone(zone).toInstant();
        Instant startedAt = endedAt.minusSeconds(durationSeconds);

        sessionService.recordManual(user, startedAt, endedAt, book);
        redirectAttributes.addFlashAttribute("message", "독서 기록을 추가했어요.");
        return "redirect:/sessions/manual";
    }

    /** {@code yyyy-MM-dd} 파싱. 비었거나 형식이 틀리면 null(검증에서 안내). */
    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 수동 입력 검증 — 통과면 null, 막히면 사용자 안내 문구. 책 미선택과 남의 책(IDOR)은 같은 안내로 묶는다. */
    private static String validateManual(Book book, LocalDate readDate, long durationSeconds, LocalDate today) {
        if (book == null) {
            return "기록할 책을 선택하세요.";
        }
        if (readDate == null) {
            return "읽은 날짜를 올바르게 입력하세요.";
        }
        if (readDate.isAfter(today)) {
            return "미래 날짜는 기록할 수 없어요.";
        }
        if (durationSeconds <= 0) {
            return "읽은 시간을 입력하세요.";
        }
        if (durationSeconds > MAX_MANUAL_SECONDS) {
            return "하루(24시간)를 넘는 기록은 할 수 없어요.";
        }
        return null;
    }

    /**
     * htmx면 라이브 프래그먼트(200)를, 아니면 대시보드로 redirect(303)를 돌려준다.
     * 에러는 htmx에선 모델로, redirect에선 플래시로 전달한다.
     */
    private String respond(boolean htmx, User user, String error,
                           Model model, RedirectAttributes redirectAttributes) {
        if (htmx) {
            if (error != null) {
                model.addAttribute("error", error);
            }
            dashboardModel.populate(model, user);
            return "dashboard :: live";
        }
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        }
        return "redirect:/";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
