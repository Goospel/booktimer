package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ReadingHistoryService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>완료된 측정 세션을 <b>유저 타임존 기준 일자</b>로 묶어 그날의 총 독서 시간과 세션 수를
 * 집계하고, 최신 일자가 먼저 오도록 정렬하는지 격리 검증한다(README 2.2 일자별 독서 기록).
 * "어떤 날인지"는 서버 UTC가 아니라 유저가 사는 곳의 자정 경계로 정해져야 하므로 경계값을 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingHistoryServiceTest {

    private static final long HOUR = 3600L;

    @Mock
    private ReadingSessionRepository sessionRepository;

    @InjectMocks
    private ReadingHistoryService service;

    private User seoulUser() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    /** 시작·종료 시각을 주어 완료된 세션을 만든다. */
    private ReadingSession session(User user, Instant start, long durationSeconds) {
        ReadingSession s = ReadingSession.start(user, start);
        s.end(start.plusSeconds(durationSeconds));
        return s;
    }

    /** 특정 책에 연결된 완료 세션. */
    private ReadingSession sessionWithBook(User user, Instant start, long durationSeconds, Book book) {
        ReadingSession s = ReadingSession.start(user, start, book);
        s.end(start.plusSeconds(durationSeconds));
        return s;
    }

    @Test
    @DisplayName("같은 날 여러 세션은 한 일자로 합산되고 그날 읽은 책이 오래 읽은 순으로 모인다")
    void dailyHistory_sumsSameDay() {
        User user = seoulUser();
        Book a = Book.register(user, "책A", null, null, null, null, null, BookStatus.READING);
        Book b = Book.register(user, "책B", null, null, null, null, null, BookStatus.READING);
        // 2026-06-01 KST 두 세션 (09:00 +30m 책A, 21:00 +1h 책B)
        Instant morning = Instant.parse("2026-06-01T00:00:00Z"); // 09:00 KST
        Instant evening = Instant.parse("2026-06-01T12:00:00Z"); // 21:00 KST
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                sessionWithBook(user, morning, 1800L, a),
                sessionWithBook(user, evening, HOUR, b)));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).hasSize(1);
        DailyReadingRecord day = history.get(0);
        assertThat(day.date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(day.totalSeconds()).isEqualTo(1800L + HOUR);
        // 오래 읽은 책이 앞이다 — 접힌 더미에서 보이는 건 맨 앞 한 장이라, 그 한 장이 그날을 대표해야 한다.
        assertThat(day.books()).containsExactly(
                new BookRead("책B", null, HOUR),
                new BookRead("책A", null, 1800L));
    }

    @Test
    @DisplayName("같은 날 같은 책을 여러 번 읽으면 한 줄로 합산된다 — 같은 책이 더미에 두 번 꽂히면 안 된다")
    void dailyHistory_distinctBookTitles() {
        User user = seoulUser();
        Book a = Book.register(user, "책A", null, null, null, null, null, BookStatus.READING);
        Book b = Book.register(user, "책B", null, null, null, null, null, BookStatus.READING);
        Instant t1 = Instant.parse("2026-06-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T03:00:00Z");
        Instant t3 = Instant.parse("2026-06-01T06:00:00Z");
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                sessionWithBook(user, t1, HOUR, a),
                sessionWithBook(user, t2, HOUR, a),   // 같은 책 — 중복 안 됨
                sessionWithBook(user, t3, HOUR, b)));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).books()).containsExactly(
                new BookRead("책A", null, 2 * HOUR),   // 두 세션이 한 줄로 합쳐진다
                new BookRead("책B", null, HOUR));
    }

    @Test
    @DisplayName("책마다 표지 주소가 함께 온다 — 기록 화면이 첫 글자 대신 진짜 표지를 세운다")
    void dailyHistory_carriesCoverUrl() {
        User user = seoulUser();
        Book a = Book.register(user, "책A", null, null, "https://img/a.jpg", null, null, BookStatus.READING);
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                sessionWithBook(user, Instant.parse("2026-06-01T01:00:00Z"), HOUR, a)));

        assertThat(service.dailyHistory(user).get(0).books())
                .containsExactly(new BookRead("책A", "https://img/a.jpg", HOUR));
    }

    @Test
    @DisplayName("읽은 시간이 같으면 먼저 편 책이 앞에 온다 — 볼 때마다 더미 순서가 뒤바뀌면 안 된다")
    void dailyHistory_tieKeepsReadingOrder() {
        User user = seoulUser();
        Book first = Book.register(user, "먼저 편 책", null, null, null, null, null, BookStatus.READING);
        Book later = Book.register(user, "나중 편 책", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                sessionWithBook(user, Instant.parse("2026-06-01T01:00:00Z"), HOUR, first),
                sessionWithBook(user, Instant.parse("2026-06-01T05:00:00Z"), HOUR, later)));

        assertThat(service.dailyHistory(user).get(0).books()).extracting(BookRead::title)
                .containsExactly("먼저 편 책", "나중 편 책");
    }

    @Test
    @DisplayName("책을 안 고른 세션은 책 목록엔 없지만 총합엔 남는다 — 화면은 그 차이를 「책 안 고른 기록」으로 밝힌다")
    void dailyHistory_unassignedSessionStaysInTotalOnly() {
        User user = seoulUser();
        Book a = Book.register(user, "책A", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                sessionWithBook(user, Instant.parse("2026-06-01T01:00:00Z"), HOUR, a),
                session(user, Instant.parse("2026-06-01T05:00:00Z"), 1800L)));   // 책 미지정(레거시)

        DailyReadingRecord day = service.dailyHistory(user).get(0);

        assertThat(day.totalSeconds()).isEqualTo(HOUR + 1800L);
        assertThat(day.books()).containsExactly(new BookRead("책A", null, HOUR));
    }

    @Test
    @DisplayName("여러 날 기록은 최신 일자가 먼저 오도록 정렬된다")
    void dailyHistory_sortedNewestFirst() {
        User user = seoulUser();
        Instant day1 = Instant.parse("2026-06-01T01:00:00Z"); // 06-01 KST
        Instant day3 = Instant.parse("2026-06-03T01:00:00Z"); // 06-03 KST
        Instant day2 = Instant.parse("2026-06-02T01:00:00Z"); // 06-02 KST
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                session(user, day1, HOUR),
                session(user, day3, HOUR),
                session(user, day2, HOUR)));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).extracting(DailyReadingRecord::date).containsExactly(
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("일자는 유저 타임존(자정 경계) 기준으로 묶인다 — UTC와 다른 날일 수 있다")
    void dailyHistory_groupsByUserTimezone() {
        User user = seoulUser();
        // 2026-06-01T15:30:00Z == 2026-06-02 00:30 KST → 유저 기준 06-02
        Instant lateUtc = Instant.parse("2026-06-01T15:30:00Z");
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                session(user, lateUtc, HOUR)));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("진행 중(미종료) 세션은 집계에서 제외된다")
    void dailyHistory_ignoresActiveSessions() {
        User user = seoulUser();
        ReadingSession active = ReadingSession.start(user, Instant.parse("2026-06-01T01:00:00Z")); // 미종료
        ReadingSession done = session(user, Instant.parse("2026-06-01T02:00:00Z"), HOUR);
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(active, done));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).totalSeconds()).isEqualTo(HOUR); // 진행 중 0초 미포함
    }

    @Test
    @DisplayName("그날 수동 입력 세션이 하나라도 있으면 manuallyFilled=true (직접 채운 날)")
    void dailyHistory_marksManuallyFilledDay() {
        User user = seoulUser();
        Book a = Book.register(user, "책A", null, null, null, null, null, BookStatus.READING);
        Instant t = Instant.parse("2026-05-20T03:00:00Z"); // 05-20 KST
        ReadingSession manual = ReadingSession.manual(user, t, t.plusSeconds(HOUR), a);
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(manual));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).manuallyFilled()).isTrue();
    }

    @Test
    @DisplayName("실시간 측정만 있는 날은 manuallyFilled=false")
    void dailyHistory_realtimeDay_notManuallyFilled() {
        User user = seoulUser();
        Instant t = Instant.parse("2026-06-01T01:00:00Z");
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(session(user, t, HOUR)));

        List<DailyReadingRecord> history = service.dailyHistory(user);

        assertThat(history.get(0).manuallyFilled()).isFalse();
    }

    @Test
    @DisplayName("기록이 없으면 빈 리스트")
    void dailyHistory_empty() {
        User user = seoulUser();
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of());

        assertThat(service.dailyHistory(user)).isEmpty();
    }

    // --- 월별 묶음(history 화면 '한 번에 한 달' 보기): YearMonth 그룹·최신월 먼저·월 합계 ---

    @Test
    @DisplayName("monthlyHistory: 일자별 기록을 월별로 묶고 최신 월이 먼저, 각 월 안은 최신 일이 먼저다")
    void monthlyHistory_groupsByMonthNewestFirst() {
        User user = seoulUser();
        // 6/2, 6/1, 5/20 (KST) — 입력 순서를 섞어 정렬을 검증한다
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                session(user, Instant.parse("2026-05-20T01:00:00Z"), HOUR),
                session(user, Instant.parse("2026-06-02T01:00:00Z"), HOUR),
                session(user, Instant.parse("2026-06-01T01:00:00Z"), HOUR)));

        List<MonthlyReadingSection> months = service.monthlyHistory(user);

        assertThat(months).extracting(MonthlyReadingSection::month)
                .containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 5)); // 최신 월 먼저
        assertThat(months.get(0).days()).extracting(DailyReadingRecord::date)
                .containsExactly(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1)); // 월 안 최신 일 먼저
        assertThat(months.get(1).days()).extracting(DailyReadingRecord::date)
                .containsExactly(LocalDate.of(2026, 5, 20));
    }

    @Test
    @DisplayName("monthlyHistory: 각 월 섹션은 그 달 총 독서 시간을 합산한다")
    void monthlyHistory_sumsMonthTotal() {
        User user = seoulUser();
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                session(user, Instant.parse("2026-06-02T01:00:00Z"), HOUR),
                session(user, Instant.parse("2026-06-01T01:00:00Z"), 1800L)));

        List<MonthlyReadingSection> months = service.monthlyHistory(user);

        assertThat(months).hasSize(1);
        assertThat(months.get(0).month()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(months.get(0).totalSeconds()).isEqualTo(HOUR + 1800L);
    }

    @Test
    @DisplayName("monthlyHistory: 월 경계(말일/다음달 1일)는 서로 다른 섹션으로 분리된다")
    void monthlyHistory_monthBoundarySeparates() {
        User user = seoulUser();
        // 06-30 12:00 KST, 07-01 12:00 KST
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of(
                session(user, Instant.parse("2026-06-30T03:00:00Z"), HOUR),
                session(user, Instant.parse("2026-07-01T03:00:00Z"), HOUR)));

        List<MonthlyReadingSection> months = service.monthlyHistory(user);

        assertThat(months).extracting(MonthlyReadingSection::month)
                .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 6));
        assertThat(months).allSatisfy(m -> assertThat(m.days()).hasSize(1));
    }

    @Test
    @DisplayName("monthlyHistory: 기록이 없으면 빈 리스트")
    void monthlyHistory_empty() {
        User user = seoulUser();
        when(sessionRepository.findByUserWithBook(user)).thenReturn(List.of());

        assertThat(service.monthlyHistory(user)).isEmpty();
    }
}
