package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션↔책 연결과 책별 누적 시간 집계 테스트 (실제 레포 + H2).
 */
@SpringBootTest
@Transactional
class BookSessionTimeTest {

    @Autowired
    private ReadingSessionService sessionService;
    @Autowired
    private BookReadingStatsService statsService;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private ReadingTimerRepository timerRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email) {
        User user = userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
        timerRepository.save(ReadingTimer.startFor(user, 3600, 36000, LocalDate.of(2026, 6, 1)));
        return user;
    }

    private Book newBook(User user, String title) {
        return bookRepository.save(
                Book.register(user, title, null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("책을 지정해 측정을 시작하면 세션에 그 책이 연결된다")
    void start_withBook_attachesBook() {
        User u = newUser("a@booktimer.com");
        Book book = newBook(u, "클린 코드");

        ReadingSession session = sessionService.start(u, Instant.parse("2026-06-02T01:00:00Z"), book);

        assertThat(session.getBook()).isNotNull();
        assertThat(session.getBook().getId()).isEqualTo(book.getId());
    }

    @Test
    @DisplayName("책별 누적 시간은 그 책의 완료 세션 시간 합이다(미지정·진행중 제외)")
    void totalSecondsByBook_sumsCompletedSessions() {
        User u = newUser("b@booktimer.com");
        Book clean = newBook(u, "클린 코드");
        Book effective = newBook(u, "이펙티브 자바");

        // 클린 코드: 30분 + 20분 (완료 2건)
        sessionService.start(u, Instant.parse("2026-06-02T01:00:00Z"), clean);
        sessionService.stop(u, Instant.parse("2026-06-02T01:30:00Z"));
        sessionService.start(u, Instant.parse("2026-06-02T02:00:00Z"), clean);
        sessionService.stop(u, Instant.parse("2026-06-02T02:20:00Z"));
        // 이펙티브 자바: 10분 (완료 1건)
        sessionService.start(u, Instant.parse("2026-06-02T03:00:00Z"), effective);
        sessionService.stop(u, Instant.parse("2026-06-02T03:10:00Z"));
        // 책 미지정 측정 (집계 제외)
        sessionService.start(u, Instant.parse("2026-06-02T04:00:00Z"));
        sessionService.stop(u, Instant.parse("2026-06-02T04:15:00Z"));
        // 진행 중(미완료) — 클린 코드, 집계 제외
        sessionService.start(u, Instant.parse("2026-06-02T05:00:00Z"), clean);

        Map<Long, Long> totals = statsService.totalSecondsByBook(u);

        assertThat(totals.get(clean.getId())).isEqualTo(3000L);     // 30분+20분
        assertThat(totals.get(effective.getId())).isEqualTo(600L);  // 10분
        assertThat(totals).doesNotContainKey(null);
    }

    @Test
    @DisplayName("측정 기록이 없으면 빈 집계다")
    void totalSecondsByBook_emptyWhenNoSessions() {
        User u = newUser("c@booktimer.com");
        assertThat(statsService.totalSecondsByBook(u)).isEmpty();
    }
}
