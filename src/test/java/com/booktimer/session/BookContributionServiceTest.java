package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 책별 상세(잔디 + 일자별 기록 + 누적 시간) 서비스 단위 테스트 — 레포 mock + fixed Clock.
 *
 * <p>그리드 구성은 {@link ContributionGraphBuilderTest}, "오늘" 경계는 {@link ReadingContributionServiceTest}가
 * 본다. 여기선 "이 책의 완료 세션만" 일자별로 묶어 잔디·기록·총합으로 환산하는 조립을 본다.
 */
class BookContributionServiceTest {

    // 2026-06-02T15:30Z — Asia/Seoul(UTC+9)로는 6/3 00:30(=오늘)
    private static final Instant NOW = Instant.parse("2026-06-02T15:30:00Z");

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
    @DisplayName("이 책의 완료 세션을 일자별로 묶어 잔디·기록·누적 시간을 만든다(진행 중은 제외)")
    void detail_aggregatesThisBooksCompletedSessions() {
        ReadingSessionRepository repo = mock(ReadingSessionRepository.class);
        ReadingTimerRepository timers = mock(ReadingTimerRepository.class);
        when(timers.findByUser(any())).thenReturn(Optional.empty());
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        BookContributionService service = new BookContributionService(repo, timers, fixed);

        User user = seoulUser();
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);

        // 서울 기준 6/1(30분), 6/2(60분) 완료 세션 + 진행 중 세션 1개(집계 제외)
        ReadingSession s1 = completed(user, book, "2026-06-01T01:00:00Z", 1800);
        ReadingSession s2 = completed(user, book, "2026-06-02T01:00:00Z", 3600);
        ReadingSession active = ReadingSession.start(user, NOW, book);
        when(repo.findByUserAndBook(user, book)).thenReturn(List.of(s1, s2, active));

        BookReadingDetail detail = service.detail(user, book);

        assertThat(detail.totalSeconds()).isEqualTo(5400L); // 30분 + 60분
        assertThat(detail.dailyHistory()).hasSize(2); // 6/1, 6/2 두 날
        assertThat(detail.dailyHistory().get(0).date().toString()).isEqualTo("2026-06-02"); // 최신순
        assertThat(detail.graph()).isNotNull();
        assertThat(detail.graph().totalSeconds()).isEqualTo(5400L); // 최근이라 1년 창 안
    }

    @Test
    @DisplayName("이 책 기록이 없으면 빈 기록 + 빈 잔디(총합 0)")
    void detail_emptyWhenNoSessions() {
        ReadingSessionRepository repo = mock(ReadingSessionRepository.class);
        ReadingTimerRepository timers = mock(ReadingTimerRepository.class);
        when(timers.findByUser(any())).thenReturn(Optional.empty());
        BookContributionService service = new BookContributionService(repo, timers, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = seoulUser();
        Book book = Book.register(user, "안 읽은 책", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(repo.findByUserAndBook(user, book)).thenReturn(List.of());

        BookReadingDetail detail = service.detail(user, book);

        assertThat(detail.totalSeconds()).isZero();
        assertThat(detail.dailyHistory()).isEmpty();
        assertThat(detail.graph().totalSeconds()).isZero();
    }
}
