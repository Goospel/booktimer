package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 세션 이중 타임아웃 검증 — 익명 24시간 / 로그인 30일.
 *
 * <p>운영 {@code SPRING_SESSION} 17,658행 중 17,648행이 익명(봇·크롤러 포함)으로 97MB를 차지했다.
 * 익명 세션까지 30일을 주면 봇 방문 한 번이 한 달짜리 행을 남긴다 → 기본값을 24시간으로 줄이고,
 * 로그인에 성공한 세션만 30일로 연장한다(로그인 사용자 체감은 무변화).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionDualTimeoutTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    private static final long ONE_DAY_SECONDS = 24L * 3600;
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
    @DisplayName("저장소 기본(=익명) 세션 타임아웃은 24시간이다 — 봇·크롤러가 30일짜리 행을 남기지 않게")
    void anonymousSessionTimeout_isOneDay() {
        // createSession()은 DB에 쓰지 않고 설정된 기본 max-inactive-interval을 그대로 반영한다.
        Session newSession = sessionRepository.createSession();

        assertThat(newSession.getMaxInactiveInterval())
                .as("익명 세션 기본 타임아웃은 24시간이어야 한다")
                .isEqualTo(Duration.ofSeconds(ONE_DAY_SECONDS));
    }

    @Test
    @DisplayName("폼 로그인에 성공한 세션은 30일로 연장돼 저장소에 반영된다 — 독서 중 서버 요청이 없어도 유지")
    void authenticatedSessionTimeout_isExtendedToThirtyDays() throws Exception {
        User u = User.of("reader@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader1");
        userRepository.save(u);

        // 실제 브라우저 흐름 그대로: 로그인 페이지 GET에서 세션(CSRF 토큰 보관)이 먼저 생기고,
        // 그 세션 쿠키를 들고 POST 한다. MockMvc formLogin()은 세션 없이 CSRF를 우회해 이 경로를 못 재현한다.
        MvcResult loginPage = mockMvc.perform(get("/login")).andReturn();
        Cookie sessionCookie = loginPage.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).as("로그인 페이지 GET에서 익명 세션 쿠키가 발급되어야 한다").isNotNull();

        mockMvc.perform(post("/login")
                        .param("username", "reader1")
                        .param("password", "rawpw1234")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(authenticated().withUsername("reader1"));

        // 세션 고정 보호(changeSessionId) 이후에도 연장된 값이 살아남아 DB에 저장되어야 한다.
        Integer maxInactive = jdbcTemplate.queryForObject(
                "SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class, "reader1");

        assertThat(maxInactive)
                .as("로그인 세션의 타임아웃은 30일이어야 한다")
                .isEqualTo((int) THIRTY_DAYS_SECONDS);
    }

    @Test
    @DisplayName("요청 스레드 밖(RequestAttributes 없음)에서 인증 성공 이벤트가 와도 예외 없이 no-op")
    void listener_noRequestAttributes_isNoOp() {
        RequestContextHolder.resetRequestAttributes();

        new SessionLifetimeListener().onAuthenticationSuccess(successEvent());
        // 예외 없이 통과하면 성공 (배치·테스트 등 비웹 인증 경로 보호)
    }

    @Test
    @DisplayName("세션 없는 인증(미니앱 Bearer STATELESS)에서는 세션을 새로 만들지 않고 no-op")
    void listener_noSession_doesNotCreateSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            new SessionLifetimeListener().onAuthenticationSuccess(successEvent());

            assertThat(request.getSession(false))
                    .as("STATELESS 인증에 세션을 새로 만들면 안 된다")
                    .isNull();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static AuthenticationSuccessEvent successEvent() {
        return new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("reader1", null, List.of()));
    }
}
