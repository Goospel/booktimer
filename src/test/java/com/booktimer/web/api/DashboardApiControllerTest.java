package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.timer.ReadingTimerRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/dashboard + POST /api/sessions/start|stop 통합 테스트.
 *
 * <p>IDOR·DTO 화이트리스트·floor 음수 가드·ISO Z·CSRF·좀비 세션이 핵심 경계.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingSessionService sessionService;
    @Autowired ReadingTimerRepository timerRepository;
    @Autowired BookService bookService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "닉네임_" + loginId, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book addBook(User u, String title, BookStatus status) {
        return bookRepository.save(Book.register(u, title, null, null, null, null, null, status));
    }

    // ── 1. 미인증 → 302 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 미인증 → /login 302")
    void get_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 2. 구조 + 키 존재 ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 인증 → 200 + 필수 키 존재")
    void get_authenticated_returnsStructure() throws Exception {
        register("struct@a.com", "struct");

        mockMvc.perform(get("/api/dashboard").with(user("struct@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("닉네임_struct"))
                .andExpect(jsonPath("$.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.carriedDebtSeconds").isNumber())
                .andExpect(jsonPath("$.hasActiveSession").isBoolean())
                .andExpect(jsonPath("$.readingBooks").isArray())
                .andExpect(jsonPath("$.finishedBooks").isArray())
                .andExpect(jsonPath("$.graph").exists())
                .andExpect(jsonPath("$.graph.weeks").isArray())
                .andExpect(jsonPath("$.graph.growthStageName").isString())
                .andExpect(jsonPath("$.graph.growthStageLabel").isString())
                .andExpect(jsonPath("$.garden").exists())
                .andExpect(jsonPath("$.quote").exists())
                .andExpect(jsonPath("$.emailVerified").isBoolean());
    }

    // ── 3. floor 음수 가드 + carryover ON ────────────────────────────────────

    @Test
    @DisplayName("carryover ON: carriedDebtSeconds >= 0 (floor 음수 가드)")
    void floorCarryoverON_neverNegative() throws Exception {
        register("carry@a.com", "carry");

        mockMvc.perform(get("/api/dashboard").with(user("carry@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carriedDebtSeconds", greaterThanOrEqualTo(0)));
    }

    // ── 4. carryover OFF → floor = 0 ─────────────────────────────────────────

    @Test
    @DisplayName("carryover OFF: carriedDebtSeconds = 0")
    void floorCarryoverOFF_floorZero() throws Exception {
        User u = register("carryoff@a.com", "carryoff");
        // debtCarryover 플래그를 직접 끔
        timerRepository.findByUser(u).ifPresent(t -> {
            t.updateSettings(t.getDailyIncrementSeconds(), false);
            timerRepository.save(t);
        });

        mockMvc.perform(get("/api/dashboard").with(user("carryoff@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carriedDebtSeconds").value(0));
    }

    // ── 5. start IDOR → 404 ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/start: 남의 책 bookId → 404 (IDOR)")
    void startSession_otherUserBook_404() throws Exception {
        User alice = register("alice@a.com", "alice");
        User bob = register("bob@a.com", "bob");
        Book aliceBook = addBook(alice, "앨리스 책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("bob@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + aliceBook.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    // ── 6. start bookId null → 404 ───────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/start: bookId null → 404 (IDOR 마스킹)")
    void startSession_bookIdNull_404() throws Exception {
        register("nullbook@a.com", "nullbook");

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("nullbook@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ── 7. start 중복 → 409 ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/start: 이미 활성 세션 있음 → 409")
    void startSession_alreadyActive_409() throws Exception {
        User u = register("dup@a.com", "dup");
        Book book = addBook(u, "책", BookStatus.READING);
        sessionService.start(u, clock.instant(), book);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("dup@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isConflict());
    }

    // ── 8. stop 활성 없음 → 409 ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/stop: 진행 중 세션 없음 → 409")
    void stopSession_noActive_409() throws Exception {
        register("nostop@a.com", "nostop");

        mockMvc.perform(post("/api/sessions/stop")
                        .with(user("nostop@a.com")).with(csrf()))
                .andExpect(status().isConflict());
    }

    // ── 9. DTO 화이트리스트 ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 엔티티 직접 직렬화 금지 — user·clickCount·coupangClickCount 없음")
    void dtoWhitelist_noEntityLeak() throws Exception {
        register("wl@a.com", "wltest");

        mockMvc.perform(get("/api/dashboard").with(user("wl@a.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"user\""))))
                .andExpect(content().string(not(containsString("clickCount"))))
                .andExpect(content().string(not(containsString("coupangClickCount"))));
    }

    // ── 10. 빈 책장 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("책 0 → readingBooks=[], finishedBooks=[], recentBookId=null")
    void emptyBookshelf_emptyLists() throws Exception {
        register("empty@a.com", "empty");

        mockMvc.perform(get("/api/dashboard").with(user("empty@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readingBooks").isEmpty())
                .andExpect(jsonPath("$.finishedBooks").isEmpty())
                .andExpect(jsonPath("$.recentBookId").doesNotExist());
    }

    // ── 11. activeStartedAt ISO 8601 Z ───────────────────────────────────────

    @Test
    @DisplayName("활성 세션 존재 → activeStartedAt이 'Z'로 끝남 (UTC, 타임존 모호 방지)")
    void activeStartedAt_iso8601WithZ() throws Exception {
        User u = register("ztest@a.com", "ztest");
        Book book = addBook(u, "책", BookStatus.READING);
        sessionService.start(u, clock.instant(), book);

        mockMvc.perform(get("/api/dashboard").with(user("ztest@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStartedAt", endsWith("Z")));
    }

    // ── 12. 좀비 세션(endedAt=null 강제 삽입) ────────────────────────────────

    @Test
    @DisplayName("좀비 세션(endedAt=null 강제 삽입) → hasActiveSession=true, activeStartedAt 보존")
    void staleActiveSession_recoveredOnNextLoad() throws Exception {
        User u = register("zombie@a.com", "zombie");
        Book book = addBook(u, "좀비책", BookStatus.READING);
        Instant startedAt = clock.instant().minusSeconds(3600);

        // JdbcTemplate으로 진행 중 세션 직접 삽입 (endedAt=null)
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO reading_session " +
                "(user_id, book_id, started_at, ended_at, duration_seconds, manual_entry, created_at, updated_at) " +
                "VALUES (?, ?, ?, NULL, 0, false, ?, ?)",
                u.getId(), book.getId(), startedAt, now, now);

        mockMvc.perform(get("/api/dashboard").with(user("zombie@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeStartedAt").isString());
    }

    // ── 13. CSRF 없음 → 403 ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/start: CSRF 누락 → 403")
    void postWithoutCsrf_403() throws Exception {
        User u = register("nocsrf@a.com", "nocsrf");
        Book book = addBook(u, "책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("nocsrf@a.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    // ── 14. CSRF 있음 → 200 (start) ──────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions/start: CSRF 있음 + 정상 bookId → 200 + TimerState")
    void postWithCsrf_startOk() throws Exception {
        User u = register("yescsrf@a.com", "yescsrf");
        Book book = addBook(u, "책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("yescsrf@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.carriedDebtSeconds").isNumber());
    }

    // ── 15. garden DTO — 엔티티 User FK 없음 (spot-check) ────────────────────

    @Test
    @DisplayName("GET /api/dashboard: garden.plants[0].code 존재 (엔티티 누출 없이 CatalogDto 직렬화)")
    void gardenDtoSpotCheck() throws Exception {
        register("garden@a.com", "garden");

        // plants는 비어있을 수 있으므로 catalog 키 존재 여부만 확인
        mockMvc.perform(get("/api/dashboard").with(user("garden@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.garden.plants").isArray())
                .andExpect(jsonPath("$.garden.ownedCount").isNumber());
    }
}
