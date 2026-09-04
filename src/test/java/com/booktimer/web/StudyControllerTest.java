package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    @Autowired StudyController controller;
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

    /**
     * 마크업 단언({@code name="_csrf"})만으로는 이 줄을 못 지킨다 — 메타 태그는 Thymeleaf가 그리므로
     * {@code precommit} 호출을 지워도 그대로 초록이다. 그 줄이 막는 것은 「큰 페이지에서 세션이 늦게
     * 생겨 응답 커밋 뒤 500」이라 <b>토큰을 실제로 당겼는가</b>로만 잴 수 있다(T-033·T-049 · FeedbackController 선례).
     */
    @Test
    @DisplayName("GET /study: 렌더 전 CSRF 토큰을 선확정한다 — 마크업 단언이 못 잡는 자리")
    void study_precommitsCsrfToken() {
        register("csrfstudy");
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);
        Principal principal = () -> "csrfstudy";

        controller.study(principal, request);

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /study/history: 미인증 → 로그인으로 차단")
    void studyHistory_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/study/history"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /**
     * 공부 기록은 <b>같은 셸</b>이다 — 섬({@code study/main.ts})이 {@code location.pathname}으로 달력/기록을
     * 고른다. 이 단언이 지키는 것은 「새 경로가 404가 아니고, 같은 뷰·같은 마운트 포인트를 낸다」이다.
     */
    @Test
    @DisplayName("GET /study/history: /study와 같은 셸을 낸다 — 섬이 경로로 화면을 고른다")
    void studyHistory_rendersSameShell() throws Exception {
        register("studyhistshell");

        mockMvc.perform(get("/study/history").with(user("studyhistshell")))
                .andExpect(status().isOk())
                .andExpect(view().name("study"))
                .andExpect(content().string(containsString("id=\"study-app\"")));
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
