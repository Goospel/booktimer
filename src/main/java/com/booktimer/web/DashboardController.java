package com.booktimer.web;

import com.booktimer.quote.QuoteService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
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

    private final CurrentUserService currentUserService;
    private final DashboardModel dashboardModel;
    private final ReadingContributionService contributionService;
    private final QuoteService quoteService;

    public DashboardController(CurrentUserService currentUserService,
                               DashboardModel dashboardModel,
                               ReadingContributionService contributionService,
                               QuoteService quoteService) {
        this.currentUserService = currentUserService;
        this.dashboardModel = dashboardModel;
        this.contributionService = contributionService;
        this.quoteService = quoteService;
    }

    @GetMapping("/")
    public String dashboard(Principal principal, HttpServletRequest request, Model model) {
        // 비로그인 방문자·검색/광고 크롤러는 로그인으로 튕기지 않고 공개 소개 페이지를 본다.
        // 루트가 곧 랜딩이라 크롤러가 "무엇을 하는 서비스인가"를 읽을 수 있다(AdSense 콘텐츠 심사 대비).
        // principal이 없으면 아래 대시보드 로직(개인 데이터 로드)을 타지 않으므로 노출 위험 없음.
        if (principal == null) {
            return "landing";
        }

        User user = currentUserService.resolve(principal);

        // 운영자(ADMIN)는 독서 대시보드가 아니라 운영 화면으로 직행한다 — 책을 읽는 주체가 아니다.
        // 로그인 후 착지점이 "/"라 폼·OAuth 어느 경로든 여기서 /admin으로 보낸다(온보딩 게이트보다 먼저).
        if (user.getRole() == Role.ADMIN) {
            return "redirect:/admin";
        }

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
        // 인사말 자리에 띄울 작가 격언 — 전체 페이지 경로에서만 뽑는다(htmx 라이브 영역 밖이라
        // 측정 start/stop엔 안 바뀌고 페이지 로드 때만 갱신). DashboardModel에 두면 라이브 경로와
        // 공유돼 start/stop마다 헛돌므로 여기서만 싣는다(잔디 graph와 같은 이유).
        model.addAttribute("quote", quoteService.random());
        // 미검증이면 인증 유도 배너를 띄운다(정책 ③ — 미검증이어도 사용은 허용하되 인증을 권한다).
        // 기존 가입자는 V31에서 true 백필돼 배너가 안 뜬다(grandfather).
        model.addAttribute("emailVerified", user.isEmailVerified());
        return "dashboard";
    }
}
