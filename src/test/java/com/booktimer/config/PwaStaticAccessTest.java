package com.booktimer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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

    @Test
    @DisplayName("미인증 상태에서 /sw.js 가 200 을 반환한다 (Service Worker 전역 등록 필수)")
    void swJs_isPublic_returns200() throws Exception {
        mvc().perform(get("/sw.js"))
                .andExpect(status().isOk());
    }

    /**
     * PWA 콜드 런치 시작점 회귀 가드 — manifest.json 의 {@code start_url} 이 실제 라우트여야 한다.
     *
     * <p>OS 가 standalone PWA 를 메모리에서 내린 뒤 다시 열면 마지막 라우트가 아니라
     * {@code start_url} 부터 새로 로딩한다. 이 값이 존재하지 않는 경로(예: 매핑 없는 {@code /dashboard})면
     * 콜드 런치마다 404 → 에러 페이지가 떠 사용자가 "대시보드로" 링크를 한 번 더 눌러야 한다.
     * manifest 에서 직접 읽어 그 경로가 404 가 아님을 단언해, 오기를 결정적으로 잡는다.
     */
    @Test
    @DisplayName("manifest.json 의 start_url 경로가 404 가 아니다 (콜드 런치 시작점은 실제 라우트여야 함)")
    void manifestStartUrl_isNotNotFound() throws Exception {
        JsonNode manifest = new ObjectMapper()
                .readTree(Files.readString(Path.of("src/main/resources/static/manifest.json")));
        String startUrl = manifest.get("start_url").asText();

        mvc().perform(get(startUrl))
                .andExpect(status().is(not(404)));
    }
}
