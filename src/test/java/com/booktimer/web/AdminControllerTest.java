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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 관리자 대시보드 인가 경계 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>1순위는 접근 제어다 — 운영 데이터(개인정보)가 걸려 있어 ADMIN만 닿아야 한다.
 * ① 미인증 → 로그인 리다이렉트 ② 일반 USER → 403 금지 ③ ADMIN → 200 렌더.
 * 권한은 {@code .with(user(...).roles(...))} 합성 주체로 부여한다(검색·기록 테스트와 동일 양식).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

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
    @DisplayName("GET /admin: 미인증이면 로그인 페이지로 리다이렉트된다")
    void admin_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /admin: 일반 USER 권한이면 403 금지")
    void admin_user_forbidden() throws Exception {
        mockMvc.perform(get("/admin").with(user("u@booktimer.com"))) // 기본 ROLE_USER
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin: ADMIN 권한이면 200, admin 뷰를 렌더한다")
    void admin_admin_rendersDashboard() throws Exception {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.ADMIN, today());

        mockMvc.perform(get("/admin").with(user("boss@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
    }

    @Test
    @DisplayName("GET /admin: 운영 통계 요약(stats)이 모델에 실린다")
    void admin_admin_modelHasStats() throws Exception {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.ADMIN, today());

        mockMvc.perform(get("/admin").with(user("boss@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("stats"));
    }

    @Test
    @DisplayName("GET /admin: 로그아웃 폼(POST /logout)이 렌더된다 — 운영자도 로그아웃 가능")
    void admin_admin_hasLogoutForm() throws Exception {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.ADMIN, today());

        mockMvc.perform(get("/admin").with(user("boss@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("action=\"/logout\""),
                        containsString("method=\"post\""))));
    }
}
