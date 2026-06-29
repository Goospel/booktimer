package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    @DisplayName("랜딩에 독서 마을 소개 섹션·앵커 동선(#village)이 있다")
    void landing_hasVillageSection() throws Exception {
        // 비로그인 방문자를 로그인 필요한 /village로 직접 보내면 로그인으로 튕긴다.
        // 대신 페이지 내 마을 소개 섹션(#village)으로 안내하고 회원가입(/signup)으로 유도한다.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"village\"")))
                .andExpect(content().string(containsString("#village")));
    }

    @Test
    @DisplayName("랜딩에 함께 읽기 소개 섹션·앵커 동선(#together)이 있다")
    void landing_hasTogetherSection() throws Exception {
        // '함께 읽기'는 단일 페이지(/social)가 없고 검색·팔로우·공개 프로필로 분산돼 있어,
        // 비로그인 랜딩에선 소개 섹션(#together)으로 안내한 뒤 회원가입으로 유도한다.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"together\"")))
                .andExpect(content().string(containsString("#together")));
    }

    @Test
    @DisplayName("랜딩 본문에 핵심 키워드(마을·잔디)가 있다")
    void landing_hasCoreKeywords() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("마을")))
                .andExpect(content().string(containsString("잔디")));
    }

    @Test
    @DisplayName("랜딩 브랜드 로고가 앱 전역과 같은 통일 SVG(brand-ico)를 쓴다(옛 📚 이모지 잔재 제거)")
    void landing_usesUnifiedBrandLogo() throws Exception {
        // 홈·책장·기록·프로필·로그인·회원가입 전부 brand-ico 책등 SVG로 통일됐는데 랜딩만 📚 이모지가 남아 있었다.
        // 로고가 앱 통일 마크와 어긋나지 않도록 구조 불변식으로 가드한다.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("brand-ico")));
    }

    @Test
    @DisplayName("랜딩은 '남의 잔디·마을 구경'처럼 현재 없는 기능을 광고하지 않는다")
    void landing_doesNotAdvertiseViewingOthersGardenOrGrass() throws Exception {
        // 남의 독서 잔디·마을 열람은 미구현(마을·잔디 라우트는 본인 전용, 남의 공개 잔디는 직렬화 안 되는 dead-code).
        // 거짓 광고가 다시 새어들지 않게 진실성 불변식으로 가드한다. '마을'·'잔디' 자체(본인 기능 소개)는 허용.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("독서 잔디와 마을을 구경"))))
                .andExpect(content().string(not(containsString("서로의 잔디를 구경"))));
    }
}
