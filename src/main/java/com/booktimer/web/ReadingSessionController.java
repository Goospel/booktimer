package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 측정 세션 시작/종료 — 대시보드 Vue 섬의 API({@link com.booktimer.web.api.DashboardApiController})가
 * start/stop JSON API를 흡수했으므로, 이 컨트롤러는 폼 폴백(JS 없는 환경)과 수동 기록만 담당한다.
 *
 * <p>폼 전송은 {@code redirect:/}로 대시보드 재요청(플래시 메시지로 에러 전달).
 * htmx 라이브 프래그먼트 경로는 Vue 전환으로 제거됐다.
 *
 * <p>시각은 주입된 {@link Clock}으로 격리(N-010) — start/stop 모두 "지금"을 시계에서 읽는다.
 */
@Controller
@RequestMapping("/sessions")
public class ReadingSessionController {

    private final CurrentUserService currentUserService;
    private final ReadingSessionService sessionService;
    private final BookRepository bookRepository;
    private final ReadingDebtService debtService;
    private final Clock clock;

    public ReadingSessionController(CurrentUserService currentUserService,
                                    ReadingSessionService sessionService,
                                    BookRepository bookRepository,
                                    ReadingDebtService debtService,
                                    Clock clock) {
        this.currentUserService = currentUserService;
        this.sessionService = sessionService;
        this.bookRepository = bookRepository;
        this.debtService = debtService;
        this.clock = clock;
    }

    @PostMapping("/start")
    public String start(Principal principal,
                        @RequestParam(value = "bookId", required = false) Long bookId,
                        RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        // bookId 없이 시작하면 책 미지정 세션(발견 1 — 시작을 막지 않음).
        // bookId가 '있는데' 소유 아님/미존재면 에러(IDOR 방지) — 조용히 책 없이 시작하지 않는다.
        Book book = null;
        if (bookId != null) {
            book = bookRepository.findByIdAndUser(bookId, user).orElse(null);
            if (book == null) {
                redirectAttributes.addFlashAttribute("error", "측정할 책을 찾을 수 없어요.");
                return "redirect:/";
            }
        }
        try {
            sessionService.start(user, clock.instant(), book);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "이미 진행 중인 측정이 있습니다.");
        }
        return "redirect:/";
    }

    @PostMapping("/stop")
    public String stop(Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            sessionService.stop(user, clock.instant());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "진행 중인 측정이 없습니다.");
        }
        return "redirect:/";
    }

    /** 하루를 넘는 단일 수동 기록은 오입력으로 보고 거부한다(살찐 손가락 방지). */
    private static final long MAX_MANUAL_SECONDS = 24 * 3600L;

    /**
     * 사후 수동 입력 폼 — 측정 시작을 깜빡한 독서를 직접 적는 화면.
     */
    @GetMapping("/manual")
    public String manualForm(Principal principal,
                             @RequestParam(value = "date", required = false) String date,
                             Model model) {
        User user = currentUser(principal);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate windowStart = debtService.debtWindowStart(user);

        model.addAttribute("books", bookRepository.findByUserOrderByCreatedAtDesc(user));
        model.addAttribute("today", today.toString());
        model.addAttribute("minDate", windowStart.toString());
        LocalDate prefill = parseDate(date);
        boolean inWindow = prefill != null && !prefill.isAfter(today) && !prefill.isBefore(windowStart);
        model.addAttribute("selectedDate", inWindow ? prefill.toString() : today.toString());
        return "manual-session";
    }

    /**
     * 사후 수동 입력 제출.
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

        String error = validateManual(book, readDate, durationSeconds, today, debtService.debtWindowStart(user));
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

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @param windowStart 부채 계산 창의 시작일({@link ReadingDebtService#debtWindowStart}) — 입력 가능 하한을
     *                    부채 대상 구간과 같게 맞춘다. 어긋나면 "부채는 있는데 채울 수 없는 날"이 생긴다.
     */
    private static String validateManual(Book book, LocalDate readDate, long durationSeconds,
                                         LocalDate today, LocalDate windowStart) {
        if (book == null) return "기록할 책을 선택하세요.";
        if (readDate == null) return "읽은 날짜를 올바르게 입력하세요.";
        if (readDate.isAfter(today)) return "미래 날짜는 기록할 수 없어요.";
        if (readDate.isBefore(windowStart))
            return "밀린 기록을 채울 수 있는 기간(" + windowStart + " 이후)을 벗어난 날짜예요.";
        if (durationSeconds <= 0) return "읽은 시간을 입력하세요.";
        if (durationSeconds > MAX_MANUAL_SECONDS) return "하루(24시간)를 넘는 기록은 할 수 없어요.";
        return null;
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
