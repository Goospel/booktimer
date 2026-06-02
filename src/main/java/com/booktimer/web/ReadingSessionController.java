package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;

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

    private final UserRepository userRepository;
    private final ReadingSessionService sessionService;
    private final DashboardModel dashboardModel;
    private final BookRepository bookRepository;
    private final Clock clock;

    public ReadingSessionController(UserRepository userRepository,
                                    ReadingSessionService sessionService,
                                    DashboardModel dashboardModel,
                                    BookRepository bookRepository,
                                    Clock clock) {
        this.userRepository = userRepository;
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
        // bookId가 있으면 내 책일 때만 연결(소유권 검사) — 아니면 책 미지정으로 시작.
        Book book = (bookId == null) ? null : bookRepository.findByIdAndUser(bookId, user).orElse(null);
        String error = null;
        try {
            sessionService.start(user, clock.instant(), book);
        } catch (IllegalStateException e) {
            error = "이미 진행 중인 측정이 있습니다.";
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
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
