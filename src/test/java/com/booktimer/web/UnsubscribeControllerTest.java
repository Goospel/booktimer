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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 구독해지 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>수신거부 링크는 비로그인으로 열려야 하고(공개), <b>GET은 토큰을 소비하지 않는다</b>(메일 프리페치 안전 —
 * verify-email과 동일 패턴). 사람이 버튼을 눌러야 하는 POST에서만 동의가 꺼지고 토큰이 소진된다(일회용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnsubscribeControllerTest {

    private static final Clock CLK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailTokenService tokenService;

    private User consentedUser(String email, String handle) {
        User u = User.of(email, "hash", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        u.consentToMarketing(CLK);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("GET /unsubscribe: 비로그인도 열리는 확인 페이지를 보여주되 해지하지 않는다(프리페치 안전)")
    void get_showsConfirm_withoutUnsubscribing() throws Exception {
        User user = consentedUser("keep@booktimer.com", "keepme");
        String raw = tokenService.issue(user, EmailTokenType.UNSUBSCRIBE);

        mockMvc.perform(get("/unsubscribe").param("token", raw))
                .andExpect(status().isOk())
                .andExpect(view().name("unsubscribe-confirm"))
                .andExpect(model().attribute("token", raw));

        // GET은 동의를 끄지 않는다 — 여전히 수신 동의 유지
        assertThat(userRepository.findByEmail("keep@booktimer.com").orElseThrow().isMarketingEmailConsent()).isTrue();
    }

    @Test
    @DisplayName("POST /unsubscribe: 유효 토큰이면 동의를 끄고 완료 페이지를 보여준다")
    void post_validToken_withdrawsConsent() throws Exception {
        User user = consentedUser("bye@booktimer.com", "byebye");
        String raw = tokenService.issue(user, EmailTokenType.UNSUBSCRIBE);

        mockMvc.perform(post("/unsubscribe").param("token", raw).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("unsubscribe-done"))
                .andExpect(model().attribute("unsubscribed", true));

        assertThat(userRepository.findByEmail("bye@booktimer.com").orElseThrow().isMarketingEmailConsent()).isFalse();
    }

    @Test
    @DisplayName("POST /unsubscribe: 토큰은 일회용 — 두 번째 POST는 unsubscribed=false")
    void post_tokenIsSingleUse() throws Exception {
        User user = consentedUser("once@booktimer.com", "onceuser");
        String raw = tokenService.issue(user, EmailTokenType.UNSUBSCRIBE);

        mockMvc.perform(post("/unsubscribe").param("token", raw).with(csrf()))
                .andExpect(model().attribute("unsubscribed", true));
        mockMvc.perform(post("/unsubscribe").param("token", raw).with(csrf()))
                .andExpect(model().attribute("unsubscribed", false)); // 재사용 거부
    }

    @Test
    @DisplayName("POST /unsubscribe: 무효 토큰이면 unsubscribed=false 안내, 상태 불변")
    void post_invalidToken_showsFailure() throws Exception {
        consentedUser("safe@booktimer.com", "safeuser");

        mockMvc.perform(post("/unsubscribe").param("token", "bogus").with(csrf()))
                .andExpect(model().attribute("unsubscribed", false));

        assertThat(userRepository.findByEmail("safe@booktimer.com").orElseThrow().isMarketingEmailConsent()).isTrue();
    }
}
