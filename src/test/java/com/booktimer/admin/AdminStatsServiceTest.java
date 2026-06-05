package com.booktimer.admin;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영자 대시보드 통계 집계 서비스 테스트 (관리자 대시보드 PR-B — plan.md "통계 요약").
 *
 * <p>모두 <b>읽기 전용 집계</b>(N-037: 새 저장 아님)다 — 기존 테이블을 count/sum 으로 요약할 뿐
 * 스키마를 건드리지 않는다. 가입자 수는 <b>USER 역할만</b> 세어 운영자(ADMIN) 계정이 지표를
 * 부풀리지 않게 한다. 활성 사용자는 최근 N일 안에 측정(세션 startedAt)이 있는 distinct 사용자다.
 */
@SpringBootTest
@Transactional
class AdminStatsServiceTest {

    @Autowired
    private AdminStatsService statsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private Clock clock;

    private User user(String email, String loginId, boolean onboarded) {
        User u = User.of(email, "hash", loginId, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        if (onboarded) {
            u.completeOnboarding();
        }
        return userRepository.save(u);
    }

    private User admin(String email, String loginId) {
        User u = User.of(email, "hash", loginId, "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        u.completeOnboarding();
        return userRepository.save(u);
    }

    private Book book(User owner, String title) {
        return bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    /** 완료된 측정 세션 1건 — startedAt 부터 durationSec 초 동안 읽은 것으로 만든다. */
    private void session(User owner, Book b, Instant startedAt, long durationSec) {
        ReadingSession s = ReadingSession.start(owner, startedAt, b);
        s.end(startedAt.plusSeconds(durationSec));
        sessionRepository.save(s);
    }

    @Test
    @DisplayName("가입자 수는 USER 역할만 센다 — 운영자(ADMIN)는 지표에서 제외된다")
    void totalUsers_excludesAdmin() {
        user("u1@booktimer.com", "userone", true);
        user("u2@booktimer.com", "usertwo", true);
        admin("admin@booktimer.com", "adminhandle");

        AdminStats stats = statsService.summary();

        assertThat(stats.totalUsers()).isEqualTo(2);
    }

    @Test
    @DisplayName("온보딩 완료자 수는 onboarded=true 인 USER만 센다")
    void onboardedUsers_countsOnlyCompleted() {
        user("u1@booktimer.com", "userone", true);
        user("u2@booktimer.com", "usertwo", false); // 온보딩 미완
        admin("admin@booktimer.com", "adminhandle"); // ADMIN 은 onboarded여도 가입자 아님

        AdminStats stats = statsService.summary();

        assertThat(stats.totalUsers()).isEqualTo(2);
        assertThat(stats.onboardedUsers()).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 N일 활성 사용자는 그 창 안에 측정한 distinct 사용자만 센다")
    void activeUsers_countsRecentDistinct() {
        User u1 = user("u1@booktimer.com", "userone", true);
        User u2 = user("u2@booktimer.com", "usertwo", true);
        User u3 = user("u3@booktimer.com", "userthree", true);
        Book b1 = book(u1, "책1");
        Book b2 = book(u2, "책2");
        Book b3 = book(u3, "책3");

        Instant now = clock.instant();
        // u1: 최근(어제) 활성, 두 세션이어도 사용자는 1로 distinct 집계
        session(u1, b1, now.minus(Duration.ofDays(1)), 100);
        session(u1, b1, now.minus(Duration.ofHours(2)), 200);
        // u2: 최근(3일 전) 활성
        session(u2, b2, now.minus(Duration.ofDays(3)), 300);
        // u3: 오래 전(30일 전)만 — 활성 아님
        session(u3, b3, now.minus(Duration.ofDays(30)), 400);

        AdminStats stats = statsService.summary();

        assertThat(stats.activeWindowDays()).isEqualTo(7);
        assertThat(stats.activeUsers()).isEqualTo(2); // u1, u2 (u3 제외)
    }

    @Test
    @DisplayName("책/세션/총 독서시간/사용자당 평균을 집계한다")
    void booksSessionsAndReadingTime() {
        User u1 = user("u1@booktimer.com", "userone", true);
        User u2 = user("u2@booktimer.com", "usertwo", true);
        Book b1 = book(u1, "책1");
        Book b2 = book(u1, "책2");
        Book b3 = book(u2, "책3");

        Instant now = clock.instant();
        session(u1, b1, now.minus(Duration.ofDays(1)), 600);
        session(u1, b2, now.minus(Duration.ofDays(2)), 400);
        session(u2, b3, now.minus(Duration.ofDays(1)), 1000);

        AdminStats stats = statsService.summary();

        assertThat(stats.totalBooks()).isEqualTo(3);
        assertThat(stats.totalSessions()).isEqualTo(3);
        assertThat(stats.totalReadingSeconds()).isEqualTo(2000);
        // 평균 = 총 독서시간 / 가입자 수(USER) = 2000 / 2 = 1000
        assertThat(stats.avgReadingSecondsPerUser()).isEqualTo(1000);
    }

    @Test
    @DisplayName("가입자가 0명이면 평균은 0으로 안전하게 처리한다(0 나눗셈 회피)")
    void avgReadingTime_zeroUsers_isZero() {
        AdminStats stats = statsService.summary();

        assertThat(stats.totalUsers()).isEqualTo(0);
        assertThat(stats.avgReadingSecondsPerUser()).isEqualTo(0);
    }
}
