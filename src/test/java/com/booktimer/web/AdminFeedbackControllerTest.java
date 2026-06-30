package com.booktimer.web;

import com.booktimer.feedback.Feedback;
import com.booktimer.feedback.FeedbackRepository;
import com.booktimer.feedback.FeedbackService;
import com.booktimer.feedback.FeedbackStatus;
import com.booktimer.feedback.FeedbackType;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 문의함 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>접근 경계가 1순위 — {@code /admin/**}은 ADMIN만(미인증→/login, USER→403). ADMIN은 전체 문의를
 * 작성자와 함께 조회하고 읽음/처리완료/삭제를 표시한다(쓰기도 같은 인가 경계).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private FeedbackRepository feedbackRepository;

    private User newUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$x", "유저", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    @Autowired
    private AdminFeedbackController controller;

    @Test
    @DisplayName("GET /admin/feedback: 렌더 전 CSRF 토큰을 선확정한다 — SSR 폼 commit-후-500 방어(T-049 재발)")
    void list_precommitsCsrfToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        controller.list(request, null, new ConcurrentModel());

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /admin/feedback: 미인증이면 로그인으로 리다이렉트")
    void list_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/admin/feedback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /admin/feedback: 일반 USER는 403 금지")
    void list_user_forbidden() throws Exception {
        mockMvc.perform(get("/admin/feedback").with(user("u@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/feedback: ADMIN은 200, 모든 작성자의 문의가 보인다")
    void list_admin_seesAll() throws Exception {
        User a = newUser("a@booktimer.com", "alphauser");
        User b = newUser("b@booktimer.com", "bravouser");
        feedbackService.submit(a, FeedbackType.BUG, "알파제목AAA", "내용");
        feedbackService.submit(b, FeedbackType.SUGGESTION, "브라보제목BBB", "내용");

        mockMvc.perform(get("/admin/feedback").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-feedback"))
                .andExpect(content().string(containsString("알파제목AAA")))
                .andExpect(content().string(containsString("브라보제목BBB")));
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/read: ADMIN이 읽음 표시 → 상태 READ")
    void read_admin_setsRead() throws Exception {
        User a = newUser("r@booktimer.com", "readeruser");
        Feedback f = feedbackService.submit(a, FeedbackType.ETC, "문의", "내용");

        mockMvc.perform(post("/admin/feedback/{id}/read", f.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/feedback"));

        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getStatus).isEqualTo(FeedbackStatus.READ);
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/resolve: ADMIN이 처리완료 → 상태 RESOLVED")
    void resolve_admin_setsResolved() throws Exception {
        User a = newUser("rs@booktimer.com", "resolveru");
        Feedback f = feedbackService.submit(a, FeedbackType.ETC, "문의", "내용");

        mockMvc.perform(post("/admin/feedback/{id}/resolve", f.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getStatus).isEqualTo(FeedbackStatus.RESOLVED);
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/delete: ADMIN이 삭제 → 제거됨")
    void delete_admin_removes() throws Exception {
        User a = newUser("d@booktimer.com", "deleteru");
        Feedback f = feedbackService.submit(a, FeedbackType.ETC, "스팸", "광고");

        mockMvc.perform(post("/admin/feedback/{id}/delete", f.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackRepository.findById(f.getId())).isEmpty();
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/read: 일반 USER는 403(쓰기도 인가 경계)")
    void read_user_forbidden() throws Exception {
        User a = newUser("x@booktimer.com", "victimuser");
        Feedback f = feedbackService.submit(a, FeedbackType.ETC, "문의", "내용");

        mockMvc.perform(post("/admin/feedback/{id}/read", f.getId())
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getStatus).isEqualTo(FeedbackStatus.SUBMITTED); // 변화 없음
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/reply: ADMIN 답장 → 저장 + 자동 읽음")
    void reply_admin_savesAndReads() throws Exception {
        User a = newUser("rp@booktimer.com", "replyadmin");
        Feedback f = feedbackService.submit(a, FeedbackType.BUG, "버그", "내용");

        mockMvc.perform(post("/admin/feedback/{id}/reply", f.getId())
                        .param("reply", "확인했어요. 곧 고칠게요.")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackRepository.findById(f.getId())).get().satisfies(saved -> {
            assertThat(saved.getReply()).isEqualTo("확인했어요. 곧 고칠게요.");
            assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.READ);
        });
    }

    @Test
    @DisplayName("POST /admin/feedback/{id}/reply: 일반 USER는 403, 답장 안 달림")
    void reply_user_forbidden() throws Exception {
        User a = newUser("rpu@booktimer.com", "replyvictim");
        Feedback f = feedbackService.submit(a, FeedbackType.ETC, "문의", "내용");

        mockMvc.perform(post("/admin/feedback/{id}/reply", f.getId())
                        .param("reply", "남이 답장")
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getReply).isNull();
    }

    @Test
    @DisplayName("GET /admin/feedback?type=BUG: 해당 유형만 보인다(필터)")
    void list_filterByType() throws Exception {
        User a = newUser("ft@booktimer.com", "filtertest");
        feedbackService.submit(a, FeedbackType.BUG, "버그제목ZZZ", "내용");
        feedbackService.submit(a, FeedbackType.SUGGESTION, "제안제목YYY", "내용");

        mockMvc.perform(get("/admin/feedback").param("type", "BUG").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("버그제목ZZZ")))
                .andExpect(content().string(not(containsString("제안제목YYY"))));
    }
}
