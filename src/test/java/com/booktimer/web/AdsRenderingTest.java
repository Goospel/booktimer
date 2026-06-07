package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 광고 배선 종단 검증 — 게시자 ID가 설정되면 부가 화면에 AdSense 로더 스크립트 + 광고 단위가 실제로 렌더된다.
 *
 * <p>비활성(기본) 방향은 {@link HistoryControllerTest#history_noAdsWhenDisabled()}가, ID 경계는
 * {@code AdsPropertiesTest}가 본다. 여기선 설정→advice→fragment→템플릿 전 사슬이 이어져
 * 실제 광고 마크업이 나오는지(배선)만 본다(N-009 테스트 피라미드). 광고는 별도 컨텍스트에서만 켠다.
 */
@SpringBootTest(properties = {
        "booktimer.ads.client-id=ca-pub-test123",
        "booktimer.ads.slot=9988776655"
})
@AutoConfigureMockMvc
@Transactional
class AdsRenderingTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("게시자 ID가 설정되면 /history에 AdSense 로더와 광고 단위가 렌더된다")
    void adsRendered_whenClientIdConfigured() throws Exception {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
        registrationService.register("ads@booktimer.com", "rawpw1234", "광고", SEOUL, Role.USER, today);

        mockMvc.perform(get("/history").with(user("ads@booktimer.com")))
                .andExpect(status().isOk())
                // 로더 스크립트(게시자 ID 포함)
                .andExpect(content().string(containsString("adsbygoogle.js")))
                .andExpect(content().string(containsString("ca-pub-test123")))
                // 광고 단위(슬롯)
                .andExpect(content().string(containsString("class=\"adsbygoogle\"")))
                .andExpect(content().string(containsString("9988776655")));
    }
}
