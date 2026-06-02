package com.booktimer.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * 인증 결과 이벤트를 듣고 {@link LoginAttemptService}에 실패/성공을 집계한다.
 *
 * <p>Spring Security가 폼 로그인 인증을 마치면 성공/실패 이벤트를 발행한다(이벤트 발행은
 * {@code SecurityConfig}의 {@code AuthenticationEventPublisher} 빈으로 보장). 여기서 클라이언트
 * IP를 꺼내 키로 삼는다 — IP는 인증 토큰의 {@link WebAuthenticationDetails}에 실려 온다.
 * IP를 알 수 없는 경우(예: 비웹 인증)는 집계하지 않는다.
 */
@Component
public class LoginAttemptEventListener {

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptEventListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        clientIp(event.getAuthentication()).ifPresent(loginAttemptService::recordFailure);
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        clientIp(event.getAuthentication()).ifPresent(loginAttemptService::recordSuccess);
    }

    private static java.util.Optional<String> clientIp(Authentication authentication) {
        if (authentication != null
                && authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return java.util.Optional.ofNullable(details.getRemoteAddress());
        }
        return java.util.Optional.empty();
    }
}
