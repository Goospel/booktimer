package com.booktimer.web;

import com.booktimer.admin.AdminStatsService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 관리자(운영자) 대시보드 — 운영 데이터 확인 화면.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음 — 보안 경계는 필터에서 한 번만). 미인증은 로그인으로,
 * 일반 USER는 403으로 막힌다.
 *
 * <p>1단계는 접근 경계 확립 + 최소 랜딩이다. 통계 요약(가입자·활성·총 독서시간)·데이터 조회는
 * 다음 단계에서 읽기 전용 집계로 얹는다.
 */
@Controller
public class AdminController {

    private final CurrentUserService currentUserService;
    private final AdminStatsService adminStatsService;

    public AdminController(CurrentUserService currentUserService,
                          AdminStatsService adminStatsService) {
        this.currentUserService = currentUserService;
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/admin")
    public String dashboard(Principal principal, Model model) {
        User admin = currentUserService.resolve(principal);
        model.addAttribute("nickname", admin.getNickname());
        // 운영 통계 요약(읽기 전용 집계) — 매번 RDS에 붙지 않고 웹에서 지표 확인(N-037).
        model.addAttribute("stats", adminStatsService.summary());
        return "admin";
    }
}
