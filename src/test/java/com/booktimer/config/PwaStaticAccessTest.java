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

    /**
     * PWA 콜드 런치 별칭 회귀 가드 — 미인증 상태에서 {@code /dashboard} 가 {@code /} 와 동일하게 200 이어야 한다.
     *
     * <p>이미 설치된 PWA 는 {@code start_url} 을 설치 시점에 동결한다. #473 이 manifest 를
     * {@code /dashboard → /} 로 고쳤어도, 구 install 은 여전히 콜드 런치마다 {@code /dashboard} 를 쏜다.
     * 서버가 이 경로를 {@code /} 별칭으로 받아야 한다 — 미인증이면 {@code /} 처럼 landing(200) 을 줘야지,
     * permitAll 에서 빠지면 default-deny 가 {@code /login} 으로 302 튕겨 콜드 런치 경험이 깨진다.
     * "404 아님"보다 강하게 "{@code /} 와 같은 200"을 박아 permitAll 매처 누락을 결정적으로 잡는다.
     */
    @Test
    @DisplayName("미인증 상태에서 /dashboard 가 200(landing) — / 와 동일 (permitAll 누락 가드)")
    void dashboardAlias_unauthenticated_isPublicLikeRoot() throws Exception {
        mvc().perform(get("/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미인증 상태에서 /pwa-install.js 가 200 을 반환한다 (PWA 설치 칩 스크립트 공개 자산)")
    void pwaInstallJs_isPublic_returns200() throws Exception {
        mvc().perform(get("/pwa-install.js"))
                .andExpect(status().isOk());
    }

    /**
     * content-hash 정적자산 인증 누수 회귀 가드 — {@code @{/pwa-install.js}}·{@code @{/manifest.json}} 는
     * spring.web.resources.chain 으로 {@code /pwa-install-<md5>.js}·{@code /manifest-<md5>.json} 으로 렌더된다.
     * permitAll 이 정확 경로만 두면 이 해시 URL 이 default-deny 에 걸려 미인증 302(login)→SavedRequest 로
     * 저장되고, 로그인 성공 후 그 자산으로 리다이렉트되는 버그가 난다(캐시 빈 신규 세션에서만 재현 — E2E 가 발견).
     * 위 manifest/icon 케이스가 해시 없는 정확 경로만 박아 이 변형이 사각으로 샜다(N-055). 해시 변형 경로가
     * 인증 거부(302)되지 않음을 박는다 — 파일 부재로 인한 404 는 무방(인가는 통과한 것).
     */
    @Test
    @DisplayName("content-hash 변형 정적자산(/pwa-install-<hash>.js·/manifest-<hash>.json)이 미인증 302 로 안 튕긴다")
    void contentHashedStaticAssets_unauthenticated_notRedirected() throws Exception {
        mvc().perform(get("/pwa-install-deadbeef0123.js"))
                .andExpect(status().is(not(302)));
        mvc().perform(get("/manifest-deadbeef0123.json"))
                .andExpect(status().is(not(302)));
    }
}
