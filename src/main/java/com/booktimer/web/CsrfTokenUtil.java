package com.booktimer.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * CSRF 토큰 선확정 유틸 — SSR 폼 페이지의 "응답 버퍼 커밋-후-세션생성 500" 방어.
 *
 * <p>Thymeleaf가 {@code th:action} 폼의 CSRF 숨김필드를 렌더할 때 세션을 lazy 생성하는데, 페이지가 커
 * 응답 버퍼(기본 ~8KB)가 이미 커밋된 뒤면 {@code getSession(true)}가
 * {@code IllegalStateException("Cannot create a session after the response has been committed")}로 터져
 * 그 페이지만 500이 난다. 폼을 가진 SSR GET 핸들러는 렌더 전에 이 메서드로 토큰을 한 번 당겨 세션을
 * 응답 커밋 이전에 확정한다. 배경: T-033·T-049, 개념 N-044·N-077.
 */
public final class CsrfTokenUtil {

    private CsrfTokenUtil() {
    }

    /** 요청에 CsrfToken이 있으면 {@code getToken()}을 호출해 세션을 즉시 생성(선확정)한다. 없으면 no-op. */
    public static void precommit(HttpServletRequest request) {
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
