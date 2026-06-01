package com.booktimer.web;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 대시보드(홈) — 로그인 후 착지점.
 *
 * <p>인증 주체의 식별자(username=email, {@code BookTimerUserDetailsService} 매핑)로 도메인
 * {@link User}를 찾아, 접속 시점에 누적을 따라잡고(Lazy accrual, N-001) 현재 잔여 시간과
 * 진행 중 세션을 화면에 싣는다. 라이브 영역 모델은 {@link DashboardModel}에 위임해
 * htmx 무리로드 경로({@link ReadingSessionController})와 동일한 상태를 보장한다.
 */
@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final DashboardModel dashboardModel;

    public DashboardController(UserRepository userRepository, DashboardModel dashboardModel) {
        this.userRepository = userRepository;
        this.dashboardModel = dashboardModel;
    }

    @GetMapping("/")
    public String dashboard(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));

        dashboardModel.populate(model, user);
        return "dashboard";
    }
}
