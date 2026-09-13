package com.booktimer.web.api;

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

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 기능 신청 API 통합 테스트 (H2).
 *
 * <p>이 문은 사용자가 스스로 밟는 유일한 승인 경로다 — 수락·거절·회수는 관리자만 한다
 * ({@code AdminStudyAiControllerTest}). 그래서 여기서 지킬 것은 둘이다: <b>세션 인증 + CSRF</b>가 걸려
 * 있는가, 그리고 <b>같은 사람이 두 번 신청해도 대기 큐가 부풀지 않는가</b>(409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyAiAccessApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String REQUEST_URL = "/api/study/ai-access/request";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;

    private User register(String loginId) {
        User user = registerUnverified(loginId);
        // 신청 게이트가 이메일 검증을 요구한다(S-4) — 로컬 가입은 미검증으로 시작한다. 이 한 줄이
        // 아래 「미검증 403」 테스트의 <b>양성 대조군</b>이다(검증만 채우면 같은 문이 열린다).
        user.verifyEmail();
        userRepository.saveAndFlush(user);
        return user;
    }

    /** 이메일 검증 없이 등록한다 — 로컬 가입의 실제 초기 상태. */
    private User registerUnverified(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private StudyAiAccess accessOf(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow().getStudyAiAccess();
    }

    @Test
    @DisplayName("POST 신청: 미인증 → 로그인으로 차단")
    void request_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post(REQUEST_URL).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST 신청: CSRF 토큰이 없으면 403 — 상태는 그대로다")
    void request_withoutCsrf_isForbidden() throws Exception {
        register("aiuser1");

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser1")))
                .andExpect(status().isForbidden());

        assertThat(accessOf("aiuser1")).isEqualTo(StudyAiAccess.NONE);
    }

    @Test
    @DisplayName("POST 신청: NONE이면 200 PENDING이 되고 전이 시각이 실린다")
    void request_fromNone_becomesPending() throws Exception {
        register("aiuser2");

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser2")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAccess").value("PENDING"))
                .andExpect(jsonPath("$.aiAccessAt").exists());

        assertThat(accessOf("aiuser2")).isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("POST 신청: 이미 대기 중인데 또 누르면 409 — 대기 큐가 부풀지 않는다")
    void request_twice_conflicts() throws Exception {
        register("aiuser3");
        mockMvc.perform(post(REQUEST_URL).with(user("aiuser3")).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser3")).with(csrf()))
                .andExpect(status().isConflict())
                // 본문 문구까지 잰다 — 화면이 409 본문을 그대로 상태줄에 띄우므로(`errorMessage`가
                // 400·409만 본문을 믿는다), 여기가 영어나 빈 값이 되면 사용자가 그걸 읽게 된다.
                .andExpect(content().string(containsString("이미 신청")));

        assertThat(accessOf("aiuser3")).isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("POST 신청: 승인된 사용자가 또 신청하면 409 — 승인이 대기로 되돌아가지 않는다")
    void request_whenApproved_conflictsAndKeepsApproval() throws Exception {
        User user = register("aiuser4");
        user.requestStudyAi(clock.instant());
        user.approveStudyAi(clock.instant());
        userRepository.saveAndFlush(user);

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser4")).with(csrf()))
                .andExpect(status().isConflict());

        assertThat(accessOf("aiuser4")).isEqualTo(StudyAiAccess.APPROVED);
    }

    @Test
    @DisplayName("POST 신청: 거절당한 사용자는 다시 신청할 수 있다(PENDING)")
    void request_whenRejected_canRequestAgain() throws Exception {
        User user = register("aiuser5");
        user.requestStudyAi(clock.instant());
        user.rejectStudyAi(clock.instant());
        userRepository.saveAndFlush(user);

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser5")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAccess").value("PENDING"));

        assertThat(accessOf("aiuser5")).isEqualTo(StudyAiAccess.PENDING);
    }

    // 이 문의 403은 사용자가 읽고 <b>행동을 바꿀 수 있는</b> 실패다(이메일 인증을 하면 된다) — 그래서
    // 사유가 화면까지 닿아야 한다. Content-Type을 같이 재는 이유는 둘이다: ① charset을 빼면 한글이
    // 깨지고 ② text/html로 협상되면 브라우저가 본문을 렌더한다. 그리고 전역 처리기에 맡기면 본문이
    // `error.html` 문서 전체가 되어 화면의 `errorMessage`가 「<로 시작하면 못 믿는다」 규칙으로 버린다.
    @Test
    @DisplayName("POST 신청: 이메일 미검증이면 403 — 사유가 평문 UTF-8 한글로 나간다(S-4)")
    void request_unverifiedEmail_isForbiddenWithPlainTextReason() throws Exception {
        registerUnverified("aiuser7");

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser7")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("이메일 인증 후 신청할 수 있어요"));

        assertThat(accessOf("aiuser7")).isEqualTo(StudyAiAccess.NONE);
    }

    @Test
    @DisplayName("GET agenda: 신청 전엔 NONE·전이 시각 null, 신청 뒤엔 PENDING·시각이 실린다")
    void agenda_carriesAccessState() throws Exception {
        register("aiuser6");
        String month = YearMonth.from(today()).toString();

        mockMvc.perform(get("/api/study/agenda").param("month", month).with(user("aiuser6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAccess").value("NONE"))
                .andExpect(jsonPath("$.aiAccessAt").doesNotExist());

        mockMvc.perform(post(REQUEST_URL).with(user("aiuser6")).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(get("/api/study/agenda").param("month", month).with(user("aiuser6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAccess").value("PENDING"))
                .andExpect(jsonPath("$.aiAccessAt").exists())
                // 승인만으론 AI가 켜지지 않는다(키가 따로다) — 이 판에선 언제나 꺼짐이다.
                .andExpect(jsonPath("$.aiEnabled").value(false));
    }
}
