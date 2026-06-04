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

    private User newUser(String email, String nick) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("POST /report: 대상을 신고하고 리다이렉트한다")
    void report_createsAndRedirects() throws Exception {
        User me = newUser("rme@booktimer.com", "뷰어");
        User target = newUser("rt@booktimer.com", "타겟");

        mockMvc.perform(post("/report")
                        .param("nickname", "타겟")
                        .param("reason", "SPAM")
                        .param("detail", "광고 도배")
                        .with(user("rme@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.existsByReporterAndReported(me, target)).isTrue();
    }

    @Test
    @DisplayName("자기 자신 신고는 조용히 무시된다(행 없음)")
    void report_self_silentlyIgnored() throws Exception {
        User me = newUser("sme@booktimer.com", "본인");

        mockMvc.perform(post("/report")
                        .param("nickname", "본인")
                        .param("reason", "OTHER")
                        .with(user("sme@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.existsByReporterAndReported(me, me)).isFalse();
    }

    @Test
    @DisplayName("비로그인은 POST /report 시 로그인으로 리다이렉트(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(post("/report").param("nickname", "타겟").param("reason", "SPAM").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
