package com.booktimer.config;

import com.booktimer.security.CspNonceFilter;
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
     * 익명 세션 수명 — 24시간. 저장소 <b>기본값</b>이라 로그인 전 모든 세션(사람 방문객 + 봇·크롤러)에 적용된다.
     *
     * <p>이전에는 익명·로그인 구분 없이 30일이었는데, 운영 {@code SPRING_SESSION} 17,658행 중 <b>17,648행이 익명</b>으로
     * 97MB를 차지했다(t3.small MySQL 버퍼풀 256MB 대비 과대). 봇·크롤러의 방문 한 번이 한 달짜리 행을 남기는 구조라,
     * 로그인하지 않은 세션은 24시간만 유지한다.
     */
    private static final Duration ANONYMOUS_SESSION_TIMEOUT = Duration.ofDays(1);

    /**
     * 로그인 세션 수명 — 30일. 한 달간 비활성이어도 로그인이 유지되고 브라우저를 닫았다 와도 로그인이 살아 있게 한다.
     * 독서 타이머는 클라이언트(JS)에서만 돌고 읽는 동안 서버 요청이 없어, 짧은 타임아웃이면 측정 중 로그아웃된다.
     *
     * <p>기본값이 아니라 <b>로그인 성공 시에만</b> 적용된다 —
     * {@code com.booktimer.security.SessionLifetimeListener}가 이 상수로 세션을 연장한다(값의 단일 출처).
     */
    public static final Duration AUTHENTICATED_SESSION_TIMEOUT = Duration.ofDays(30);

    /**
     * 세션 비활성 타임아웃의 <b>기본값</b>을 {@link #ANONYMOUS_SESSION_TIMEOUT}으로 강제한다(이중 타임아웃의 하한).
     *
     * <p>application.properties의 {@code server.servlet.session.timeout}은 Boot 4 + Spring Session JDBC
     * 조합에서 세션 저장소의 기본 max-inactive-interval에 <b>연결되지 않는다</b>(기본 30분 그대로 — cookieSerializer·
     * ForwardedHeaderFilter와 같은 계열의 "프로퍼티 무동작" 함정). 그래서 저장소 빈에 직접 설정해 확실히 적용한다.
     */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionTimeoutCustomizer() {
        return repository -> repository.setDefaultMaxInactiveInterval(ANONYMOUS_SESSION_TIMEOUT);
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
     * CSP nonce 발급 + {@code Content-Security-Policy} 헤더 (근거·정책은 {@link CspNonceFilter} 주석).
     *
     * <p>Spring Security의 {@code headers().contentSecurityPolicy(...)}가 아니라 서블릿 필터로 두는 이유:
     * 그쪽은 <b>정적 문자열</b>만 받아 요청별 nonce를 낼 수 없다. 또 보안 체인이 두 개(세션·미니앱 Bearer)라
     * 필터로 두면 어느 체인을 타든, 로그인 리다이렉트·에러 응답이든 빠짐없이 붙는다.
     *
     * <p>순서는 {@code ForwardedHeaderFilter} 바로 뒤 — 뷰가 렌더되기 훨씬 전에 요청 속성을 심어야
     * 템플릿이 {@code ${cspNonce}}로 읽을 수 있다.
     */
    @Bean
    public FilterRegistrationBean<CspNonceFilter> cspNonceFilter() {
        FilterRegistrationBean<CspNonceFilter> registration =
                new FilterRegistrationBean<>(new CspNonceFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
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
        // 브라우저 종료 후에도 로그인 유지. 기본 -1이면 세션 쿠키라 창 닫으면 소멸.
        // 쿠키는 익명(24h)이 아니라 로그인 수명(30일)에 맞춘다 — 쿠키 하나로 두 수명을 나눌 수 없고,
        // 익명에게 30일 쿠키가 남아도 서버 세션이 24h에 만료되면 그 쿠키는 무효라 무해하다(비대칭 의도적).
        serializer.setCookieMaxAge((int) AUTHENTICATED_SESSION_TIMEOUT.toSeconds());
        return serializer;
    }
}
