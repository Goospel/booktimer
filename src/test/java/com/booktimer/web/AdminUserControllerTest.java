package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 관리자 데이터 조회 컨트롤러 통합 테스트 (admin-data-lookup-design §2).
 *
 * <p>접근 경계가 1순위다 — 운영 데이터(PII)가 걸려 있어 ADMIN만 닿아야 한다. 목록·드릴다운 둘 다
 * 미인증→로그인, USER→403, ADMIN→200을 회귀로 건다. 없는 loginId는 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

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
    @DisplayName("GET /admin/users: 미인증이면 로그인으로 리다이렉트")
    void list_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /admin/users: 일반 USER는 403 금지")
    void list_user_forbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("u@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/users: ADMIN은 200, userPage가 모델에 실린다")
    void list_admin_ok() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-users"))
                .andExpect(model().attributeExists("userPage"));
    }

    @Test
    @DisplayName("GET /admin/users/{loginId}: 일반 USER는 403 금지")
    void detail_user_forbidden() throws Exception {
        registrationService.register("target@booktimer.com", "rawpw1234", "대상", SEOUL, Role.USER, today());
        mockMvc.perform(get("/admin/users/{loginId}", "someone").with(user("u@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/users/{loginId}: ADMIN이 실제 사용자를 조회하면 200, detail 모델")
    void detail_admin_ok() throws Exception {
        // 7-arg register: (email, rawPassword, loginId, nickname, timezone, role, startDate)
        registrationService.register("target@booktimer.com", "rawpw1234", "targetuser", "대상", SEOUL, Role.USER, today());

        mockMvc.perform(get("/admin/users/{loginId}", "targetuser").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-user-detail"))
                .andExpect(model().attributeExists("detail"));
    }

    @Test
    @DisplayName("GET /admin/users/{loginId}: 없는 loginId면 404")
    void detail_unknown_404() throws Exception {
        mockMvc.perform(get("/admin/users/{loginId}", "nosuchid").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
