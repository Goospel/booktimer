package com.booktimer.security;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 아이폰 앱 경로({@code /api/app/**})가 API 체인(STATELESS · CSRF 없음 · 401)으로 가는가(v1 REQ-05).
 *
 * <p>목 없는 실제 빈 세계다 — 테스트 설정엔 Apple 키가 없어 검증기가 꺼져 있다. 그래서 첫 테스트는
 * 「키 없이 배포돼도 서버는 뜨고 그 경로만 503으로 닫힌다」(placeholder 동작)의 증명이기도 하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppSecurityChainTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired ApiTokenService apiTokenService;

    @Test
    @DisplayName("REQ-05 · Bearer 없는 /api/app/auth/apple은 로그인 페이지로 튕기지 않는다")
    void authEndpointReachesController() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/app/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"t\",\"intent\":\"LOGIN\"}"))
                // CSRF 토큰도 세션도 없이 컨트롤러에 닿았다 — 키가 없는 이 세계에선 컨트롤러가 503으로 닫는다
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("앱 로그인이 아직 준비되지 않았어요"))
                .andReturn();
        assertThat(r.getResponse().getHeader("Location")).isNull();
    }

    @Test
    @DisplayName("REQ-82 · Bearer 없는 google·kakao 문은 302가 아니다")
    void googleAndKakaoReachController() throws Exception {
        // 실제 빈 세계엔 구글 iOS 클라이언트 ID · 카카오 앱 키가 없다 — 컨트롤러에 닿아 그 제공자만 503으로 닫힌다
        String[][] doors = {{"google", "구글 로그인이 아직 준비되지 않았어요"}, {"kakao", "카카오 로그인이 아직 준비되지 않았어요"}};
        for (String[] door : doors) {
            MvcResult r = mockMvc.perform(post("/api/app/auth/" + door[0]).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identityToken\":\"t\",\"intent\":\"LOGIN\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(door[1]))
                    .andReturn();
            assertThat(r.getResponse().getHeader("Location")).as(door[0]).isNull();
        }
    }

    @Test
    @DisplayName("REQ-109 · /api/app/logout은 Bearer로 인증된다(토큰 없으면 401)")
    void appLogoutNeedsBearer() throws Exception {
        mockMvc.perform(post("/api/app/logout")).andExpect(status().isUnauthorized());

        User user = registrationService.registerOAuth("chain-logout@booktimer.com", "앱", "Asia/Seoul",
                AuthProvider.APPLE, LocalDate.of(2026, 10, 23), false);
        String token = apiTokenService.issue(user);
        mockMvc.perform(post("/api/app/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("REQ-05 · 토큰 없는 /api/app/drafts는 401")
    void otherAppPathsNeedToken() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/app/drafts")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(r.getResponse().getHeader("Location")).isNull();
        mockMvc.perform(post("/api/app/account/delete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQ-05 · 회귀 가드 — /api/app 밖 토큰 없는 /api 요청은 그대로 웹 체인(로그인 302)")
    void webChainUnchanged() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().is3xxRedirection());
    }
}
