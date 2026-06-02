package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OAuth2 로그인 배선 통합 테스트 (MockMvc).
 *
 * <p>오리지널 OIDC 로그인 콜백(토큰 교환)은 외부 provider가 필요해 단위로 재현하지 않는다.
 * 대신 "스프링이 google 클라이언트 등록 + oauth2Login 필터를 실제로 구성했는가"를
 * <b>인가 요청 리다이렉트</b>로 검증한다 — 미인증 사용자가 {@code /oauth2/authorization/google}에
 * 가면 구글 인가 엔드포인트로 302되어야 한다. 이게 되면 의존성/등록/필터가 모두 살아있다는 뜻.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2LoginWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 GET /oauth2/authorization/google → 구글 인가 엔드포인트로 리다이렉트")
    void authorizationRequest_redirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("accounts.google.com")))
                .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
    }
}
