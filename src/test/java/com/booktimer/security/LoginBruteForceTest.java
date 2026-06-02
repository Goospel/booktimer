package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * 로그인 무차별 대입 방어 통합 테스트 (MockMvc, 실제 Security 필터 체인).
 *
 * <p>같은 IP에서 비밀번호를 {@link LoginAttemptService#MAX_ATTEMPTS}회 틀리면, 그 뒤로는
 * <b>올바른 비밀번호로도</b> 로그인이 차단(미인증)되어야 한다. 다른 IP는 영향을 받지 않는다.
 * 실패/성공 집계는 인증 이벤트로, 차단은 {@code LoginAttemptFilter}가 담당한다.
 */
@SpringBootTest
@Transactional
class LoginBruteForceTest {

    private static final String EMAIL = "victim@booktimer.com";
    private static final String PASSWORD = "rawpw1234";
    private static final String ATTACKER_IP = "203.0.113.7";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.save(User.of(
                EMAIL, passwordEncoder.encode(PASSWORD), "책벌레", "Asia/Seoul", Role.USER));
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void attemptLogin(String ip, String password) throws Exception {
        mvc.perform(post("/login")
                .param("username", EMAIL)
                .param("password", password)
                .with(csrf())
                .with(fromIp(ip)));
    }

    @Test
    @DisplayName("같은 IP에서 MAX회 실패하면, 올바른 비밀번호로도 차단된다")
    void afterMaxFailures_correctPasswordBlocked() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            attemptLogin(ATTACKER_IP, "WRONG");
        }

        // 이제 올바른 비밀번호여도 차단 — 미인증 + 로그인 에러로 리다이렉트.
        mvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .with(csrf())
                        .with(fromIp(ATTACKER_IP)))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("한 IP가 잠겨도 다른 IP는 올바른 비밀번호로 정상 로그인된다")
    void otherIp_unaffected() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            attemptLogin(ATTACKER_IP, "WRONG");
        }

        mvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .with(csrf())
                        .with(fromIp("198.51.100.20")))
                .andExpect(authenticated().withUsername(EMAIL));
    }
}
