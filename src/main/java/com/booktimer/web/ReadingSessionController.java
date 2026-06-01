package com.booktimer.web;

import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;

/**
 * 측정 세션 시작/종료 — 대시보드의 버튼이 호출하는 경로.
 *
 * <p>인증 식별자(email)로 도메인 {@link User}를 찾아(N-012) {@link ReadingSessionService}에
 * 위임한다. 잘못된 상태(이미 진행 중인데 start, 진행 없는데 stop)는 서비스가
 * {@link IllegalStateException}으로 알리며, 여기선 이를 <b>플래시 메시지</b>로 바꿔
 * 대시보드에서 사용자에게 안내한다(예외 페이지 대신 부드러운 피드백).
 *
 * <p>시각은 주입된 {@link Clock}으로 격리(N-010) — start/stop 모두 "지금"을 시계에서 읽는다.
 */
@Controller
@RequestMapping("/sessions")
public class ReadingSessionController {

    private final UserRepository userRepository;
    private final ReadingSessionService sessionService;
    private final Clock clock;

    public ReadingSessionController(UserRepository userRepository,
                                    ReadingSessionService sessionService,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @PostMapping("/start")
    public String start(Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            sessionService.start(user, clock.instant());
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

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
