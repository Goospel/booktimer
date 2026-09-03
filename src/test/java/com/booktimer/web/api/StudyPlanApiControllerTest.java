package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.study.StudyPlanItem;
import com.booktimer.study.StudyPlanItemRepository;
import com.booktimer.study.StudyPlanService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired Clock clock;

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
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
}
