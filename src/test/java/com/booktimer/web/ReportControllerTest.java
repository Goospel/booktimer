package com.booktimer.web;

import com.booktimer.report.ReportRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2) — SNS 5단계.
 *
 * <p>{@code POST /report} 는 대상을 신고하고 원래 화면으로 돌아온다. 자기 신고·존재 누설은 조용히 무시.
 * 비로그인은 default-deny로 /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String loginId, String nick) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("POST /report: login_id로 대상을 신고하고 리다이렉트한다")
    void report_createsAndRedirects() throws Exception {
        User me = newUser("rme@booktimer.com", "viewer", "뷰어");
        User target = newUser("rt@booktimer.com", "target", "타겟");

        mockMvc.perform(post("/report")
                        .param("loginId", "target")
                        .param("reason", "SPAM")
                        .param("detail", "광고 도배")
                        .with(user("rme@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.existsByReporterAndReported(me, target)).isTrue();
    }

    @Test
    @DisplayName("POST /report: 닉네임이 같아도 login_id로 정확한 대상만 신고한다")
    void report_duplicateNickname_targetsByLoginId() throws Exception {
        User me = newUser("dme@booktimer.com", "viewer", "뷰어");
        User a = newUser("da@booktimer.com", "alpha", "동명이인");
        User b = newUser("db@booktimer.com", "bravo", "동명이인"); // 같은 닉네임

        mockMvc.perform(post("/report")
                        .param("loginId", "bravo")
                        .param("reason", "SPAM")
                        .with(user("dme@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.existsByReporterAndReported(me, b)).isTrue();   // 정확히 bravo만
        assertThat(reportRepository.existsByReporterAndReported(me, a)).isFalse();  // alpha는 아님
    }

    @Test
    @DisplayName("자기 자신 신고는 조용히 무시된다(행 없음)")
    void report_self_silentlyIgnored() throws Exception {
        User me = newUser("sme@booktimer.com", "myself", "본인");

        mockMvc.perform(post("/report")
                        .param("loginId", "myself")
                        .param("reason", "OTHER")
                        .with(user("sme@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.existsByReporterAndReported(me, me)).isFalse();
    }

    @Test
    @DisplayName("비로그인은 POST /report 시 로그인으로 리다이렉트(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(post("/report").param("loginId", "target").param("reason", "SPAM").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
