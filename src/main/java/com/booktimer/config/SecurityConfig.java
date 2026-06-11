package com.booktimer.config;

import com.booktimer.security.BookTimerOidcUserService;
import com.booktimer.security.LoginAttemptFilter;
import com.booktimer.security.LoginAttemptService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 웹 보안 설정 — 폼 로그인 + 세션 기반 인증.
 *
 * <p>인증 주체 조회는 {@link com.booktimer.security.BookTimerUserDetailsService}(이메일=식별자),
 * 비밀번호 검증은 여기 등록한 BCrypt {@link PasswordEncoder}가 담당한다. 두 빈이 있으면
 * Spring이 DaoAuthenticationProvider를 자동 구성해 폼 로그인 인증을 처리한다.
 *
 * <p>인가 정책: 기본 차단(default-deny). 로그인 페이지·정적 리소스·에러만 공개하고,
 * 나머지는 인증을 요구한다. 로그인 <b>화면</b>은 커스텀 {@code /login}({@link com.booktimer.web.LoginController})을
 * 쓰고, 인증 <b>처리</b>(POST /login)는 Security 필터가 담당한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 인증 성공/실패 이벤트 발행을 명시 보장한다. {@link com.booktimer.security.LoginAttemptEventListener}가
     * 이 이벤트로 IP별 실패를 집계하므로(무차별 대입 방어), 발행 빈을 직접 등록해 결정적으로 만든다.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BookTimerOidcUserService oidcUserService,
                                                   LoginAttemptService loginAttemptService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 루트("/")는 공개 소개(랜딩) 페이지를 겸한다 — 익명이면 서비스 소개, 로그인이면 대시보드로
                        // 컨트롤러가 분기한다(DashboardController). default-deny면 익명 루트가 로그인으로 302 튕겨
                        // 검색/광고 크롤러가 본문을 못 본다. 대시보드 데이터는 principal이 있을 때만 로드되므로 노출 없음.
                        // /verify-email: 가입 인증 링크는 비로그인 상태로 메일에서 열릴 수 있어 공개. (재발송 POST는
                        // 로그인 필요 — default-deny가 처리.) 토큰 자체가 자격이라 공개여도 안전(추측 불가·일회용·만료).
                        // /password/**: 비밀번호를 잊은 사용자는 비로그인 상태라 재설정 요청·확정 경로(forgot/reset)는 공개.
                        // 재설정도 토큰이 자격이라 공개여도 안전. CSRF는 POST 폼에 유지(아래 기본 활성).
                        // /unsubscribe: 마케팅 메일 수신거부 링크는 비로그인 상태로 메일에서 열린다(토큰이 신원 증명).
                        .requestMatchers("/", "/signup", "/login", "/privacy", "/verify-email", "/password/**", "/unsubscribe", "/error", "/actuator/health", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        // ads.txt(AdSense 소유권·수익 보호) + robots.txt(크롤 지시): 크롤러가 비인증으로 읽어야 하는
                        // 공개 정적 파일. default-deny라 명시 안 하면 로그인으로 302 튕겨 크롤러에게 모호한 신호가 된다.
                        .requestMatchers("/ads.txt", "/robots.txt").permitAll()
                        // OAuth2 인가요청·콜백 엔드포인트는 미인증 상태에서 접근 가능해야 한다.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 관리자 대시보드는 운영 데이터(개인정보)가 걸려 있어 ADMIN만 — default-deny 위에 역할 매처.
                        // (ROLE_ 접두는 BookTimerUserDetailsService가 부여, hasRole이 접두를 자동 보정한다.)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                // 소셜 로그인: 같은 커스텀 로그인 화면을 쓰고, OIDC 사용자 처리는 우리 어댑터에 위임한다.
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)))
                // 무차별 대입 방어: 잠긴 IP의 로그인 시도를 인증 필터에 닿기 전에 단락한다.
                .addFilterBefore(new LoginAttemptFilter(loginAttemptService),
                        UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout.permitAll());
        // CSRF는 기본 활성 유지 — 세션 기반 로그인이라 토큰 보호가 필요하다(REST 토큰 방식 아님).
        return http.build();
    }
}
