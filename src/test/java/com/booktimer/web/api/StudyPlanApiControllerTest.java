package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.study.ClaudeStudyAssistant;
import com.booktimer.study.ClaudeStudyAssistant.AiResult;
import com.booktimer.study.ClaudeStudyAssistant.Failure;
import com.booktimer.study.ClaudeStudyAssistant.PlanDay;
import com.booktimer.study.ClaudeStudyAssistant.PlanDraft;
import com.booktimer.study.StudyAiUsage;
import com.booktimer.study.StudyAiUsageRepository;
import com.booktimer.study.StudyPlanItem;
import com.booktimer.study.StudyPlanItemRepository;
import com.booktimer.study.StudyPlanService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/study/agenda}·{@code /api/study/plan/items} 통합 테스트 (H2).
 *
 * <p>에러 계약은 {@link StudyApiController}와 같다 — IAE→400 한국어 문구, 남의 것·없는 것→404.
 *
 * <p>여기에만 있는 <b>회귀 가드</b>: 이 화면은 기존 미니앱용 {@code /api/study/calendar}·{@code /check}를
 * <b>웹 세션으로</b> 그대로 쓴다(설계 §1.2). 그 라우팅은 {@code SecurityConfig}의 「Bearer 헤더 없는
 * {@code /api/**}는 세션 체인」 규칙에 얹혀 있어, 규칙이 바뀌면 화면이 통째로 죽는다 — 코드 판독이 아니라
 * 실제 200으로 못 박는다(U-11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyPlanApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired StudyPlanService planService;
    @Autowired StudyPlanItemRepository planItemRepository;
    @Autowired StudyAiUsageRepository usageRepository;
    @Autowired Clock clock;

    /** 어댑터는 늘 목이다 — 「불렸나/안 불렸나」가 게이트 테스트의 판정 근거라 네트워크를 태우지 않는다. */
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

    private List<StudyAiUsage> usageOf(String loginId) {
        return usageRepository.findByUser(userRepository.findByLoginId(loginId).orElseThrow());
    }

    /** 기본 생성 요청 — 필요한 칸만 바꿔 쓰라고 문자열 조립으로 둔다. */
    private String generateBody(String subject, String scope, LocalDate examDate,
                                int dailyMinutes, int daysPerWeek) {
        return "{\"subject\":\"" + subject + "\",\"scope\":\"" + scope + "\",\"examDate\":\""
                + iso(examDate) + "\",\"dailyMinutes\":" + dailyMinutes
                + ",\"daysPerWeek\":" + daysPerWeek + "}";
    }

    private String defaultGenerateBody() {
        return generateBody("정보보안기사", "1장 접근통제\\n2장 암호학", today().plusDays(30), 120, 5);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private String thisMonth() {
        return YearMonth.from(today()).toString();
    }

    private static String iso(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private StudyBook studyBook(User user, String title) {
        return studyBookRepository.save(StudyBook.register(user, title, "저자", null, null, null, null));
    }

    // ── 인증 경계 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/study/agenda: 미인증 → 로그인으로 차단")
    void agenda_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/api/study/agenda").param("month", "2026-09"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/study/plan/items: 미인증 → 로그인으로 차단")
    void addItem_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/plan/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-02\",\"subject\":\"과목\",\"task\":\"할 일\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 회귀 가드: 웹 세션이 기존 공부 달력 API를 그대로 쓴다 (U-11) ──────

    @Test
    @DisplayName("GET /api/study/calendar: Bearer 없는 웹 세션으로도 200 — /study 화면이 이 라우팅에 얹혀 있다")
    void legacyCalendarApi_isReachableWithWebSession() throws Exception {
        register("weblegacy");

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("weblegacy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isArray());
    }

    @Test
    @DisplayName("POST /api/study/check: 웹 세션 + CSRF 헤더로 저장된다 — 셀 탭 3상태 순환의 서버 쪽")
    void legacyCheckApi_savesWithWebSession() throws Exception {
        register("webcheck");

        mockMvc.perform(post("/api/study/check").with(user("webcheck")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + iso(today()) + "\",\"kept\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kept").value(true));
    }

    // ── agenda ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/study/agenda: 달 형식이 틀리면 400 한국어 문구")
    void agenda_badMonth_isBadRequest() throws Exception {
        register("agendabad");

        mockMvc.perform(get("/api/study/agenda").param("month", "2026-13").with(user("agendabad")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/study/agenda: today·aiEnabled(false)·items·recalls를 준다 — AI는 이번 판에 없다")
    void agenda_returnsShape() throws Exception {
        User user = register("agendashape");
        LocalDate day = today();
        planService.add(user, day, null, "정보처리기사", "1장 p.1-20");

        mockMvc.perform(get("/api/study/agenda").param("month", thisMonth()).with(user("agendashape")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(iso(day)))
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.recalls").isArray())
                .andExpect(jsonPath("$.recalls").isEmpty())
                .andExpect(jsonPath("$.items[0].date").value(iso(day)))
                .andExpect(jsonPath("$.items[0].subject").value("정보처리기사"))
                .andExpect(jsonPath("$.items[0].task").value("1장 p.1-20"))
                .andExpect(jsonPath("$.items[0].bookId").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/study/agenda: 남의 일정은 안 보인다")
    void agenda_hidesOtherUsersItems() throws Exception {
        User other = register("agendaother");
        register("agendame");
        planService.add(other, today(), null, "남의 과목", "남의 일정");

        mockMvc.perform(get("/api/study/agenda").param("month", thisMonth()).with(user("agendame")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ── 일정 추가 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/plan/items: 200 + 저장된 항목")
    void addItem_returnsSavedItem() throws Exception {
        User user = register("additem");
        StudyBook book = studyBook(user, "정보처리기사 실기");

        mockMvc.perform(post("/api/study/plan/items").with(user("additem")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + iso(today()) + "\",\"bookId\":" + book.getId()
                                + ",\"subject\":\"정보처리기사 실기\",\"task\":\"3장 함수 p.45-70\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bookId").value(book.getId()))
                .andExpect(jsonPath("$.task").value("3장 함수 p.45-70"));

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(user, today(), today()))
                .hasSize(1);
    }

    @Test
    @DisplayName("POST /api/study/plan/items: 미래 날짜도 넣을 수 있다 — 일정은 앞날을 위한 것이다")
    void addItem_futureDate_isAllowed() throws Exception {
        register("addfuture");

        mockMvc.perform(post("/api/study/plan/items").with(user("addfuture")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + iso(today().plusDays(10)) + "\",\"subject\":\"과목\",\"task\":\"할 일\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/study/plan/items: 빈 할 일은 400")
    void addItem_blankTask_isBadRequest() throws Exception {
        register("addblankapi");

        mockMvc.perform(post("/api/study/plan/items").with(user("addblankapi")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + iso(today()) + "\",\"subject\":\"과목\",\"task\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/study/plan/items: 날짜 형식이 틀리면 400")
    void addItem_badDate_isBadRequest() throws Exception {
        register("addbaddate");

        mockMvc.perform(post("/api/study/plan/items").with(user("addbaddate")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"어제\",\"subject\":\"과목\",\"task\":\"할 일\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/study/plan/items: 남의 bookId는 404(존재 비노출)")
    void addItem_othersBook_isNotFound() throws Exception {
        User other = register("bookowner");
        register("bookthief");
        StudyBook othersBook = studyBook(other, "남의 책");

        mockMvc.perform(post("/api/study/plan/items").with(user("bookthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + iso(today()) + "\",\"bookId\":" + othersBook.getId()
                                + ",\"subject\":\"과목\",\"task\":\"할 일\"}"))
                .andExpect(status().isNotFound());
    }

    // ── 일정 삭제 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/plan/items/{id}/delete: 내 항목은 200이고 실제로 지워진다")
    void deleteItem_own_isOk() throws Exception {
        User user = register("delapiown");
        StudyPlanItem item = planService.add(user, today(), null, "과목", "할 일");

        mockMvc.perform(post("/api/study/plan/items/" + item.getId() + "/delete")
                        .with(user("delapiown")).with(csrf()))
                .andExpect(status().isOk());

        assertThat(planItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("POST /api/study/plan/items/{id}/delete: 남의 항목은 404이고 행은 남는다(IDOR)")
    void deleteItem_others_isNotFound() throws Exception {
        User other = register("delapiother");
        register("delapithief");
        StudyPlanItem othersItem = planService.add(other, today(), null, "남의 과목", "남의 일정");

        mockMvc.perform(post("/api/study/plan/items/" + othersItem.getId() + "/delete")
                        .with(user("delapithief")).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(planItemRepository.findById(othersItem.getId())).isPresent();
    }

    @Test
    @DisplayName("POST /api/study/plan/items/{id}/delete: 없는 id도 404")
    void deleteItem_missing_isNotFound() throws Exception {
        register("delapimissing");

        mockMvc.perform(post("/api/study/plan/items/99999999/delete")
                        .with(user("delapimissing")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── 게이트: 승인 안 된 상태 셋은 전부 403이고, 어댑터도 상한도 건드리지 않는다 ──
    //
    // 「403이 떴다」로 끝내면 「호출은 하고 결과만 버리는」 구현도 통과한다 — 남의 키로 도는 유료 API라
    // 어댑터 무호출(verifyNoInteractions)과 상한 행 0까지 함께 잰다.

    @Test
    @DisplayName("게이트: 미승인(NONE) 일정 생성 → 403 · 어댑터 무호출 · 상한 행 0")
    void generate_whenNotRequested_isForbiddenWithoutTouchingAnything() throws Exception {
        registerWith("plangatenone", StudyAiAccess.NONE);

        mockMvc.perform(post("/api/study/plan/generate").with(user("plangatenone")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("plangatenone")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 대기 중(PENDING) 일정 생성 → 403 · 어댑터 무호출 · 상한 행 0")
    void generate_whenPending_isForbidden() throws Exception {
        registerWith("plangatepending", StudyAiAccess.PENDING);

        mockMvc.perform(post("/api/study/plan/generate").with(user("plangatepending")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("plangatepending")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 거절(REJECTED) 일정 생성 → 403 · 어댑터 무호출 · 상한 행 0")
    void generate_whenRejected_isForbidden() throws Exception {
        registerWith("plangatereject", StudyAiAccess.REJECTED);

        mockMvc.perform(post("/api/study/plan/generate").with(user("plangatereject")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
        assertThat(usageOf("plangatereject")).isEmpty();
    }

    @Test
    @DisplayName("게이트: 미승인이면 잘못된 입력이어도 403이다 — 승인 판정이 검증보다 앞이다")
    void generate_whenNotApproved_gateBeatsValidation() throws Exception {
        registerWith("plangatefirst", StudyAiAccess.NONE);

        mockMvc.perform(post("/api/study/plan/generate").with(user("plangatefirst")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("", "범위", today().minusDays(1), 5, 9)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assistant);
    }

    @Test
    @DisplayName("POST /api/study/plan/generate: 미인증 → 로그인으로 차단")
    void generate_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/plan/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 일정 생성: 검증 400 전수 (승인됐고 키도 있는 상태에서) ──────────────

    private void performGenerateBadRequest(String loginId, String body) throws Exception {
        mockMvc.perform(post("/api/study/plan/generate").with(user(loginId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assistant);
    }

    @Test
    @DisplayName("generate 검증: 시험일이 오늘이거나 지났으면 400 — 어댑터를 부르지 않는다")
    void generate_examDateNotFuture_isBadRequest() throws Exception {
        registerWith("planexamtoday", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planexamtoday",
                generateBody("정보보안기사", "1장", today(), 120, 5));
    }

    @Test
    @DisplayName("generate 검증: 시험일이 1년을 넘으면 400")
    void generate_examDateTooFar_isBadRequest() throws Exception {
        registerWith("planexamfar", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planexamfar",
                generateBody("정보보안기사", "1장", today().plusDays(366), 120, 5));
    }

    @Test
    @DisplayName("generate 검증: 하루 공부 시간이 10분 미만이면 400")
    void generate_dailyMinutesTooSmall_isBadRequest() throws Exception {
        registerWith("planminlow", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planminlow",
                generateBody("정보보안기사", "1장", today().plusDays(30), 9, 5));
    }

    @Test
    @DisplayName("generate 검증: 하루 공부 시간이 600분을 넘으면 400")
    void generate_dailyMinutesTooLarge_isBadRequest() throws Exception {
        registerWith("planminhigh", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planminhigh",
                generateBody("정보보안기사", "1장", today().plusDays(30), 601, 5));
    }

    @Test
    @DisplayName("generate 검증: 주 공부일수가 0이거나 7을 넘으면 400")
    void generate_daysPerWeekOutOfRange_isBadRequest() throws Exception {
        registerWith("planweek", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planweek",
                generateBody("정보보안기사", "1장", today().plusDays(30), 120, 0));
        performGenerateBadRequest("planweek",
                generateBody("정보보안기사", "1장", today().plusDays(30), 120, 8));
    }

    @Test
    @DisplayName("generate 검증: 과목이 비면 400")
    void generate_blankSubject_isBadRequest() throws Exception {
        registerWith("plannosubject", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("plannosubject",
                generateBody("   ", "1장", today().plusDays(30), 120, 5));
    }

    @Test
    @DisplayName("generate 검증: 범위가 4000자를 넘으면 400")
    void generate_scopeTooLong_isBadRequest() throws Exception {
        registerWith("planscope", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planscope",
                generateBody("정보보안기사", "가".repeat(4001), today().plusDays(30), 120, 5));
    }

    @Test
    @DisplayName("generate 검증: 예상 항목 수가 상한을 넘으면 400 — 4개월·주 6일은 타임아웃에 걸린다")
    void generate_tooManyEstimatedItems_isBadRequest() throws Exception {
        registerWith("plantoolong", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        // 122일 × 6/7 = 104항목 → 회귀식으로 약 96초, 클라이언트 타임아웃 90초를 넘긴다
        performGenerateBadRequest("plantoolong",
                generateBody("정보보안기사", "1장", today().plusDays(122), 120, 6));
    }

    @Test
    @DisplayName("generate 검증: 항목 수 경계 — 90개는 통과한다")
    void generate_estimatedItemsAtLimit_isAllowed() throws Exception {
        registerWith("planatlimit", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().plusDays(1)), "1장 접근통제")))));

        // 90일 × 7/7 = 90항목 — 상한과 같으므로 통과다(넘을 때만 막는다)
        mockMvc.perform(post("/api/study/plan/generate").with(user("planatlimit")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("과목", "1장", today().plusDays(90), 120, 7)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("generate 검증: 항목 수 경계 — 91개는 400이고 어댑터를 부르지 않는다")
    void generate_estimatedItemsOverLimit_isBadRequest() throws Exception {
        registerWith("planoverlimit", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planoverlimit",
                generateBody("과목", "1장", today().plusDays(91), 120, 7));
    }

    @Test
    @DisplayName("generate 검증: 1년짜리라도 주 1일이면 통과한다 — 기간이 아니라 항목 수로 막는다")
    void generate_longRangeWithFewDaysPerWeek_isAllowed() throws Exception {
        registerWith("planlongthin", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().plusDays(7)), "1장 접근통제")))));

        // 365일 × 1/7 = 52항목 — 기간만 보고 막으면 이 정당한 장기 계획까지 막힌다
        mockMvc.perform(post("/api/study/plan/generate").with(user("planlongthin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("과목", "1장", today().plusDays(365), 120, 1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("generate 검증: 시험일 형식이 틀리면 400")
    void generate_badExamDate_isBadRequest() throws Exception {
        registerWith("planbaddate", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);

        performGenerateBadRequest("planbaddate",
                "{\"subject\":\"과목\",\"scope\":\"범위\",\"examDate\":\"내년\","
                        + "\"dailyMinutes\":120,\"daysPerWeek\":5}");
    }

    // ── 일정 생성: 키·상한·실패 ────────────────────────────────────────────

    @Test
    @DisplayName("generate: AI가 꺼져 있으면 503이고 상한도 안 깎인다")
    void generate_whenDisabled_isServiceUnavailable() throws Exception {
        registerWith("planoff", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(false);

        mockMvc.perform(post("/api/study/plan/generate").with(user("planoff")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("AI 기능이 꺼져 있어요"));

        assertThat(usageOf("planoff")).isEmpty();
    }

    @Test
    @DisplayName("generate: 오늘 몫(3회)을 다 쓰면 429이고 어댑터를 더 부르지 않는다")
    void generate_whenCapSpent_isTooManyRequests() throws Exception {
        registerWith("plancap", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().plusDays(1)), "1장 접근통제")))));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/study/plan/generate").with(user("plancap")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(defaultGenerateBody()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/study/plan/generate").with(user("plancap")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("generate: 어댑터 실패(UNAVAILABLE)면 503이고 선점한 몫은 환불된다")
    void generate_whenAdapterFails_refundsQuota() throws Exception {
        registerWith("planfail", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.fail(Failure.UNAVAILABLE));

        mockMvc.perform(post("/api/study/plan/generate").with(user("planfail")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isServiceUnavailable());

        assertThat(usageOf("planfail")).allSatisfy(u -> assertThat(u.getUsed()).isZero());
    }

    @Test
    @DisplayName("generate: 레이트리밋은 429로 옮겨진다")
    void generate_whenRateLimited_isTooManyRequests() throws Exception {
        registerWith("planratelimit", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.fail(Failure.RATE_LIMITED));

        mockMvc.perform(post("/api/study/plan/generate").with(user("planratelimit")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("generate: 정제하고 나면 남는 게 없는 초안은 503이고 환불된다")
    void generate_whenEverythingSanitizedAway_isServiceUnavailable() throws Exception {
        registerWith("planempty", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        // 전부 과거 날짜 — 정제가 통째로 버린다
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().minusDays(3)), "지난 일정")))));

        mockMvc.perform(post("/api/study/plan/generate").with(user("planempty")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isServiceUnavailable());

        assertThat(usageOf("planempty")).allSatisfy(u -> assertThat(u.getUsed()).isZero());
    }

    // ── 일정 생성: 응답이 정제된 것이고 replaceCount가 맞다 ────────────────

    @Test
    @DisplayName("generate: 모델이 범위 밖·중복·주 초과를 섞어 보내도 응답은 정제된 것이다")
    void generate_sanitizesModelDraft() throws Exception {
        registerWith("plansanitize", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        LocalDate exam = today().plusDays(30);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(List.of(
                new PlanDay(iso(today().minusDays(1)), "어제 — 버려진다"),
                new PlanDay(iso(exam), "시험날 — 버려진다"),
                new PlanDay(iso(today().plusDays(1)), "1장 접근통제"),
                new PlanDay(iso(today().plusDays(1)), "중복 — 버려진다"),
                new PlanDay("2026-13-45", "파싱 실패 — 버려진다"),
                new PlanDay(iso(today().plusDays(2)), "   ")))));

        mockMvc.perform(post("/api/study/plan/generate").with(user("plansanitize")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("정보보안기사", "1장 접근통제", exam, 120, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value(iso(today().plusDays(1))))
                .andExpect(jsonPath("$.days[0].task").value("1장 접근통제"));
    }

    @Test
    @DisplayName("generate: replaceCount는 오늘 이후 항목 수 — 과거는 세지 않는다")
    void generate_replaceCountCountsTodayAndLater() throws Exception {
        User user = registerWith("planreplace", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().plusDays(1)), "1장 접근통제")))));
        planService.add(user, today().minusDays(2), null, "과목", "지난 일정");
        planService.add(user, today(), null, "과목", "오늘 일정");
        planService.add(user, today().plusDays(1), null, "과목", "내일 일정");

        mockMvc.perform(post("/api/study/plan/generate").with(user("planreplace")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replaceCount").value(2));
    }

    // ── 달력에 적용 ────────────────────────────────────────────────────────

    private String applyBody(String days) {
        return "{\"subject\":\"정보보안기사\",\"days\":[" + days + "]}";
    }

    private String applyDay(LocalDate date, String task) {
        return "{\"date\":\"" + iso(date) + "\",\"task\":\"" + task + "\"}";
    }

    @Test
    @DisplayName("POST /api/study/plan/apply: 미인증 → 로그인으로 차단")
    void apply_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/plan/apply").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(LocalDate.of(2026, 9, 30), "할 일"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("apply: 승인이 없어도 된다 — AI를 쓰지 않는 저장이라 막을 이유가 없다")
    void apply_worksWithoutAiApproval() throws Exception {
        registerWith("applynoai", StudyAiAccess.NONE);

        mockMvc.perform(post("/api/study/plan/apply").with(user("applynoai")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(today().plusDays(1), "1장 접근통제"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.removed").value(0));

        verifyNoInteractions(assistant);
    }

    @Test
    @DisplayName("apply: 오늘 이후를 갈아치우고 과거는 남긴다 — 적용 후 agenda에 그대로 뜬다")
    void apply_replacesFutureKeepsPast() throws Exception {
        User user = register("applyreplace");
        planService.add(user, today().minusDays(1), null, "과목", "어제 일정");
        planService.add(user, today(), null, "과목", "오늘 옛 일정");
        planService.add(user, today().plusDays(3), null, "과목", "미래 옛 일정");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applyreplace")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(today(), "1장 접근통제") + ","
                                + applyDay(today().plusDays(1), "2장 암호학"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.removed").value(2));

        mockMvc.perform(get("/api/study/agenda").param("month", thisMonth()).with(user("applyreplace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.task == '1장 접근통제')]").exists())
                .andExpect(jsonPath("$.items[?(@.task == '오늘 옛 일정')]").doesNotExist());

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                user, today().minusDays(1), today().minusDays(1))).hasSize(1);
    }

    @Test
    @DisplayName("apply: 오늘보다 이른 날짜가 섞이면 400이고 기존 일정은 그대로다")
    void apply_pastDate_isBadRequestAndKeepsExisting() throws Exception {
        User user = register("applypast");
        planService.add(user, today().plusDays(2), null, "과목", "지켜져야 할 일정");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applypast")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(today().minusDays(1), "지난 일"))))
                .andExpect(status().isBadRequest());

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                user, today(), today().plusDays(5))).hasSize(1);
    }

    @Test
    @DisplayName("apply: 같은 날짜가 두 번 들어오면 400")
    void apply_duplicateDate_isBadRequest() throws Exception {
        register("applydup");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applydup")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(today(), "하나") + "," + applyDay(today(), "둘"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("apply: 빈 목록은 400 — 「적용」이 조용한 전체 삭제가 되면 안 된다")
    void apply_emptyDays_isBadRequest() throws Exception {
        User user = register("applyempty");
        planService.add(user, today().plusDays(1), null, "과목", "남아 있어야 할 일정");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applyempty")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody("")))
                .andExpect(status().isBadRequest());

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                user, today(), today().plusDays(5))).hasSize(1);
    }

    @Test
    @DisplayName("apply: 366일을 넘으면 400")
    void apply_tooManyDays_isBadRequest() throws Exception {
        register("applytoomany");
        StringBuilder days = new StringBuilder();
        for (int i = 0; i <= StudyPlanService.MAX_DAYS; i++) {
            if (i > 0) {
                days.append(',');
            }
            days.append(applyDay(today().plusDays(i), "할 일"));
        }

        mockMvc.perform(post("/api/study/plan/apply").with(user("applytoomany")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(days.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("apply: 500자를 넘는 할 일은 400")
    void apply_taskTooLong_isBadRequest() throws Exception {
        register("applylongtask");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applylongtask")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(applyDay(today(), "가".repeat(501)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("apply: 남의 bookId는 404(존재 비노출)이고 일정은 안 바뀐다")
    void apply_othersBook_isNotFound() throws Exception {
        User other = register("applybookowner");
        User me = register("applybookthief");
        StudyBook othersBook = studyBook(other, "남의 책");
        planService.add(me, today().plusDays(1), null, "과목", "남아 있어야 할 일정");

        mockMvc.perform(post("/api/study/plan/apply").with(user("applybookthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"과목\",\"bookId\":" + othersBook.getId()
                                + ",\"days\":[" + applyDay(today(), "할 일") + "]}"))
                .andExpect(status().isNotFound());

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                me, today(), today().plusDays(5))).hasSize(1);
    }

    @Test
    @DisplayName("agenda: remaining.plan은 오늘 남은 일정 생성 몫이다")
    void agenda_reportsRemainingPlan() throws Exception {
        registerWith("planremaining", StudyAiAccess.APPROVED);
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.generatePlan(any())).willReturn(AiResult.ok(new PlanDraft(
                List.of(new PlanDay(iso(today().plusDays(1)), "1장 접근통제")))));

        mockMvc.perform(get("/api/study/agenda").param("month", thisMonth()).with(user("planremaining")))
                .andExpect(jsonPath("$.remaining.plan").value(3));

        mockMvc.perform(post("/api/study/plan/generate").with(user("planremaining")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultGenerateBody()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/study/agenda").param("month", thisMonth()).with(user("planremaining")))
                .andExpect(jsonPath("$.remaining.plan").value(2));
    }
}
