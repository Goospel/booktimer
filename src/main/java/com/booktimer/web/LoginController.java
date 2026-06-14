package com.booktimer.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 커스텀 로그인 화면.
 *
 * <p>Spring Security 기본 생성 페이지 대신 우리 템플릿({@code login.html})을 보여준다.
 * 인증 처리(POST /login)는 여전히 Security 필터가 담당하고, 이 컨트롤러는 GET 화면만 그린다.
 * 에러/로그아웃/가입완료 안내는 템플릿이 쿼리 파라미터({@code ?error/?logout/?registered})로 처리한다.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginForm(HttpServletRequest request) {
        // 렌더 전에 CSRF 토큰을 선확정한다. 로그인 폼(th:action)의 CSRF 숨김필드는 세션이 없으면 그 순간
        // 새로 만드는데, head가 커지면(GA4 #338) 폼 렌더 전에 응답이 커밋돼 "session after response
        // committed"로 500이 난다(IllegalStateException). 로그인 화면은 익명이라 세션이 늘 없어 특히 취약.
        // DashboardController·PersonalityController와 동일 방어(T-033·T-049, N-044·N-077).
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            token.getToken();
        }
        return "login";
    }
}
