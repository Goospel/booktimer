package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 보안 화면/처리 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>비밀번호 변경과 회원 탈퇴는 모두 PRG(Post-Redirect-Get)로 동작한다 — 성공/실패 메시지는
 * 플래시로 싣고 리다이렉트한다. 와이어링(인증→서비스→리다이렉트·플래시)을 보고, 검증 규칙은
 * 하위 단위 테스트에 위임한다(N-009). 비밀번호가 실제로 바뀌었는지/계정이 지워졌는지는 직접 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private void register(String email) {
        registrationService.register(email, "oldpw1234", "독서가", SEOUL, Role.USER, today());
    }

    private void registerOAuth(String email) {
        registrationService.registerOAuth(email, "구글러", SEOUL,
                com.booktimer.user.AuthProvider.GOOGLE, today());
    }

    // --- 비밀번호 변경 ---

    @Test
    @DisplayName("POST /settings/password: 현재 비밀번호가 맞으면 새 비밀번호로 바꾸고 성공 플래시")
    void changePassword_valid_updatesAndFlashes() throws Exception {
        register("pw@booktimer.com");

        mockMvc.perform(post("/settings/password").with(user("pw@booktimer.com")).with(csrf())
                        .param("currentPassword", "oldpw1234")
                        .param("newPassword", "newpw5678")
                        .param("confirmPassword", "newpw5678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("message"));

        String hash = userRepository.findByEmail("pw@booktimer.com").orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches("newpw5678", hash)).isTrue();
    }

    @Test
    @DisplayName("POST /settings/password: 현재 비밀번호가 틀리면 에러 플래시, 비밀번호 유지")
    void changePassword_wrongCurrent_errorFlash() throws Exception {
        register("pwbad@booktimer.com");

        mockMvc.perform(post("/settings/password").with(user("pwbad@booktimer.com")).with(csrf())
                        .param("currentPassword", "WRONGpw")
                        .param("newPassword", "newpw5678")
                        .param("confirmPassword", "newpw5678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));

        String hash = userRepository.findByEmail("pwbad@booktimer.com").orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches("oldpw1234", hash)).isTrue(); // 그대로
    }

    @Test
    @DisplayName("POST /settings/password: 새 비밀번호 확인이 일치하지 않으면 에러 플래시")
    void changePassword_mismatchConfirm_errorFlash() throws Exception {
        register("pwmis@booktimer.com");

        mockMvc.perform(post("/settings/password").with(user("pwmis@booktimer.com")).with(csrf())
                        .param("currentPassword", "oldpw1234")
                        .param("newPassword", "newpw5678")
                        .param("confirmPassword", "DIFFERENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));
    }

    // --- 회원 탈퇴 ---

    @Test
    @DisplayName("POST /settings/delete: 비밀번호가 맞으면 계정을 지우고 로그인(?deleted)으로 보낸다")
    void deleteAccount_valid_removesAccountAndRedirects() throws Exception {
        register("del@booktimer.com");

        mockMvc.perform(post("/settings/delete").with(user("del@booktimer.com")).with(csrf())
                        .param("password", "oldpw1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.findByEmail("del@booktimer.com")).isEmpty();
    }

    @Test
    @DisplayName("POST /settings/delete: 비밀번호가 틀리면 에러 플래시, 계정 유지")
    void deleteAccount_wrongPassword_errorFlash() throws Exception {
        register("delbad@booktimer.com");

        mockMvc.perform(post("/settings/delete").with(user("delbad@booktimer.com")).with(csrf())
                        .param("password", "WRONG"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));

        assertThat(userRepository.findByEmail("delbad@booktimer.com")).isPresent();
    }

    // --- 소셜(OAuth) 계정: 비밀번호 없음 ---

    @Test
    @DisplayName("GET /settings: 소셜 계정이면 localAccount=false (비밀번호 변경 카드 숨김 신호)")
    void settings_oauthUser_localAccountFalse() throws Exception {
        registerOAuth("oauth@booktimer.com");

        mockMvc.perform(get("/settings").with(user("oauth@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("localAccount", false));
    }

    @Test
    @DisplayName("GET /settings: LOCAL 계정이면 localAccount=true")
    void settings_localUser_localAccountTrue() throws Exception {
        register("localview@booktimer.com");

        mockMvc.perform(get("/settings").with(user("localview@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("localAccount", true));
    }

    @Test
    @DisplayName("POST /settings/delete: 소셜 계정은 비밀번호가 없어 본인 @핸들이 일치하면 탈퇴하고 로그인(?deleted)으로 보낸다")
    void deleteAccount_oauthUser_handleConfirmed_removesAccount() throws Exception {
        registerOAuth("oauthdel@booktimer.com");
        var social = userRepository.findByEmail("oauthdel@booktimer.com").orElseThrow();
        social.assignLoginId("oauthdel");
        userRepository.save(social);

        mockMvc.perform(post("/settings/delete").with(user("oauthdel@booktimer.com")).with(csrf())
                        .param("confirmHandle", "oauthdel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.findByEmail("oauthdel@booktimer.com")).isEmpty();
    }
}
