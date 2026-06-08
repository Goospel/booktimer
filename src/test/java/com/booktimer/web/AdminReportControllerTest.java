package com.booktimer.web;

import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportRepository;
import com.booktimer.report.ReportService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 관리자 신고함 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>접근 경계가 1순위 — {@code /admin/**}은 ADMIN만(미인증→/login, USER→403). ADMIN은 모든 신고를
 * 신고자·대상과 함께 조회하고, 처리 끝난 신고를 삭제한다(쓰기도 같은 인가 경계). 신고함은 문의함과 같은 구조.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportRepository reportRepository;

    private User newUser(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$x", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("GET /admin/reports: 미인증이면 로그인으로 리다이렉트")
    void list_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /admin/reports: 일반 USER는 403 금지")
    void list_user_forbidden() throws Exception {
        mockMvc.perform(get("/admin/reports").with(user("u@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/reports: ADMIN은 200, 신고자·대상·사유·상세가 보인다")
    void list_admin_seesAll() throws Exception {
        User reporter = newUser("rp@booktimer.com", "reporterone", "신고자원");
        User target = newUser("tg@booktimer.com", "targetone", "대상원");
        reportService.report(reporter, target, ReportReason.SPAM, "광고도배내용XYZ");

        mockMvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-reports"))
                .andExpect(content().string(containsString("reporterone")))
                .andExpect(content().string(containsString("targetone")))
                .andExpect(content().string(containsString("광고도배내용XYZ")));
    }

    @Test
    @DisplayName("POST /admin/reports/{id}/delete: ADMIN이 삭제 → 제거됨")
    void delete_admin_removes() throws Exception {
        User reporter = newUser("rd@booktimer.com", "reporterdel", "신고자삭제");
        User target = newUser("td@booktimer.com", "targetdel", "대상삭제");
        reportService.report(reporter, target, ReportReason.HARASSMENT, "욕설");
        Long id = reportRepository.findAll().get(0).getId();

        mockMvc.perform(post("/admin/reports/{id}/delete", id)
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/reports"));

        assertThat(reportRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("POST /admin/reports/{id}/delete: 일반 USER는 403, 신고는 남는다")
    void delete_user_forbidden() throws Exception {
        User reporter = newUser("rf@booktimer.com", "reporterfb", "신고자FB");
        User target = newUser("tf@booktimer.com", "targetfb", "대상FB");
        reportService.report(reporter, target, ReportReason.SPAM, null);
        Long id = reportRepository.findAll().get(0).getId();

        mockMvc.perform(post("/admin/reports/{id}/delete", id)
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.findById(id)).isPresent(); // 변화 없음
    }
}
