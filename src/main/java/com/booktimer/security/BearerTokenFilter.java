package com.booktimer.security;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer <token>} 헤더를 인증으로 바꾸는 필터 — 미니앱 전용 체인에만 등록된다.
 *
 * <p><b>principal 이름을 {@code loginId != null ? loginId : email}로 맞추는 것이 이 필터의 핵심</b>이다.
 * {@link CurrentUserService}가 login_id → email 순으로 브리지 조회를 하므로, 이름만 이렇게 맞춰 주면
 * <b>기존 {@code /api/**} 컨트롤러를 한 줄도 고치지 않고</b> 그대로 재사용할 수 있다(설계 §2.3).
 * 미니앱 신규 계정은 login_id가 없어(§2.4) email 브리지를 타는 게 정상 경로다.
 *
 * <p>토큰이 없거나 무효면 <b>인증을 세우지 않고 그냥 통과</b>시킨다 — 거부는 체인의
 * {@code anyRequest().authenticated()} + 401 엔트리포인트가 한다(필터가 응답을 직접 쓰면 permitAll
 * 경로인 {@code /api/toss/login}까지 막힌다).
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final ApiTokenService apiTokenService;

    public BearerTokenFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    /** 요청에서 Bearer 토큰을 꺼낸다(없으면 null) — 체인 라우팅 판정과 공유하는 단일 규칙. */
    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiTokenService.authenticate(token).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {
        String principalName = user.getLoginId() != null ? user.getLoginId() : user.getEmail();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principalName, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
