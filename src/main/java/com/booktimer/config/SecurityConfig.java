package com.booktimer.config;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.security.BearerTokenFilter;
import com.booktimer.security.BookTimerOidcUserService;
import com.booktimer.security.LoginAttemptFilter;
import com.booktimer.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    /**
     * 미니앱(앱인토스) 전용 인증 체인 — <b>기존 세션 체인은 한 글자도 건드리지 않는다</b>(설계 §2.3).
     *
     * <p>라우팅 스위치는 <b>{@code Authorization: Bearer} 헤더의 유무</b>다. 웹 Vue 섬의 {@code /api/**}
     * 호출은 쿠키·CSRF 토큰을 달고 Bearer 헤더는 없으므로 이 체인에 걸리지 않고 기존 체인으로 그대로 흐른다 —
     * 그래서 웹 채널이 무변경으로 산다(채널 병행이 요구사항이다).
     *
     * <p>매처에 두 가지를 더 얹는다:
     * <ul>
     *   <li>{@code /api/toss/**} — 로그인·가입·연결 요청은 <b>아직 토큰이 없어서</b> Bearer 헤더를 달 수 없다.
     *       헤더 조건만 두면 이 경로가 기존 체인으로 흘러 {@code /login} 302가 되어 미니앱이 시작조차 못 한다.</li>
     *   <li>CORS 프리플라이트(OPTIONS) — 브라우저는 프리플라이트에 {@code Authorization}을 싣지 않는다
     *       (요청 헤더 이름만 알린다). 여기서 안 받으면 교차 출처 호출이 첫 왕복부터 막힌다.</li>
     * </ul>
     *
     * <p>이 체인은 STATELESS(세션 미생성) + CSRF 비활성이다 — 쿠키를 아예 쓰지 않으므로 CSRF의 전제
     * (브라우저가 자격증명을 자동 첨부)가 성립하지 않는다. 세션 경로의 CSRF는 그대로 살아 있다(회귀 가드로 고정).
     */
    @Bean
    @Order(0)
    public SecurityFilterChain miniappApiFilterChain(HttpSecurity http,
                                                     ApiTokenService apiTokenService,
                                                     MiniappProperties miniappProperties) throws Exception {
        RequestMatcher miniappRequests = SecurityConfig::isMiniappApiRequest;
        http
                .securityMatcher(miniappRequests)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(miniappCorsSource(miniappProperties)))
                .authorizeHttpRequests(auth -> auth
                        // 토큰을 받기 전 단계라 인증을 요구할 수 없다. 대신 매 요청이 일회성 토스 인가코드로
                        // 신원을 다시 증명하고(서버에 pending 상태 없음), 레이트리밋이 남용을 막는다.
                        .requestMatchers("/api/toss/login", "/api/toss/register", "/api/toss/link").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new BearerTokenFilter(apiTokenService),
                        UsernamePasswordAuthenticationFilter.class)
                // 기본 엔트리포인트는 403(또는 로그인 리다이렉트)이라, API 클라이언트가 "토큰 만료"를 알아채고
                // 재로그인 흐름을 타려면 401이어야 한다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /** 이 요청을 미니앱 체인이 맡는가 — {@code /api/toss/**} 전부, 그 외 {@code /api/**}는 Bearer·프리플라이트일 때만. */
    private static boolean isMiniappApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            return false;
        }
        return path.startsWith("/api/toss/")
                || BearerTokenFilter.extractToken(request) != null
                || CorsUtils.isPreFlightRequest(request);
    }

    /**
     * 미니앱 교차 출처 설정. 허용 오리진이 <b>비어 있으면 설정을 하나도 등록하지 않은 빈 소스</b>를 준다 —
     * 어느 경로에도 매칭되지 않으므로 CORS 헤더가 붙지 않는다. 실제 WebView 오리진은 PR-0 실측으로 확정되므로,
     * 그전까지는 교차 출처를 열지 않는 것이 안전 기본값이다.
     *
     * <p>{@code allowCredentials}는 false다 — 미니앱은 쿠키를 쓰지 않고 Bearer 헤더로만 인증한다(설계 §2.1).
     */
    private static CorsConfigurationSource miniappCorsSource(MiniappProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.getAllowedOrigins().isEmpty()) {
            return source; // 등록된 설정 없음 = CORS 헤더 없음(동일 출처 웹 호출에는 아무 영향 없음)
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/**", config);
        return source;
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
                        // /dashboard: "/"의 별칭 — 동결된 구 PWA install이 콜드 런치 시 쏘는 구 start_url. "/"와
                        // 똑같이 공개여야 미인증 콜드 런치도 landing을 보고 404/로그인 튕김 없이 들어온다(DashboardController).
                        // ⚠️ content-hash 정적자산(spring.web.resources.chain): @{/pwa-install.js}·@{/manifest.json}는
                        // /pwa-install-<md5>.js · /manifest-<md5>.json 으로 렌더된다. 정확 매칭만 두면 해시 URL이 누락돼
                        // 미인증 페이지 로드 시 302→SavedRequest로 저장되고, 로그인 성공 후 그 자산으로 리다이렉트되는
                        // 버그가 난다(캐시 빈 신규 세션에서만 재현 — E2E auth/garden 스펙이 회귀 가드). 와일드카드로 해시 변형까지 허용.
                        // /actuator/health/**: 전체 health에 더해 프로브 하위경로(readiness·liveness)도 공개한다.
                        // 리버스프록시(Caddy) blue-green 전환이 upstream 헬스체크로 readiness를 쓰는데, 여기가 막혀
                        // 302가 나가면 프록시가 앱을 영영 unhealthy로 보고 서비스 전체가 503이 된다.
                        // 노출 표면은 {"status":"UP"} 뿐(show-details 기본 never)이라 /actuator/health와 동급이다.
                        .requestMatchers("/", "/dashboard", "/signup", "/login", "/privacy", "/terms", "/verify-email", "/password/**", "/unsubscribe", "/error", "/actuator/health", "/actuator/health/**", "/css/**", "/js/**", "/favicon.ico", "/manifest*.json", "/icons/**", "/sw.js", "/pwa-install*.js").permitAll()
                        // ads.txt(AdSense 소유권·수익 보호) + robots.txt(크롤 지시): 크롤러가 비인증으로 읽어야 하는
                        // 공개 정적 파일. default-deny라 명시 안 하면 로그인으로 302 튕겨 크롤러에게 모호한 신호가 된다.
                        .requestMatchers("/ads.txt", "/robots.txt").permitAll()
                        // /login/toss-code: 토스 미니앱이 발급한 일회용 코드로 세션을 여는 경로. 로그인
                        // '전'이라 당연히 공개여야 하고(막으면 코드를 넣을 방법 자체가 없다), /login은 정확
                        // 매칭이라 하위 경로가 자동으로 딸려오지 않는다. 코드 자체가 자격이라 공개여도
                        // 안전하다(추측 불가·일회용·TTL 5분 + IP 상한). ⚠️ CSRF 예외로는 두지 않는다 —
                        // 예외로 두면 다른 사이트가 사용자의 브라우저로 코드를 대신 제출할 수 있다.
                        .requestMatchers("/login/toss-code").permitAll()
                        // OAuth2 인가요청·콜백 엔드포인트는 미인증 상태에서 접근 가능해야 한다.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // SES 반송·불만 SNS 웹훅 — 외부(AWS SNS)가 POST하는 공개 엔드포인트. 로그인 대신
                        // TopicArn 허용목록 + SNS 서명 검증(SnsWebhookController)으로 위조를 막는다(CSRF는 아래서 제외).
                        .requestMatchers("/internal/ses/**").permitAll()
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
                .logout(logout -> logout.permitAll())
                // SNS 웹훅은 외부 시스템(AWS)이 CSRF 토큰 없이 POST하므로 이 경로만 CSRF 제외(서명 검증으로 대체 방어).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/ses/**"));
        // CSRF는 기본 활성 유지(위 SNS 웹훅만 예외) — 세션 기반 로그인이라 토큰 보호가 필요하다(REST 토큰 방식 아님).
        return http.build();
    }
}
