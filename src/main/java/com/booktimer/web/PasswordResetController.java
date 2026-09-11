package com.booktimer.web;

import com.booktimer.email.PasswordResetService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 비밀번호 재설정 화면/처리(이메일 인프라 1단계 PR-C) — 모두 공개 경로(비밀번호를 잊은 비로그인 사용자용).
 *
 * <p><b>요청</b>: {@code GET /password/forgot} 이메일 입력 폼 → {@code POST}는 계정 존재 여부와 무관하게
 * 동일한 안내 페이지를 보여준다(열거완화 — 실제 발송은 {@link PasswordResetService}가 LOCAL 계정에만).
 *
 * <p><b>재설정</b>: {@code GET /password/reset?token=}은 <b>토큰을 소비하지 않고</b> 새 비번 폼만 렌더한다 —
 * 메일 클라이언트의 링크 프리페치(SafeLinks·백신)가 GET을 날려 토큰을 소진시키지 못하게(#296과 동일 패턴).
 * 사람이 폼을 제출한 {@code POST /password/reset}에서만 토큰을 소비해 비밀번호를 바꾼다. 새 비번 검증
 * (길이·일치)에 실패하면 토큰을 소비하지 않고 폼을 에러와 함께 다시 보여준다(링크가 살아있게).
 */
@Controller
public class PasswordResetController {

    /** 새 비밀번호 길이 — 가입 폼과 동일(BCrypt 입력 상한 72바이트, 최소 8자). */
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;

    private final PasswordResetService passwordResetService;
    private final RateLimitService rateLimitService;

    public PasswordResetController(PasswordResetService passwordResetService,
                                   RateLimitService rateLimitService) {
        this.passwordResetService = passwordResetService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/password/forgot")
    public String forgotForm(HttpServletRequest request) {
        CsrfTokenUtil.precommit(request);
        return "password-forgot";
    }

    /**
     * 재설정 요청 — 계정 존재/부재/소셜 모두 동일한 안내 페이지(열거완화). 발송 여부만 내부 차이.
     *
     * <p><b>상한은 두 키로 센다</b>({@link RateLimitAction#PASSWORD_FORGOT} IP ·
     * {@link RateLimitAction#PASSWORD_FORGOT_EMAIL} 정규화 이메일). 이메일 키는 <b>한 주소로 나가는
     * 발송량 상한</b>이다 — IP만 세면 출처를 분산해 한 사람의 수신함을 채울 수 있다.
     *
     * <p>⚠️ 이메일 키는 <b>토큰 무효화로 인한 링크 봉쇄를 끊지 못하고, 오히려 피해자 본인을 잠글 수
     * 있다</b>(공격자가 시간당 3회를 소진하면 피해자 요청이 {@code ?limited}로 거부된다). 수용한
     * 트레이드오프이고 근본 해소는 {@code EmailTokenService.issue}의 직전 토큰 무효화를 그만두는
     * 것이다 — 상세·근거는 {@link RateLimitAction#PASSWORD_FORGOT_EMAIL} JavaDoc.
     *
     * <p>계정이 없어도 <b>똑같이</b> 센다. 상한 반응이 계정 존재 여부에 따라 갈리면 그 자체가 열거 채널이다.
     */
    @PostMapping("/password/forgot")
    public String forgot(@RequestParam(name = "email", required = false) String email,
                         HttpServletRequest request) {
        // 키 정규화 — 안 하면 대소문자·앞뒤 공백만 바꿔 이메일 상한을 우회한다.
        // Locale.ROOT 고정 — 기본 로케일이 tr이면 'I'가 'ı'로 접혀 같은 주소가 다른 키가 된다.
        String normalized = (email == null ? "" : email).trim().toLowerCase(java.util.Locale.ROOT);
        // 둘 다 실제로 센다(단축평가로 한쪽을 건너뛰지 않게 && 대신 각각 호출) — 한쪽만 세면 다른 키의 상한이 샌다.
        boolean ipOk = rateLimitService.allow(RateLimitAction.PASSWORD_FORGOT, request.getRemoteAddr());
        boolean emailOk = rateLimitService.allow(RateLimitAction.PASSWORD_FORGOT_EMAIL, normalized);
        if (!ipOk || !emailOk) {
            return "redirect:/password/forgot?limited";
        }
        passwordResetService.requestReset(email == null ? "" : email);
        return "password-forgot-sent";
    }

    /** 재설정 폼 진입(공개) — 토큰을 소비하지 않고 새 비번 폼만 렌더(프리페치 안전). */
    @GetMapping("/password/reset")
    public String resetForm(@RequestParam(name = "token", required = false) String token,
                            HttpServletRequest request, Model model) {
        CsrfTokenUtil.precommit(request);
        model.addAttribute("token", token == null ? "" : token);
        return "password-reset";
    }

    /** 재설정 확정(공개·CSRF 보호) — 새 비번 검증 통과 시에만 토큰을 소비해 비밀번호를 바꾼다. */
    @PostMapping("/password/reset")
    public String reset(@RequestParam(name = "token", required = false) String token,
                        @RequestParam(name = "password", required = false) String password,
                        @RequestParam(name = "passwordConfirm", required = false) String passwordConfirm,
                        Model model) {
        model.addAttribute("token", token == null ? "" : token);

        String error = validateNewPassword(password, passwordConfirm);
        if (error != null) {
            // 검증 실패면 토큰을 소비하지 않는다 — 사용자가 고쳐서 다시 제출하면 같은 링크가 여전히 유효.
            model.addAttribute("error", error);
            return "password-reset";
        }

        boolean success = passwordResetService.reset(token, password);
        model.addAttribute("success", success);
        return "password-reset-result";
    }

    /** 새 비밀번호 형식 검증 — 통과면 null, 실패면 사용자 안내 메시지. */
    private static String validateNewPassword(String password, String passwordConfirm) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            return "비밀번호는 8자 이상 72자 이하여야 합니다.";
        }
        if (!password.equals(passwordConfirm)) {
            return "두 비밀번호가 일치하지 않습니다.";
        }
        return null;
    }
}
