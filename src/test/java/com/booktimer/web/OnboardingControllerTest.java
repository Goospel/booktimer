package com.booktimer.web;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 온보딩(첫 진입 초기 설정) 화면/처리 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>GET은 초기 설정 폼을 보여주되 이미 온보딩한 사용자는 대시보드로 돌려보낸다. POST는 분→초로
 * 변환해 {@code OnboardingService}로 위임하고, 초기값이 cap을 넘는 등 입력 오류는 화면을 다시 그린다.
 * 분↔초 변환·와이어링을 보고, 도메인 규칙은 하위 테스트에 위임(N-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingTimerRepository timerRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("GET /onboarding: 온보딩 전 사용자에게 초기 설정 폼을 보여준다")
    void getOnboarding_showsFormForNewUser() throws Exception {
        register("new@booktimer.com");

        mockMvc.perform(get("/onboarding").with(user("new@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"))
                .andExpect(model().attributeExists("onboardingForm"));
    }

    @Test
    @DisplayName("GET /onboarding: 이미 온보딩한 사용자는 대시보드로 돌려보낸다 (재온보딩 방지)")
    void getOnboarding_alreadyOnboarded_redirectsHome() throws Exception {
        User u = register("done@booktimer.com");
        u.completeOnboarding();
        userRepository.save(u);

        mockMvc.perform(get("/onboarding").with(user("done@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /onboarding: 초기값·증가값·cap(분)을 적용하고 온보딩 완료 후 대시보드로 리다이렉트")
    void postOnboarding_appliesAndRedirects() throws Exception {
        User u = register("apply@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("apply@booktimer.com")).with(csrf())
                        .param("initialMinutes", "120")    // 2h
                        .param("incrementMinutes", "90")   // 90분
                        .param("capMinutes", "600"))       // 10h
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User reloaded = userRepository.findByEmail("apply@booktimer.com").orElseThrow();
        assertThat(reloaded.isOnboarded()).isTrue();
        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getRemainingSeconds()).isEqualTo(7200L);   // 120분
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(5400L); // 90분
        assertThat(timer.getCapSeconds()).isEqualTo(36000L);        // 600분
    }

    @Test
    @DisplayName("POST /onboarding: 초기값이 상한보다 크면 화면을 다시 그리고 온보딩되지 않는다")
    void postOnboarding_initialAboveCap_rerenders() throws Exception {
        register("over@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("over@booktimer.com")).with(csrf())
                        .param("initialMinutes", "600")    // 10h
                        .param("incrementMinutes", "60")
                        .param("capMinutes", "300"))       // cap 5h < 초기값
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"));

        assertThat(userRepository.findByEmail("over@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("POST /onboarding: 음수 분이면 검증 실패로 화면을 다시 그린다")
    void postOnboarding_negativeMinutes_rerenders() throws Exception {
        register("neg@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("neg@booktimer.com")).with(csrf())
                        .param("initialMinutes", "-1")
                        .param("incrementMinutes", "60")
                        .param("capMinutes", "300"))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"));

        assertThat(userRepository.findByEmail("neg@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }
}
