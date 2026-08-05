package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

/**
 * 세션 쿠키 {@code SameSite=Lax} 명시 검증.
 *
 * <p>CSRF 토큰 방어에 더해, 세션 쿠키에 {@code SameSite=Lax}를 두면 브라우저가 교차 사이트
 * 요청에 쿠키를 자동 첨부하지 않아(최상위 GET 내비게이션은 예외) CSRF의 사전 차단이 된다.
 * Lax는 OAuth 리다이렉트(최상위 GET 콜백)와 호환되므로 Strict보다 안전한 선택이다.
 *
 * <p><b>주의</b>: 세션 외부화(Spring Session JDBC) 이후 세션 쿠키({@code SESSION})는 서블릿
 * 컨테이너가 아니라 Spring Session의 {@code DefaultCookieSerializer}가 쓴다. 따라서 이 속성이
 * 실제로 그 쿠키에 실리는지는 응답 {@code Set-Cookie} 헤더로 직접 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionCookieSameSiteTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 30일(2,592,000초) — 쿠키 Max-Age는 로그인 수명(30일)에 맞춘다(익명 세션은 서버에서 24h에 만료). */
    private static final long THIRTY_DAYS_SECONDS = 30L * 24 * 3600;

    @AfterEach
    void cleanUp() {
        try {
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION");
        } catch (Exception ignored) {
            // 세션 테이블 미존재 시 무시
        }
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인으로 발급되는 세션 쿠키(SESSION)에 SameSite=Lax가 실린다")
    void sessionCookie_hasSameSiteLax() throws Exception {
        User u = User.of("reader@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader1");
        userRepository.save(u);

        MvcResult result = mockMvc.perform(
                        formLogin("/login").user("reader1").password("rawpw1234"))
                .andExpect(authenticated().withUsername("reader1"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        String sessionCookie = setCookies.stream()
                .filter(c -> c.startsWith("SESSION="))
                .findFirst()
                .orElse(null);

        assertThat(sessionCookie)
                .as("로그인 응답에 SESSION 쿠키가 Set-Cookie로 실려야 한다")
                .isNotNull();
        assertThat(sessionCookie)
                .as("세션 쿠키에 SameSite=Lax가 명시되어야 한다")
                .containsIgnoringCase("SameSite=Lax");
        assertThat(sessionCookie)
                .as("세션 쿠키에 HttpOnly가 명시되어야 한다 (XSS 세션 탈취 방어)")
                .containsIgnoringCase("HttpOnly");
    }

    // 세션 비활성 타임아웃(익명 24h / 로그인 30일) 검증은 SessionDualTimeoutTest로 이동했다.

    @Test
    @DisplayName("세션 쿠키(SESSION)에 30일 Max-Age가 실려 브라우저를 닫아도 로그인이 유지된다")
    void sessionCookie_hasPersistentMaxAge() throws Exception {
        User u = User.of("reader2@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레2", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader2");
        userRepository.save(u);

        MvcResult result = mockMvc.perform(
                        formLogin("/login").user("reader2").password("rawpw1234"))
                .andExpect(authenticated().withUsername("reader2"))
                .andReturn();

        String sessionCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("SESSION="))
                .findFirst()
                .orElse(null);

        assertThat(sessionCookie)
                .as("로그인 응답에 SESSION 쿠키가 있어야 한다")
                .isNotNull();
        assertThat(sessionCookie)
                .as("세션 쿠키에 30일 Max-Age가 실려 브라우저 종료 후에도 유지되어야 한다")
                .containsIgnoringCase("Max-Age=" + THIRTY_DAYS_SECONDS);
    }
}
