package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 책별 상세(월별 일자 기록 + 누적 시간) 서비스 단위 테스트 — 레포 mock.
 *
 * <p>"이 책의 완료 세션만" 유저 타임존 일자로 묶고, 월별 섹션(최신 월·일 먼저)과 누적 시간으로
 * 환산하는 조립을 본다. 월 묶음 규칙 자체는 {@link ReadingHistoryServiceTest}(monthlyHistory)가
 * 전수 검증하므로, 여기선 "책 필터 + 진행 중 제외 + 월 분리"의 결합만 본다.
 */
class BookContributionServiceTest {

    private static User seoulUser() {
        return User.of("reader@booktimer.com", "h", "독자", "Asia/Seoul", Role.USER);
    }

    private static ReadingSession completed(User u, Book b, String startIso, long durationSec) {
        Instant start = Instant.parse(startIso);
        ReadingSession s = ReadingSession.start(u, start, b);
        s.end(start.plusSeconds(durationSec));
        return s;
    }

    @Test
    @DisplayName("이 책의 완료 세션을 월별로 묶어 누적 시간을 만든다(진행 중은 제외, 월 안 최신 일 먼저)")
    void detail_aggregatesThisBooksCompletedSessions() {
        ReadingSessionRepository repo = mock(ReadingSessionRepository.class);
        BookContributionService service = new BookContributionService(repo);

        User user = seoulUser();
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);

        // 서울 기준 6/1(30분), 6/2(60분) 완료 세션 + 진행 중 세션 1개(집계 제외)
        ReadingSession s1 = completed(user, book, "2026-06-01T01:00:00Z", 1800);
        ReadingSession s2 = completed(user, book, "2026-06-02T01:00:00Z", 3600);
        ReadingSession active = ReadingSession.start(user, Instant.parse("2026-06-02T15:30:00Z"), book);
        when(repo.findByUserAndBook(user, book)).thenReturn(List.of(s1, s2, active));

        BookReadingDetail detail = service.detail(user, book);

        assertThat(detail.totalSeconds()).isEqualTo(5400L); // 30분 + 60분 (진행 중 0초 미포함)
        assertThat(detail.monthlyHistory()).hasSize(1); // 6월 한 달
        MonthlyReadingSection june = detail.monthlyHistory().get(0);
        assertThat(june.month()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(june.totalSeconds()).isEqualTo(5400L);
        assertThat(june.days()).extracting(DailyReadingRecord::date)
                .containsExactly(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1)); // 최신 일 먼저
    }

    @Test
    @DisplayName("여러 달에 걸친 기록은 월별 섹션으로 나뉘고 최신 월이 먼저다")
    void detail_groupsAcrossMonths() {
        ReadingSessionRepository repo = mock(ReadingSessionRepository.class);
        BookContributionService service = new BookContributionService(repo);

        User user = seoulUser();
        Book book = Book.register(user, "전쟁과 평화", null, null, null, null, null, BookStatus.READING);
        when(repo.findByUserAndBook(user, book)).thenReturn(List.of(
                completed(user, book, "2026-05-20T01:00:00Z", 1800),
                completed(user, book, "2026-06-01T01:00:00Z", 3600)));

        BookReadingDetail detail = service.detail(user, book);

        assertThat(detail.monthlyHistory()).extracting(MonthlyReadingSection::month)
                .containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 5)); // 최신 월 먼저
        assertThat(detail.totalSeconds()).isEqualTo(5400L);
    }

    @Test
    @DisplayName("이 책 기록이 없으면 빈 월별 기록 + 누적 0")
    void detail_emptyWhenNoSessions() {
        ReadingSessionRepository repo = mock(ReadingSessionRepository.class);
        BookContributionService service = new BookContributionService(repo);

        User user = seoulUser();
        Book book = Book.register(user, "안 읽은 책", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(repo.findByUserAndBook(user, book)).thenReturn(List.of());

        BookReadingDetail detail = service.detail(user, book);

        assertThat(detail.totalSeconds()).isZero();
        assertThat(detail.monthlyHistory()).isEmpty();
    }
}
