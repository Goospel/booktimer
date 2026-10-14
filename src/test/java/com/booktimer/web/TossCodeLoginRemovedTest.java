package com.booktimer.web;

import com.booktimer.security.RateLimitService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 토스 → 웹 코드 로그인({@code POST /login/toss-code})은 2026-10에 걷었다 — 서비스 오픈 정책 §2-2
 * (미니앱이 자사 웹 이용을 돕는 컴포넌트를 두지 않는다)·비게임 체크리스트 74행.
 *
 * <p>운영 DB에는 폐지 직전에 발급돼 아직 안 쓴 {@code WEB_LOGIN} 코드가 남을 수 있다. 그 코드를 들고 와도
 * 세션이 열리지 않아야 한다 — 소비 지점이 어디에도 남아 있지 않다는 것을 행동으로 단언한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TossCodeLoginRemovedTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TossLinkCodeRepository linkCodeRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock; // ⚠️ Instant.now()를 쓰면 안 된다 — 테스트 컨텍스트의 Clock이 고정·가변이면 RED가 「만료」로 거짓 통과한다

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // 인메모리 고정 윈도우 — 테스트 간 격리
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION");
        } catch (Exception ignored) {
            // 세션 테이블 미존재 시 무시
        }
        linkCodeRepository.deleteAll(); // 자식 먼저 — FK(toss_link_code.user_id)가 users 삭제를 막는다
        userRepository.deleteAll();
    }

    /** 토스로 시작한 계정 — login_id 없음·비밀번호 없음·toss_user_key만 있다. */
    private User tossUser(String email, String userKey) {
        User u = User.ofOAuth(email, "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        u.linkTossUserKey(userKey);
        return userRepository.save(u);
    }

    /** 실제 브라우저 흐름 그대로: GET /login에서 세션(CSRF 토큰 보관)이 먼저 생기고, 그 쿠키를 들고 POST 한다. */
    private Cookie loginPageSession() throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();
        Cookie cookie = page.getResponse().getCookie("SESSION");
        assertThat(cookie).as("GET /login에서 익명 세션 쿠키가 발급되어야 한다").isNotNull();
        return cookie;
    }

    private ResultActions submit(String code, Cookie session) throws Exception {
        return mockMvc.perform(post("/login/toss-code")
                .param("code", code)
                .cookie(session)
                .with(csrf()));
    }

    @Test
    @DisplayName("REQ-09 · 토스 코드 로그인 경로는 유효한 옛 코드로도 세션을 열지 않는다")
    void tossCodeLogin_isGone_evenWithValidOldCode() throws Exception {
        User u = tossUser("toss-gone@noreply.booktimer.app", "uk-gone-1");
        String raw = "ABCD2345"; // 서버 normalize 뒤 모양(대문자·공백 없음)으로 해시해야 옛 소비 지점이 실제로 찾는다
        linkCodeRepository.save(TossLinkCode.issue(
                u, sha256(raw), clock.instant().plus(Duration.ofMinutes(5)), TossLinkCode.Purpose.WEB_LOGIN));

        submit(raw, loginPageSession()).andExpect(unauthenticated());
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
