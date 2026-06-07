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

    /** login_id(=로그인 식별자)를 가진 LOCAL 사용자를 저장한다. 폼 로그인은 이메일이 아니라 login_id로 한다. */
    private void saveUser(String email, String loginId) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        userRepository.save(u);
    }

    @Test
    @DisplayName("DB에 해시 저장된 사용자가 login_id+평문 비번으로 폼 로그인되면 인증된다 (이메일 아님)")
    void formLogin_withDbUser_authenticates() throws Exception {
        saveUser("reader@booktimer.com", "reader1");

        mvc().perform(formLogin("/login").user("reader1").password("rawpw1234"))
                .andExpect(authenticated().withUsername("reader1"));
    }

    @Test
    @DisplayName("틀린 비밀번호면 인증되지 않는다")
    void formLogin_wrongPassword_unauthenticated() throws Exception {
        saveUser("reader@booktimer.com", "reader1");

        mvc().perform(formLogin("/login").user("reader1").password("WRONG"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("보안: 이메일로는 로그인되지 않는다 (로그인 식별자는 login_id)")
    void formLogin_byEmail_unauthenticated() throws Exception {
        saveUser("reader@booktimer.com", "reader1");

        mvc().perform(formLogin("/login").user("reader@booktimer.com").password("rawpw1234"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("인증 없이 보호된 경로에 접근하면 로그인 페이지로 리다이렉트된다")
    void protectedPath_unauthenticated_redirectsToLogin() throws Exception {
        // 루트("/")는 이제 공개 랜딩 페이지라 더 이상 튕기지 않는다(LandingPageTest가 검증).
        // 보호 경로의 default-deny 리다이렉트는 실제로 인증이 필요한 /books로 확인한다.
        mvc().perform(get("/books"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("헬스체크(/actuator/health)는 인증 없이 공개된다 (배포 헬스체크용)")
    void actuatorHealth_isPublic() throws Exception {
        mvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
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
