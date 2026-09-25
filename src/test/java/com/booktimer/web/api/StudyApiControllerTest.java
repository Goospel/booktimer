package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
import com.booktimer.session.StudySessionService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/study/start|stop 통합 테스트 (H2).
 *
 * <p>계약은 독서({@code /api/sessions/*})와 같은 모양이다 — 200 + 상태 / 409 중복·무세션 / 미인증 차단.
 * 여기서만 보는 것은 <b>격리</b>다: 공부 세션을 심어도 잔디·기록이 0으로 남는지를 회귀 테스트로 고정한다.
 * "절대 안 섞인다"는 별도 테이블이라는 <b>구조</b>가 보장하지만, 그 구조가 깨졌을 때 울릴 계측기가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyApiControllerTest {

    // 이 클래스는 「지금」 기준으로 세션을 심고 API가 실제 now로 끝내므로, 운영 Clock.systemUTC()를
    // 그대로 두면 자정 경계에서만 붉어진다 — T-039의 2회차다(2026-09-06 00:29 KST CI에서
    // changeActiveBook_movesAllSecondsToNewBook이 1799 < 1800으로 실패). now-30분이 전날로 넘어가면
    // stop이 세션을 자정에서 두 행으로 쪼개고(endSplitAndSave) 행마다 durationSeconds를 따로
    // 내림해 1초가 샌다. 18:00 KST로 고정해 그 창을 없앤다 — 한낮이라 ±6시간 세션이 같은 날에 머문다.
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock fixedClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
        }
    }

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudySessionRepository studyRepository;
    @Autowired StudySessionService studySessionService;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired BookRepository bookRepository;
    @Autowired ReadingSessionService readingSessionService;
    @Autowired Clock clock;

    private User register(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "닉네임_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /**
     * <b>오늘 정오(KST)</b> — 「오늘 안이지만 경계에서 가장 먼 시각」이다.
     *
     * <p>당일 누적을 재는 테스트가 {@code now - 1시간} 같은 <b>상대 좌표</b>로 세션을 심으면
     * KST 00:00~01:00에 그 시각이 어제로 넘어가 CI가 날짜에 따라 붉어진다. 정오는 어느 쪽 경계와도
     * 12시간 떨어져 있어 그 창이 사라진다(경계 자체를 재는 것은 {@code todaySeconds_excludesYesterday}의 몫).
     *
     * <p>⚠️ <b>진행 중 세션엔 이 헬퍼를 못 쓴다</b> — API가 실제 {@code now}로 끝내므로 정오가 미래면
     * {@code endedAt < startedAt}으로 터진다. 그쪽 좌표({@code now - 30분})는 위 고정 클락이 지킨다.
     */
    private Instant todayNoon() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL))
                .atTime(12, 0)
                .atZone(ZoneId.of(SEOUL))
                .toInstant();
    }

    /** 완료된 공부 세션 한 건을 그 시각에 심는다(집계·격리 검증용). */
    private StudySession completedStudy(User user, Instant startedAt, Duration length) {
        return completedStudy(user, startedAt, length, null);
    }

    /** 완료된 공부 세션 한 건 — 책을 걸 수도 있다(책별 집계 검증용). */
    private StudySession completedStudy(User user, Instant startedAt, Duration length, StudyBook book) {
        StudySession session = StudySession.start(user, startedAt, book);
        session.end(startedAt.plus(length));
        return studyRepository.save(session);
    }

    /** 내 공부 서재에 책 한 권(검색 왕복 없이 직접 — 이 테스트가 재는 것은 세션-책 연결이다). */
    private StudyBook studyBook(User user, String title) {
        return studyBookRepository.save(StudyBook.register(user, title, "저자", null, null, null, null));
    }

    /** 독서 책장의 책 한 권 — <b>다른 테이블</b>이라 이 id는 공부 문에서 404여야 한다. */
    private Book readingBook(User user, String title) {
        return bookRepository.save(
                Book.register(user, title, null, null, null, null, null, BookStatus.READING));
    }

    /** {@code $.books[?(@.id == N)].totalSeconds} — 배열 순서에 기대지 않고 그 책의 초만 집어낸다. */
    private static String bookSeconds(StudyBook book) {
        return "$.books[?(@.id == " + book.getId() + ")].totalSeconds";
    }

    // ── 인증 경계 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/start: 미인증 → 로그인으로 차단(인증 없이 원장에 못 쓴다)")
    void start_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/start").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── start ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/start: 200 + hasActiveSession=true")
    void start_returnsActiveState() throws Exception {
        register("study-start@a.com", "studystart");

        mockMvc.perform(post("/api/study/start").with(user("studystart")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeStartedAt").isNotEmpty())
                .andExpect(jsonPath("$.todaySeconds").value(0));
    }

    @Test
    @DisplayName("POST /api/study/start: 이미 진행 중이면 409")
    void start_duplicate_conflicts() throws Exception {
        register("study-dup@a.com", "studydup");

        mockMvc.perform(post("/api/study/start").with(user("studydup")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/start").with(user("studydup")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/study/start: 독서 측정 중이면 409 — 두 원장이 같은 시간을 이중으로 세지 않는다")
    void start_whileReading_conflicts() throws Exception {
        User u = register("study-reading@a.com", "studyreading");
        readingSessionService.start(u, clock.instant(), null);

        mockMvc.perform(post("/api/study/start").with(user("studyreading")).with(csrf()))
                .andExpect(status().isConflict());
    }

    // ── stop ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/stop: 진행 중 세션이 없으면 409")
    void stop_withoutSession_conflicts() throws Exception {
        register("study-nostop@a.com", "studynostop");

        mockMvc.perform(post("/api/study/stop").with(user("studynostop")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/study/stop: 종료하면 오늘 누적에 합산된다")
    void stop_accumulatesToday() throws Exception {
        User u = register("study-sum@a.com", "studysum");
        completedStudy(u, todayNoon(), Duration.ofMinutes(25));

        mockMvc.perform(post("/api/study/start").with(user("studysum")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/stop").with(user("studysum")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(false))
                .andExpect(jsonPath("$.activeStartedAt").doesNotExist())
                // 방금 끝낸 0초짜리 + 앞서 심은 25분
                .andExpect(jsonPath("$.todaySeconds").value(greaterThanOrEqualTo(1500)));
    }

    @Test
    @DisplayName("todaySeconds: 어제 시작한 세션은 빠진다(유저 타임존 하루 경계)")
    void todaySeconds_excludesYesterday() throws Exception {
        User u = register("study-tz@a.com", "studytz");
        completedStudy(u, clock.instant().minus(Duration.ofDays(2)), Duration.ofHours(1));

        mockMvc.perform(post("/api/study/start").with(user("studytz")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySeconds").value(0));
    }

    // ── 격리 (핵심) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("격리: 공부 기록은 잔디에 0건이다 — 독서 집계가 study_session을 아예 모른다")
    void studyDoesNotLeakIntoContributionGraph() throws Exception {
        User u = register("study-iso-graph@a.com", "studyisograph");
        completedStudy(u, clock.instant().minus(Duration.ofMinutes(90)), Duration.ofHours(1));

        mockMvc.perform(get("/api/dashboard").with(user("studyisograph")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.totalSeconds").value(0))
                .andExpect(jsonPath("$.graph.activeDays").value(0));
    }

    @Test
    @DisplayName("격리: 공부 기록은 /api/history 목록에도 0건이다")
    void studyDoesNotLeakIntoHistory() throws Exception {
        register("study-iso-hist@a.com", "studyisohist");
        completedStudy(userRepository.findByLoginId("studyisohist").orElseThrow(),
                clock.instant().minus(Duration.ofMinutes(90)), Duration.ofHours(1));

        mockMvc.perform(get("/api/history").with(user("studyisohist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty());
    }

    // ── 대시보드 동봉 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 진행 중 공부 세션이 그대로 실린다 — 재진입해도 측정이 이어진다")
    void dashboard_carriesActiveStudySession() throws Exception {
        register("study-resume@a.com", "studyresume");

        mockMvc.perform(post("/api/study/start").with(user("studyresume")).with(csrf()))
                .andExpect(status().isOk());

        // 다시 들어온 앱이 보는 것과 같은 응답 — 여기에 진행 중 사실이 없으면 재진입 시 측정이 사라진다.
        mockMvc.perform(get("/api/dashboard").with(user("studyresume")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.hasActiveSession").value(true))
                .andExpect(jsonPath("$.study.activeStartedAt").isNotEmpty())
                // 독서는 그대로 쉬고 있다 — 두 원장이 서로의 상태를 물들이지 않는다.
                .andExpect(jsonPath("$.hasActiveSession").value(false));
    }

    @Test
    @DisplayName("GET /api/dashboard: study 블록을 동봉한다(미니앱이 초기 모드·누적을 여기서 받는다)")
    void dashboard_includesStudyBlock() throws Exception {
        User u = register("study-dash@a.com", "studydash");
        completedStudy(u, todayNoon(), Duration.ofMinutes(10));

        mockMvc.perform(get("/api/dashboard").with(user("studydash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.hasActiveSession").value(false))
                .andExpect(jsonPath("$.study.todaySeconds").value(600));
    }

    // ── 타이머-책 연결: start(bookId?) ────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/start: bookId를 주면 그 책으로 시작한다(응답에 activeBook)")
    void start_withBookId_setsActiveBook() throws Exception {
        User u = register("study-startbook@a.com", "studystartbook");
        StudyBook book = studyBook(u, "정보처리기사 실기");

        mockMvc.perform(post("/api/study/start").with(user("studystartbook")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeBook.id").value(book.getId()))
                .andExpect(jsonPath("$.activeBook.title").value("정보처리기사 실기"));
    }

    /**
     * <b>하위호환(U3)</b> — 12차 라이브 번들은 {@code {}}를 보내고, 더 옛 클라이언트는 body가 아예 없다.
     * {@code @RequestBody(required = false)}가 빠지면 이 둘이 400이 되어 <b>배포 창 동안 공부 시작이
     * 통째로 죽는다</b>. 옛 필드가 그대로 실리는지도 여기서 함께 못 박는다.
     */
    @Test
    @DisplayName("POST /api/study/start: 빈 객체·body 없음 모두 200 + activeBook은 null(옛 번들 하위호환)")
    void start_withoutBookId_isBackwardCompatible() throws Exception {
        register("study-startempty@a.com", "studystartempty");

        mockMvc.perform(post("/api/study/start").with(user("studystartempty")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.todaySeconds").exists())
                .andExpect(jsonPath("$.activeBook").doesNotExist());

        mockMvc.perform(post("/api/study/stop").with(user("studystartempty")).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/study/start").with(user("studystartempty")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeBook").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/study/start: 남의 공부 책 id면 404 + 세션은 아예 안 만들어진다")
    void start_withForeignBook_isNotFoundAndStartsNothing() throws Exception {
        register("study-startidor@a.com", "studystartidor");
        User stranger = register("study-startidor2@a.com", "studystartidortwo");
        StudyBook theirs = studyBook(stranger, "남의 책");

        mockMvc.perform(post("/api/study/start").with(user("studystartidor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + theirs.getId() + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/dashboard").with(user("studystartidor")))
                .andExpect(jsonPath("$.study.hasActiveSession").value(false));
    }

    /** 테이블 경계 — 독서 책장의 id는 공부 문에서 존재하지 않는 책이다(두 서재가 안 섞인다). */
    @Test
    @DisplayName("POST /api/study/start: 독서 책장의 id는 404 — 서재가 다른 테이블이다")
    void start_withReadingBookId_isNotFound() throws Exception {
        User u = register("study-startcross@a.com", "studystartcross");
        Book reading = readingBook(u, "독서 책장의 책");

        mockMvc.perform(post("/api/study/start").with(user("studystartcross")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + reading.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/study/start: 이미 진행 중이면 bookId가 있어도 409")
    void start_withBookId_whileActive_conflicts() throws Exception {
        User u = register("study-startdup2@a.com", "studystartduptwo");
        StudyBook book = studyBook(u, "책");

        mockMvc.perform(post("/api/study/start").with(user("studystartduptwo")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/start").with(user("studystartduptwo")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isConflict());
    }

    /**
     * 두 조건이 동시에 틀렸을 때 <b>어느 쪽을 말해 주는가</b>. 책 조회를 활성 검사보다 먼저 하면
     * 「측정이 이미 돈다」(409)가 아니라 「책이 없다」(404)가 나가, 클라이언트가 엉뚱한 안내를 한다
     * (캐시에 남은 낡은 bookId로 다른 탭에서 다시 시작을 누르는 경로가 실제로 그 조합이다).
     */
    @Test
    @DisplayName("POST /api/study/start: 진행 중 + 낡은 bookId면 404가 아니라 409다(활성 검사가 먼저)")
    void start_whileActive_withStaleBookId_conflictsNotNotFound() throws Exception {
        register("study-startstale@a.com", "studystartstale");

        mockMvc.perform(post("/api/study/start").with(user("studystartstale")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/start").with(user("studystartstale")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":999999}"))
                .andExpect(status().isConflict());
    }

    // ── stop 응답의 태깅 좌표 ─────────────────────────────────────────────────

    /** {@code untaggedSessionId}가 없으면 종료 후 태깅 시트가 어느 세션을 붙일지 모른다(시트가 안 열린다). */
    @Test
    @DisplayName("POST /api/study/stop: 책 없이 잰 세션이면 untaggedSessionId에 그 세션 id가 실린다")
    void stop_withoutBook_returnsUntaggedSessionId() throws Exception {
        User u = register("study-untag@a.com", "studyuntag");
        mockMvc.perform(post("/api/study/start").with(user("studyuntag")).with(csrf()))
                .andExpect(status().isOk());
        Long sessionId = studyRepository.findByUserAndEndedAtIsNull(u).orElseThrow().getId();

        mockMvc.perform(post("/api/study/stop").with(user("studyuntag")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.untaggedSessionId").value(sessionId));
    }

    @Test
    @DisplayName("POST /api/study/stop: 책을 걸고 잰 세션이면 untaggedSessionId는 null(붙일 것이 없다)")
    void stop_withBook_hasNoUntaggedSessionId() throws Exception {
        User u = register("study-tagged@a.com", "studytagged");
        StudyBook book = studyBook(u, "책");

        mockMvc.perform(post("/api/study/start").with(user("studytagged")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/stop").with(user("studytagged")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.untaggedSessionId").doesNotExist());
    }

    /** 태깅 좌표는 <b>stop 응답에서만</b> 산다 — 대시보드·start가 최근 미태깅 세션을 실어 오면 시트가 유령처럼 뜬다. */
    @Test
    @DisplayName("start·대시보드 응답의 untaggedSessionId는 언제나 null — 시트는 종료 동작 직후에만 열린다")
    void startAndDashboard_neverCarryUntaggedSessionId() throws Exception {
        User u = register("study-untagscope@a.com", "studyuntagscope");
        completedStudy(u, todayNoon(), Duration.ofMinutes(20));

        mockMvc.perform(post("/api/study/start").with(user("studyuntagscope")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.untaggedSessionId").doesNotExist());
        mockMvc.perform(get("/api/dashboard").with(user("studyuntagscope")))
                .andExpect(jsonPath("$.study.untaggedSessionId").doesNotExist());
    }

    // ── 종료 후 태깅 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/sessions/{id}/tag-book: 붙인 시간이 그 책의 totalSeconds가 되고 recentBookId가 된다")
    void tagBook_movesSecondsToThatBook() throws Exception {
        User u = register("study-tag@a.com", "studytag");
        StudyBook book = studyBook(u, "정보처리기사 실기");
        StudyBook idle = studyBook(u, "안 쓴 책");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(30));

        mockMvc.perform(post("/api/study/sessions/" + session.getId() + "/tag-book")
                        .with(user("studytag")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSeconds(book), hasItem(1800)))
                // 다른 책은 0초 — 「부재」라 미니앱이 칩을 안 그린다.
                .andExpect(jsonPath(bookSeconds(idle), hasItem(0)))
                .andExpect(jsonPath("$.recentBookId").value(book.getId().intValue()))
                // 라벨을 붙였을 뿐이라 당일 합은 그대로다(시간의 원장은 세션이다).
                .andExpect(jsonPath("$.todaySeconds").value(1800));
    }

    @Test
    @DisplayName("tag-book: 남의 세션 id는 404 「측정을 찾을 수 없습니다」 + 그 세션은 그대로다")
    void tagBook_foreignSession_isNotFound() throws Exception {
        User u = register("study-tagidor@a.com", "studytagidor");
        User stranger = register("study-tagidor2@a.com", "studytagidortwo");
        StudyBook mine = studyBook(u, "내 책");
        StudySession theirs = completedStudy(stranger, todayNoon(), Duration.ofMinutes(10));

        mockMvc.perform(post("/api/study/sessions/" + theirs.getId() + "/tag-book")
                        .with(user("studytagidor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + mine.getId() + "}"))
                .andExpect(status().isNotFound());

        assertThat(studyRepository.findById(theirs.getId()).orElseThrow().getBook()).isNull();
    }

    @Test
    @DisplayName("tag-book: 남의 책·독서 책장의 id는 404 「책을 찾을 수 없습니다」")
    void tagBook_foreignOrReadingBook_isNotFound() throws Exception {
        User u = register("study-tagbookidor@a.com", "studytagbookidor");
        User stranger = register("study-tagbookidor2@a.com", "studytagbookidortwo");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(10));
        StudyBook theirs = studyBook(stranger, "남의 공부 책");
        Book reading = readingBook(u, "내 독서 책");

        mockMvc.perform(post("/api/study/sessions/" + session.getId() + "/tag-book")
                        .with(user("studytagbookidor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + theirs.getId() + "}"))
                // ⚠️ 문구 자체는 여기서 못 잰다 — MockMvc가 ResponseStatusException을 HTML 에러 페이지로
                // 렌더해 reason·본문 어디에도 안 실린다(레포 전체에 그 단언이 없는 이유). 코드만 잠근다.
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/study/sessions/" + session.getId() + "/tag-book")
                        .with(user("studytagbookidor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + reading.getId() + "}"))
                .andExpect(status().isNotFound());

        assertThat(studyRepository.findById(session.getId()).orElseThrow().getBook()).isNull();
    }

    /** 진행 중 세션에 책을 붙이는 문은 {@code active/book}이다 — tag-book으로 오면 막힌다. */
    @Test
    @DisplayName("tag-book: 진행 중 세션이면 409 — 재는 도중은 교체 문의 몫이다")
    void tagBook_activeSession_conflicts() throws Exception {
        User u = register("study-tagactive@a.com", "studytagactive");
        StudyBook book = studyBook(u, "책");
        mockMvc.perform(post("/api/study/start").with(user("studytagactive")).with(csrf()))
                .andExpect(status().isOk());
        Long sessionId = studyRepository.findByUserAndEndedAtIsNull(u).orElseThrow().getId();

        mockMvc.perform(post("/api/study/sessions/" + sessionId + "/tag-book")
                        .with(user("studytagactive")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isConflict());

        assertThat(studyRepository.findById(sessionId).orElseThrow().getBook()).isNull();
    }

    @Test
    @DisplayName("tag-book: 이미 책이 지정된 세션의 재태깅은 409(1회성)")
    void tagBook_alreadyTagged_conflicts() throws Exception {
        User u = register("study-tagtwice@a.com", "studytagtwice");
        StudyBook first = studyBook(u, "먼저 붙인 책");
        StudyBook second = studyBook(u, "나중 책");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(10), first);

        mockMvc.perform(post("/api/study/sessions/" + session.getId() + "/tag-book")
                        .with(user("studytagtwice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + second.getId() + "}"))
                .andExpect(status().isConflict());

        assertThat(studyRepository.findById(session.getId()).orElseThrow().getBook().getId())
                .isEqualTo(first.getId());
    }

    // ── 측정 중 교체 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/active/book: 진행 중 측정이 없으면 409")
    void changeActiveBook_withoutSession_conflicts() throws Exception {
        User u = register("study-chgnone@a.com", "studychgnone");
        StudyBook book = studyBook(u, "책");

        mockMvc.perform(post("/api/study/active/book").with(user("studychgnone")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isConflict());   // 문구는 MockMvc에서 관측 불가(위 tag-book 주석)
    }

    /**
     * ⚡ 교체가 <b>새 세션을 만드는</b> 구현이면 잰 시간이 A·B로 갈라진다 — 그 구현을 여기서 잡는다.
     * 세션은 시간의 원장이고 book은 그 라벨이라, 라벨만 갈면 지금까지 잰 시간이 통째로 새 책에 붙는다.
     */
    @Test
    @DisplayName("active/book: A로 재던 시간이 통째로 B에 붙는다(세션은 안 멈춘다)")
    void changeActiveBook_movesAllSecondsToNewBook() throws Exception {
        User u = register("study-chg@a.com", "studychg");
        StudyBook a = studyBook(u, "책 A");
        StudyBook b = studyBook(u, "책 B");
        studyRepository.save(StudySession.start(u, clock.instant().minus(Duration.ofMinutes(30)), a));

        mockMvc.perform(post("/api/study/active/book").with(user("studychg")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + b.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeBook.id").value(b.getId()));

        mockMvc.perform(post("/api/study/stop").with(user("studychg")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSeconds(b), hasItem(greaterThanOrEqualTo(1800))))
                .andExpect(jsonPath(bookSeconds(a), hasItem(0)));
    }

    @Test
    @DisplayName("active/book: bookId가 null이면 「책 없이」로 되돌아간다")
    void changeActiveBook_null_clearsBook() throws Exception {
        User u = register("study-chgnull@a.com", "studychgnull");
        StudyBook book = studyBook(u, "책");
        mockMvc.perform(post("/api/study/start").with(user("studychgnull")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/study/active/book").with(user("studychgnull")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeBook").doesNotExist());
    }

    @Test
    @DisplayName("active/book: 남의 책 id면 404 — 측정은 그대로 돈다")
    void changeActiveBook_foreignBook_isNotFound() throws Exception {
        User u = register("study-chgidor@a.com", "studychgidor");
        User stranger = register("study-chgidor2@a.com", "studychgidortwo");
        StudyBook theirs = studyBook(stranger, "남의 책");
        mockMvc.perform(post("/api/study/start").with(user("studychgidor")).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/study/active/book").with(user("studychgidor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + theirs.getId() + "}"))
                .andExpect(status().isNotFound());

        assertThat(studyRepository.findByUserAndEndedAtIsNull(u)).isPresent();
    }

    // ── 대시보드 동봉 (캐러셀 재료) ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: study 블록이 서재 목록·최근 책·측정 중인 책을 함께 싣는다")
    void dashboard_carriesStudyBooksAndRecentBook() throws Exception {
        User u = register("study-dashbooks@a.com", "studydashbooks");
        StudyBook book = studyBook(u, "정보처리기사 실기");
        completedStudy(u, todayNoon(), Duration.ofMinutes(30), book);
        studyRepository.save(StudySession.start(u, clock.instant(), book));

        mockMvc.perform(get("/api/dashboard").with(user("studydashbooks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.books", hasSize(1)))
                .andExpect(jsonPath("$.study.books[0].title").value("정보처리기사 실기"))
                .andExpect(jsonPath("$.study.books[0].totalSeconds").value(1800))
                .andExpect(jsonPath("$.study.recentBookId").value(book.getId().intValue()))
                .andExpect(jsonPath("$.study.activeBook.title").value("정보처리기사 실기"));
    }

    // ── 공부 책 회당 시간 (하루 목표 → 책별 회당 시간 전환 PR-1) ──────────────────

    /** {@code $.books[?(@.id == N)].sessionGoalSeconds} — 배열 순서에 기대지 않고 그 책의 회당 시간만. */
    private static String bookSessionGoal(StudyBook book) {
        return "$.books[?(@.id == " + book.getId() + ")].sessionGoalSeconds";
    }

    private org.springframework.test.web.servlet.ResultActions postSessionGoal(String loginId, StudyBook book,
                                                                               String value) throws Exception {
        return mockMvc.perform(post("/api/study/books/" + book.getId() + "/session-goal")
                .with(user(loginId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionGoalSeconds\":" + value + "}"));
    }

    @Test
    @DisplayName("session-goal: 미인증 → 로그인으로 차단")
    void sessionGoal_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/books/1/session-goal").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionGoalSeconds\":3000}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("session-goal: 저장한 값이 응답 books·측정 중 activeBook·/api/dashboard에 모두 실린다")
    void sessionGoal_savesAndAppearsInBooksAndActiveBook() throws Exception {
        User u = register("study-sg-save@a.com", "studysgsave");
        StudyBook a = studyBook(u, "정보처리기사 필기");
        StudyBook b = studyBook(u, "안 정한 책");

        postSessionGoal("studysgsave", a, "3000")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSessionGoal(a), hasItem(3000)))
                .andExpect(jsonPath(bookSessionGoal(b), hasItem(nullValue())));

        mockMvc.perform(post("/api/study/start").with(user("studysgsave")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + a.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBook.sessionGoalSeconds").value(3000));

        mockMvc.perform(get("/api/dashboard").with(user("studysgsave")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.activeBook.sessionGoalSeconds").value(3000))
                .andExpect(jsonPath("$.study.books[?(@.id == " + a.getId() + ")].sessionGoalSeconds", hasItem(3000)));
    }

    @Test
    @DisplayName("session-goal: null은 「안 정함」으로 되돌린다(해제는 null만)")
    void sessionGoal_null_clears() throws Exception {
        User u = register("study-sg-null@a.com", "studysgnull");
        StudyBook a = studyBook(u, "수학");
        postSessionGoal("studysgnull", a, "3000").andExpect(status().isOk());

        postSessionGoal("studysgnull", a, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSessionGoal(a), hasItem(nullValue())));
    }

    /**
     * 범위 밖은 400이고 <b>저장된 값이 그대로</b>여야 한다 — 먼저 3000을 심어 두는 이유는, 잘못된 값이
     * 400을 내면서 조용히 null로 지워 버리는 회귀까지 같이 잡기 위해서다. 경계 안쪽(600·21600)은 200 —
     * 부등호가 한 칸 밀리는 회귀(600을 거부)를 잡는 양성 쌍이다. 60은 옛 하한(1분)으로 되돌아가는 회귀를 잡는다
     * — 2026-09-15 최소 10분으로 올렸다(1분짜리는 푸시가 닿자마자 정지로 원리상 안 가서 시험만 헷갈리게 했다).
     */
    @Test
    @DisplayName("session-goal: 599·60·21601·0 → 400 + 값 불변, 경계 600·21600은 저장된다(최소 10분)")
    void sessionGoal_outOfRange_isBadRequestAndKeepsValue() throws Exception {
        User u = register("study-sg-range@a.com", "studysgrange");
        StudyBook a = studyBook(u, "영어");
        postSessionGoal("studysgrange", a, "3000").andExpect(status().isOk());

        for (String bad : new String[] {"599", "60", "21601", "0"}) {
            postSessionGoal("studysgrange", a, bad).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/study/books").with(user("studysgrange")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSessionGoal(a), hasItem(3000)));

        postSessionGoal("studysgrange", a, "600")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSessionGoal(a), hasItem(600)));
        postSessionGoal("studysgrange", a, "21600")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSessionGoal(a), hasItem(21600)));
    }

    /**
     * 남의 책은 404(존재 비노출)이되, <b>범위 검사가 소유권 조회보다 먼저</b>다 — 남의 책 id에 잘못된 값을
     * 보내도 400이라 400/404로 존재 여부를 캐낼 창이 열리지 않는다(read-count 문과 같은 규약).
     */
    @Test
    @DisplayName("session-goal: 남의 책 3000 → 404(값 불변), 남의 책 599 → 400(검사 순서)")
    void sessionGoal_foreignBook_isNotFound() throws Exception {
        register("study-sg-idor@a.com", "studysgidor");
        User stranger = register("study-sg-idor2@a.com", "studysgidortwo");
        StudyBook theirs = studyBook(stranger, "남의 책");

        postSessionGoal("studysgidor", theirs, "3000").andExpect(status().isNotFound());
        postSessionGoal("studysgidor", theirs, "599").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/study/books").with(user("studysgidortwo")))
                .andExpect(jsonPath(bookSessionGoal(theirs), hasItem(nullValue())));
    }

    @Test
    @DisplayName("session-goal: 독서 책장의 책 id → 404(다른 서재)")
    void sessionGoal_readingBookId_isNotFound() throws Exception {
        User u = register("study-sg-reading@a.com", "studysgreading");
        Book reading = readingBook(u, "독서 책");

        mockMvc.perform(post("/api/study/books/" + reading.getId() + "/session-goal")
                        .with(user("studysgreading")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionGoalSeconds\":3000}"))
                .andExpect(status().isNotFound());
    }

    // ── 공부 하루 목표 삭제 계약 (PR-5) ─────────────────────────────────────────
    // 하루 목표는 책별 「회당 시간」으로 대체됐다(V90). 웹·미니앱이 더는 읽지도 쓰지도 않으므로
    // 서버 문과 응답 필드가 되살아나지 않게 못 박는다.

    @Test
    @DisplayName("POST /api/study/goal: 삭제된 문이다 — 인증·유효 body여도 404")
    void goal_endpointIsGone() throws Exception {
        register("study-goalgone@a.com", "studygoalgone");

        mockMvc.perform(post("/api/study/goal").with(user("studygoalgone")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":3600}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공부 상태(대시보드 study 블록·start)에 goalSeconds 필드가 없다")
    void studyState_hasNoGoalSeconds() throws Exception {
        register("study-nogoal@a.com", "studynogoal");

        mockMvc.perform(get("/api/dashboard").with(user("studynogoal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.todaySeconds").exists())
                .andExpect(jsonPath("$.study.goalSeconds").doesNotExist());
        mockMvc.perform(post("/api/study/start").with(user("studynogoal")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSeconds").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/study/calendar: 응답에 goalSeconds 필드가 없다")
    void calendar_hasNoGoalSeconds() throws Exception {
        register("study-calnogoal@a.com", "studycalnogoal");

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("studycalnogoal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").exists())
                .andExpect(jsonPath("$.goalSeconds").doesNotExist());
    }

    // ── 공부 일정 달력 (2차 PR-B) ─────────────────────────────────────────────

    /** 유저 타임존의 오늘 — 체크 대상 날짜와 조회할 달을 같은 시계에서 뽑는다. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private String thisMonth() {
        return today().toString().substring(0, 7);
    }

    @Test
    @DisplayName("GET /api/study/calendar: 미인증 → 로그인으로 차단")
    void calendar_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/api/study/calendar").param("month", "2026-08"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/study/calendar: 일별 측정·판정을 준다")
    void calendar_returnsDays() throws Exception {
        User u = register("study-cal@a.com", "studycal");
        completedStudy(u, todayNoon(), Duration.ofMinutes(40));

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("studycal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value(today().toString()))
                .andExpect(jsonPath("$.days[0].studiedSeconds").value(2400))
                // 측정만 있고 판정은 없는 날 — 「측정 있음 점」은 뜨되 체크는 무기록이다.
                .andExpect(jsonPath("$.days[0].kept").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/study/calendar: 달 형식이 틀리면 400 — 사용자에게 보이는 평문이다")
    void calendar_malformedMonth_isBadRequest() throws Exception {
        register("study-calbad@a.com", "studycalbad");

        mockMvc.perform(get("/api/study/calendar").param("month", "2026-13-01").with(user("studycalbad")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("달 형식이 올바르지 않아요"));
    }

    @Test
    @DisplayName("POST /api/study/check: 미인증 → 로그인으로 차단")
    void check_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/check").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-30\",\"kept\":true}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/study/check: 남긴 판정이 그 달 달력에 그대로 실린다(왕복)")
    void check_isReflectedInCalendar() throws Exception {
        register("study-check@a.com", "studycheck");
        String date = today().toString();

        mockMvc.perform(post("/api/study/check").with(user("studycheck")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"kept\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date))
                .andExpect(jsonPath("$.kept").value(true));

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("studycheck")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value(date))
                .andExpect(jsonPath("$.days[0].kept").value(true))
                .andExpect(jsonPath("$.days[0].studiedSeconds").value(0));
    }

    @Test
    @DisplayName("POST /api/study/check: kept=null이면 무기록으로 되돌아가 달력에서 빠진다(3상태 순환의 끝)")
    void check_nullClearsTheDay() throws Exception {
        register("study-checkclear@a.com", "studycheckclear");
        String date = today().toString();

        mockMvc.perform(post("/api/study/check").with(user("studycheckclear")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"kept\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/check").with(user("studycheckclear")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"kept\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kept").doesNotExist());

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("studycheckclear")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
    }

    /**
     * 미래 거부는 <b>클라이언트와 이중 방어</b>다(화면은 흐림 + no-op). 이 400 본문은
     * {@code @ExceptionHandler(IllegalArgumentException.class)}가 그대로 내보내므로 곧 사용자 문구다.
     */
    @Test
    @DisplayName("POST /api/study/check: 미래 날짜는 400 + 한국어 평문")
    void check_futureDate_isBadRequest() throws Exception {
        register("study-checkfuture@a.com", "studycheckfuture");

        mockMvc.perform(post("/api/study/check").with(user("studycheckfuture")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today().plusDays(1) + "\",\"kept\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("미래 날짜는 체크할 수 없어요"));
    }

    @Test
    @DisplayName("POST /api/study/check: 날짜 형식이 틀리면 400 — 사용자에게 보이는 평문이다")
    void check_malformedDate_isBadRequest() throws Exception {
        register("study-checkbad@a.com", "studycheckbad");

        mockMvc.perform(post("/api/study/check").with(user("studycheckbad")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"어제\",\"kept\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("날짜 형식이 올바르지 않아요"));
    }

    // ── 공부 기록 (3차) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/study/history: 미인증 → 로그인으로 차단")
    void history_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/api/study/history"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /**
     * 응답의 {@code graph}는 {@code ContributionGraph} record를 그대로 직렬화한 것이다 — 다섯 키가
     * 기존 {@code /api/dashboard}·{@code /api/history}의 DTO와 <b>글자 그대로 같아야</b> 미니앱이
     * 같은 타입으로 받는다. 하나라도 빠지면 아래 jsonPath가 붉어진다.
     */
    @Test
    @DisplayName("GET /api/study/history: 잔디 다섯 키와 월별 목록을 함께 준다")
    void history_returnsGraphAndMonths() throws Exception {
        User u = register("study-hist@a.com", "studyhist");
        completedStudy(u, todayNoon(), Duration.ofMinutes(25));

        mockMvc.perform(get("/api/study/history").with(user("studyhist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.weeks").isArray())
                .andExpect(jsonPath("$.graph.weeks.length()").value(53))
                .andExpect(jsonPath("$.graph.monthLabels").isArray())
                .andExpect(jsonPath("$.graph.totalSeconds").value(1500))
                .andExpect(jsonPath("$.graph.activeDays").value(1))
                .andExpect(jsonPath("$.graph.currentStreak").value(1))
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.months[0].month").value(thisMonth()))
                .andExpect(jsonPath("$.months[0].totalSeconds").value(1500))
                .andExpect(jsonPath("$.months[0].days[0].date").value(today().toString()))
                .andExpect(jsonPath("$.months[0].days[0].totalSeconds").value(1500));
    }

    /**
     * <b>역방향 격리</b> — 기존 두 격리 테스트는 「공부가 독서 화면에 안 샌다」만 본다. 새 화면이
     * 생겼으니 반대 방향도 계측기가 필요하다: 독서 세션은 공부 기록에 한 건도 안 나타나야 한다.
     */
    @Test
    @DisplayName("격리(역방향): 독서 세션은 공부 기록에 0건이다")
    void readingDoesNotLeakIntoStudyHistory() throws Exception {
        User u = register("study-histiso@a.com", "studyhistiso");
        readingSessionService.start(u, todayNoon(), null);
        readingSessionService.stop(u, todayNoon().plus(Duration.ofHours(1)));

        mockMvc.perform(get("/api/study/history").with(user("studyhistiso")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.totalSeconds").value(0))
                .andExpect(jsonPath("$.graph.activeDays").value(0))
                .andExpect(jsonPath("$.months").isEmpty());
    }

    /**
     * <b>진행 중 세션은 기록에 없다</b> — 히어로가 매초 더하는 몫은 기록의 것이 아니다(독서와 같은 분업).
     *
     * <p>이 불변식은 <b>리포지토리 파생 쿼리 이름</b>({@code ...AndEndedAtIsNotNull})에만 있어서, 그 쿼리를
     * 스텁하는 서비스 단위 테스트로는 원리상 못 잡는다(필터를 지운 돌연변이가 단위층에서 생존한다).
     * 그래서 여기 H2 통합에 잠근다 — 필터가 사라지면 0초짜리 진행 중 세션이 그날 행으로 서서 이 단언이 죽는다.
     */
    @Test
    @DisplayName("GET /api/study/history: 진행 중 세션은 집계에서 빠진다 — 0초짜리 오늘 행이 생기지 않는다")
    void history_excludesActiveSession() throws Exception {
        register("study-histactive@a.com", "studyhistactive");

        mockMvc.perform(post("/api/study/start").with(user("studyhistactive")).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/study/history").with(user("studyhistactive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty())
                .andExpect(jsonPath("$.graph.totalSeconds").value(0))
                .andExpect(jsonPath("$.graph.activeDays").value(0));
    }

    @Test
    @DisplayName("GET /api/study/history: 신규 유저는 빈 목록 + 빈 잔디 53주 — 가입 직후가 여기로 온다")
    void history_newUserIsEmpty() throws Exception {
        register("study-histnew@a.com", "studyhistnew");

        mockMvc.perform(get("/api/study/history").with(user("studyhistnew")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty())
                .andExpect(jsonPath("$.graph.weeks.length()").value(53))
                .andExpect(jsonPath("$.graph.totalSeconds").value(0));
    }

    // ── 자정 분할 (하위 집계 자동 정합) ──────────────────────────────────────

    /**
     * <b>저장 시점 분할만으로 하위 집계가 전부 맞는다</b>는 이 PR의 주장 그 자체를 재는 유일한 자리다.
     * 기록·달력은 한 줄도 안 고쳤으므로, 두 화면이 두 날짜로 갈려 보이면 그건 세션 행이 실제로
     * 두 개로 저장됐다는 뜻이다(단위 테스트의 mock 저장으론 여기까지 못 본다).
     *
     * <p>시각은 <b>고정 과거 일자</b>로 만든다 — {@code now} 기준 상대 좌표로 심으면 자정 근처에
     * 돌린 CI에서만 붉어진다.
     */
    @Test
    @DisplayName("자정 분할: 23:50→익일 00:40 공부는 기록·달력에서 두 날짜(10분·40분)로 갈린다")
    void midnightSplit_isReflectedInHistoryAndCalendar() throws Exception {
        User u = register("study-mid@a.com", "studymid");
        studySessionService.start(u, LocalDateTime.parse("2026-06-01T23:50").atZone(ZoneId.of(SEOUL)).toInstant(), null);
        studySessionService.stop(u, LocalDateTime.parse("2026-06-02T00:40").atZone(ZoneId.of(SEOUL)).toInstant());

        mockMvc.perform(get("/api/study/history").with(user("studymid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.months[0].month").value("2026-06"))
                .andExpect(jsonPath("$.months[0].totalSeconds").value(3000))
                // 최신 일 먼저 — 06-02(40분) 다음 06-01(10분)
                .andExpect(jsonPath("$.months[0].days.length()").value(2))
                .andExpect(jsonPath("$.months[0].days[0].date").value("2026-06-02"))
                .andExpect(jsonPath("$.months[0].days[0].totalSeconds").value(2400))
                .andExpect(jsonPath("$.months[0].days[1].date").value("2026-06-01"))
                .andExpect(jsonPath("$.months[0].days[1].totalSeconds").value(600));

        mockMvc.perform(get("/api/study/calendar").param("month", "2026-06").with(user("studymid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.days[0].studiedSeconds").value(600))
                .andExpect(jsonPath("$.days[1].date").value("2026-06-02"))
                .andExpect(jsonPath("$.days[1].studiedSeconds").value(2400));
    }

    // ── 자정 분할 × 책 라벨 (실 DB 왕복) ────────────────────────────────────

    /** 유저 TZ 로컬 시각을 Instant로 — 손으로 UTC를 환산하지 않는다(고정 과거 일자). */
    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.of(SEOUL)).toInstant();
    }

    /**
     * 책을 걸고 자정을 넘기면 <b>두 조각이 같은 책</b>을 들어야 책별 누적이 온전하다 — 상속이 없으면
     * 둘째 조각이 미태깅이라 칩이 40분(2400)만 세고 나머지 10분이 조용히 샌다.
     */
    @Test
    @DisplayName("자정 분할 + 시작 시 책: 두 조각이 같은 책을 들어 totalSeconds가 50분 전부를 센다")
    void midnightSplit_withBook_keepsAllSecondsOnThatBook() throws Exception {
        User u = register("study-midbook@a.com", "studymidbook");
        StudyBook book = studyBook(u, "정보처리기사 실기");
        studySessionService.start(u, seoul("2026-06-01T23:50"), book);
        studySessionService.stop(u, seoul("2026-06-02T00:40"));

        mockMvc.perform(get("/api/dashboard").with(user("studymidbook")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.books[?(@.id == " + book.getId() + ")].totalSeconds",
                        hasItem(3000)))
                .andExpect(jsonPath("$.study.recentBookId").value(book.getId().intValue()));
    }

    /**
     * 책 <b>없이</b> 자정을 넘겨 잰 뒤 태깅하면, 좌표는 마지막 조각인데 앞 조각까지 함께 붙어야 한다
     * (⚡ 체인이 없으면 2400만 붙는다). 태깅은 라벨만 다는 일이라 <b>기록의 날짜별 합은 불변</b>이다.
     */
    @Test
    @DisplayName("자정 분할 + 종료 후 태깅: 앞 조각까지 체인으로 붙어 50분 전부가 그 책에 간다")
    void midnightSplit_tagBook_walksChain() throws Exception {
        User u = register("study-midtag@a.com", "studymidtag");
        StudyBook book = studyBook(u, "토익 RC");
        studySessionService.start(u, seoul("2026-06-01T23:50"), null);
        Long lastPieceId = studySessionService.stop(u, seoul("2026-06-02T00:40")).getId();

        mockMvc.perform(post("/api/study/sessions/" + lastPieceId + "/tag-book")
                        .with(user("studymidtag")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSeconds(book), hasItem(3000)))
                .andExpect(jsonPath("$.recentBookId").value(book.getId().intValue()));

        // 라벨을 달았을 뿐이라 날짜별 합은 그대로다(시간의 원장은 세션 행이다).
        mockMvc.perform(get("/api/study/history").with(user("studymidtag")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].days[0].totalSeconds").value(2400))
                .andExpect(jsonPath("$.months[0].days[1].totalSeconds").value(600));
    }

    // ── 끝난 측정의 책 정정 (기록 화면 붙이기·바꾸기·떼기) ─────────────────────

    private org.springframework.test.web.servlet.ResultActions assign(String login, Long sessionId, String body)
            throws Exception {
        return mockMvc.perform(post("/api/study/sessions/" + sessionId + "/book")
                .with(user(login)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long studyBookIdOf(Long sessionId) {
        StudyBook b = studyRepository.findById(sessionId).orElseThrow().getBook();
        return b == null ? null : b.getId();
    }

    @Test
    @DisplayName("POST /api/study/sessions/{id}/book: 끝난 측정에 붙이면 그 책 totalSeconds·recentBookId가 된다")
    void assignBook_attachesAndMovesSeconds() throws Exception {
        User u = register("study-asg1@a.com", "studyasg1");
        StudyBook book = studyBook(u, "정보처리기사 실기");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(30));

        assign("studyasg1", session.getId(), "{\"bookId\":" + book.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSeconds(book), hasItem(1800)))
                .andExpect(jsonPath("$.recentBookId").value(book.getId().intValue()));
    }

    @Test
    @DisplayName("POST /api/study/sessions/{id}/book: 책 → 다른 책 · 명시적 null로 떼기 모두 200")
    void assignBook_replacesAndDetaches() throws Exception {
        User u = register("study-asg2@a.com", "studyasg2");
        StudyBook first = studyBook(u, "먼저 책");
        StudyBook other = studyBook(u, "다른 책");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(10), first);

        assign("studyasg2", session.getId(), "{\"bookId\":" + other.getId() + "}").andExpect(status().isOk());
        assertThat(studyBookIdOf(session.getId())).isEqualTo(other.getId());

        assign("studyasg2", session.getId(), "{\"bookId\":null}").andExpect(status().isOk());
        assertThat(studyBookIdOf(session.getId())).isNull();
    }

    @Test
    @DisplayName("POST /api/study/sessions/{id}/book: {} → 400, 책 불변(떼기는 명시적이어야 한다)")
    void assignBook_missingBookId_isBadRequest() throws Exception {
        User u = register("study-asg3@a.com", "studyasg3");
        StudyBook first = studyBook(u, "먼저 책");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(10), first);

        assign("studyasg3", session.getId(), "{}").andExpect(status().isBadRequest());

        assertThat(studyBookIdOf(session.getId())).isEqualTo(first.getId());
    }

    /** 이 컨트롤러엔 전역 IAE → 400 핸들러가 있다 — 서비스 IAE를 잡지 않으면 IDOR 마스킹이 400으로 샌다. */
    @Test
    @DisplayName("POST /api/study/sessions/{남의 id}/book → 404이고 400이 아니다(전역 IAE 핸들러 함정)")
    void assignBook_foreignSession_isNotFoundNotBadRequest() throws Exception {
        User u = register("study-asg4@a.com", "studyasg4");
        User stranger = register("study-asg4b@a.com", "studyasg4b");
        StudyBook mine = studyBook(u, "내 책");
        StudySession theirs = completedStudy(stranger, todayNoon(), Duration.ofMinutes(10));

        assign("studyasg4", theirs.getId(), "{\"bookId\":" + mine.getId() + "}")
                .andExpect(status().isNotFound());

        assertThat(studyBookIdOf(theirs.getId())).isNull();
    }

    @Test
    @DisplayName("POST /api/study/sessions/{id}/book: 남의 공부 책·독서 책장의 id → 404")
    void assignBook_foreignOrReadingBook_isNotFound() throws Exception {
        User u = register("study-asg5@a.com", "studyasg5");
        User stranger = register("study-asg5b@a.com", "studyasg5b");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(10));
        StudyBook theirs = studyBook(stranger, "남의 공부 책");
        Book reading = readingBook(u, "내 독서 책");

        assign("studyasg5", session.getId(), "{\"bookId\":" + theirs.getId() + "}").andExpect(status().isNotFound());
        assign("studyasg5", session.getId(), "{\"bookId\":" + reading.getId() + "}").andExpect(status().isNotFound());

        assertThat(studyBookIdOf(session.getId())).isNull();
    }

    @Test
    @DisplayName("POST /api/study/sessions/{id}/book: 진행 중 측정 → 409(그쪽은 active/book)")
    void assignBook_activeSession_conflicts() throws Exception {
        User u = register("study-asg6@a.com", "studyasg6");
        StudyBook book = studyBook(u, "책");
        mockMvc.perform(post("/api/study/start").with(user("studyasg6")).with(csrf()))
                .andExpect(status().isOk());
        Long activeId = studyRepository.findByUserAndEndedAtIsNull(u).orElseThrow().getId();

        assign("studyasg6", activeId, "{\"bookId\":" + book.getId() + "}").andExpect(status().isConflict());

        assertThat(studyBookIdOf(activeId)).isNull();
    }

    /**
     * 교차 원장 id — 전제: <b>그 사용자에게 같은 번호의 공부 세션이 없을 때</b> 404다. 두 원장이 각자
     * IDENTITY라 번호가 겹칠 수 있으므로 이건 IDOR 마스킹 확인이지 원장 분리 보장이 아니다(방어선은
     * 클라이언트가 시트를 열 때 원장을 고정하는 것).
     */
    @Test
    @DisplayName("POST /api/study/sessions/{독서 세션 id}/book: 같은 번호의 공부 세션이 없으면 404")
    void assignBook_readingSessionId_isNotFoundWhenNoStudySessionShareIt() throws Exception {
        User u = register("study-asg7@a.com", "studyasg7");
        StudyBook book = studyBook(u, "책");
        readingSessionService.start(u, todayNoon(), null);
        Long readingId = readingSessionService.stop(u, todayNoon().plus(Duration.ofMinutes(10))).getId();
        assertThat(studyRepository.findByIdAndUser(readingId, u)).as("전제: 같은 번호의 내 공부 세션 없음").isEmpty();

        assign("studyasg7", readingId, "{\"bookId\":" + book.getId() + "}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("assignBook(공부): 앞 조각을 고치면 자정 너머 뒤 조각까지 — 50분 전부가 그 책에 간다")
    void assignBook_earlierPiece_walksChainForward() throws Exception {
        User u = register("study-asg8@a.com", "studyasg8");
        StudyBook book = studyBook(u, "토익 RC");
        studySessionService.start(u, seoul("2026-06-01T23:50"), null);
        studySessionService.stop(u, seoul("2026-06-02T00:40"));
        Long earlierId = studyRepository.findByUserAndEndedAtIsNotNull(u).stream()
                .filter(s -> s.getEndedAt().equals(seoul("2026-06-02T00:00"))).findFirst().orElseThrow().getId();

        assign("studyasg8", earlierId, "{\"bookId\":" + book.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath(bookSeconds(book), hasItem(3000)));
    }

    @Test
    @DisplayName("assignBook(공부): 다른 사용자의 세션이 자정에 인접하고 같은 라벨(null)이어도 딸려오지 않는다")
    void assignBook_otherUsersAdjacentSession_isNotDragged() throws Exception {
        User alice = register("study-asg9a@a.com", "studyasg9a");
        User bob = register("study-asg9b@a.com", "studyasg9b");
        StudyBook bobBook = studyBook(bob, "밥 책");
        studySessionService.start(alice, seoul("2026-06-01T23:30"), null);
        Long aliceId = studySessionService.stop(alice, seoul("2026-06-02T00:00")).getId();
        studySessionService.start(bob, seoul("2026-06-02T00:00"), null);
        Long bobId = studySessionService.stop(bob, seoul("2026-06-02T00:30")).getId();

        assign("studyasg9b", bobId, "{\"bookId\":" + bobBook.getId() + "}").andExpect(status().isOk());

        assertThat(studyBookIdOf(bobId)).isEqualTo(bobBook.getId());
        assertThat(studyBookIdOf(aliceId)).isNull();
    }

    /** 23:50→00:40을 재 자정에서 갈린 두 조각의 id — [앞, 뒤]. */
    private List<Long> splitPieceIds(User u, StudyBook book) {
        studySessionService.start(u, seoul("2026-06-01T23:50"), book);
        Long laterId = studySessionService.stop(u, seoul("2026-06-02T00:40")).getId();
        Long earlierId = studyRepository.findByUserAndEndedAtIsNotNull(u).stream()
                .filter(s -> s.getEndedAt().equals(seoul("2026-06-02T00:00"))).findFirst().orElseThrow().getId();
        return List.of(earlierId, laterId);
    }

    @Test
    @DisplayName("assignBook(공부): 뒤 조각을 고치면 자정 앞 조각까지 — 책→다른 책도 체인을 걷는다")
    void assignBook_laterPiece_walksChainBackward() throws Exception {
        User u = register("study-asg10@a.com", "studyasg10");
        StudyBook first = studyBook(u, "먼저 책");
        StudyBook other = studyBook(u, "다른 책");
        List<Long> pieces = splitPieceIds(u, first);

        assign("studyasg10", pieces.get(1), "{\"bookId\":" + other.getId() + "}").andExpect(status().isOk());

        assertThat(pieces).extracting(this::studyBookIdOf).containsExactly(other.getId(), other.getId());
    }

    /* 방향마다 붙이기를 한 번만 한다 — 위 테스트(뒤로 걷기)와 짝. 둘을 한 테스트에 묶으면 finder 하나의 user 조건이 빠져도 초록이다. */
    @Test
    @DisplayName("assignBook(공부): 앞으로 걷기 — 자정에 시작한 다른 사용자의 같은 라벨(null) 세션은 딸려오지 않는다")
    void assignBook_forwardWalk_doesNotDragOtherUsersSession() throws Exception {
        User alice = register("study-asg11a@a.com", "studyasg11a");
        User bob = register("study-asg11b@a.com", "studyasg11b");
        StudyBook aliceBook = studyBook(alice, "앨리스 책");
        studySessionService.start(alice, seoul("2026-06-01T23:30"), null);
        Long aliceId = studySessionService.stop(alice, seoul("2026-06-02T00:00")).getId();
        studySessionService.start(bob, seoul("2026-06-02T00:00"), null);
        Long bobId = studySessionService.stop(bob, seoul("2026-06-02T00:30")).getId();

        assign("studyasg11a", aliceId, "{\"bookId\":" + aliceBook.getId() + "}").andExpect(status().isOk());

        assertThat(studyBookIdOf(aliceId)).isEqualTo(aliceBook.getId());
        assertThat(studyBookIdOf(bobId)).isNull();
    }

    @Test
    @DisplayName("assignBook(공부): 앞 조각을 고칠 때 뒤 조각 라벨이 고치기 전과 다르면(이미 따로 고침) 딸려오지 않는다")
    void assignBook_laterNeighbourWithDifferentLabel_isNotDragged() throws Exception {
        User u = register("study-asg12@a.com", "studyasg12");
        StudyBook separately = studyBook(u, "따로 고친 책");
        StudyBook picked = studyBook(u, "새 책");
        List<Long> pieces = splitPieceIds(u, null);
        StudySession later = studyRepository.findById(pieces.get(1)).orElseThrow();
        later.assignBook(separately); // 뒤 조각만 따로 라벨이 붙은 상태(엔티티 직접 — 픽스처)
        studyRepository.save(later);

        assign("studyasg12", pieces.get(0), "{\"bookId\":" + picked.getId() + "}").andExpect(status().isOk());

        assertThat(pieces).extracting(this::studyBookIdOf).containsExactly(picked.getId(), separately.getId());
    }

    @Test
    @DisplayName("assignBook(공부): 뒤 조각을 고칠 때 앞 조각 라벨이 고치기 전과 다르면(이미 따로 고침) 딸려오지 않는다")
    void assignBook_earlierNeighbourWithDifferentLabel_isNotDragged() throws Exception {
        User u = register("study-asg13@a.com", "studyasg13");
        StudyBook separately = studyBook(u, "따로 고친 책");
        StudyBook picked = studyBook(u, "새 책");
        List<Long> pieces = splitPieceIds(u, null);
        StudySession earlier = studyRepository.findById(pieces.get(0)).orElseThrow();
        earlier.assignBook(separately); // 앞 조각만 따로 라벨이 붙은 상태(엔티티 직접 — 픽스처)
        studyRepository.save(earlier);

        assign("studyasg13", pieces.get(1), "{\"bookId\":" + picked.getId() + "}").andExpect(status().isOk());

        assertThat(pieces).extracting(this::studyBookIdOf).containsExactly(separately.getId(), picked.getId());
    }

    @Test
    @DisplayName("assignBook(공부): 같은 시각(자정 아님)에 시작·종료한 0초 세션 둘은 서로 딸려오지 않는다")
    void assignBook_zeroSecondSessionsAtSameInstant_areNotChained() throws Exception {
        User u = register("study-asg14@a.com", "studyasg14");
        StudyBook book = studyBook(u, "책");
        Instant t = seoul("2026-06-01T18:00");
        studySessionService.start(u, t, null);
        Long firstId = studySessionService.stop(u, t).getId();
        studySessionService.start(u, t, null);
        Long secondId = studySessionService.stop(u, t).getId();

        assign("studyasg14", secondId, "{\"bookId\":" + book.getId() + "}").andExpect(status().isOk());

        assertThat(studyBookIdOf(secondId)).isEqualTo(book.getId());
        assertThat(studyBookIdOf(firstId)).isNull();
    }

    @Test
    @DisplayName("assignBook(공부): 같은 자정에 시작한 진행 중 세션은 딸려오지 않는다(EndedAtIsNotNull)")
    void assignBook_activeSessionAtSameMidnight_isNotDragged() throws Exception {
        User u = register("study-asg15@a.com", "studyasg15");
        StudyBook book = studyBook(u, "책");
        studySessionService.start(u, seoul("2026-06-01T23:30"), null);
        Long endedId = studySessionService.stop(u, seoul("2026-06-02T00:00")).getId();
        Long activeId = studySessionService.start(u, seoul("2026-06-02T00:00"), null).getId();

        assign("studyasg15", endedId, "{\"bookId\":" + book.getId() + "}").andExpect(status().isOk());

        assertThat(studyBookIdOf(endedId)).isEqualTo(book.getId());
        assertThat(studyBookIdOf(activeId)).isNull();
    }

    @Test
    @DisplayName("GET /api/study/history: 날마다 sessions에 측정 한 건씩(id·HH:mm·책 제목) — 책은 fetch join이라 lazy 예외 없이")
    void history_carriesSessionRows() throws Exception {
        User u = register("study-histrows@a.com", "studyhistrows");
        StudyBook book = studyBook(u, "정보처리기사 실기");
        StudySession session = completedStudy(u, todayNoon(), Duration.ofMinutes(25), book);

        mockMvc.perform(get("/api/study/history").with(user("studyhistrows")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].id").value(session.getId().intValue()))
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].start").value("12:00"))
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].end").value("12:25"))
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].bookId").value(book.getId().intValue()))
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].bookTitle").value("정보처리기사 실기"))
                .andExpect(jsonPath("$.months[0].days[0].sessions[0].manual").value(false));
    }

    /** ⚡ 정렬이 {@code Asc}면 <b>처음</b> 공부한 책이 기본 선택으로 떠 캐러셀이 엉뚱한 데서 시작한다. */
    @Test
    @DisplayName("recentBookId: 가장 최근에 책을 걸고 잰 쪽이다(먼저 잰 책이 아니다)")
    void recentBookId_isTheLatestTaggedSession() throws Exception {
        User u = register("study-recent@a.com", "studyrecent");
        StudyBook older = studyBook(u, "먼저 공부한 책");
        StudyBook newer = studyBook(u, "나중 공부한 책");
        completedStudy(u, seoul("2026-06-01T10:00"), Duration.ofMinutes(30), older);
        completedStudy(u, seoul("2026-06-02T10:00"), Duration.ofMinutes(10), newer);

        mockMvc.perform(get("/api/dashboard").with(user("studyrecent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.recentBookId").value(newer.getId().intValue()));
    }

    /**
     * <b>격리의 셋째 축</b> — 세션(원장)·목표에 이어 <b>일정 체크</b>도 독서 표면에 0 영향이어야 한다.
     * 새 테이블이라 구조적으로 샐 길이 없지만, 그 구조가 깨졌을 때 울릴 계측기를 남긴다.
     */
    @Test
    @DisplayName("격리: 공부 일정 체크는 잔디·기록 목록 어디에도 안 나타난다")
    void studyCheckDoesNotLeakIntoReadingSurfaces() throws Exception {
        User u = register("study-checkiso@a.com", "studycheckiso");
        completedStudy(u, todayNoon(), Duration.ofHours(1));

        mockMvc.perform(post("/api/study/check").with(user("studycheckiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today() + "\",\"kept\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").with(user("studycheckiso")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.totalSeconds").value(0))
                .andExpect(jsonPath("$.graph.activeDays").value(0));
        mockMvc.perform(get("/api/history").with(user("studycheckiso")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty());
    }
}
