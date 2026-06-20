package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PWA 정적 자산 공개 접근 회귀 가드.
 *
 * <p>manifest.json·아이콘은 설치 배너·스플래시 표시를 위해 미인증 상태에서도 읽혀야 한다.
 * SecurityConfig default-deny 속에서 이 경로들이 공개 허용됐음을 결정적으로 박는다.
 */
@SpringBootTest
class PwaStaticAccessTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("미인증 상태에서 /manifest.json 이 200 을 반환한다 (PWA 설치 필수 자산)")
    void manifestJson_isPublic_returns200() throws Exception {
        mvc().perform(get("/manifest.json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미인증 상태에서 /icons/icon-192.png 가 200 을 반환한다 (PWA 아이콘 설치 필수)")
    void iconPng_isPublic_returns200() throws Exception {
        mvc().perform(get("/icons/icon-192.png"))
                .andExpect(status().isOk());
    }
}
