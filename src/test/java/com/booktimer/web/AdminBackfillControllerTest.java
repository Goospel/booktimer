package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 카탈로그 백필 컨트롤러 통합 테스트(책BTI Phase 1b).
 *
 * <p>접근 경계가 1순위 — /admin/** 은 ADMIN만. 쓰기 액션(POST)이므로 CSRF 필요. 일반 USER는 403.
 * 백필 실행 자체(외부 호출)는 서비스 테스트가 보고, 여기선 인가 경계·라우팅·결과 플래시 배선을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /admin/books/backfill-catalog: ADMIN이면 실행하고 결과 플래시와 함께 /admin으로 리다이렉트")
    void backfill_admin_runsAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/books/backfill-catalog")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    @DisplayName("POST /admin/books/backfill-catalog: 일반 USER는 403 금지")
    void backfill_user_forbidden() throws Exception {
        mockMvc.perform(post("/admin/books/backfill-catalog")
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/books/backfill-purchase-links: ADMIN이면 실행하고 결과 플래시와 함께 /admin으로 리다이렉트")
    void backfillPurchaseLinks_admin_runsAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/books/backfill-purchase-links")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    @DisplayName("POST /admin/books/backfill-purchase-links: 일반 USER는 403 금지")
    void backfillPurchaseLinks_user_forbidden() throws Exception {
        mockMvc.perform(post("/admin/books/backfill-purchase-links")
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
