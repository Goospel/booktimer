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
import com.booktimer.timer.ReadingGoalChangeRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudySessionRepository studyRepository;
    @Autowired StudySessionService studySessionService;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired BookRepository bookRepository;
    @Autowired ReadingSessionService readingSessionService;
    @Autowired ReadingTimerRepository timerRepository;
    @Autowired ReadingGoalChangeRepository goalChangeRepository;
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
     * 통째로 죽는다</b>. 옛 네 필드가 그대로 실리는지도 여기서 함께 못 박는다.
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
                .andExpect(jsonPath("$.goalSeconds").exists())
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

    // ── 공부 하루 목표 (2차) ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/goal: 미인증 → 로그인으로 차단")
    void goal_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/goal").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":3600}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/study/goal: 200 + 응답 상태에 goalSeconds가 실린다")
    void goal_savesAndEchoes() throws Exception {
        register("study-goal@a.com", "studygoal");

        mockMvc.perform(post("/api/study/goal").with(user("studygoal")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":3600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSeconds").value(3600))
                .andExpect(jsonPath("$.hasActiveSession").value(false));
    }

    @Test
    @DisplayName("POST /api/study/goal: 음수는 400 — 도메인 규칙이 문 앞에서 걸린다")
    void goal_negative_isBadRequest() throws Exception {
        register("study-goalneg@a.com", "studygoalneg");

        mockMvc.perform(post("/api/study/goal").with(user("studygoalneg")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/study/goal: 저장한 목표가 대시보드 study 블록에 그대로 실린다(재진입 유지)")
    void goal_isCarriedByDashboard() throws Exception {
        register("study-goaldash@a.com", "studygoaldash");

        mockMvc.perform(post("/api/study/goal").with(user("studygoaldash")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":5400}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").with(user("studygoaldash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.goalSeconds").value(5400));
    }

    @Test
    @DisplayName("start/stop 응답도 goalSeconds를 실어 준다 — 측정 왕복 뒤 게이지가 분모를 잃지 않는다")
    void startStop_carryGoalSeconds() throws Exception {
        register("study-goalss@a.com", "studygoalss");

        mockMvc.perform(post("/api/study/goal").with(user("studygoalss")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":1800}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/study/start").with(user("studygoalss")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSeconds").value(1800));
        mockMvc.perform(post("/api/study/stop").with(user("studygoalss")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSeconds").value(1800));
    }

    /**
     * <b>격리의 다른 축</b> — 세션 원장이 아니라 <b>목표</b>가 안 섞이는지를 본다.
     *
     * <p>공부 목표를 저장할 때 {@code ReadingGoalService.record}를 부르면 독서 목표 이력에 공부 값이
     * 섞여 <b>부채 판정이 오염</b>된다(그날 목표로 과거를 판정하는 원장이라 조용히 틀린 값이 된다).
     */
    @Test
    @DisplayName("격리: 공부 목표를 저장해도 독서 목표·목표 변경 이력은 그대로다")
    void studyGoalDoesNotTouchReadingGoal() throws Exception {
        User u = register("study-goaliso@a.com", "studygoaliso");
        long readingGoalBefore = timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds();
        int goalChangesBefore = goalChangeRepository.findByUserOrderByEffectiveDateAsc(u).size();

        mockMvc.perform(post("/api/study/goal").with(user("studygoaliso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":7200}"))
                .andExpect(status().isOk());

        assertThat(timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds())
                .isEqualTo(readingGoalBefore);
        assertThat(goalChangeRepository.findByUserOrderByEffectiveDateAsc(u)).hasSize(goalChangesBefore);
        mockMvc.perform(get("/api/dashboard").with(user("studygoaliso")))
                .andExpect(jsonPath("$.todayGoalSeconds").value((int) readingGoalBefore));
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
    @DisplayName("GET /api/study/calendar: 목표와 일별 측정·판정을 함께 준다")
    void calendar_returnsGoalAndDays() throws Exception {
        User u = register("study-cal@a.com", "studycal");
        completedStudy(u, todayNoon(), Duration.ofMinutes(40));

        mockMvc.perform(post("/api/study/goal").with(user("studycal")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyGoalSeconds\":3600}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/study/calendar").param("month", thisMonth()).with(user("studycal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSeconds").value(3600))
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
