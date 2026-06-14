package com.booktimer.web;

import com.booktimer.email.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
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

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password/forgot")
    public String forgotForm(HttpServletRequest request) {
        precommitCsrfToken(request);
        return "password-forgot";
    }

    /** 재설정 요청 — 계정 존재/부재/소셜 모두 동일한 안내 페이지(열거완화). 발송 여부만 내부 차이. */
    @PostMapping("/password/forgot")
    public String forgot(@RequestParam(name = "email", required = false) String email) {
        passwordResetService.requestReset(email == null ? "" : email);
        return "password-forgot-sent";
    }

    /** 재설정 폼 진입(공개) — 토큰을 소비하지 않고 새 비번 폼만 렌더(프리페치 안전). */
    @GetMapping("/password/reset")
    public String resetForm(@RequestParam(name = "token", required = false) String token,
                            HttpServletRequest request, Model model) {
        precommitCsrfToken(request);
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

    /**
     * 렌더 전에 CSRF 토큰을 선확정해 세션을 응답 커밋 이전에 만든다. 두 GET(forgot·reset) 모두 익명 폼
     * 페이지라 세션이 없어, 폼의 CSRF 숨김필드가 렌더 중(응답 커밋 후) 세션을 만들려다 500을 내던 함정
     * 방어(IllegalStateException, T-049 · DashboardController와 동일 패턴).
     */
    private static void precommitCsrfToken(HttpServletRequest request) {
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            token.getToken();
        }
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
