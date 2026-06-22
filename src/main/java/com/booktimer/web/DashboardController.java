package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 대시보드(홈) — 얇은 셸 반환. 콘텐츠는 {@code /api/dashboard}를 호출하는 Vue 섬이 채운다.
 *
 * <p>인증·온보딩·역할 분기는 여기서 처리하고, 실제 사용자 데이터는 REST API 계층({@link com.booktimer.web.api.DashboardApiController})이 담당한다.
 */
@Controller
public class DashboardController {

    private final CurrentUserService currentUserService;

    public DashboardController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/")
    public String dashboard(Principal principal) {
        if (principal == null) {
            return "landing";
        }

        User user = currentUserService.resolve(principal);

        if (user.getRole() == Role.ADMIN) {
            return "redirect:/admin";
        }

        if (!user.isOnboarded()) {
            return "redirect:/onboarding";
        }

        return "dashboard";
    }
}
