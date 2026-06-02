package com.booktimer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 무차별 대입 방어 — 잠긴 클라이언트에서 온 로그인 시도를 인증 매니저에 닿기 전에 단락한다.
 *
 * <p>{@code POST /login}만 가로채, {@link LoginAttemptService}가 그 IP를 잠금 상태로 보면
 * 인증을 시도하지 않고 곧장 {@code /login?error}로 리다이렉트한다(폼 로그인 실패와 동일한 UX —
 * 잠금 여부를 따로 노출하지 않는다). 잠금이 아니면 평소대로 다음 필터로 흘려보낸다.
 *
 * <p>실패/성공 집계는 인증 이벤트({@link LoginAttemptEventListener})가 하므로, 차단된 요청은
 * 인증에 도달하지 않아 카운터를 더 늘리지 않는다 — 잠금 시간이 끝나면 자연히 풀린다.
 */
public class LoginAttemptFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isLoginPost(request) && loginAttemptService.isBlocked(request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 로그인 처리 엔드포인트(POST /login)인지 — 컨텍스트 경로를 제외한 경로로 판정. */
    private static boolean isLoginPost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "/login".equals(path);
    }
}
