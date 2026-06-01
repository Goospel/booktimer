package com.booktimer.web;

import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.Optional;

/**
 * 대시보드(홈) — 로그인 후 착지점.
 *
 * <p>인증 주체의 식별자(username=email, {@code BookTimerUserDetailsService} 매핑)로 도메인
 * {@link User}를 찾아, 접속 시점에 누적을 따라잡고(Lazy accrual, N-001) 현재 잔여 시간과
 * 진행 중 세션을 화면에 싣는다.
 */
@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final ReadingTimerService timerService;
    private final ReadingSessionRepository sessionRepository;

    public DashboardController(UserRepository userRepository,
                               ReadingTimerService timerService,
                               ReadingSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.timerService = timerService;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/")
    public String dashboard(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));

        ReadingTimer timer = timerService.accrueToToday(user);
        Optional<ReadingSession> activeSession = sessionRepository.findByUserAndEndedAtIsNull(user);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("remainingSeconds", timer.getRemainingSeconds());
        model.addAttribute("hasActiveSession", activeSession.isPresent());
        model.addAttribute("activeStartedAt", activeSession.map(ReadingSession::getStartedAt).orElse(null));
        return "dashboard";
    }
}
