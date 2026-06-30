package com.booktimer.web;

import com.booktimer.email.EmailVerificationService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 가입 이메일 인증 화면/처리(이메일 인프라 1단계 PR-B).
 *
 * <p>{@code GET /verify-email?token=}은 공개(메일 링크는 비로그인 상태로 열릴 수 있음) — 토큰을 소비해 결과
 * 페이지를 보여준다. {@code POST /verify-email/resend}는 로그인 사용자가 인증 메일을 다시 받는다.
 */
@Controller
public class EmailVerificationController {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationController.class);

    private final EmailVerificationService emailVerificationService;
    private final CurrentUserService currentUserService;

    public EmailVerificationController(EmailVerificationService emailVerificationService,
                                       CurrentUserService currentUserService) {
        this.emailVerificationService = emailVerificationService;
        this.currentUserService = currentUserService;
    }

    /**
     * 인증 링크 진입(공개) — <b>토큰을 소비하지 않고</b> 확인 페이지(버튼 폼)만 렌더한다.
     *
     * <p>일회용 토큰 소비를 GET이 아니라 POST({@link #confirm})로 미룬 이유: 메일 클라이언트의 링크 프리페치
     * (Outlook SafeLinks·백신·메신저 언펄링)는 GET을 자동으로 날린다 — GET에서 소비하면 사용자가 클릭하기도 전에
     * 토큰이 소진돼 "만료" 페이지를 보게 된다. GET은 폼만 보여주고, 사람이 버튼을 눌러야 일어나는 POST에서 소비한다.
     */
    @GetMapping("/verify-email")
    public String verifyForm(HttpServletRequest request, @RequestParam(name = "token", required = false) String token, Model model) {
        CsrfTokenUtil.precommit(request); // 폼 렌더 전 세션 선확정 — commit-후-500 방어(T-049)
        model.addAttribute("token", token == null ? "" : token);
        return "verify-email-confirm";
    }

    /** 인증 확정(공개·CSRF 보호) — 버튼 클릭 시에만 토큰을 소비해 검증하고 결과 페이지를 렌더한다. */
    @PostMapping("/verify-email")
    public String confirm(@RequestParam(name = "token", required = false) String token, Model model) {
        boolean verified = emailVerificationService.verify(token);
        model.addAttribute("verified", verified);
        return "verify-email-result";
    }

    /** 인증 메일 재발송(로그인) — 이미 검증됐으면 다시 보내지 않고, 발송 실패는 격리한다. */
    @PostMapping("/verify-email/resend")
    public String resend(Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUserService.resolve(principal);
        if (user.isEmailVerified()) {
            redirectAttributes.addFlashAttribute("verifyResendResult", "already");
            return "redirect:/settings";
        }
        try {
            emailVerificationService.sendVerification(user);
            redirectAttributes.addFlashAttribute("verifyResendResult", "sent");
        } catch (RuntimeException e) {
            log.warn("인증 메일 재발송 실패 — email={}", user.getEmail());
            redirectAttributes.addFlashAttribute("verifyResendResult", "failed");
        }
        return "redirect:/settings";
    }
}
