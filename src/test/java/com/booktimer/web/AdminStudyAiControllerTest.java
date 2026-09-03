package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.DispatcherServlet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 기능 승인 관리자 액션 통합 테스트 (H2).
 *
 * <p>1순위는 <b>인가 경계</b>다 — 이 세 문은 남의 계정의 AI 사용 권한을 바꾼다. 미인증·일반 USER·CSRF
 * 부재를 각각 못 박고, 그다음에 전이 규칙(잘못된 전이는 500이 아니라 플래시 오류)을 본다.
 *
 * <p>접근 제어 자체는 {@code SecurityConfig}의 {@code /admin/**} → {@code hasRole("ADMIN")} 한 줄이 하고
 * 컨트롤러는 재검사하지 않는다({@link AdminController} javadoc) — 그래서 그 한 줄이 실제로 이 경로를
 * 덮는지를 여기서 실행으로 확인한다(경로가 늘어날 때 조용히 빠지는 자리다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminStudyAiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    /** 주어진 상태의 사용자를 만든다 — 전이는 도메인 메서드로만 밟는다(테스트가 규칙을 우회하지 않게). */
    private User register(String loginId, StudyAiAccess state) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        switch (state) {
            case NONE -> { }
            case PENDING -> user.requestStudyAi(clock.instant());
            case APPROVED -> {
                user.requestStudyAi(clock.instant());
                user.approveStudyAi(clock.instant());
            }
            case REJECTED -> {
                user.requestStudyAi(clock.instant());
                user.rejectStudyAi(clock.instant());
            }
        }
        return userRepository.saveAndFlush(user);
    }

    private StudyAiAccess accessOf(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getStudyAiAccess();
    }

    private String url(String loginId, String action) {
        return "/admin/study-ai/" + loginId + "/" + action;
    }

    // ── 인가 경계 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 수락: 미인증 → 로그인으로 리다이렉트, 상태 불변")
    void approve_unauthenticated_redirectsToLogin() throws Exception {
        register("target1", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target1", "approve")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(accessOf("target1")).isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("POST 수락: 일반 USER면 403 — 남이 자기 권한을 켤 수 없다")
    void approve_asUser_isForbidden() throws Exception {
        register("target2", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target2", "approve")).with(user("target2")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(accessOf("target2")).isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("POST 수락: CSRF 토큰이 없으면 ADMIN이어도 403")
    void approve_withoutCsrf_isForbidden() throws Exception {
        register("target3", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target3", "approve")).with(user("boss").roles("ADMIN")))
                .andExpect(status().isForbidden());

        assertThat(accessOf("target3")).isEqualTo(StudyAiAccess.PENDING);
    }

    // ── 정상 전이 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 수락: PENDING → APPROVED, /admin으로 돌아가며 플래시 안내가 붙는다")
    void approve_pending_approves() throws Exception {
        register("target4", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target4", "approve")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));

        assertThat(accessOf("target4")).isEqualTo(StudyAiAccess.APPROVED);
    }

    @Test
    @DisplayName("POST 거절: PENDING → REJECTED")
    void reject_pending_rejects() throws Exception {
        register("target5", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target5", "reject")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));

        assertThat(accessOf("target5")).isEqualTo(StudyAiAccess.REJECTED);
    }

    @Test
    @DisplayName("POST 회수: APPROVED → REJECTED")
    void revoke_approved_revokes() throws Exception {
        register("target6", StudyAiAccess.APPROVED);

        mockMvc.perform(post(url("target6", "revoke")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));

        assertThat(accessOf("target6")).isEqualTo(StudyAiAccess.REJECTED);
    }

    // ── 잘못된 전이·대상 ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST 수락: 신청한 적 없는(NONE) 사용자면 플래시 error + 상태 불변 — 404가 아니다(사람은 존재한다)")
    void approve_noneUser_flashesErrorAndKeepsState() throws Exception {
        register("target7", StudyAiAccess.NONE);

        mockMvc.perform(post(url("target7", "approve")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("error"))
                .andExpect(flash().attributeCount(1));

        assertThat(accessOf("target7")).isEqualTo(StudyAiAccess.NONE);
    }

    @Test
    @DisplayName("POST 회수: 승인 상태가 아니면 플래시 error + 상태 불변")
    void revoke_notApproved_flashesError() throws Exception {
        register("target8", StudyAiAccess.PENDING);

        mockMvc.perform(post(url("target8", "revoke")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("error"));

        assertThat(accessOf("target8")).isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("플래시 오류가 대시보드에 실제로 렌더된다 — 모델에 있는 것만으론 사용자가 못 본다")
    void flashError_isRenderedOnDashboard() throws Exception {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.ADMIN, today());

        // 리다이렉트 뒤의 GET을 흉내 낸다: DispatcherServlet은 넘겨받은 플래시를 이 요청 속성에 넣고,
        // 핸들러 어댑터가 그걸 모델로 옮긴다 — 그래서 여기에 직접 넣으면 실제 「리다이렉트 다음 요청」과
        // 같은 모양이 된다(MockMvc의 flashAttr·세션 왕복으로는 이 지점까지 오지 않는다).
        // 앞의 전이 테스트들은 「플래시에 error가 담겼다」까지만 보므로, 이 단언이 없으면
        // admin.html의 렌더 줄을 통째로 지워도 전부 초록이다 — 오류가 조용히 사라지는 자리다.
        mockMvc.perform(get("/admin")
                        .requestAttr(DispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE,
                                Map.of("error", "이미 처리된 신청이에요"))
                        .with(user("boss@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 처리된 신청이에요")));
    }

    @Test
    @DisplayName("POST 수락: 없는 아이디면 404")
    void approve_unknownLoginId_isNotFound() throws Exception {
        mockMvc.perform(post(url("nosuchuser", "approve")).with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
