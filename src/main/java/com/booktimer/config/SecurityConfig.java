package com.booktimer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 웹 보안 설정 — 폼 로그인 + 세션 기반 인증.
 *
 * <p>인증 주체 조회는 {@link com.booktimer.security.BookTimerUserDetailsService}(이메일=식별자),
 * 비밀번호 검증은 여기 등록한 BCrypt {@link PasswordEncoder}가 담당한다. 두 빈이 있으면
 * Spring이 DaoAuthenticationProvider를 자동 구성해 폼 로그인 인증을 처리한다.
 *
 * <p>인가 정책: 기본 차단(default-deny). 로그인 페이지·정적 리소스·에러만 공개하고,
 * 나머지는 인증을 요구한다. 로그인 페이지/처리는 Security가 자동 생성한 {@code /login}을 쓴다
 * (커스텀 화면은 웹 계층 증분에서 교체).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.permitAll())
                .logout(logout -> logout.permitAll());
        // CSRF는 기본 활성 유지 — 세션 기반 폼 로그인이라 토큰 보호가 필요하다(REST 토큰 방식 아님).
        return http.build();
    }
}
