package com.booktimer.web;

import com.booktimer.session.ReadingSessionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 start/stop 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>화면에서 측정을 시작/종료하는 경로의 <b>와이어링</b>을 본다 — 진행 세션 생성/종료,
 * 잘못된 상태(중복 start / 없는 stop)의 플래시 에러 처리. 차감 <i>금액</i>은
 * 서비스·통합 테스트가 이미 검증하므로 여기선 다루지 않는다(N-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadingSessionControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("POST /sessions/start: 진행 중 세션을 만들고 대시보드로 리다이렉트한다")
    void start_createsActiveSession() throws Exception {
        User user = register("start@booktimer.com");

        mockMvc.perform(post("/sessions/start").with(user("start@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isPresent();
    }

    @Test
    @DisplayName("POST /sessions/stop: 진행 중 세션을 종료하고 대시보드로 리다이렉트한다")
    void stop_endsActiveSession() throws Exception {
        User user = register("stop@booktimer.com");
        sessionService.start(user, clock.instant());

        mockMvc.perform(post("/sessions/stop").with(user("stop@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/start: 이미 진행 중이면 플래시 에러로 안내한다 (세션 중복 생성 없음)")
    void start_whenActiveExists_flashesError() throws Exception {
        User user = register("dup@booktimer.com");
        sessionService.start(user, clock.instant());

        mockMvc.perform(post("/sessions/start").with(user("dup@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).hasSize(1);
    }

    @Test
    @DisplayName("POST /sessions/stop: 진행 중 세션이 없으면 플래시 에러로 안내한다")
    void stop_whenNoneActive_flashesError() throws Exception {
        register("none@booktimer.com");

        mockMvc.perform(post("/sessions/stop").with(user("none@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    // --- htmx(무리로드) 경로: HX-Request 헤더가 있으면 redirect 대신 200 + 라이브 프래그먼트 ---

    @Test
    @DisplayName("POST /sessions/start (htmx): 리다이렉트 대신 200 + 대시보드 라이브 프래그먼트를 반환한다")
    void start_htmx_returnsFragment() throws Exception {
        User user = register("hxstart@booktimer.com");

        mockMvc.perform(post("/sessions/start").header("HX-Request", "true")
                        .with(user("hxstart@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-live")));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isPresent();
    }

    @Test
    @DisplayName("POST /sessions/stop (htmx): 200 + 프래그먼트를 반환하고 진행 세션을 종료한다")
    void stop_htmx_returnsFragment() throws Exception {
        User user = register("hxstop@booktimer.com");
        sessionService.start(user, clock.instant());

        mockMvc.perform(post("/sessions/stop").header("HX-Request", "true")
                        .with(user("hxstop@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-live")));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/start (htmx): 이미 진행 중이면 200 프래그먼트에 에러 메시지를 담는다 (리다이렉트·플래시 아님)")
    void start_htmx_whenActiveExists_returnsFragmentWithError() throws Exception {
        User user = register("hxdup@booktimer.com");
        sessionService.start(user, clock.instant());

        mockMvc.perform(post("/sessions/start").header("HX-Request", "true")
                        .with(user("hxdup@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 진행 중")));

        assertThat(sessionRepository.findByUser(user)).hasSize(1);
    }
}
