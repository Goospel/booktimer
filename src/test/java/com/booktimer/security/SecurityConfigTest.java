package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security 설정 통합 테스트 — formLogin + 세션 + BCrypt 인증 체인 실증.
 *
 * <p>2a의 {@code UserDetailsService}와 이 증분의 {@code PasswordEncoder}·{@code SecurityFilterChain}이
 * 맞물려, DB에 해시로 저장된 사용자가 평문 비번으로 실제 로그인되는지(그리고 틀린 비번은 거부되는지)
 * 본다. 기본 보안 대비 우리 설정의 본질은 <b>DB 기반 인증 + BCrypt</b>다.
 */
@SpringBootTest
@Transactional
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }
        return mockMvc;
    }

    @Test
    @DisplayName("DB에 해시 저장된 사용자가 평문 비번으로 폼 로그인되면 인증된다")
    void formLogin_withDbUser_authenticates() throws Exception {
        userRepository.save(User.of(
                "reader@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER));

        mvc().perform(formLogin("/login").user("reader@booktimer.com").password("rawpw1234"))
                .andExpect(authenticated().withUsername("reader@booktimer.com"));
    }

    @Test
    @DisplayName("틀린 비밀번호면 인증되지 않는다")
    void formLogin_wrongPassword_unauthenticated() throws Exception {
        userRepository.save(User.of(
                "reader@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER));

        mvc().perform(formLogin("/login").user("reader@booktimer.com").password("WRONG"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("인증 없이 보호된 경로에 접근하면 로그인 페이지로 리다이렉트된다")
    void protectedPath_unauthenticated_redirectsToLogin() throws Exception {
        mvc().perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("PasswordEncoder 빈은 BCrypt 해시를 만들고 매칭한다")
    void passwordEncoder_isBcrypt_andMatches() {
        String hash = passwordEncoder.encode("rawpw1234");

        assertThat(hash).startsWith("$2");
        assertThat(passwordEncoder.matches("rawpw1234", hash)).isTrue();
        assertThat(passwordEncoder.matches("WRONG", hash)).isFalse();
    }
}
