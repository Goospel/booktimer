package com.booktimer.web;

import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 비밀번호 재설정 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>핵심: {@code /password/forgot} POST는 계정 존재/부재/소셜 여부와 무관하게 <b>동일한 응답</b>이어야 한다
 * (열거완화). {@code GET /password/reset}은 토큰을 소비하지 않고 폼만 보여줘 메일 프리페치에 안전하며,
 * 사람이 폼을 제출한 {@code POST}에서만 토큰을 소비해 비밀번호를 바꾼다(#296 하드닝과 동일 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailTokenService tokenService;
    @Autowired
    private com.booktimer.email.EmailTokenRepository tokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetController passwordResetController;

    @Autowired
    private com.booktimer.security.RateLimitService rateLimitService;

    @org.junit.jupiter.api.BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // 인메모리 고정 윈도우 — 테스트 간 격리
    }

    /** 재설정 요청 — 출처 IP를 지정해 "IP를 바꿔 가며"를 재현한다. */
    private org.springframework.test.web.servlet.ResultActions submitForgot(String email, String ip)
            throws Exception {
        return mockMvc.perform(post("/password/forgot")
                .param("email", email)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrf()));
    }

    @Test
    @DisplayName("POST /password/forgot: 같은 IP의 11번째 요청은 막힌다 (10번째까지는 안내 페이지 — 양성 대조군)")
    void forgot_eleventhFromSameIp_limited() throws Exception {
        for (int i = 1; i <= 10; i++) {
            submitForgot("ip" + i + "@booktimer.com", "203.0.113.9")
                    .andExpect(view().name("password-forgot-sent"));
        }
        User victim = persistLocal("ip11@booktimer.com", "ipeleven");

        submitForgot("ip11@booktimer.com", "203.0.113.9")
                .andExpect(redirectedUrl("/password/forgot?limited"));

        // 막혔으니 requestReset에 닿지 않았다 — 실재 계정인데도 토큰이 하나도 발급되지 않았다.
        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(victim, EmailTokenType.PASSWORD_RESET)).isEmpty();
    }

    @Test
    @DisplayName("POST /password/forgot: 같은 이메일(대소문자·공백 변형 포함)은 IP를 바꿔도 4번째부터 막힌다 — 피해자의 재설정 링크가 계속 무효화되는 봉쇄를 끊는다")
    void forgot_sameEmailAcrossIps_limitedAtFourth() throws Exception {
        User victim = persistLocal("victim@booktimer.com", "victimone");

        // IP를 매번 바꾼다 — IP 키만 있으면 셋 다 통과하고 4번째도 통과한다.
        submitForgot("victim@booktimer.com", "198.51.100.1").andExpect(view().name("password-forgot-sent"));
        submitForgot("VICTIM@BookTimer.com", "198.51.100.2").andExpect(view().name("password-forgot-sent"));
        submitForgot("  victim@booktimer.com  ", "198.51.100.3").andExpect(view().name("password-forgot-sent"));

        Long liveToken = tokenRepository.findByUserAndTypeAndUsedAtIsNull(victim, EmailTokenType.PASSWORD_RESET)
                .get(0).getId();

        submitForgot("victim@booktimer.com", "198.51.100.4")
                .andExpect(redirectedUrl("/password/forgot?limited"));

        // 4번째가 requestReset에 닿았다면 issue가 직전 토큰을 죽이고 새 토큰을 냈을 것이다 —
        // 즉 피해자가 손에 든 링크가 또 무효화됐을 것이다. 살아 있는 토큰이 그대로여야 한다.
        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(victim, EmailTokenType.PASSWORD_RESET))
                .extracting(com.booktimer.email.EmailToken::getId)
                .containsExactly(liveToken);
    }

    @Test
    @DisplayName("POST /password/forgot: 존재하지 않는 이메일도 똑같이 4번째부터 막힌다 — 상한 반응이 계정 존재를 알려주는 채널이 되지 않는다")
    void forgot_absentEmail_hitsSameLimit_noEnumerationChannel() throws Exception {
        for (int i = 1; i <= 3; i++) {
            submitForgot("ghost@booktimer.com", "192.0.2." + i)
                    .andExpect(view().name("password-forgot-sent"));
        }

        submitForgot("ghost@booktimer.com", "192.0.2.9")
                .andExpect(redirectedUrl("/password/forgot?limited"));
    }

    @Test
    @DisplayName("GET /password/forgot?limited: 한도 초과 안내가 화면에 실제로 뜬다")
    void forgotForm_limitedParam_showsNotice() throws Exception {
        mockMvc.perform(get("/password/forgot").param("limited", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("요청이 너무 잦습니다")));
    }

    @Test
    @DisplayName("GET /password/forgot: 렌더 전 CSRF 토큰을 선확정한다 — 익명 폼 commit-후-500 방어(T-049 재발)")
    void forgotForm_precommitsCsrfToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        passwordResetController.forgotForm(request);

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /password/reset: 렌더 전 CSRF 토큰을 선확정한다 — 익명 폼 commit-후-500 방어(T-049 재발)")
    void resetForm_precommitsCsrfToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        passwordResetController.resetForm("any-token", request, mock(Model.class));

        verify(token).getToken();
    }

    private User persistLocal(String email, String handle) {
        User u = User.of(email, passwordEncoder.encode("oldPassword1"), "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        return userRepository.saveAndFlush(u);
    }

    private User persistSocial(String email, String handle) {
        User u = User.ofOAuth(email, "소셜러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        u.assignLoginId(handle);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("GET /password/forgot: 비로그인도 열리는 이메일 입력 폼을 보여준다")
    void forgotForm_isPublic() throws Exception {
        mockMvc.perform(get("/password/forgot"))
                .andExpect(status().isOk())
                .andExpect(view().name("password-forgot"));
    }

    @Test
    @DisplayName("POST /password/forgot: 존재하는 LOCAL 계정이면 발송 안내 페이지(토큰 발급됨)")
    void forgot_existingLocal_showsSentAndIssuesToken() throws Exception {
        User user = persistLocal("exists@booktimer.com", "existsone");

        mockMvc.perform(post("/password/forgot").param("email", "exists@booktimer.com").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-forgot-sent"));

        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.PASSWORD_RESET)).isNotEmpty();
    }

    @Test
    @DisplayName("POST /password/forgot: 존재하지 않는 이메일도 동일한 안내 페이지(열거완화)")
    void forgot_absentEmail_showsSameSentPage() throws Exception {
        mockMvc.perform(post("/password/forgot").param("email", "nobody@booktimer.com").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-forgot-sent"));
    }

    @Test
    @DisplayName("POST /password/forgot: 소셜 계정이면 동일한 안내 페이지지만 토큰은 발급되지 않는다")
    void forgot_socialAccount_showsSamePageButNoToken() throws Exception {
        User user = persistSocial("social@booktimer.com", "socialone");

        mockMvc.perform(post("/password/forgot").param("email", "social@booktimer.com").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-forgot-sent"));

        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.PASSWORD_RESET)).isEmpty();
    }

    @Test
    @DisplayName("GET /password/reset: 새 비번 폼을 보여주되 토큰을 소비하지 않는다(프리페치 안전)")
    void resetForm_showsFormWithoutConsuming() throws Exception {
        User user = persistLocal("reset@booktimer.com", "resetone");
        String raw = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);

        mockMvc.perform(get("/password/reset").param("token", raw))
                .andExpect(status().isOk())
                .andExpect(view().name("password-reset"))
                .andExpect(model().attribute("token", raw));

        // GET은 소비하지 않는다 — 토큰 여전히 유효(이후 POST로 재설정 가능)
        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.PASSWORD_RESET)).isNotEmpty();
    }

    @Test
    @DisplayName("POST /password/reset: 유효 토큰·일치하는 새 비번이면 비밀번호를 바꾸고 성공 페이지")
    void reset_validToken_changesPassword() throws Exception {
        User user = persistLocal("change@booktimer.com", "changeone");
        String raw = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);

        mockMvc.perform(post("/password/reset")
                        .param("token", raw)
                        .param("password", "brandNewPass1")
                        .param("passwordConfirm", "brandNewPass1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-reset-result"))
                .andExpect(model().attribute("success", true));

        User reloaded = userRepository.findByEmail("change@booktimer.com").orElseThrow();
        assertThat(passwordEncoder.matches("brandNewPass1", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("oldPassword1", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("POST /password/reset: 무효 토큰이면 success=false, 비밀번호는 그대로")
    void reset_invalidToken_showsFailure() throws Exception {
        User user = persistLocal("keep@booktimer.com", "keepone");

        mockMvc.perform(post("/password/reset")
                        .param("token", "bogus-token")
                        .param("password", "brandNewPass1")
                        .param("passwordConfirm", "brandNewPass1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-reset-result"))
                .andExpect(model().attribute("success", false));

        User reloaded = userRepository.findByEmail("keep@booktimer.com").orElseThrow();
        assertThat(passwordEncoder.matches("oldPassword1", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /password/reset: 토큰은 일회용 — 두 번째 POST는 success=false")
    void reset_tokenIsSingleUse() throws Exception {
        User user = persistLocal("once@booktimer.com", "onceone");
        String raw = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);

        mockMvc.perform(post("/password/reset")
                        .param("token", raw).param("password", "firstNewPass1").param("passwordConfirm", "firstNewPass1")
                        .with(csrf()))
                .andExpect(model().attribute("success", true));
        mockMvc.perform(post("/password/reset")
                        .param("token", raw).param("password", "secondNewPass1").param("passwordConfirm", "secondNewPass1")
                        .with(csrf()))
                .andExpect(model().attribute("success", false)); // 재사용 거부
    }

    @Test
    @DisplayName("POST /password/reset: 새 비번 불일치면 폼을 에러와 함께 다시 보여주고 토큰을 소비하지 않는다")
    void reset_mismatchedConfirm_reRendersFormKeepingToken() throws Exception {
        User user = persistLocal("mismatch@booktimer.com", "mismatch1");
        String raw = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);

        mockMvc.perform(post("/password/reset")
                        .param("token", raw)
                        .param("password", "brandNewPass1")
                        .param("passwordConfirm", "different9999")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-reset"))
                .andExpect(model().attribute("token", raw));

        // 검증 실패라 토큰을 소비하지 않았다 — 사용자가 고쳐서 다시 제출하면 여전히 유효
        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.PASSWORD_RESET)).isNotEmpty();
    }

    @Test
    @DisplayName("POST /password/reset: 너무 짧은 새 비번이면 폼을 에러와 함께 다시 보여주고 토큰을 소비하지 않는다")
    void reset_tooShortPassword_reRendersForm() throws Exception {
        User user = persistLocal("short@booktimer.com", "shortone");
        String raw = tokenService.issue(user, EmailTokenType.PASSWORD_RESET);

        mockMvc.perform(post("/password/reset")
                        .param("token", raw)
                        .param("password", "short")
                        .param("passwordConfirm", "short")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("password-reset"));

        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.PASSWORD_RESET)).isNotEmpty();
    }
}
