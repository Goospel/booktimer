package com.booktimer.web;

import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 대시보드(홈) 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>로그인 주체(username=email)를 도메인 User로 매핑하고, 접속 시 누적(accrueToToday)을
 * 적용한 뒤 잔여 시간·진행 중 세션을 화면에 싣는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /: 로그인 사용자에게 대시보드를 그리고 잔여 시간을 싣는다")
    void dashboard_rendersForLoggedInUser() throws Exception {
        registrationService.register("dash@booktimer.com", "rawpw1234", "책벌레", SEOUL, Role.USER, today());

        mockMvc.perform(get("/").with(user("dash@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("nickname", "책벌레"))
                // 가입 당일 1증가값 시드(1h) — 같은 날 접속이라 추가 누적 없음
                .andExpect(model().attribute("remainingSeconds", 3600L))
                .andExpect(model().attribute("hasActiveSession", false));
    }

    @Test
    @DisplayName("GET /: 접속 시 경과 일수만큼 누적이 적용된다 (시작일 1h 시드 + 2일치 2h = 3h)")
    void dashboard_appliesAccrualOnAccess() throws Exception {
        // 2일 전 시작 → 시드 1h + 접속 시 2일치(1h*2) 누적 = 3h(10800s)
        registrationService.register("acc@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today().minusDays(2));

        mockMvc.perform(get("/").with(user("acc@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 10800L));
    }

    @Test
    @DisplayName("GET /: 진행 중 세션이 있으면 hasActiveSession=true")
    void dashboard_showsActiveSession() throws Exception {
        User user = registrationService.register("act@booktimer.com", "rawpw1234", "진행중", SEOUL, Role.USER, today());
        sessionService.start(user, clock.instant());

        mockMvc.perform(get("/").with(user("act@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActiveSession", true));
    }

    @Test
    @DisplayName("GET /: 누적 잔여가 cap에 도달하면 atCap=true (상한 경고 배지)")
    void dashboard_atCapWhenRemainingHitsCap() throws Exception {
        // 10일 전 시작 → 시드 1h + 10일치 누적이 cap(기본 5h=18000s)으로 클램프 → 잔여 == cap
        registrationService.register("cap@booktimer.com", "rawpw1234", "상한", SEOUL, Role.USER, today().minusDays(10));

        mockMvc.perform(get("/").with(user("cap@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 18000L))
                .andExpect(model().attribute("atCap", true));
    }

    @Test
    @DisplayName("GET /: 잔여가 cap 미만이면 atCap=false")
    void dashboard_notAtCapWhenBelowCap() throws Exception {
        registrationService.register("below@booktimer.com", "rawpw1234", "여유", SEOUL, Role.USER, today());

        mockMvc.perform(get("/").with(user("below@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("atCap", false));
    }
}
