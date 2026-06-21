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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /history 셸 컨트롤러 통합 테스트 (선별 SPA 단계 1b 이후 슬림화).
 *
 * <p>데이터 단언(months·graph·weeklyShortfall)은 HistoryApiControllerTest로 이관.
 * 이 테스트는 ①인증·뷰 배선, ②닉네임 모델, ③광고 불변식(스캐폴드 안전)만 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /history: 로그인 사용자에게 셸 화면(뷰 'history')을 반환하고 nickname을 모델에 싣는다")
    void history_rendersShellForLoggedInUser() throws Exception {
        registrationService.register("hist@booktimer.com", "rawpw1234", "기록가", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("hist@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attribute("nickname", "기록가"));
    }

    @Test
    @DisplayName("GET /history: 광고 게시자 ID가 없으면(기본) 광고 스크립트가 새지 않는다 (스캐폴드 안전 불변식)")
    void history_noAdsWhenDisabled() throws Exception {
        registrationService.register("noads@booktimer.com", "rawpw1234", "무광고", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("noads@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("adsbygoogle"))));
    }
}
