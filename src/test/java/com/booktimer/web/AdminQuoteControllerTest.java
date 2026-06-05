package com.booktimer.web;

import com.booktimer.quote.Quote;
import com.booktimer.quote.QuoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
 * 관리자 격언 관리 컨트롤러 통합 테스트.
 *
 * <p>접근 경계가 1순위 — /admin/** 은 ADMIN만. 미인증→로그인, USER→403, ADMIN→200/처리를 회귀로 건다.
 * 쓰기 경로(POST 추가/삭제)는 CSRF가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Test
    @DisplayName("GET /admin/quotes: 미인증이면 로그인으로 리다이렉트")
    void list_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/admin/quotes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /admin/quotes: 일반 USER는 403 금지")
    void list_user_forbidden() throws Exception {
        mockMvc.perform(get("/admin/quotes").with(user("u@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/quotes: ADMIN은 200, quotes가 모델에 실린다")
    void list_admin_ok() throws Exception {
        mockMvc.perform(get("/admin/quotes").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-quotes"))
                .andExpect(model().attributeExists("quotes"));
    }

    @Test
    @DisplayName("POST /admin/quotes: ADMIN이 격언을 추가하면 저장되고 목록으로 리다이렉트")
    void add_admin_persistsAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/quotes")
                        .param("text", "추가된 격언")
                        .param("author", "추가 작가")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/quotes"));

        assertThat(quoteRepository.findAll())
                .extracting(Quote::getText)
                .contains("추가된 격언");
    }

    @Test
    @DisplayName("POST /admin/quotes: 공백 격언은 저장하지 않는다")
    void add_blank_doesNotPersist() throws Exception {
        long before = quoteRepository.count();

        mockMvc.perform(post("/admin/quotes")
                        .param("text", "   ")
                        .param("author", "작가")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(quoteRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("POST /admin/quotes: 일반 USER는 403 금지")
    void add_user_forbidden() throws Exception {
        mockMvc.perform(post("/admin/quotes")
                        .param("text", "x").param("author", "y")
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/quotes/{id}/delete: ADMIN이 격언을 삭제하면 제거되고 목록으로 리다이렉트")
    void delete_admin_removesAndRedirects() throws Exception {
        Quote saved = quoteRepository.save(Quote.of("지울 격언", "지움 작가"));

        mockMvc.perform(post("/admin/quotes/{id}/delete", saved.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/quotes"));

        assertThat(quoteRepository.findById(saved.getId())).isEmpty();
    }
}
