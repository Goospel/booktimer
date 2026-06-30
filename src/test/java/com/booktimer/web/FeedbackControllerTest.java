package com.booktimer.web;

import com.booktimer.feedback.Feedback;
import com.booktimer.feedback.FeedbackRepository;
import com.booktimer.feedback.FeedbackService;
import com.booktimer.feedback.FeedbackType;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.security.Principal;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사용자 피드백/문의 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>핵심 경계: 비로그인은 default-deny로 /login, 내 문의 화면은 <b>본인 글만</b> 노출,
 * 삭제는 <b>본인 글만</b>(남의 글 id는 IDOR로 차단). 작성은 PRG.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String loginId) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), "유저", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Autowired
    private FeedbackController controller;

    @Test
    @DisplayName("GET /feedback: 렌더 전 CSRF 토큰을 선확정한다 — SSR 폼 commit-후-500 방어(T-049 재발)")
    void page_precommitsCsrfToken() {
        newUser("csrf-fb@booktimer.com", "csrffb");
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);
        Principal principal = () -> "csrf-fb@booktimer.com";

        controller.page(request, principal, new ConcurrentModel());

        verify(token).getToken();
    }

    @Test
    @DisplayName("비로그인 GET /feedback → 로그인으로 리다이렉트(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/feedback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /feedback: 내 문의만 보이고 남의 문의는 안 보인다(스코핑)")
    void page_showsOnlyMine() throws Exception {
        User me = newUser("me@booktimer.com", "writer");
        User other = newUser("other@booktimer.com", "reader");
        feedbackService.submit(me, FeedbackType.BUG, "내가쓴제목XYZ", "내 내용");
        feedbackService.submit(other, FeedbackType.BUG, "남이쓴제목QPR", "남 내용");

        mockMvc.perform(get("/feedback").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback"))
                .andExpect(content().string(containsString("내가쓴제목XYZ")))
                .andExpect(content().string(not(containsString("남이쓴제목QPR"))));
    }

    @Test
    @DisplayName("POST /feedback: 문의를 접수하고 /feedback으로 리다이렉트, 내 목록에 1건")
    void submit_createsAndRedirects() throws Exception {
        User me = newUser("s@booktimer.com", "submitter");

        mockMvc.perform(post("/feedback")
                        .param("type", "BUG").param("title", "버그요").param("content", "안돼요")
                        .with(user("s@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback"));

        assertThat(feedbackService.myFeedback(me)).hasSize(1);
    }

    @Test
    @DisplayName("POST /feedback: 빈 내용은 접수 안 됨(검증), 행 없음")
    void submit_blankContent_noRow() throws Exception {
        User me = newUser("b@booktimer.com", "blanker");

        mockMvc.perform(post("/feedback")
                        .param("type", "BUG").param("title", "제목").param("content", "   ")
                        .with(user("b@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackService.myFeedback(me)).isEmpty();
    }

    @Test
    @DisplayName("POST /feedback/{id}/delete: 남의 글 id로는 삭제 안 됨(IDOR)")
    void delete_othersId_idorBlocked() throws Exception {
        newUser("a1@booktimer.com", "attacker");
        User other = newUser("v1@booktimer.com", "victim");
        Feedback victimF = feedbackService.submit(other, FeedbackType.ETC, "피해자글", "내용");

        mockMvc.perform(post("/feedback/{id}/delete", victimF.getId())
                        .with(user("a1@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackRepository.findById(victimF.getId())).isPresent(); // 안 지워짐
    }

    @Test
    @DisplayName("POST /feedback/{id}/delete: 본인 글은 삭제됨")
    void delete_own_removed() throws Exception {
        User me = newUser("o1@booktimer.com", "owneruser");
        Feedback mineF = feedbackService.submit(me, FeedbackType.ETC, "내글", "내용");

        mockMvc.perform(post("/feedback/{id}/delete", mineF.getId())
                        .with(user("o1@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(feedbackRepository.findById(mineF.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /feedback: 개발자 답장이 있으면 내 문의에 함께 보인다")
    void page_showsReply() throws Exception {
        User me = newUser("rs@booktimer.com", "replyseer");
        Feedback f = feedbackService.submit(me, FeedbackType.BUG, "버그제목", "내용");
        feedbackService.reply(f.getId(), "개발자답장내용WWW");

        mockMvc.perform(get("/feedback").with(user("rs@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("개발자답장내용WWW")));
    }
}
