package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.session.WeeklyDebt;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 start/stop 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>화면에서 측정을 시작/종료하는 경로의 <b>와이어링</b>을 본다 — 진행 세션 생성/종료,
 * 잘못된 상태(중복 start / 없는 stop)의 플래시 에러 처리. 차감 <i>금액</i>은
 * 서비스·통합 테스트가 이미 검증하므로 여기선 다루지 않는다(N-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadingSessionControllerTest {

    // 통합 테스트는 운영 Clock.systemUTC()(실시간)를 쓰는데, "오늘 수동 입력"이 now 기준이라
    // 자정 경계(예: 06-08 00:0x KST)엔 now-30분이 전날로 넘어가 오늘 부채가 안 줄어 플레이키하게 깨진다. 결정적 날짜로 고정.
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock fixedClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
        }
    }

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReadingDebtService debtService;

    @Autowired
    private ReadingGoalChangeRepository goalChangeRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        // 닉네임은 유니크(uk_users_nickname) — 한 테스트가 두 사용자를 등록(IDOR)하므로 이메일로 구분한다.
        String nickname = email.substring(0, email.indexOf('@'));
        return registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, today());
    }

    // 측정은 책이 필수다 — 시작 setup이 쓸 책 한 권.
    private Book book(User owner) {
        return bookRepository.save(
                Book.register(owner, "클린 코드", null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("POST /sessions/start: bookId 없이 시작하면 책 미지정 세션을 만든다(발견 1 — 시작을 막지 않음, 에러 없음)")
    void start_withoutBookId_startsBooklessSession() throws Exception {
        User user = register("nobook@booktimer.com");

        mockMvc.perform(post("/sessions/start").with(user("nobook@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeCount(0));

        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user).orElseThrow();
        assertThat(active.getBook()).isNull();
    }

    @Test
    @DisplayName("POST /sessions/stop: 진행 중 세션을 종료하고 대시보드로 리다이렉트한다")
    void stop_endsActiveSession() throws Exception {
        User user = register("stop@booktimer.com");
        sessionService.start(user, clock.instant(), book(user));

        mockMvc.perform(post("/sessions/stop").with(user("stop@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/start: 이미 진행 중이면 플래시 에러로 안내한다 (세션 중복 생성 없음)")
    void start_whenActiveExists_flashesError() throws Exception {
        User user = register("dup@booktimer.com");
        Book book = book(user);
        sessionService.start(user, clock.instant(), book);

        mockMvc.perform(post("/sessions/start").param("bookId", String.valueOf(book.getId()))
                        .with(user("dup@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).hasSize(1);
    }

    @Test
    @DisplayName("POST /sessions/stop: 진행 중 세션이 없으면 플래시 에러로 안내한다")
    void stop_whenNoneActive_flashesError() throws Exception {
        register("none@booktimer.com");

        mockMvc.perform(post("/sessions/stop").with(user("none@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /sessions/start: bookId를 주면 그 책이 세션에 연결된다")
    void start_withBookId_attachesBook() throws Exception {
        User user = register("withbook@booktimer.com");
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/sessions/start").param("bookId", String.valueOf(book.getId()))
                        .with(user("withbook@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/"));

        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user).orElseThrow();
        assertThat(active.getBook()).isNotNull();
        assertThat(active.getBook().getId()).isEqualTo(book.getId());
    }

    @Test
    @DisplayName("POST /sessions/start: 읽고싶음 책으로 시작하면 그 책이 읽는중으로 자동 전환된다")
    void start_withWantToReadBook_autoTransitionsToReading() throws Exception {
        User user = register("autoread@booktimer.com");
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(post("/sessions/start").param("bookId", String.valueOf(book.getId()))
                        .with(user("autoread@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/"));

        Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("POST /sessions/start: 남의 책 bookId면 책 선택 안내 에러 + 세션을 만들지 않는다(IDOR 방지)")
    void start_withOtherUsersBookId_flashesError_noSession() throws Exception {
        User owner = register("bookowner@booktimer.com");
        User attacker = register("bookattacker@booktimer.com");
        Book othersBook = bookRepository.save(
                Book.register(owner, "남의 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/sessions/start").param("bookId", String.valueOf(othersBook.getId()))
                        .with(user("bookattacker@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUserAndEndedAtIsNull(attacker)).isEmpty();
    }

    // --- 사후 수동 입력: GET 폼 + POST /sessions/manual ---

    @Test
    @DisplayName("GET /sessions/manual: 수동 기록 폼을 렌더한다(책 선택·날짜·시간 입력 필드)")
    void manualForm_renders() throws Exception {
        User user = register("mform@booktimer.com");
        book(user); // 폼은 고를 책이 있을 때만 렌더된다(책 미지정 기록 금지)

        mockMvc.perform(get("/sessions/manual").with(user("mform@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"bookId\"")))
                .andExpect(content().string(containsString("name=\"date\"")));
    }

    @Test
    @DisplayName("POST /sessions/manual: 책+날짜+시간을 주면 완료 세션을 만들고 안내 메시지와 함께 폼으로 돌아간다")
    void manualSubmit_recordsCompletedSession() throws Exception {
        User user = register("mok@booktimer.com");
        Book book = book(user);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().toString())
                        .param("hours", "1").param("minutes", "30")
                        .with(user("mok@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("message"));

        List<ReadingSession> sessions = sessionRepository.findByUser(user);
        assertThat(sessions).hasSize(1);
        ReadingSession s = sessions.get(0);
        assertThat(s.isActive()).isFalse();
        assertThat(s.getDurationSeconds()).isEqualTo(90 * 60L); // 1시간 30분
        assertThat(s.getBook().getId()).isEqualTo(book.getId());
    }

    @Test
    @DisplayName("POST /sessions/manual: 과거 날짜로 기록하면 그 날짜에 안착한다(잔디·기록 일자)")
    void manualSubmit_pastDate_landsOnThatDate() throws Exception {
        User user = register("mpast@booktimer.com");
        Book book = book(user);
        LocalDate past = today().minusDays(3);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", past.toString())
                        .param("hours", "0").param("minutes", "20")
                        .with(user("mpast@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"));

        ReadingSession s = sessionRepository.findByUser(user).get(0);
        Instant startedAt = s.getStartedAt();
        assertThat(LocalDate.ofInstant(startedAt, ZoneId.of(SEOUL))).isEqualTo(past);
    }

    @Test
    @DisplayName("POST /sessions/manual: 과거 날짜 20시간 기록도 전날로 새지 않는다 — 앵커가 그날 00:00 순방향(정오 역산 폐지)")
    void manualSubmit_pastDateLongDuration_staysWithinThatDate() throws Exception {
        User user = register("mlong@booktimer.com");
        Book book = book(user);
        LocalDate past = today().minusDays(3);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", past.toString())
                        .param("hours", "20").param("minutes", "0")
                        .with(user("mlong@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"));

        // 정오 역산이면 20시간이 전날 16:00부터 시작해 전날에 8시간이 샌다 — 그 꼴이 아님을 못 박는다.
        List<ReadingSession> sessions = sessionRepository.findByUser(user);
        assertThat(sessions).hasSize(1);
        ReadingSession s = sessions.get(0);
        assertThat(s.getStartedAt()).isEqualTo(past.atStartOfDay(ZoneId.of(SEOUL)).toInstant());
        assertThat(s.getDurationSeconds()).isEqualTo(20 * 3600L);
        assertThat(LocalDate.ofInstant(s.getStartedAt(), ZoneId.of(SEOUL))).isEqualTo(past);
        assertThat(LocalDate.ofInstant(s.getEndedAt(), ZoneId.of(SEOUL))).isEqualTo(past);
    }

    @Test
    @DisplayName("POST /sessions/manual: 오늘 날짜로 기록하면 오늘 부채가 그만큼 준다(세션 저장으로 자동 반영)")
    void manualSubmit_today_reducesTodayDebt() throws Exception {
        User user = register("mtoday@booktimer.com");
        Book book = book(user);
        long todayDebtBefore = debtService.weeklyDebt(user).todayDebtSeconds();

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().toString())
                        .param("hours", "0").param("minutes", "30")
                        .with(user("mtoday@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"));

        long todayDebtAfter = debtService.weeklyDebt(user).todayDebtSeconds();
        assertThat(todayDebtAfter).isEqualTo(Math.max(0, todayDebtBefore - 30 * 60L)); // 30분 채움
    }

    @Test
    @DisplayName("POST /sessions/manual: 윈도우 내 과거 날짜로 기록하면 그 날 부채만 줄고 오늘 부채는 그대로다 — 과거를 오늘에 적용하지 않는다")
    void manualSubmit_pastInWindow_reducesThatDayNotToday() throws Exception {
        User user = register("mpastkeep@booktimer.com");
        Book book = book(user);
        long todayDebtBefore = debtService.weeklyDebt(user).todayDebtSeconds();
        long totalDebtBefore = debtService.weeklyDebt(user).totalDebtSeconds();

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().minusDays(3).toString()) // 윈도우(7일) 안의 과거
                        .param("hours", "0").param("minutes", "30")
                        .with(user("mpastkeep@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"));

        assertThat(sessionRepository.findByUser(user)).hasSize(1);
        WeeklyDebt after = debtService.weeklyDebt(user);
        assertThat(after.todayDebtSeconds()).isEqualTo(todayDebtBefore);          // 오늘 부채 불변(사용자 버그 회귀 방지)
        assertThat(after.totalDebtSeconds()).isEqualTo(totalDebtBefore - 30 * 60L); // 그 날 부채만 30분 감소
    }

    @Test
    @DisplayName("POST /sessions/manual: 부채 창 밖 날짜는 거부(에러 안내 + 세션 없음) — 목표 이력 없으면 창은 7일 폴백")
    void manualSubmit_outsideWindow_flashesError_noSession() throws Exception {
        User user = register("moutwin@booktimer.com");
        Book book = book(user);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().minusDays(8).toString()) // 폴백 창(7일) 밖
                        .param("hours", "1")
                        .with(user("moutwin@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/manual: 부채 창이 넓으면 8일 전도 기록된다 — 입력 하한 = 부채 창(7일 고정 폐지)")
    void manualSubmit_eightDaysAgo_allowedWhenDebtWindowIsWider() throws Exception {
        User user = register("mwidewin@booktimer.com");
        Book book = book(user);
        // 첫 목표를 30일 전으로 두면 그날부터가 부채 대상 구간 → 그 안의 8일 전은 채울 수 있어야 한다.
        goalChangeRepository.save(ReadingGoalChange.of(user, today().minusDays(30), 3600L));
        long totalBefore = debtService.weeklyDebt(user).totalDebtSeconds();

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().minusDays(8).toString())
                        .param("hours", "0").param("minutes", "30")
                        .with(user("mwidewin@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("message"));

        assertThat(sessionRepository.findByUser(user)).hasSize(1);
        assertThat(debtService.weeklyDebt(user).totalDebtSeconds()).isEqualTo(totalBefore - 30 * 60L);
    }

    @Test
    @DisplayName("POST /sessions/manual: 부채 창(첫 목표일) 이전 날짜는 여전히 거부 — 시작 전은 채울 대상이 아니다")
    void manualSubmit_beforeDebtWindowStart_rejected() throws Exception {
        User user = register("mbeforewin@booktimer.com");
        Book book = book(user);
        goalChangeRepository.save(ReadingGoalChange.of(user, today().minusDays(30), 3600L));

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().minusDays(31).toString()) // 첫 목표일 하루 전
                        .param("hours", "1")
                        .with(user("mbeforewin@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/manual: 책을 안 고르면 에러 안내 + 세션을 만들지 않는다")
    void manualSubmit_withoutBook_flashesError_noSession() throws Exception {
        User user = register("mnobook@booktimer.com");

        mockMvc.perform(post("/sessions/manual")
                        .param("date", today().toString())
                        .param("hours", "1")
                        .with(user("mnobook@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/manual: 남의 책 bookId면 에러 안내 + 세션을 만들지 않는다(IDOR 방지)")
    void manualSubmit_withOtherUsersBook_flashesError_noSession() throws Exception {
        User owner = register("mowner@booktimer.com");
        User attacker = register("mattacker@booktimer.com");
        Book othersBook = bookRepository.save(
                Book.register(owner, "남의 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(othersBook.getId()))
                        .param("date", today().toString())
                        .param("hours", "1")
                        .with(user("mattacker@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(attacker)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/manual: 미래 날짜는 거부(에러 안내 + 세션 없음)")
    void manualSubmit_futureDate_flashesError_noSession() throws Exception {
        User user = register("mfuture@booktimer.com");
        Book book = book(user);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().plusDays(1).toString())
                        .param("hours", "1")
                        .with(user("mfuture@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("POST /sessions/manual: 읽은 시간이 0이면 거부(에러 안내 + 세션 없음)")
    void manualSubmit_zeroDuration_flashesError_noSession() throws Exception {
        User user = register("mzero@booktimer.com");
        Book book = book(user);

        mockMvc.perform(post("/sessions/manual")
                        .param("bookId", String.valueOf(book.getId()))
                        .param("date", today().toString())
                        .param("hours", "0").param("minutes", "0")
                        .with(user("mzero@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/sessions/manual"))
                .andExpect(flash().attributeExists("error"));

        assertThat(sessionRepository.findByUser(user)).isEmpty();
    }
}
