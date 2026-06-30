package com.booktimer.web;

import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 가입 이메일 인증 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>인증 링크(GET /verify-email)는 비로그인으로 열려야 하고(공개), 유효 토큰이면 사용자가 검증 완료로
 * 바뀌며, 무효/만료 토큰이면 안내 페이지를 보여주되 아무 것도 검증하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailTokenService tokenService;
    @Autowired
    private com.booktimer.email.EmailTokenRepository tokenRepository;

    private User persistUser(String email, String handle) {
        User u = User.of(email, "hash", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        return userRepository.saveAndFlush(u);
    }

    @Autowired
    private EmailVerificationController controller;

    @Test
    @DisplayName("GET /verify-email: 렌더 전 CSRF 토큰을 선확정한다 — SSR 폼 commit-후-500 방어(T-049 재발)")
    void verifyForm_precommitsCsrfToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        controller.verifyForm(request, "tok", new ConcurrentModel());

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /verify-email: 비로그인도 열리는 확인 페이지를 보여주되 토큰을 소비하지 않는다(프리페치 안전)")
    void get_showsConfirmPage_withoutConsuming() throws Exception {
        User user = persistUser("verify@booktimer.com", "verifyme");
        String raw = tokenService.issue(user, EmailTokenType.VERIFICATION);

        mockMvc.perform(get("/verify-email").param("token", raw))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-email-confirm"))
                .andExpect(model().attribute("token", raw));

        // GET은 소비하지 않는다 — 사용자 미검증 유지 + 토큰 여전히 유효(이후 POST로 검증 가능)
        assertThat(userRepository.findByEmail("verify@booktimer.com").orElseThrow().isEmailVerified()).isFalse();
        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.VERIFICATION)).isNotEmpty();
    }

    @Test
    @DisplayName("POST /verify-email: 유효 토큰이면 사용자를 검증 완료로 바꾸고 결과 페이지를 보여준다")
    void post_validToken_marksVerified() throws Exception {
        User user = persistUser("postverify@booktimer.com", "postverify");
        String raw = tokenService.issue(user, EmailTokenType.VERIFICATION);

        mockMvc.perform(post("/verify-email").param("token", raw).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-email-result"))
                .andExpect(model().attribute("verified", true));

        assertThat(userRepository.findByEmail("postverify@booktimer.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("POST /verify-email: 무효 토큰이면 verified=false 안내 페이지, 검증되지 않는다")
    void post_invalidToken_showsFailure() throws Exception {
        User user = persistUser("noverify@booktimer.com", "noverify");

        mockMvc.perform(post("/verify-email").param("token", "bogus-token").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-email-result"))
                .andExpect(model().attribute("verified", false));

        assertThat(userRepository.findByEmail("noverify@booktimer.com").orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("POST /verify-email: 토큰은 일회용 — 두 번째 POST는 verified=false")
    void post_tokenIsSingleUse() throws Exception {
        User user = persistUser("once@booktimer.com", "onceuser");
        String raw = tokenService.issue(user, EmailTokenType.VERIFICATION);

        mockMvc.perform(post("/verify-email").param("token", raw).with(csrf()))
                .andExpect(model().attribute("verified", true));
        mockMvc.perform(post("/verify-email").param("token", raw).with(csrf()))
                .andExpect(model().attribute("verified", false)); // 재사용 거부
    }

    @Test
    @DisplayName("프리페치 안전: GET을 여러 번 해도 토큰이 소진되지 않아 이후 POST로 정상 검증된다")
    void get_prefetchDoesNotBurnToken() throws Exception {
        User user = persistUser("prefetch@booktimer.com", "prefetchu");
        String raw = tokenService.issue(user, EmailTokenType.VERIFICATION);

        // 메일 클라이언트 프리페치 시뮬레이션 — GET 2회
        mockMvc.perform(get("/verify-email").param("token", raw)).andExpect(status().isOk());
        mockMvc.perform(get("/verify-email").param("token", raw)).andExpect(status().isOk());

        // 사람이 버튼을 눌러 POST하면 여전히 검증 성공
        mockMvc.perform(post("/verify-email").param("token", raw).with(csrf()))
                .andExpect(model().attribute("verified", true));
        assertThat(userRepository.findByEmail("prefetch@booktimer.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("POST /verify-email/resend: 로그인·미검증이면 인증 토큰을 재발급하고 설정으로 리다이렉트")
    void resend_unverified_issuesTokenAndRedirects() throws Exception {
        User user = persistUser("resend@booktimer.com", "resender");

        mockMvc.perform(post("/verify-email/resend").with(user("resender")).with(csrf()))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("verifyResendResult", "sent"));

        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.VERIFICATION)).isNotEmpty();
    }

    @Test
    @DisplayName("POST /verify-email/resend: 이미 검증된 사용자면 재발송하지 않고 already 안내")
    void resend_alreadyVerified_skips() throws Exception {
        User user = persistUser("already@booktimer.com", "already1");
        user.verifyEmail();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/verify-email/resend").with(user("already1")).with(csrf()))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("verifyResendResult", "already"));

        assertThat(tokenRepository.findByUserAndTypeAndUsedAtIsNull(user, EmailTokenType.VERIFICATION)).isEmpty();
    }
}
