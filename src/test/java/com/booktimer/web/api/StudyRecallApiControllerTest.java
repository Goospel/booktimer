package com.booktimer.web.api;

import com.booktimer.study.ClaudeStudyAssistant;
import com.booktimer.study.StudyAiUsage;
import com.booktimer.study.StudyAiUsageRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 백지복습 API 통합 테스트 (H2) — <b>승인 게이트가 이 파일의 첫 관심사</b>다.
 *
 * <p>이 판이 승인제({@code StudyAiAccessService.requireApproved})의 <b>첫 호출자</b>다. 게이트가 새면
 * 남의 키로 돌아가는 유료 API가 전 사용자에게 열린다 — 그래서 「403이 떴다」로 끝내지 않고 <b>어댑터가
 * 아예 안 불렸는지</b>({@code verifyNoInteractions})와 <b>상한 행이 안 생겼는지</b>(usage 0행)까지 잰다.
 * 403만 재는 테스트는 「호출은 하고 결과만 버리는」 구현도 통과시킨다.
 *
 * <p>저장({@code POST /api/study/recall})은 <b>승인 없이도</b> 된다 — AI를 안 쓰는 글쓰기라 막을 이유가
 * 없고, 그래야 「AI 없이 저장만」 폴백이 성립한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyRecallApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudyAiUsageRepository usageRepository;
    @Autowired Clock clock;

    /** 어댑터는 늘 목이다 — 네트워크 없이 「불렸나/안 불렸나」를 재는 것이 이 파일의 요점이다. */
    @MockitoBean ClaudeStudyAssistant assistant;

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private User registerWith(String loginId, StudyAiAccess access) {
        User user = register(loginId);
        Instant now = clock.instant();
        switch (access) {
            case NONE -> { }
            case PENDING -> user.requestStudyAi(now);
            case APPROVED -> {
                user.requestStudyAi(now);
                user.approveStudyAi(now);
            }
            case REJECTED -> {
                user.requestStudyAi(now);
                user.rejectStudyAi(now);
            }
        }
        return userRepository.save(user);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private void saveRecall(String loginId, String body) throws Exception {
        saveRecallOn(loginId, today(), body);
    }

    private String analyzeUrl() {
        return "/api/study/recall/" + today() + "/analyze";
    }

    private List<StudyAiUsage> usageOf(String loginId) {
        return usageRepository.findByUser(userRepository.findByLoginId(loginId).orElseThrow());
    }

    // ── 게이트: 승인 안 된 상태 셋은 전부 403이고, 어댑터도 상한도 건드리지 않는다 ──

    @Test
    @DisplayName("게이트: 미승인(NONE) 분석 → 403 · 어댑터 무호출 · 상한 행 0")
    void analyze_whenNotRequested_isForbiddenWithoutTouchingAnything() throws Exception {
        registerWith("gatenone", StudyAiAccess.NONE);
        saveRecall("gatenone", "오늘 배운 것을 적었어요");

        mockMvc.perform(post(analyzeUrl()).with(user("gatenone")).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("gatenone")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 대기 중(PENDING) 분석 → 403 · 어댑터 무호출 · 상한 행 0")
    void analyze_whenPending_isForbidden() throws Exception {
        registerWith("gatepending", StudyAiAccess.PENDING);
        saveRecall("gatepending", "오늘 배운 것을 적었어요");

        mockMvc.perform(post(analyzeUrl()).with(user("gatepending")).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("gatepending")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 거절(REJECTED) 분석 → 403 · 어댑터 무호출 · 상한 행 0")
    void analyze_whenRejected_isForbidden() throws Exception {
        registerWith("gaterejected", StudyAiAccess.REJECTED);
        saveRecall("gaterejected", "오늘 배운 것을 적었어요");

        mockMvc.perform(post(analyzeUrl()).with(user("gaterejected")).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("gaterejected")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 승인 회수 뒤에는 같은 사용자도 403 — 승인은 한 번 받으면 끝이 아니다")
    void analyze_afterRevoke_isForbidden() throws Exception {
        User user = registerWith("gaterevoked", StudyAiAccess.APPROVED);
        saveRecall("gaterevoked", "오늘 배운 것을 적었어요");
        user.revokeStudyAi(clock.instant());
        userRepository.save(user);

        mockMvc.perform(post(analyzeUrl()).with(user("gaterevoked")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("승인")));

        verifyNoInteractions(assistant);
        assertThat(usageOf("gaterevoked")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 미승인은 키가 있어도 403 — 「AI가 꺼졌다」가 아니라 「승인이 필요하다」다")
    void analyze_whenNotApprovedButKeyPresent_isForbiddenNot503() throws Exception {
        given(assistant.isEnabled()).willReturn(true);
        registerWith("gatekeyed", StudyAiAccess.NONE);
        saveRecall("gatekeyed", "오늘 배운 것을 적었어요");

        mockMvc.perform(post(analyzeUrl()).with(user("gatekeyed")).with(csrf()))
                .andExpect(status().isForbidden());

        // isEnabled 조차 묻지 않는다 — 게이트가 키 검사보다 앞이라는 뜻이다.
        org.mockito.Mockito.verify(assistant, org.mockito.Mockito.never()).analyzeRecall(any());
        assertThat(usageOf("gatekeyed")).isEmpty();
    }

    // ── agenda.aiEnabled — (키 있음 AND 승인됨)일 때만 true. 네 조합 전수 ──

    @Test
    @DisplayName("agenda.aiEnabled: 키 없음 × 미승인 → false")
    void aiEnabled_noKeyNotApproved_isFalse() throws Exception {
        given(assistant.isEnabled()).willReturn(false);
        registerWith("agendaa", StudyAiAccess.NONE);

        expectAiEnabled("agendaa", false);
    }

    @Test
    @DisplayName("agenda.aiEnabled: 키 없음 × 승인됨 → false (승인만으론 켜지지 않는다)")
    void aiEnabled_noKeyApproved_isFalse() throws Exception {
        given(assistant.isEnabled()).willReturn(false);
        registerWith("agendab", StudyAiAccess.APPROVED);

        expectAiEnabled("agendab", false);
    }

    @Test
    @DisplayName("agenda.aiEnabled: 키 있음 × 미승인 → false (키만으론 켜지지 않는다)")
    void aiEnabled_keyNotApproved_isFalse() throws Exception {
        given(assistant.isEnabled()).willReturn(true);
        registerWith("agendac", StudyAiAccess.PENDING);

        expectAiEnabled("agendac", false);
    }

    @Test
    @DisplayName("agenda.aiEnabled: 키 있음 × 승인됨 → true (유일하게 켜지는 조합)")
    void aiEnabled_keyApproved_isTrue() throws Exception {
        given(assistant.isEnabled()).willReturn(true);
        registerWith("agendad", StudyAiAccess.APPROVED);

        expectAiEnabled("agendad", true);
    }

    private void expectAiEnabled(String loginId, boolean expected) throws Exception {
        mockMvc.perform(get("/api/study/agenda").param("month", YearMonth.from(today()).toString())
                        .with(user(loginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(expected));
    }

    // ── 저장 — 승인과 무관하게 늘 된다(「AI 없이 저장만」이 성립하는 자리) ──

    @Test
    @DisplayName("저장: 미인증 → 로그인으로 차단 · CSRF 없으면 403")
    void save_requiresSessionAndCsrf() throws Exception {
        register("saveguard");

        mockMvc.perform(post("/api/study/recall").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(today(), "글")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/api/study/recall").with(user("saveguard"))
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(today(), "글")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("저장: 같은 날 두 번 저장하면 덮어쓴다(하루 한 장) — 행이 늘지 않는다")
    void save_sameDateTwice_upserts() throws Exception {
        registerWith("saveupsert", StudyAiAccess.NONE);

        saveRecall("saveupsert", "첫 번째 글");
        saveRecall("saveupsert", "두 번째 글");

        mockMvc.perform(get("/api/study/recall/" + today()).with(user("saveupsert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("두 번째 글"))
                .andExpect(jsonPath("$.analyzedAt").doesNotExist());
    }

    @Test
    @DisplayName("저장: 미래 날짜는 400 — 아직 오지 않은 날의 복습은 없다")
    void save_futureDate_isBadRequest() throws Exception {
        register("savefuture");

        mockMvc.perform(post("/api/study/recall").with(user("savefuture")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson(today().plusDays(1), "미래의 글")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("저장: 빈 본문 400 · 8001자 400 — 경계 바로 안쪽(8000자)은 통과한다")
    void save_bodyLengthBoundaries() throws Exception {
        register("savelen");

        mockMvc.perform(post("/api/study/recall").with(user("savelen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(today(), "   ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/study/recall").with(user("savelen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(today(), "가".repeat(8001))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/study/recall").with(user("savelen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(today(), "가".repeat(8000))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("조회: 쓴 적 없는 날은 404 — 남의 글도 같은 404다(유저별 날짜 키)")
    void get_missingDate_isNotFound() throws Exception {
        register("getmissing");
        registerWith("getother", StudyAiAccess.NONE);
        saveRecall("getother", "남의 글");

        // 같은 날에 남이 쓴 글이 있어도, 내겐 없는 날이다
        mockMvc.perform(get("/api/study/recall/" + today()).with(user("getmissing")))
                .andExpect(status().isNotFound());
    }

    // ── 분석 (승인된 사용자) ──

    @Test
    @DisplayName("분석: 키가 없으면 503이고 상한은 안 깎인다 — 글은 저장돼 있다")
    void analyze_whenDisabled_is503WithoutSpendingShare() throws Exception {
        given(assistant.isEnabled()).willReturn(false);
        registerWith("andisabled", StudyAiAccess.APPROVED);
        saveRecall("andisabled", "오늘 배운 것");

        mockMvc.perform(post(analyzeUrl()).with(user("andisabled")).with(csrf()))
                .andExpect(status().isServiceUnavailable());

        assertThat(usageOf("andisabled")).isEmpty();
    }

    @Test
    @DisplayName("분석: 성공하면 정리·구멍·문제·모델·분석시각이 실린다")
    void analyze_success_savesThreeOutputs() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis(
                "함수의 정의와 호출을 정리했어요.", List.of("반환값 설명이 빠졌어요"), List.of("함수의 반환값은 무엇인가요?")));
        registerWith("anok", StudyAiAccess.APPROVED);
        saveRecall("anok", "함수는 입력을 받아 출력을 낸다");

        mockMvc.perform(post(analyzeUrl()).with(user("anok")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("함수의 정의와 호출을 정리했어요."))
                .andExpect(jsonPath("$.holes[0]").value("반환값 설명이 빠졌어요"))
                .andExpect(jsonPath("$.questions[0]").value("함수의 반환값은 무엇인가요?"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-test"))
                .andExpect(jsonPath("$.analyzedAt").exists());
    }

    @Test
    @DisplayName("분석: 이미 분석된 글을 다시 분석하면 409 — 어댑터도 안 부른다")
    void analyze_twice_isConflict() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("정리", List.of(), List.of("문제")));
        registerWith("antwice", StudyAiAccess.APPROVED);
        saveRecall("antwice", "오늘 배운 것");

        mockMvc.perform(post(analyzeUrl()).with(user("antwice")).with(csrf()))
                .andExpect(status().isOk());

        org.mockito.Mockito.clearInvocations(assistant);
        mockMvc.perform(post(analyzeUrl()).with(user("antwice")).with(csrf()))
                .andExpect(status().isConflict());

        org.mockito.Mockito.verify(assistant, org.mockito.Mockito.never()).analyzeRecall(any());
    }

    @Test
    @DisplayName("분석: 응답을 못 받으면 503이고 <b>깎았던 몫을 환불</b>한다 — 장애로 오늘 몫을 잃지 않는다")
    void analyze_whenUnavailable_refundsShare() throws Exception {
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.analyzeRecall(any()))
                .willReturn(ClaudeStudyAssistant.AiResult.fail(ClaudeStudyAssistant.Failure.UNAVAILABLE));
        registerWith("anfail", StudyAiAccess.APPROVED);
        saveRecall("anfail", "오늘 배운 것");

        mockMvc.perform(post(analyzeUrl()).with(user("anfail")).with(csrf()))
                .andExpect(status().isServiceUnavailable());

        assertThat(usageOf("anfail")).allSatisfy(row -> assertThat(row.getUsed()).isZero());

        // 환불됐으니 같은 날 다시 시도할 수 있다(이번엔 성공)
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("정리", List.of(), List.of()));
        mockMvc.perform(post(analyzeUrl()).with(user("anfail")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("분석: 429(레이트리밋)도 환불한다 — 문구는 「잠시 후 다시」")
    void analyze_whenRateLimited_is429AndRefunds() throws Exception {
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.analyzeRecall(any()))
                .willReturn(ClaudeStudyAssistant.AiResult.fail(ClaudeStudyAssistant.Failure.RATE_LIMITED));
        registerWith("anlimited", StudyAiAccess.APPROVED);
        saveRecall("anlimited", "오늘 배운 것");

        mockMvc.perform(post(analyzeUrl()).with(user("anlimited")).with(csrf()))
                .andExpect(status().isTooManyRequests());

        assertThat(usageOf("anlimited")).allSatisfy(row -> assertThat(row.getUsed()).isZero());
    }

    @Test
    @DisplayName("분석: 결과가 쓸 만하지 않으면(정리 빈값) 503 + 환불 — 빈 분석을 저장하지 않는다")
    void analyze_whenResultIsEmptyAfterNormalize_is503() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("   ", List.of("구멍"), List.of("문제")));
        registerWith("anempty", StudyAiAccess.APPROVED);
        saveRecall("anempty", "오늘 배운 것");

        mockMvc.perform(post(analyzeUrl()).with(user("anempty")).with(csrf()))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/study/recall/" + today()).with(user("anempty")))
                .andExpect(jsonPath("$.analyzedAt").doesNotExist())
                .andExpect(jsonPath("$.body").value("오늘 배운 것"));
        assertThat(usageOf("anempty")).allSatisfy(row -> assertThat(row.getUsed()).isZero());
    }

    @Test
    @DisplayName("분석: 오늘 몫(1회)을 다 쓰면 429이고 어댑터를 안 부른다 — 상한이 호출보다 앞이다")
    void analyze_whenDailyCapSpent_is429WithoutCallingAdapter() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("정리", List.of(), List.of()));
        registerWith("ancap", StudyAiAccess.APPROVED);
        LocalDate yesterday = today().minusDays(1);
        saveRecallOn("ancap", yesterday, "어제 배운 것");
        saveRecall("ancap", "오늘 배운 것");

        // 어제 글을 분석하면 오늘 몫(ANALYZE 1)이 소진된다 — 상한 날짜는 「호출한 날」이다
        mockMvc.perform(post("/api/study/recall/" + yesterday + "/analyze").with(user("ancap")).with(csrf()))
                .andExpect(status().isOk());

        org.mockito.Mockito.clearInvocations(assistant);
        mockMvc.perform(post(analyzeUrl()).with(user("ancap")).with(csrf()))
                .andExpect(status().isTooManyRequests());

        org.mockito.Mockito.verify(assistant, org.mockito.Mockito.never()).analyzeRecall(any());
    }

    @Test
    @DisplayName("분석 후 본문을 고치면 분석 결과가 비워진다 — 옛 분석이 새 글에 붙어 있으면 거짓이다")
    void save_afterAnalysis_clearsStaleAnalysis() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("정리", List.of("구멍"), List.of("문제")));
        registerWith("anstale", StudyAiAccess.APPROVED);
        saveRecall("anstale", "처음 쓴 글");
        mockMvc.perform(post(analyzeUrl()).with(user("anstale")).with(csrf()))
                .andExpect(status().isOk());

        saveRecall("anstale", "고쳐 쓴 글");

        mockMvc.perform(get("/api/study/recall/" + today()).with(user("anstale")))
                .andExpect(jsonPath("$.body").value("고쳐 쓴 글"))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.analyzedAt").doesNotExist())
                .andExpect(jsonPath("$.holes").isEmpty())
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    @DisplayName("agenda: 그 달의 복습 표식이 실린다 — 분석 여부와 다음날 문제 유무")
    void agenda_carriesRecallMarks() throws Exception {
        givenAnalysis(new ClaudeStudyAssistant.RecallAnalysis("정리", List.of(), List.of("문제 하나")));
        registerWith("agendamarks", StudyAiAccess.APPROVED);
        saveRecall("agendamarks", "오늘 배운 것");
        mockMvc.perform(post(analyzeUrl()).with(user("agendamarks")).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/study/agenda").param("month", YearMonth.from(today()).toString())
                        .with(user("agendamarks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalls[0].date").value(today().toString()))
                .andExpect(jsonPath("$.recalls[0].analyzed").value(true))
                .andExpect(jsonPath("$.recalls[0].hasQuestions").value(true))
                .andExpect(jsonPath("$.remaining.analyze").value(0));
    }

    private void givenAnalysis(ClaudeStudyAssistant.RecallAnalysis analysis) {
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.model()).willReturn("claude-sonnet-5-test");
        given(assistant.analyzeRecall(any())).willReturn(ClaudeStudyAssistant.AiResult.ok(analysis));
    }

    private static String bodyJson(LocalDate date, String body) {
        return """
                {"date":"%s","subject":"정보처리기사","scope":"3장 함수","body":"%s","source":"TEXT"}
                """.formatted(date, body);
    }

    private void saveRecallOn(String loginId, LocalDate date, String body) throws Exception {
        mockMvc.perform(post("/api/study/recall").with(user(loginId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyJson(date, body)))
                .andExpect(status().isOk());
    }
}
