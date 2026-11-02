package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/sessions/finish} · {@code /api/study/finish} — 앱이 기기에서 잰 끝난 측정을 올리는 마감 문
 * (설계 2026-11-02 §6.2 · §9.2).
 *
 * <p>클라이언트 시각 · bookId를 받는 <b>새 쓰기 문</b>이라 경계가 핵심이다: 새 행만 적는다(있던 행 그대로) ·
 * 겹친 실측은 뺀다 · 남의 책 · 남의 행은 건드리지도 섞지도 않는다 · 422(지움) · 409(미룸) · 400(남김)이 갈린다.
 *
 * <p>시각은 실제 시계 기준 상대값이다(밀리초 경계는 {@link MeasureFinishPolicyTest}). 날짜를 걸치는 구간은
 * <b>지난 자정</b>(어제 · 그제의 고정 시각)으로 잡는다 — 실행 시각에 따라 자정 분할이 끼어들지 않게.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeasureFinishApiControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String READING = "/api/sessions/finish";
    private static final String STUDY = "/api/study/finish";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired ApiTokenService apiTokenService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired StudySessionRepository studyRepository;
    @Autowired BookRepository bookRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired Clock clock;

    private User user(String name) {
        return registrationService.registerOAuth(name + "@noreply.booktimer.app", "마감_" + name, SEOUL.getId(),
                AuthProvider.TOSS, LocalDate.ofInstant(clock.instant(), SEOUL), false);
    }

    private ResultActions finish(String path, User u, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + apiTokenService.issue(u))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(Instant s, Instant e, Long bookId) {
        return "{\"startedAt\":\"" + s + "\",\"endedAt\":\"" + e + "\""
                + (bookId == null ? "" : ",\"bookId\":" + bookId) + "}";
    }

    /** 사용자 시간대로 오늘에서 {@code daysAgo}일 전의 {@code hh:mm}. */
    private Instant day(int daysAgo, String hhmm) {
        return LocalDate.ofInstant(clock.instant(), SEOUL).minusDays(daysAgo)
                .atTime(LocalTime.parse(hhmm)).atZone(SEOUL).toInstant();
    }

    private Instant yesterday(String hhmm) {
        return day(1, hhmm);
    }

    private ReadingSession ended(User u, Instant s, Instant e, Book book) {
        ReadingSession r = ReadingSession.start(u, s, book);
        r.end(e);
        return sessionRepository.save(r);
    }

    private StudySession endedStudy(User u, Instant s, Instant e) {
        StudySession r = StudySession.start(u, s);
        r.end(e);
        return studyRepository.save(r);
    }

    /** 마감이 만든 행(실측 · 끝남) — 시작 시각순. */
    private List<ReadingSession> realEnded(User u) {
        return sessionRepository.findByUser(u).stream()
                .filter(s -> !s.isManualEntry() && s.getEndedAt() != null)
                .sorted(java.util.Comparator.comparing(ReadingSession::getStartedAt))
                .toList();
    }

    private List<StudySession> studyEnded(User u) {
        return studyRepository.findByUserAndEndedAtIsNotNull(u).stream()
                .sorted(java.util.Comparator.comparing(StudySession::getStartedAt))
                .toList();
    }

    // ------------------------------------------------------------------ REQ-353

    @Test
    @DisplayName("REQ-353 · 서버가 모르는 측정을 기기 시각 그대로 새 행으로 적는다(책 · 실측 · 읽고싶음 → 읽는중)")
    void finish_storesDeviceTimesAsNewRow() throws Exception {
        User u = user("f-new");
        Book book = bookRepository.save(Book.register(u, "읽고 싶은 책", null, null, null, null, null,
                BookStatus.WANT_TO_READ));
        Instant s = yesterday("10:00").plusNanos(123_456_789);
        Instant e = yesterday("10:40").plusNanos(987_654_321);

        finish(READING, u, body(s, e, book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trimmedSeconds").value(0));

        assertThat(realEnded(u)).singleElement().satisfies(r -> {
            assertThat(r.getStartedAt()).isEqualTo(s.truncatedTo(ChronoUnit.MILLIS));
            assertThat(r.getEndedAt()).isEqualTo(e.truncatedTo(ChronoUnit.MILLIS));
            assertThat(r.isManualEntry()).isFalse();
            assertThat(r.getBook()).isSameAs(book);
        });
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(book.getStartedReadingAt()).isEqualTo(s.truncatedTo(ChronoUnit.MILLIS)); // 지금이 아니라 측정 시작
    }

    @Test
    @DisplayName("REQ-353 · 구간 전체가 이미 있는 실측에 덮여 적을 조각이 없으면 읽고싶음 책을 읽는중으로 바꾸지 않는다")
    void finish_fullyCovered_leavesWantToReadBook() throws Exception {
        User u = user("f-covered");
        Book book = bookRepository.save(Book.register(u, "아직 안 읽은 책", null, null, null, null, null,
                BookStatus.WANT_TO_READ));
        ended(u, yesterday("09:00"), yesterday("12:00"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), book.getId()))
                .andExpect(status().isOk());

        assertThat(realEnded(u)).singleElement() // 덮은 실측 하나뿐 — 새 행 0
                .satisfies(r -> assertThat(r.getBook()).isNull());
        assertThat(book.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
        assertThat(book.getStartedReadingAt()).isNull();
    }

    @Test
    @DisplayName("REQ-353 · 남의 · 없는 책이면 책 없이 적는다(404가 아니다)")
    void finish_foreignOrMissingBook_storesWithoutBook() throws Exception {
        User u = user("f-book");
        User other = user("f-book-other");
        Book foreign = bookRepository.save(Book.register(other, "남의 책", null, null, null, null, null, BookStatus.READING));
        Book mine = bookRepository.save(Book.register(u, "내 책", null, null, null, null, null, BookStatus.READING));

        finish(READING, u, body(yesterday("08:00"), yesterday("08:30"), foreign.getId())).andExpect(status().isOk());
        finish(READING, u, body(yesterday("09:00"), yesterday("09:30"), 987_654_321L)).andExpect(status().isOk());
        // 양성 대조: 내 책이면 붙는다
        finish(READING, u, body(yesterday("10:00"), yesterday("10:30"), mine.getId())).andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getBook).containsExactly(null, null, mine);
        assertThat(realEnded(other)).isEmpty();
    }

    @Test
    @DisplayName("REQ-353 · 같은 마감을 다시 보내면 한 번만 적고 겹침으로 알리지 않는다")
    void finish_resent_storesOnceAndReportsNoTrim() throws Exception {
        User u = user("f-twice");
        String payload = body(yesterday("10:00"), yesterday("11:00"), null);

        finish(READING, u, payload).andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));
        finish(READING, u, payload).andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));

        assertThat(realEnded(u)).singleElement()
                .satisfies(r -> assertThat(r.getDurationSeconds()).isEqualTo(3600L));
    }

    @Test
    @DisplayName("REQ-353 · 6시간 상한을 먼저 걸고 사용자 시간대 자정에서 나눈다")
    void finish_capsThenSplitsAtMidnight() throws Exception {
        User u = user("f-cap");

        finish(READING, u, body(day(2, "22:00"), yesterday("06:00"), null)).andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(day(2, "22:00"), yesterday("00:00")),
                        tuple(yesterday("00:00"), yesterday("04:00")));
    }

    @Test
    @DisplayName("REQ-353 · 있던 행은 그대로다 — 켜진 측정 · 끝난 기록 · 손 기록을 닫지도 줄이지도 지우지도 않는다")
    void finish_leavesExistingRowsUntouched() throws Exception {
        User u = user("f-keep");
        Book book = bookRepository.save(Book.register(u, "책", null, null, null, null, null, BookStatus.READING));
        ReadingSession done = ended(u, yesterday("10:00"), yesterday("10:20"), null);
        ReadingSession manual = sessionRepository.save(
                ReadingSession.manual(u, yesterday("10:00"), yesterday("11:00"), book));
        ReadingSession open = sessionRepository.save(ReadingSession.start(u, yesterday("10:40")));

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null)).andExpect(status().isOk());

        assertThat(sessionRepository.findByUser(u)).hasSize(4); // 양성 대조: 새 행 하나가 생겼다
        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("10:00"), yesterday("10:20")),
                        tuple(yesterday("10:20"), yesterday("10:40")));
        assertThat(done.getEndedAt()).isEqualTo(yesterday("10:20"));
        assertThat(done.getDurationSeconds()).isEqualTo(1200L);
        assertThat(manual.getEndedAt()).isEqualTo(yesterday("11:00"));
        assertThat(manual.getDurationSeconds()).isEqualTo(3600L);
        assertThat(open.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("REQ-353 · 공부 — 공부 책으로 · 책 없이 새로 적고 남의 공부 책이면 책 없이")
    void studyFinish_booksAndForeignBook() throws Exception {
        User u = user("f-study");
        User other = user("f-study-other");
        StudyBook mine = studyBookRepository.save(StudyBook.register(u, "내 공부 책", "저자", null, null, null, null));
        StudyBook foreign = studyBookRepository.save(StudyBook.register(other, "남의 공부 책", "저자", null, null, null, null));

        finish(STUDY, u, body(yesterday("08:00"), yesterday("08:30"), mine.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));
        finish(STUDY, u, body(yesterday("09:00"), yesterday("09:30"), null)).andExpect(status().isOk());
        finish(STUDY, u, body(yesterday("10:00"), yesterday("10:30"), foreign.getId())).andExpect(status().isOk());

        assertThat(studyEnded(u)).extracting(StudySession::getStartedAt, StudySession::getBook)
                .containsExactly(tuple(yesterday("08:00"), mine), tuple(yesterday("09:00"), null),
                        tuple(yesterday("10:00"), null));
        assertThat(studyEnded(u)).allSatisfy(s -> assertThat(s.getDurationSeconds()).isEqualTo(1800L));
        assertThat(realEnded(u)).isEmpty(); // 공부 마감은 독서 원장에 적지 않는다
    }

    // ------------------------------------------------------------------ REQ-354

    @Test
    @DisplayName("REQ-354 · 끝난 독서 실측과 겹친 시간은 빼고 적고 걸친 겹침 초를 돌려준다")
    void finish_subtractsEndedReadingAndReportsCrossing() throws Exception {
        User u = user("f-overlap");
        ended(u, yesterday("09:30"), yesterday("10:15"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trimmedSeconds").value(900));

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("09:30"), yesterday("10:15")),
                        tuple(yesterday("10:15"), yesterday("11:00")));
    }

    @Test
    @DisplayName("REQ-354 · 독서 마감은 끝난 공부와, 공부 마감은 끝난 독서와 겹친 시간도 뺀다(원장 사이 배타)")
    void finish_subtractsAcrossLedgers() throws Exception {
        User u = user("f-cross");
        endedStudy(u, yesterday("09:50"), yesterday("10:30"));
        ended(u, yesterday("13:50"), yesterday("14:30"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(1800));
        finish(STUDY, u, body(yesterday("14:00"), yesterday("15:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(1800));

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt)
                .containsExactly(yesterday("10:30"), yesterday("13:50"));
        assertThat(studyEnded(u)).extracting(StudySession::getStartedAt)
                .containsExactly(yesterday("09:50"), yesterday("14:30"));
    }

    @Test
    @DisplayName("REQ-354 · 켜져 있는 측정과 겹친 시간은 빼고 그 측정은 열린 채 둔다 — 6시간 넘게 켜진 측정은 시작 + 6시간까지만 뺀다")
    void finish_subtractsOpenSessionUpToCap() throws Exception {
        User u = user("f-open");
        // 어제 08:00에 켜져 스윕 전인 측정 — 바쁜 구간은 [08:00, 14:00]까지만
        ReadingSession open = sessionRepository.save(ReadingSession.start(u, yesterday("08:00")));

        finish(READING, u, body(yesterday("13:00"), yesterday("15:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(3600));

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("14:00"), yesterday("15:00")));
        assertThat(open.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("REQ-354 · 독서 마감은 켜져 있는 공부와 겹친 시간도 빼고 그 공부는 열린 채 둔다")
    void finish_subtractsOpenStudy() throws Exception {
        User u = user("f-open-study");
        // PC 웹 공부가 어제 10:30부터 켜져 있다 — 폰 앱 독서 마감 [10:00, 11:00]
        StudySession openStudy = studyRepository.save(StudySession.start(u, yesterday("10:30")));

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(1800));

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("10:00"), yesterday("10:30")));
        assertThat(openStudy.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("REQ-354 · 켜진 행이 둘이어도 마감은 200이고 둘 다 뺀다")
    void finish_twoOpenRows_subtractsBoth() throws Exception {
        User u = user("f-two-open");
        // 웹 start 경주의 결과 — 같은 사용자의 열린 독서 행 둘
        sessionRepository.save(ReadingSession.start(u, yesterday("10:00")));
        sessionRepository.save(ReadingSession.start(u, yesterday("10:30")));

        finish(READING, u, body(yesterday("09:30"), yesterday("11:00"), null)).andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("09:30"), yesterday("10:00")));
    }

    @Test
    @DisplayName("REQ-354 · 손 기록은 겹침으로 보지 않는다")
    void finish_manualEntryIsNotBusy() throws Exception {
        User u = user("f-manual");
        Book book = bookRepository.save(Book.register(u, "책", null, null, null, null, null, BookStatus.READING));
        sessionRepository.save(ReadingSession.manual(u, yesterday("10:00"), yesterday("11:00"), book));

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));

        assertThat(realEnded(u)).singleElement()
                .satisfies(r -> assertThat(r.getDurationSeconds()).isEqualTo(3600L));
    }

    @Test
    @DisplayName("REQ-354 · 측정 안에 통째로 든 짧은 실측은 빼되 알리지 않는다(알려진 빈틈)")
    void finish_innerShortSession_subtractedButNotReported() throws Exception {
        User u = user("f-inner");
        ended(u, yesterday("10:20"), yesterday("10:30"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(yesterday("10:00"), yesterday("10:20")),
                        tuple(yesterday("10:20"), yesterday("10:30")),
                        tuple(yesterday("10:30"), yesterday("11:00")));
    }

    @Test
    @DisplayName("REQ-354 · 겹침을 빼고 남은 1초 미만 자투리는 적지 않는다 — 1초는 적는다(독서 · 공부)")
    void subSecondLeftoverIsNotStored() throws Exception {
        User u = user("f-sliver");
        // 독서 실측이 두 문 모두의 바쁜 구간이다 — 앞머리에 0.5초(버림) · 1초(적음) 자투리가 남게 둔다
        ended(u, yesterday("10:00").plusMillis(500), yesterday("11:00"), null);
        ended(u, yesterday("12:00").plusSeconds(1), yesterday("13:00"), null);
        ended(u, yesterday("14:00").plusMillis(500), yesterday("15:00"), null);
        ended(u, yesterday("16:00").plusSeconds(1), yesterday("17:00"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null)).andExpect(status().isOk());
        finish(READING, u, body(yesterday("12:00"), yesterday("13:00"), null)).andExpect(status().isOk());
        finish(STUDY, u, body(yesterday("14:00"), yesterday("15:00"), null)).andExpect(status().isOk());
        finish(STUDY, u, body(yesterday("16:00"), yesterday("17:00"), null)).andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt)
                .containsExactly(yesterday("10:00").plusMillis(500), yesterday("12:00"),
                        yesterday("12:00").plusSeconds(1), yesterday("14:00").plusMillis(500),
                        yesterday("16:00").plusSeconds(1));
        assertThat(studyEnded(u)).extracting(StudySession::getStartedAt, StudySession::getEndedAt)
                .containsExactly(tuple(yesterday("16:00"), yesterday("16:00").plusSeconds(1)));
    }

    @Test
    @DisplayName("REQ-353~354 · 독서 — 다른 사용자의 행은 겹침에 넣지 않고 건드리지 않는다")
    void finish_otherUsersReadingRowsIgnored() throws Exception {
        User u = user("f-iso");
        User other = user("f-iso-other");
        ReadingSession otherDone = ended(other, yesterday("10:00"), yesterday("10:30"), null);
        ReadingSession otherOpen = sessionRepository.save(ReadingSession.start(other, yesterday("10:40")));
        // 양성 대조: 같은 시각 배치가 내 행이면 빠진다(그제)
        ended(u, day(2, "10:00"), day(2, "10:30"), null);

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trimmedSeconds").value(0));
        finish(READING, u, body(day(2, "10:00"), day(2, "11:00"), null)).andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getEndedAt)
                .containsExactly(tuple(day(2, "10:00"), day(2, "10:30")),
                        tuple(day(2, "10:30"), day(2, "11:00")),
                        tuple(yesterday("10:00"), yesterday("11:00")));
        assertThat(otherDone.getEndedAt()).isEqualTo(yesterday("10:30"));
        assertThat(otherOpen.getEndedAt()).isNull();
        assertThat(sessionRepository.findByUser(other)).hasSize(2);
    }

    @Test
    @DisplayName("REQ-353~354 · 공부 — 다른 사용자의 행은 겹침에 넣지 않고 건드리지 않는다")
    void finish_otherUsersStudyRowsIgnored() throws Exception {
        User u = user("f-iso-s");
        User other = user("f-iso-s-other");
        StudySession otherDone = endedStudy(other, yesterday("10:00"), yesterday("10:30"));
        StudySession otherOpen = studyRepository.save(StudySession.start(other, yesterday("10:40")));
        endedStudy(u, day(2, "10:00"), day(2, "10:30")); // 양성 대조

        finish(STUDY, u, body(yesterday("10:00"), yesterday("11:00"), null)).andExpect(status().isOk());
        finish(STUDY, u, body(day(2, "10:00"), day(2, "11:00"), null)).andExpect(status().isOk());

        assertThat(studyEnded(u)).extracting(StudySession::getStartedAt, StudySession::getEndedAt)
                .containsExactly(tuple(day(2, "10:00"), day(2, "10:30")),
                        tuple(day(2, "10:30"), day(2, "11:00")),
                        tuple(yesterday("10:00"), yesterday("11:00")));
        assertThat(otherDone.getEndedAt()).isEqualTo(yesterday("10:30"));
        assertThat(otherOpen.getEndedAt()).isNull();
    }

    // ------------------------------------------------------------------ REQ-379

    @Test
    @DisplayName("REQ-379 · 마감 조각 바로 뒤에서 시작한 웹 측정을 끝내고 태깅해도 마감 조각엔 책이 붙지 않는다(독서 — 통합)")
    void webTagAfterFinish_doesNotTagAdjacentFinishPiece() throws Exception {
        User u = user("f-tag");
        Book book = bookRepository.save(Book.register(u, "웹에서 읽은 책", null, null, null, null, null, BookStatus.READING));
        ReadingSession web = sessionRepository.save(ReadingSession.start(u, yesterday("10:30")));

        finish(READING, u, body(yesterday("10:00"), yesterday("11:00"), null)).andExpect(status().isOk());
        String bearer = "Bearer " + apiTokenService.issue(u);
        mockMvc.perform(post("/api/sessions/stop").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/" + web.getId() + "/tag-book").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk());

        assertThat(realEnded(u)).extracting(ReadingSession::getStartedAt, ReadingSession::getBook)
                .containsExactly(tuple(yesterday("10:00"), null), tuple(yesterday("10:30"), book));
    }

    // ------------------------------------------------------------------ REQ-355

    @Test
    @DisplayName("REQ-355 · 끝 < 시작 · 시작이 지금 + 5분보다 늦음 · 7일보다 오래됨은 422이고 행이 안 남는다(독서 · 공부)")
    void policyViolations_422_noRows() throws Exception {
        User u = user("f-422");
        Instant now = clock.instant();
        for (String path : List.of(READING, STUDY)) {
            finish(path, u, body(yesterday("11:00"), yesterday("10:00"), null))
                    .andExpect(status().isUnprocessableContent());
            finish(path, u, body(now.plus(Duration.ofMinutes(6)), now.plus(Duration.ofMinutes(7)), null))
                    .andExpect(status().isUnprocessableContent());
            finish(path, u, body(now.minus(Duration.ofDays(7)).minusSeconds(60), now.minus(Duration.ofDays(6)), null))
                    .andExpect(status().isUnprocessableContent());
            // 양성 대조: 시작이 지금 + 4분이면 정책 안 — 끝이 미래라 409(남긴다)
            finish(path, u, body(now.plus(Duration.ofMinutes(4)), now.plus(Duration.ofMinutes(4)), null))
                    .andExpect(status().isConflict());
        }
        assertThat(sessionRepository.findByUser(u)).isEmpty();
        assertThat(studyEnded(u)).isEmpty();

        // 양성 대조: 6일 23시간 전이면 200(독서 · 공부를 서로 다른 시각에)
        finish(READING, u, body(now.minus(Duration.ofDays(6).plusHours(23)),
                now.minus(Duration.ofDays(6).plusHours(22)), null)).andExpect(status().isOk());
        finish(STUDY, u, body(now.minus(Duration.ofDays(6).plusHours(21)),
                now.minus(Duration.ofDays(6).plusHours(20)), null)).andExpect(status().isOk());
        assertThat(realEnded(u)).isNotEmpty();
        assertThat(studyEnded(u)).isNotEmpty();
    }

    @Test
    @DisplayName("REQ-355 · 본문 · 시각 파싱 실패 · 시각 없음은 400이다 — 422가 아니라서 앱이 칸을 남긴다(독서 · 공부)")
    void unreadableBodies_400_noRows() throws Exception {
        User u = user("f-400");
        String s = yesterday("10:00").toString();
        String e = yesterday("11:00").toString();
        for (String path : List.of(READING, STUDY)) {
            finish(path, u, "{\"startedAt\":\"" + s).andExpect(status().isBadRequest());
            finish(path, u, "{\"startedAt\":\"" + s + "\",\"endedAt\":\"" + e + "\",\"bookId\":\"abc\"}")
                    .andExpect(status().isBadRequest());
            finish(path, u, "{\"startedAt\":\"2026-11-02 10:00\",\"endedAt\":\"" + e + "\"}")
                    .andExpect(status().isBadRequest());
            finish(path, u, "{\"endedAt\":\"" + e + "\"}").andExpect(status().isBadRequest());
            // 양성 대조: 같은 문에서 정책 거절은 422로 갈린다
            finish(path, u, body(yesterday("11:00"), yesterday("10:00"), null))
                    .andExpect(status().isUnprocessableContent());
        }
        assertThat(sessionRepository.findByUser(u)).isEmpty();
        assertThat(studyEnded(u)).isEmpty();
    }

    @Test
    @DisplayName("REQ-355 · 끝이 지금보다 늦으면 409이고 아무것도 적지 않는다")
    void futureEnd_409_noRows() throws Exception {
        User u = user("f-409");
        Instant now = clock.instant();

        finish(READING, u, body(now.minusSeconds(600), now.plus(Duration.ofMinutes(3)), null))
                .andExpect(status().isConflict());
        assertThat(sessionRepository.findByUser(u)).isEmpty();

        // 양성 대조: 끝 = 지금 − 1초면 200 · 행이 생긴다
        finish(READING, u, body(now.minusSeconds(600), now.minusSeconds(1), null)).andExpect(status().isOk());
        assertThat(realEnded(u)).isNotEmpty();
    }

    /**
     * ⚠️ <b>음성 판정 전용</b> — 문이 아예 없어도 체인이 먼저 401을 낸다. 「문이 산다」의 양성 대조군은 위 200 케이스들이다.
     */
    @Test
    @DisplayName("REQ-355 · 유효한 토큰이 없으면 401")
    void invalidToken_401() throws Exception {
        for (String path : List.of(READING, STUDY)) {
            mockMvc.perform(post(path).header("Authorization", "Bearer 지어낸토큰")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(yesterday("10:00"), yesterday("11:00"), null)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
