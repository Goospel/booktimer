package com.booktimer.web;

import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * 사용자 신고 액션 (SNS 5단계, sns-design §7.5).
 *
 * <p>{@code POST /report} — 대상 닉네임으로 신고를 저장하고 원래 화면으로 돌아간다(CSRF, PRG).
 * 자기 신고·존재 누설은 조용히 무시한다(예외를 화면에 노출하지 않음). 도메인 규칙(자기 신고 금지·멱등)은
 * {@link ReportService}가 강제한다. 오픈 리다이렉트는 내부 상대경로만 허용한다(FollowController와 동일).
 *
 * <p>신고 남용 방어로 사용자별 레이트리밋({@link RateLimitAction#REPORT})을 건다 — 한도 초과 시 접수를
 * 건너뛰고(조용히 드롭) 원래 화면으로 돌아간다(§7.5·§9).
 */
@Controller
public class ReportController {

    private final UserRepository userRepository;
    private final ReportService reportService;
    private final RateLimitService rateLimitService;

    public ReportController(UserRepository userRepository, ReportService reportService,
                            RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.reportService = reportService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/report")
    public String report(@RequestParam String nickname,
                         @RequestParam(value = "reason", required = false) String reason,
                         @RequestParam(value = "detail", required = false) String detail,
                         @RequestParam(value = "redirect", required = false) String redirect,
                         Principal principal) {
        User me = currentUser(principal);
        if (rateLimitService.allow(RateLimitAction.REPORT, me.getId())) {
            userRepository.findByNickname(nickname).ifPresent(target -> {
                try {
                    reportService.report(me, target, ReportReason.from(reason), detail);
                } catch (IllegalArgumentException ignored) {
                    // 자기 신고 등 — 조용히 무시(버튼이 애초에 안 떠야 정상)
                }
            });
        }
        return "redirect:" + safeRedirect(redirect, nickname);
    }

    /**
     * 돌아갈 경로 — <b>내부 상대경로만</b> 허용(오픈 리다이렉트 방어). "/"로 시작하고 "//"가 아니면 그대로,
     * 아니면 대상 프로필로. (FollowController.safeRedirect와 동일 정책)
     */
    private String safeRedirect(String redirect, String nickname) {
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            return redirect;
        }
        return "/u/" + nickname;
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
