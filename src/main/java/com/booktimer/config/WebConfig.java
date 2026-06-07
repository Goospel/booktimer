package com.booktimer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.time.Duration;

/**
 * 프록시(ALB) 뒤에서의 HTTPS 인식 설정.
 *
 * <p>운영에서 ALB가 TLS를 종료(termination)하고 평문 HTTP로 앱에 전달한다. 앱이 그대로면 자기 주소를
 * http로 인식해, 리다이렉트 URL과 특히 구글로 보내는 OAuth {@code redirect_uri}를 {@code http://...}로
 * 만든다 → 구글은 https만 허용하므로 {@code redirect_uri_mismatch}가 난다. {@link ForwardedHeaderFilter}가
 * {@code X-Forwarded-Proto/Host/Port}를 반영해 요청을 감싸면, 앱이 "나는 https로 호출됐다"를 올바로 인식한다.
 *
 * <p>{@code server.forward-headers-strategy=framework} 프로퍼티로도 같은 필터가 등록되지만, Boot 4의
 * 모듈 분리 환경에서 그 자동구성 빈이 일관되게 활성화되지 않아(컨텍스트에 등록 안 됨), 버전·환경에 무관하게
 * 확실히 동작하도록 필터를 명시 빈으로 직접 등록한다. 앱은 ALB 뒤(사설 네트워크)에만 노출되므로
 * forwarded 헤더 신뢰가 안전하다(N-021).
 */
@Configuration
public class WebConfig {

    /**
     * 세션·쿠키 수명 — 30일. 서버 세션 비활성 타임아웃과 쿠키 Max-Age를 <b>같은 값</b>으로 맞춰,
     * 한 달간 비활성이어도 로그인이 유지되고 브라우저를 닫았다 와도 로그인이 살아 있게 한다.
     * 독서 타이머는 클라이언트(JS)에서만 돌고 읽는 동안 서버 요청이 없어, 짧은 타임아웃이면 측정 중 로그아웃된다.
     */
    private static final Duration SESSION_TIMEOUT = Duration.ofDays(30);

    /**
     * 세션 비활성 타임아웃을 {@link #SESSION_TIMEOUT}으로 강제한다.
     *
     * <p>application.properties의 {@code server.servlet.session.timeout}은 Boot 4 + Spring Session JDBC
     * 조합에서 세션 저장소의 기본 max-inactive-interval에 <b>연결되지 않는다</b>(기본 30분 그대로 — cookieSerializer·
     * ForwardedHeaderFilter와 같은 계열의 "프로퍼티 무동작" 함정). 그래서 저장소 빈에 직접 설정해 확실히 적용한다.
     */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionTimeoutCustomizer() {
        return repository -> repository.setDefaultMaxInactiveInterval(SESSION_TIMEOUT);
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        // 보안 필터 체인보다 먼저 실행돼 요청 스킴/호스트를 먼저 바로잡아야 한다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 세션 쿠키({@code SESSION}) 속성을 명시 직렬화기로 설정한다.
     *
     * <p>세션 외부화(Spring Session JDBC) 이후 세션 쿠키는 서블릿 컨테이너가 아니라 Spring Session의
     * {@link DefaultCookieSerializer}가 쓴다. 이 조합에서는 {@code server.servlet.session.cookie.*}
     * 프로퍼티(same-site/secure/http-only)가 그 직렬화기에 연결되지 않아 <b>무동작</b>이다
     * (위 ForwardedHeaderFilter와 같은 Boot 4 함정 — 프로퍼티 대신 명시 빈으로 확실히 적용).
     * 이 빈이 없으면 SESSION 쿠키는 {@code Path=/}만 붙어 SameSite/Secure/HttpOnly가 모두 빠진다.
     *
     * <ul>
     *   <li><b>SameSite=Lax</b> — 교차 사이트 요청에 쿠키 자동 첨부 차단(CSRF 사전 방어).
     *       최상위 GET 내비게이션은 예외라 OAuth 리다이렉트 콜백과 호환된다(Strict는 콜백을 깸).</li>
     *   <li><b>HttpOnly</b> — JS에서 쿠키 접근 차단(XSS로 세션 탈취 방어). 모든 프로필 적용.</li>
     *   <li><b>Secure</b> — HTTPS에서만 전송. prod 전용(로컬은 http라 켜면 쿠키가 안 실려 로그인 불가)
     *       → {@code server.servlet.session.cookie.secure} 값으로 분기.</li>
     * </ul>
     */
    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("Lax");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        // 브라우저 종료 후에도 로그인 유지(서버 세션 타임아웃과 동일 수명). 기본 -1이면 세션 쿠키라 창 닫으면 소멸.
        serializer.setCookieMaxAge((int) SESSION_TIMEOUT.toSeconds());
        return serializer;
    }
}
