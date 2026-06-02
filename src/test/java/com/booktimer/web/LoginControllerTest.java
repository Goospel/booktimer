package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 커스텀 로그인 페이지 테스트 (MockMvc).
 *
 * <p>Spring Security 기본 생성 페이지 대신 우리 템플릿({@code login.html})을 쓰는지,
 * 그리고 회원가입으로 가는 링크가 있는지(기본 페이지엔 없던 동선) 본다. 인증 로직 자체는
 * {@code SecurityConfigTest}가 검증하므로 여기선 화면/링크만.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /login: 커스텀 로그인 뷰를 렌더하고 회원가입 링크를 포함한다 (인증 불필요)")
    void getLogin_rendersCustomPageWithSignupLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("/signup")));
    }

    @Test
    @DisplayName("GET /login?registered: 가입 완료 안내와 함께 200으로 렌더된다 (whitelabel 에러 아님)")
    void getLogin_registered_rendersOk() throws Exception {
        mockMvc.perform(get("/login").param("registered", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("회원가입이 완료")));
    }

    @Test
    @DisplayName("GET /login?error: 인증 실패 안내와 함께 200으로 렌더된다")
    void getLogin_error_rendersOk() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("올바르지 않")));
    }

    @Test
    @DisplayName("GET /login?logout: 로그아웃 안내와 함께 200으로 렌더된다")
    void getLogin_logout_rendersOk() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("로그아웃")));
    }

    @Test
    @DisplayName("GET /login: 구글 소셜 로그인 버튼(인가요청 링크)을 포함한다")
    void getLogin_includesGoogleOAuthButton() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
    }
}
