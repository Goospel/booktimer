package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/history 컨트롤러 통합 테스트 (선별 SPA 단계 1b).
 *
 * <p>①인증 게이트(default-deny), ②JSON 구조(nickname·months·graph·weeklyShortfall),
 * ③빈 데이터 경계(신규 유저), ④직렬화 형태(YearMonth·LocalDate·enum 평탄화).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /api/history 미인증 → 302 로그인 리다이렉트 (default-deny)")
    void getHistory_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/history 인증 → 200 JSON (nickname·months·graph·weeklyShortfall 키 존재)")
    void getHistory_authenticated_returnsJsonStructure() throws Exception {
        registrationService.register("histapi1@booktimer.com", "rawpw1234", "기록API1", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/history")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("histapi1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nickname").value("기록API1"))
                .andExpect(jsonPath("$.months").isArray())
                .andExpect(jsonPath("$.graph").exists())
                .andExpect(jsonPath("$.graph.weeks").isArray())
                .andExpect(jsonPath("$.weeklyShortfall").isArray());
    }

    @Test
    @DisplayName("빈 데이터 경계: 세션 0 신규 유저 → months 빈 배열, graph.activeDays 0, graph.currentStreak 0")
    void getHistory_newUser_emptyMonths_graphStillFilled() throws Exception {
        registrationService.register("histapi-empty@booktimer.com", "rawpw1234", "빈기록", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/history")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("histapi-empty@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty())
                .andExpect(jsonPath("$.graph.weeks").isArray())
                .andExpect(jsonPath("$.graph.activeDays").value(0))
                .andExpect(jsonPath("$.graph.currentStreak").value(0));
    }

    @Test
    @DisplayName("직렬화: YearMonth='yyyy-MM', LocalDate='yyyy-MM-dd'")
    void getHistory_withSession_serializationFormats() throws Exception {
        User u = registrationService.register("histser@booktimer.com", "rawpw1234", "직렬화", SEOUL, Role.USER, today());
        Book book = bookRepository.save(
                Book.register(u, "직렬화책", null, null, null, null, null, BookStatus.READING));
        Instant start = clock.instant();
        sessionService.start(u, start, book);
        sessionService.stop(u, start.plusSeconds(3600));

        mockMvc.perform(get("/api/history")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("histser@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].month")
                        .value(today().format(DateTimeFormatter.ofPattern("yyyy-MM"))))
                .andExpect(jsonPath("$.months[0].days[0].date")
                        .value(today().format(DateTimeFormatter.ISO_LOCAL_DATE)));
    }
}
