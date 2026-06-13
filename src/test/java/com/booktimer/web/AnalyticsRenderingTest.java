package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GA4 배선 종단 검증(활성) — 측정 ID가 설정되면 공개 랜딩(/)에 gtag.js 로더 + config 호출이 실제로 렌더된다.
 *
 * <p>설정→advice→fragment→템플릿 전 사슬이 이어져 gtag 마크업이 나오는지(핵심 불변식)만 본다.
 * ID 경계는 {@code AnalyticsPropertiesTest}가, 비활성(안전) 방향은 {@link AnalyticsDisabledRenderingTest}가
 * 본다. 랜딩은 비인증 공개 페이지라 사용자 셋업 없이 가장 싸게 검증된다(AdSense {@code AdsRenderingTest} 선례).
 */
@SpringBootTest(properties = "booktimer.analytics.ga-id=G-TEST123")
@AutoConfigureMockMvc
class AnalyticsRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("측정 ID가 설정되면 랜딩에 GA4 gtag 로더와 측정 ID가 렌더된다")
    void gtagRendered_whenGaIdConfigured() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // gtag.js 로더(측정 ID 포함)
                .andExpect(content().string(containsString("googletagmanager.com/gtag/js")))
                // config 호출에 실제 측정 ID가 치환돼 들어간다
                .andExpect(content().string(containsString("G-TEST123")));
    }
}

/**
 * GA4 배선 안전 불변식(비활성) — 측정 ID가 없으면(기본 스캐폴드) gtag 스크립트가 새지 않는다.
 *
 * <p>config-gate가 동작하는지 본다 — ga-id 미설정 시 어떤 페이지에도 통계가 붙으면 안 된다(잘못된 측정 방지).
 * AdSense가 이 비활성 방향을 {@code HistoryControllerTest#history_noAdsWhenDisabled()}로 보는 것과 동일.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsDisabledRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("측정 ID가 없으면(기본) 랜딩에 GA4 스크립트가 새지 않는다 (스캐폴드 안전 불변식)")
    void noGtag_whenGaIdUnset() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("googletagmanager"))));
    }
}
