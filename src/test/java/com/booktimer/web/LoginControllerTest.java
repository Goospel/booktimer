package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Autowired
    private LoginController loginController;

    @Test
    @DisplayName("GET /login: 렌더 전 CSRF 토큰을 선확정한다 — head가 커져 응답이 커밋된 뒤 세션 생성하다 깨지는 500 방어(T-049 재발)")
    void getLogin_precommitsCsrfToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        loginController.loginForm(request);

        // 폼의 CSRF 숨김필드가 렌더되기 전에 토큰을 미리 읽어 세션을 응답 커밋 이전에 만든다.
        verify(token).getToken();
    }

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

    @Test
    @DisplayName("GET /login: 토스 코드 로그인 폼을 포함한다 — 토스로 시작한 계정의 유일한 웹 진입로라 화면에 없으면 기능이 없는 것과 같다")
    void getLogin_hasTossCodeForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/login/toss-code")));
    }

    @Test
    @DisplayName("GET /login?codeError: 코드 전용 안내가 뜨고 접힘 섹션이 펼쳐진다 — 접힌 채면 사용자가 방금 실패한 입력칸을 못 찾는다")
    void getLogin_codeError_rendersBannerAndOpensDetails() throws Exception {
        mockMvc.perform(get("/login").param("codeError", ""))
                .andExpect(status().isOk())
                // 비밀번호 안내(?error)와 분리한다 — 코드 실패에 "아이디 또는 비밀번호" 안내는 오안내다.
                .andExpect(content().string(containsString("코드가 올바르지 않거나")))
                .andExpect(content().string(matchesPattern("(?s).*<details[^>]*\\sopen.*")));
    }

    @Test
    @DisplayName("GET /login?codeLimited: 시도 초과 안내가 뜬다 (코드 오류와 다른 문구 — 원인이 다르다)")
    void getLogin_codeLimited_rendersBanner() throws Exception {
        mockMvc.perform(get("/login").param("codeLimited", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시도가 너무 많아요")));
    }

    @Test
    @DisplayName("GET /login: 파라미터가 없으면 코드 섹션은 접혀 있다 — 기본 동선(아이디·구글)을 밀어내지 않는다")
    void getLogin_noParam_detailsCollapsed() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(matchesPattern("(?s).*<details[^>]*\\sopen.*"))));
    }
}
