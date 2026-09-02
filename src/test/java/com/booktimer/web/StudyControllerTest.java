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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * GET /study 셸 컨트롤러 통합 테스트.
 *
 * <p>데이터는 전부 {@code /api/study/**}가 나른다 — 이 셸이 지는 책임은 셋뿐이다: ①인증, ②마운트 포인트,
 * ③<b>CSRF 메타</b>. ③이 빠지면 섬의 POST(체크 순환·일정 추가·삭제)가 통째로 403이 된다 — 화면은 멀쩡히
 * 뜨는데 아무것도 저장되지 않는 조용한 고장이라 여기서 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private void register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("GET /study: 미인증 → 로그인으로 차단")
    void study_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/study"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /study: 셸 뷰 + 마운트 포인트 + CSRF 메타를 낸다")
    void study_rendersShell() throws Exception {
        register("studyshell");

        mockMvc.perform(get("/study").with(user("studyshell")))
                .andExpect(status().isOk())
                .andExpect(view().name("study"))
                .andExpect(content().string(containsString("id=\"study-app\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }
}
