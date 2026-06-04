package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ContributionGraph;
import com.booktimer.session.DailyReadingRecord;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(model().attributeExists("records"))
                .andExpect(model().attributeExists("graph"))
                .andReturn();

        // 독서 잔디에 그 30분 세션이 반영돼야 한다(종단 와이어링: 세션→집계→그리드)
        ContributionGraph graph = (ContributionGraph) result.getModelAndView().getModel().get("graph");
        assertThat(graph.activeDays()).isEqualTo(1);
        assertThat(graph.totalSeconds()).isEqualTo(1800L);
        assertThat(graph.weeks()).hasSize(53);
    }

    @Test
    @DisplayName("GET /history: 기록이 없으면 빈 목록을 싣는다(화면은 정상 렌더)")
    void history_emptyForNewUser() throws Exception {
        registrationService.register("empty@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("empty@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attribute("records", List.<DailyReadingRecord>of()));
    }
}
