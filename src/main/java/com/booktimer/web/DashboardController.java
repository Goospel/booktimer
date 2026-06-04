package com.booktimer.web;

import com.booktimer.session.ReadingContributionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
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
 *
 * <p>독서 잔디(컨트리뷰션 그래프)도 함께 싣는다 — {@code /history}와 같은 모델({@link ReadingContributionService})을
 * 쓰되, 잔디는 라이브 영역 밖이라 htmx 무리로드 시 다시 그릴 필요가 없어 전체 페이지 렌더에서만 채운다.
 */
@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final DashboardModel dashboardModel;
    private final ReadingContributionService contributionService;

    public DashboardController(UserRepository userRepository,
                               DashboardModel dashboardModel,
                               ReadingContributionService contributionService) {
        this.userRepository = userRepository;
        this.dashboardModel = dashboardModel;
        this.contributionService = contributionService;
    }

    @GetMapping("/")
    public String dashboard(Principal principal, HttpServletRequest request, Model model) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));

        // 첫 진입 게이트: 초기 설정(온보딩)을 마치지 않은 신규 가입자는 온보딩 페이지로 유도한다.
        // 로그인 후 착지점이 "/"라 LOCAL·OAuth 첫 가입 모두 여기서 걸린다.
        if (!user.isOnboarded()) {
            return "redirect:/onboarding";
        }

        // 렌더 전에 CSRF 토큰을 미리 확정(세션 생성)한다. 대시보드는 잔디 그래프(수백 칸)로 응답 버퍼가
        // 렌더 도중 커밋될 수 있는데, 그 뒤 첫 폼(맨 아래 로그아웃 등)의 CSRF 숨김필드가 세션을 새로
        // 만들려 하면 "response already committed"로 500이 난다. 폼 위치에 의존하지 않게 여기서 선확정.
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            token.getToken();
        }

        dashboardModel.populate(model, user);
        model.addAttribute("graph", contributionService.contributionGraph(user));
        return "dashboard";
    }
}
