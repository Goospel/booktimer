package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookService;
import com.booktimer.session.ReadingDebtService;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired ReadingDebtService readingDebtService;
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
                .andExpect(jsonPath("$.quotes").isArray())
                .andExpect(jsonPath("$.quotes[0].text").isString())
                .andExpect(jsonPath("$.quotes[0].author").isString())
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

    // ── 프로필 사진(도감 작가 얼굴) ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 프로필 작가를 선택했으면 profileCharacterCode를 응답에 싣는다")
    void get_withProfileCharacter_includesCode() throws Exception {
        User u = register("pcdash@a.com", "pcdash");
        u.selectProfileCharacter("han_gang"); // 엔티티 직접(보유검증 우회) — 노출 경로만 검증
        userRepository.save(u);

        mockMvc.perform(get("/api/dashboard").with(user("pcdash@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCharacterCode").value("han_gang"));
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

    // ── 6. start bookId 없음 → 200 + 책 미지정 세션 (발견 1 — 책 없이 시작) ──────
    // 과거엔 bookId null을 404(IDOR 마스킹)로 막았으나, "시작을 책 선택으로 가로막지 않는다"는
    // 발견 1 취지로 bookId를 아예 안 주면 책 미지정 세션을 허용한다. (bookId가 '있는데' 남의 것/미존재면
    // 여전히 404 — 그 IDOR 경계는 startSession_otherUserBook_404가 지킨다.)

    @Test
    @DisplayName("POST /api/sessions/start: bookId 없이 → 200 + 책 미지정 세션 시작(발견 1)")
    void startSession_noBookId_startsWithoutBook() throws Exception {
        User u = register("nobookstart@a.com", "nobookstart");

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("nobookstart@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true));

        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(u).orElseThrow();
        assertThat(active.getBook()).isNull();
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

    // ── 8b. stop 성공 → timer + graph 동봉 (측정 종료 즉시 잔디 갱신용) ────────
    // 종료 응답이 타이머만 주면 클라이언트가 잔디(contribution graph)를 새로고침 없이 못 갱신한다.
    // 측정 종료는 잔디가 변하는 바로 그 순간이므로, 응답에 방금 확정된 세션이 반영된 graph를 동봉한다.

    @Test
    @DisplayName("POST /api/sessions/stop: 성공 응답에 timer + graph(잔디) 동봉 — 새로고침 없이 잔디 갱신")
    void stopSession_responseIncludesGraph() throws Exception {
        User u = register("stopgraph@a.com", "stopgraph");
        Book book = addBook(u, "책", BookStatus.READING);
        sessionService.start(u, clock.instant(), book);

        mockMvc.perform(post("/api/sessions/stop")
                        .with(user("stopgraph@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timer.hasActiveSession").value(false))
                .andExpect(jsonPath("$.timer.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.timer.todayGoalSeconds").isNumber())
                .andExpect(jsonPath("$.graph").exists())
                .andExpect(jsonPath("$.graph.weeks").isArray())
                .andExpect(jsonPath("$.graph.currentStreak").isNumber());
    }

    // ── 8c. stop 응답 sessionId + untagged (종료 후 태깅 트리거) ───────────────
    // 책 없이 시작한 세션은 종료 시 "무슨 책이었나요?" 태깅 시트를 띄운다 — 프론트가 방금 세션의 id와
    // "미태깅 여부"를 알아야 하므로 응답에 담는다. 책 골라 시작한 세션은 untagged=false(시트 안 뜸).

    @Test
    @DisplayName("POST /api/sessions/stop: 책 없이 시작한 세션 → 응답에 sessionId + untagged=true")
    void stop_booklessSession_responseHasSessionIdAndUntagged() throws Exception {
        User u = register("stopuntag@a.com", "stopuntag");
        sessionService.start(u, clock.instant(), null); // 책 없이 시작

        mockMvc.perform(post("/api/sessions/stop").with(user("stopuntag@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.untagged").value(true));
    }

    @Test
    @DisplayName("POST /api/sessions/stop: 책 골라 시작한 세션 → untagged=false (태깅 시트 안 뜸)")
    void stop_taggedSession_untaggedFalse() throws Exception {
        User u = register("stoptag@a.com", "stoptag");
        Book book = addBook(u, "책", BookStatus.READING);
        sessionService.start(u, clock.instant(), book);

        mockMvc.perform(post("/api/sessions/stop").with(user("stoptag@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.untagged").value(false));
    }

    // ── 8e. stop 응답 firstCompletedSession (첫 완료 축하 트리거) ───────────────
    // 신규 유저가 첫 기록을 남긴 바로 그 순간에만 true — 클라이언트가 축하 배너 + 잔디 하이라이트를 띄운다.
    // "정확히 1이 되는 순간"이 경계다: 2번째부터는 false여야 축하가 매번 뜨는 잡음이 되지 않는다.

    @Test
    @DisplayName("POST /api/sessions/stop: 완료 기록 0건이던 유저의 첫 종료 → firstCompletedSession=true")
    void stop_firstEverCompletion_flagsTrue() throws Exception {
        User u = register("firstsess@a.com", "firstsess");
        sessionService.start(u, clock.instant(), null);

        mockMvc.perform(post("/api/sessions/stop").with(user("firstsess@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstCompletedSession").value(true));
    }

    @Test
    @DisplayName("POST /api/sessions/stop: 이미 완료 1건이 있던 유저의 두 번째 종료 → false (축하는 한 번뿐)")
    void stop_secondCompletion_flagsFalse() throws Exception {
        User u = register("secondsess@a.com", "secondsess");
        sessionService.start(u, clock.instant(), null);
        sessionService.stop(u, clock.instant());
        sessionService.start(u, clock.instant(), null);

        mockMvc.perform(post("/api/sessions/stop").with(user("secondsess@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstCompletedSession").value(false));
    }

    @Test
    @DisplayName("POST /api/sessions/stop: 수동 기록이 선행돼 있으면 첫 실시간 종료도 false — 첫 '기록'의 의미를 지킨다")
    void stop_afterManualRecord_flagsFalse() throws Exception {
        User u = register("manualfirst@a.com", "manualfirst");
        Book book = addBook(u, "손으로 적은 책", BookStatus.READING);
        Instant now = clock.instant();
        sessionService.recordManual(u, now.minusSeconds(3600), now.minusSeconds(1800), book);
        sessionService.start(u, now, null);

        mockMvc.perform(post("/api/sessions/stop").with(user("manualfirst@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstCompletedSession").value(false));
    }

    @Test
    @DisplayName("POST /api/sessions/stop: 남의 완료 세션은 내 count에 안 섞인다 — 남이 읽었다고 내 첫 기록이 사라지지 않는다")
    void stop_othersCompletionsDoNotCount() throws Exception {
        User other = register("othersess@a.com", "othersess");
        sessionService.start(other, clock.instant(), null);
        sessionService.stop(other, clock.instant()); // 남의 완료 세션 1건

        User me = register("minesess@a.com", "minesess");
        sessionService.start(me, clock.instant(), null);

        mockMvc.perform(post("/api/sessions/stop").with(user("minesess@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstCompletedSession").value(true));
    }

    // ── 8d. 종료 후 태깅 엔드포인트 (IDOR·재태깅 경계) ─────────────────────────

    @Test
    @DisplayName("POST /api/sessions/{id}/tag-book: 책 미지정 세션에 책 연결 + 읽고싶음→읽는중 전환")
    void tagBook_linksBookAndFlipsWantToRead() throws Exception {
        User u = register("tagok@a.com", "tagok");
        Book book = addBook(u, "태깅책", BookStatus.WANT_TO_READ);
        ReadingSession s = sessionService.start(u, clock.instant(), null);
        sessionService.stop(u, clock.instant());

        mockMvc.perform(post("/api/sessions/" + s.getId() + "/tag-book")
                        .with(user("tagok@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(s.getId().intValue()))
                .andExpect(jsonPath("$.bookTitle").value("태깅책"));

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getBook().getId())
                .isEqualTo(book.getId());
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/tag-book: 남의 세션 태깅 → 404 (IDOR)")
    void tagBook_othersSession_404() throws Exception {
        User alice = register("tagalice@a.com", "tagalice");
        User bob = register("tagbob@a.com", "tagbob");
        ReadingSession aliceSession = sessionService.start(alice, clock.instant(), null);
        sessionService.stop(alice, clock.instant());
        Book bobBook = addBook(bob, "밥책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/" + aliceSession.getId() + "/tag-book")
                        .with(user("tagbob@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bobBook.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/tag-book: 남의 책으로 태깅 → 404 (IDOR)")
    void tagBook_othersBook_404() throws Exception {
        User alice = register("tbalice@a.com", "tbalice");
        User bob = register("tbbob@a.com", "tbbob");
        ReadingSession bobSession = sessionService.start(bob, clock.instant(), null);
        sessionService.stop(bob, clock.instant());
        Book aliceBook = addBook(alice, "앨리스책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/" + bobSession.getId() + "/tag-book")
                        .with(user("tbbob@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + aliceBook.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/tag-book: 이미 책이 지정된 세션 재태깅 → 409")
    void tagBook_alreadyTagged_409() throws Exception {
        User u = register("retag@a.com", "retag");
        Book first = addBook(u, "첫 책", BookStatus.READING);
        Book second = addBook(u, "둘째 책", BookStatus.READING);
        ReadingSession s = sessionService.start(u, clock.instant(), first);
        sessionService.stop(u, clock.instant());

        mockMvc.perform(post("/api/sessions/" + s.getId() + "/tag-book")
                        .with(user("retag@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + second.getId() + "}"))
                .andExpect(status().isConflict());
    }

    // ── 8e. wantToReadBooks 노출 (태깅 시트용) ────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: wantToReadBooks(읽고싶음) 목록을 응답에 싣는다 — 태깅 시트용")
    void get_includesWantToReadBooks() throws Exception {
        User u = register("wtr@a.com", "wtr");
        addBook(u, "읽고싶은 책", BookStatus.WANT_TO_READ);
        addBook(u, "읽는 책", BookStatus.READING);

        mockMvc.perform(get("/api/dashboard").with(user("wtr@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wantToReadBooks").isArray())
                .andExpect(jsonPath("$.wantToReadBooks.length()").value(1))
                .andExpect(jsonPath("$.wantToReadBooks[0].title").value("읽고싶은 책"))
                .andExpect(jsonPath("$.readingBooks.length()").value(1));
    }

    // ── 8f. BookOption 표지·저자 (미니앱 홈 표지 캐러셀용) ────────────────────

    @Test
    @DisplayName("GET /api/dashboard: readingBooks에 coverUrl·author를 함께 싣는다 — 미니앱 홈 표지 캐러셀의 입력")
    void get_bookOptionCarriesCoverAndAuthor() throws Exception {
        User u = register("cover@a.com", "covertest");
        bookRepository.save(Book.register(u, "데미안", "헤르만 헤세", null,
                "https://img.example/demian.jpg", null, null, BookStatus.READING));

        mockMvc.perform(get("/api/dashboard").with(user("cover@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readingBooks[0].coverUrl").value("https://img.example/demian.jpg"))
                .andExpect(jsonPath("$.readingBooks[0].author").value("헤르만 헤세"));
    }

    @Test
    @DisplayName("GET /api/dashboard: 표지·저자가 없는 책은 null로 실린다 — 미니앱이 자리 표지로 떨어뜨린다")
    void get_bookOptionNullCoverAndAuthor() throws Exception {
        User u = register("nocover@a.com", "nocover");
        addBook(u, "손으로 넣은 책", BookStatus.READING);

        mockMvc.perform(get("/api/dashboard").with(user("nocover@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readingBooks[0].title").value("손으로 넣은 책"))
                .andExpect(jsonPath("$.readingBooks[0].coverUrl").doesNotExist())
                .andExpect(jsonPath("$.readingBooks[0].author").doesNotExist());
    }

    // ── 9. DTO 화이트리스트 ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 엔티티 직접 직렬화 금지 — user·clickCount·coupangClickCount·yes24ClickCount 없음")
    void dtoWhitelist_noEntityLeak() throws Exception {
        register("wl@a.com", "wltest");

        mockMvc.perform(get("/api/dashboard").with(user("wl@a.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"user\""))))
                .andExpect(content().string(not(containsString("clickCount"))))
                .andExpect(content().string(not(containsString("coupangClickCount"))))
                .andExpect(content().string(not(containsString("yes24ClickCount"))));
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
    @DisplayName("GET /api/dashboard: garden CatalogDto — 작가 카운트 필드 존재, 건물 필드는 은퇴로 부재")
    void gardenDtoSpotCheck() throws Exception {
        register("garden@a.com", "garden");

        mockMvc.perform(get("/api/dashboard").with(user("garden@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.garden.ownedAuthorCharacterCount").isNumber())
                .andExpect(jsonPath("$.garden.totalAuthorCharacterCount").isNumber())
                .andExpect(jsonPath("$.garden.ownedBuildingCount").doesNotExist())
                .andExpect(jsonPath("$.garden.totalBuildingCount").doesNotExist());
    }

    // ── 16. todayGoalSeconds 필드 존재 ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: todayGoalSeconds 포함 (진행바 분모 단일출처)")
    void get_todayGoalSeconds_present() throws Exception {
        register("goal@a.com", "goaltest");

        mockMvc.perform(get("/api/dashboard").with(user("goal@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayGoalSeconds").isNumber())
                .andExpect(jsonPath("$.todayGoalSeconds", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /api/sessions/start: 응답 TimerState에 todayGoalSeconds 포함")
    void start_todayGoalSeconds_present() throws Exception {
        User u = register("startgoal@a.com", "startgoal");
        Book book = addBook(u, "책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("startgoal@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayGoalSeconds").isNumber());
    }

    @Test
    @DisplayName("todayGoalSeconds == ReadingDebtService.todayGoalSeconds (부채 분모 단일출처, getDailyIncrementSeconds 직접호출 차단)")
    void todayGoalSeconds_matchesDebtServiceValue() throws Exception {
        User u = register("consistent@a.com", "consistent");
        long expected = readingDebtService.todayGoalSeconds(u);

        mockMvc.perform(get("/api/dashboard").with(user("consistent@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayGoalSeconds").value(expected));
    }

    // ── 17. carryover 플래그 노출 (computeProgress 입력 — 진행바 floor 차감 분기) ──────

    @Test
    @DisplayName("GET /api/dashboard: carryover 불리언 포함 (기본 ON)")
    void get_carryover_present() throws Exception {
        register("carryflag@a.com", "carryflag");

        mockMvc.perform(get("/api/dashboard").with(user("carryflag@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carryover").isBoolean())
                .andExpect(jsonPath("$.carryover").value(true));
    }

    @Test
    @DisplayName("carryover OFF 설정 → 응답 carryover=false (설정 반영)")
    void get_carryover_reflectsOffSetting() throws Exception {
        User u = register("carryoffflag@a.com", "carryoffflag");
        timerRepository.findByUser(u).ifPresent(t -> {
            t.updateSettings(t.getDailyIncrementSeconds(), false);
            timerRepository.save(t);
        });

        mockMvc.perform(get("/api/dashboard").with(user("carryoffflag@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carryover").value(false));
    }

    @Test
    @DisplayName("POST /api/sessions/start: 응답 TimerState에 carryover 포함")
    void start_carryover_present() throws Exception {
        User u = register("startcarry@a.com", "startcarry");
        Book book = addBook(u, "책", BookStatus.READING);

        mockMvc.perform(post("/api/sessions/start")
                        .with(user("startcarry@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carryover").isBoolean());
    }
}
