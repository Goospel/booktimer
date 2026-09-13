package com.booktimer.web;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 전역 예외 처리 통합 테스트 — 컨트롤러에서 터진 예외를 whitelabel 대신 친절한 'error' 뷰로 변환.
 *
 * <p>인증은 됐지만(principal 존재) DB에 그 사용자가 없으면 DashboardController가
 * {@code AuthenticatedUserNotFoundException}을 던진다. 이런 예기치 못한 예외가 500 whitelabel로 새지 않고
 * {@link GlobalExceptionHandler}가 잡아 'error' 뷰(+상태 500)로 렌더되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    /** catch-all의 error 로그 문구 — 소스에 따옴표째 있는 리터럴이다. */
    private static final String UNHANDLED_LOG = "처리되지 않은 예외";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudyBookRepository studyBookRepository;

    @Test
    @DisplayName("컨트롤러에서 예외가 터지면 친절한 error 뷰(500)로 렌더한다 (whitelabel 아님)")
    void unhandledException_rendersErrorView(CapturedOutput output) throws Exception {
        // 인증 주체는 있으나 DB에 미등록 → DashboardController가 AuthenticatedUserNotFoundException
        mockMvc.perform(get("/").with(user("ghost@booktimer.com")))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("status"));
        // 양성 대조군 — 캡처가 실제로 로그를 보고 있어야 아래 「0건」 단언이 의미를 갖는다.
        assertThat(output.getAll()).contains(UNHANDLED_LOG);
    }

    // ── 잘못된 요청 바디·Content-Type — 서버 결함이 아니라 클라이언트 오류다 ──────────────
    // ghost 사용자로 충분하다: 바디 파싱이 컨트롤러 본문(사용자 조회)보다 먼저 실패한다.

    private ResultActions postSessionGoal(MediaType type, String body) throws Exception {
        var req = post("/api/study/books/1/session-goal")
                .with(user("ghost@booktimer.com")).with(csrf())
                .contentType(type);
        return mockMvc.perform(body == null ? req : req.content(body));
    }

    @Test
    @DisplayName("JSON 바디가 없으면 500이 아니라 400 + error 뷰, error 로그 없음")
    void missingBody_returns400(CapturedOutput output) throws Exception {
        postSessionGoal(MediaType.APPLICATION_JSON, null)
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"));
        assertThat(output.getAll()).doesNotContain(UNHANDLED_LOG);
    }

    @Test
    @DisplayName("깨진 JSON은 400")
    void malformedJson_returns400(CapturedOutput output) throws Exception {
        postSessionGoal(MediaType.APPLICATION_JSON, "{")
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"));
        assertThat(output.getAll()).doesNotContain(UNHANDLED_LOG);
    }

    @Test
    @DisplayName("필드 타입이 틀린 JSON(숫자 자리에 \"abc\")은 400")
    void typeMismatchJson_returns400(CapturedOutput output) throws Exception {
        postSessionGoal(MediaType.APPLICATION_JSON, "{\"sessionGoalSeconds\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"));
        assertThat(output.getAll()).doesNotContain(UNHANDLED_LOG);
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type(text/plain)은 415 — 4xx ErrorResponse의 상태를 보존")
    void unsupportedMediaType_returns415(CapturedOutput output) throws Exception {
        postSessionGoal(MediaType.TEXT_PLAIN, "hello")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(view().name("error"));
        assertThat(output.getAll()).doesNotContain(UNHANDLED_LOG);
    }

    @Test
    @DisplayName("5xx를 들고 온 ErrorResponse(503 AsyncRequestTimeoutException)는 보존하지 않고 500 + error 로그")
    void serverErrorResponse_staysUnexpected500(CapturedOutput output) {
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        String view = new GlobalExceptionHandler().handleUnexpected(
                new org.springframework.web.context.request.async.AsyncRequestTimeoutException(),
                new org.springframework.ui.ExtendedModelMap(), response);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(output.getAll()).contains(UNHANDLED_LOG);
    }

    @Test
    @DisplayName("양성 대조: 실제 사용자·공부 책에 정상 JSON이면 200 (모든 요청을 4xx로 만드는 과잉 수정 차단)")
    void validBody_returns200() throws Exception {
        registrationService.register("geh-ok@a.com", "pw1234qwer!!", "gehok", "닉네임_gehok", "Asia/Seoul",
                Role.USER, LocalDate.now());
        User u = userRepository.findByLoginId("gehok").orElseThrow();
        StudyBook book = studyBookRepository.save(StudyBook.register(u, "수학", "저자", null, null, null, null));

        mockMvc.perform(post("/api/study/books/" + book.getId() + "/session-goal")
                        .with(user("gehok")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionGoalSeconds\":3000}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하지 않는 리소스(favicon 등)는 500이 아니라 404로 응답한다 (핸들러가 삼키지 않음)")
    void missingResource_returns404NotSwallowedAs500() throws Exception {
        // 핸들러 매핑·정적 리소스 어디에도 없는 경로 → NoResourceFoundException(404).
        // @ExceptionHandler(Exception.class)가 이걸 잡아 500으로 만들면 안 된다.
        mockMvc.perform(get("/this-path-does-not-exist.xyz").with(user("ghost@booktimer.com")))
                .andExpect(status().isNotFound());
    }
}
