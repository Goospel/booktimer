package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ContributionGraph;
import com.booktimer.session.MonthlyReadingSection;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 일자별 독서 기록 화면 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>로그인 주체(username=email)를 도메인 User로 매핑하고, 완료된 세션을 일자별로 집계해
 * 화면 모델에 싣는지 검증한다(README 2.2). 집계 로직 자체는 단위 테스트가 검증하므로
 * 여기서는 <b>와이어링</b>(인증→유저→집계→뷰)을 확인한다(N-009 테스트 피라미드).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /history: 로그인 사용자에게 일자별 기록 화면을 그리고 집계를 싣는다")
    @SuppressWarnings("unchecked")
    void history_rendersForLoggedInUser() throws Exception {
        User user = registrationService.register("hist@booktimer.com", "rawpw1234", "기록가", SEOUL, Role.USER, today());
        // 완료된 세션 하나 (30분)
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING));
        Instant start = clock.instant();
        sessionService.start(user, start, book);
        sessionService.stop(user, start.plusSeconds(1800));

        var result = mockMvc.perform(get("/history").with(user("hist@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attribute("nickname", "기록가"))
                .andExpect(model().attributeExists("months"))
                .andExpect(model().attributeExists("graph"))
                .andReturn();

        // 30분 세션이 월별 묶음으로 화면에 실린다(종단 와이어링: 세션→집계→월 그룹→뷰)
        List<MonthlyReadingSection> months = (List<MonthlyReadingSection>) result.getModelAndView().getModel().get("months");
        assertThat(months).hasSize(1);
        assertThat(months.get(0).totalSeconds()).isEqualTo(1800L);

        // 독서 잔디에도 그 30분 세션이 반영돼야 한다(종단 와이어링: 세션→집계→그리드)
        ContributionGraph graph = (ContributionGraph) result.getModelAndView().getModel().get("graph");
        assertThat(graph.activeDays()).isEqualTo(1);
        assertThat(graph.totalSeconds()).isEqualTo(1800L);
        assertThat(graph.weeks()).hasSize(53);
    }

    @Test
    @DisplayName("GET /history: 수동 입력(직접 채운)으로 채운 날은 잔디 칸에 manual 표시로 실린다 (종단: recordManual→집계→그리드→뷰)")
    void history_manualEntryDay_markedOnGrass() throws Exception {
        User user = registrationService.register("histman@booktimer.com", "rawpw1234", "수동", SEOUL, Role.USER, today());
        Book book = bookRepository.save(
                Book.register(user, "전쟁과 평화 1", null, null, null, null, null, BookStatus.READING));
        // 3일 전을 수동으로 채움(빠뜨린 날 채우기) — 잔디 윈도우 안.
        LocalDate filledDate = today().minusDays(3);
        Instant manualStart = filledDate.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        sessionService.recordManual(user, manualStart, manualStart.plusSeconds(3600), book);

        var result = mockMvc.perform(get("/history").with(user("histman@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andReturn();

        ContributionGraph graph = (ContributionGraph) result.getModelAndView().getModel().get("graph");
        boolean filledDayIsManual = graph.weeks().stream()
                .flatMap(List::stream)
                .anyMatch(d -> filledDate.equals(d.date()) && d.manual());
        assertThat(filledDayIsManual).as("직접 채운 날 칸은 manual=true여야 한다").isTrue();
    }

    @Test
    @DisplayName("GET /history: 광고 게시자 ID가 없으면(기본) 광고 스크립트가 새지 않는다 (스캐폴드 안전 불변식)")
    void history_noAdsWhenDisabled() throws Exception {
        // 테스트 컨텍스트는 booktimer.ads.client-id가 비어 있다 → 광고 비활성.
        registrationService.register("noads@booktimer.com", "rawpw1234", "무광고", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("noads@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("adsbygoogle"))));
    }

    @Test
    @DisplayName("GET /history: 기록이 없으면 빈 목록을 싣는다(화면은 정상 렌더)")
    void history_emptyForNewUser() throws Exception {
        registrationService.register("empty@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("empty@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attribute("months", List.of()));
    }

    @Test
    @DisplayName("GET /history: 이번 주 빠뜨린 날 목록(weeklyShortfall)을 모델에 싣는다 (대시보드에서 이 화면으로 이전)")
    void history_includesWeeklyShortfall() throws Exception {
        // 갓 가입해 아무 날도 안 읽은 유저 → 윈도우 내 과거 날이 빠뜨린 날로 잡혀 목록이 비지 않는다.
        // (계산 자체는 단위 테스트가 검증 — 여기선 컨트롤러가 그 목록을 화면에 엮는 와이어링만 본다.)
        registrationService.register("histshort@booktimer.com", "rawpw1234", "빠뜨림", SEOUL, Role.USER, today());

        var result = mockMvc.perform(get("/history").with(user("histshort@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attributeExists("weeklyShortfall"))
                // 옮긴 섹션이 이 화면에서 실제로 렌더된다(Thymeleaf 표현식이 새 자리에서 깨지지 않음 + 채우기 링크 살아있음)
                .andExpect(content().string(containsString("이번 주 빠뜨린 날")))
                .andExpect(content().string(containsString("/sessions/manual")))
                .andReturn();

        List<?> shortfall = (List<?>) result.getModelAndView().getModel().get("weeklyShortfall");
        assertThat(shortfall).isNotEmpty();
    }
}
