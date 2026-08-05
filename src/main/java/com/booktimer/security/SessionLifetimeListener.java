package com.booktimer.security;

import com.booktimer.config.WebConfig;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 인증에 성공한 세션만 수명을 {@link WebConfig#AUTHENTICATED_SESSION_TIMEOUT}(30일)으로 연장한다.
 *
 * <p>저장소 기본값은 익명 24시간이다({@code WebConfig#sessionTimeoutCustomizer}) — 운영 세션 테이블의
 * 대부분이 봇·크롤러가 남긴 익명 행이라 기본을 짧게 잡고, 실제 로그인한 세션만 여기서 늘린다.
 *
 * <p><b>왜 성공 핸들러가 아니라 이벤트인가</b> — 폼 로그인과 구글 OAuth(OIDC)는 성공 핸들러가 서로 다르지만
 * 둘 다 {@code AuthenticationManager}를 지나 {@link AuthenticationSuccessEvent}를 발행한다
 * ({@code SecurityConfig}의 {@code AuthenticationEventPublisher} 빈으로 보장). 훅 하나로 두 경로를 커버한다
 * (선례: {@link LoginAttemptEventListener}와 같은 이유).
 *
 * <p><b>세션 고정 보호와의 순서</b> — 이 이벤트는 인증 필터의 session fixation 처리
 * ({@code changeSessionId}) <b>이전</b>에 발행되지만, {@code changeSessionId}는 세션 객체를 유지한 채
 * ID만 바꾸므로 여기서 늘린 max-inactive-interval이 그대로 살아남는다.
 */
@Component
public class SessionLifetimeListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return; // 요청 스레드 밖(배치·테스트) — no-op
        }
        // getSession(false) 필수: 미니앱 Bearer 체인은 STATELESS라 세션이 없고, 여기서 만들면 안 된다.
        HttpSession session = servletAttrs.getRequest().getSession(false);
        if (session == null) {
            return;
        }
        session.setMaxInactiveInterval((int) WebConfig.AUTHENTICATED_SESSION_TIMEOUT.toSeconds());
    }
}
