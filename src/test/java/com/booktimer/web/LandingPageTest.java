package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 공개 소개(랜딩) 페이지 — 비로그인 방문자·검색/광고 크롤러용.
 *
 * <p>루트 "/"는 인증 주체가 없으면(익명) 로그인으로 튕기지 않고 서비스 소개를 그린다.
 * AdSense 콘텐츠 심사는 로그인 뒤 본문을 못 보므로(크롤러 미인증), 공개 본문을 루트에 둬
 * 크롤러가 "무엇을 하는 서비스인가"를 읽을 수 있게 한다. 로그인 사용자의 대시보드 분기는
 * {@code DashboardControllerTest}가 따로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LandingPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("익명 GET / → 200, 랜딩 뷰, 서비스 소개 본문(로그인으로 302 안 튕김)")
    void anonymousRoot_rendersLanding() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("landing"))
                // 서비스가 무엇인지 크롤러가 읽을 실제 본문이 있어야 한다(저가치 콘텐츠 반려 방지).
                .andExpect(content().string(containsString("BookTimer")))
                .andExpect(content().string(containsString("하루")));
    }

    @Test
    @DisplayName("랜딩에 로그인·회원가입 진입 동선이 있다")
    void landing_hasAuthEntryLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/login")))
                .andExpect(content().string(containsString("/signup")));
    }

    @Test
    @DisplayName("랜딩 '시작하기' 자리에 Google 원탭 진입(OAuth)도 노출한다")
    void landing_offersGoogleSignIn() throws Exception {
        // 구글 OAuth는 신규면 자동 가입이라 '무료로 시작하기' 옆 한 탭 진입이 마찰을 줄인다.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("Google")));
    }

    @Test
    @DisplayName("랜딩에 독서 마을 진입 동선(/village)이 있다")
    void landing_hasVillageLink() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/village")));
    }

    @Test
    @DisplayName("랜딩 본문에 핵심 키워드(마을·잔디)가 있다")
    void landing_hasCoreKeywords() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("마을")))
                .andExpect(content().string(containsString("잔디")));
    }
}
