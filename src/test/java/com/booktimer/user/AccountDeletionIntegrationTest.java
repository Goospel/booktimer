package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportService;
import com.booktimer.session.ReadingGoalWaiver;
import com.booktimer.session.ReadingGoalWaiverRepository;
import com.booktimer.timer.ReadingGoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 회원 탈퇴 통합 테스트 (실제 빈 + H2) — 책을 가진 사용자도 FK 위반 없이 탈퇴되는지 본다.
 *
 * <p>book은 user_id로 users를 FK 참조한다(cascade 없음). purge가 book을 안 지우면 유저 삭제 시
 * 제약 위반이 난다 — mock 단위테스트는 못 잡으므로 실제 스키마로 검증한다.
 */
@SpringBootTest
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ReadingGoalService goalService;
    @Autowired
    private ReadingGoalWaiverRepository waiverRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("책을 가진 사용자가 탈퇴해도 FK 위반 없이 계정·책이 삭제된다")
    void deleteAccount_withBooks_succeeds() {
        String email = "owner@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "책주인", "Asia/Seoul", Role.USER));
        bookRepository.saveAndFlush(
                Book.register(user, "내 책", null, null, null, null, null, BookStatus.READING));

        // book FK가 정리되지 않으면 여기서 제약 위반 예외가 난다(쿼리 시 flush 강제).
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("신고를 했거나 당한 사용자도 FK 위반 없이 탈퇴된다(report 정리)")
    void deleteAccount_withReports_succeeds() {
        User reporter = userRepository.saveAndFlush(
                User.of("reporter@booktimer.com", passwordEncoder.encode("rawpw1234"), "신고자", "Asia/Seoul", Role.USER));
        User target = userRepository.saveAndFlush(
                User.of("victim@booktimer.com", passwordEncoder.encode("rawpw1234"), "피신고", "Asia/Seoul", Role.USER));
        reportService.report(reporter, target, ReportReason.SPAM, null);

        // report.reporter_id / reported_id 가 users 를 FK 참조한다 — purge 가 정리 안 하면 위반.
        assertThatCode(() -> {
            accountService.deleteAccount("reporter@booktimer.com", "rawpw1234"); // 신고한 쪽 탈퇴
            accountService.deleteAccount("victim@booktimer.com", "rawpw1234");   // 신고당한 쪽 탈퇴
            assertThat(userRepository.findByEmail("reporter@booktimer.com")).isEmpty();
            assertThat(userRepository.findByEmail("victim@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("목표 변경 이력(reading_goal_change)을 가진 사용자도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withGoalChangeHistory_succeeds() {
        String email = "goal@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "목표러", "Asia/Seoul", Role.USER));
        goalService.record(user, 1800L);   // 실유저가 온보딩/설정에서 행을 만드는 바로 그 진입점 (N-055 정신)

        // reading_goal_change FK가 정리되지 않으면 flush 시 제약 위반 → findByEmail 쿼리가 flush를 강제한다.
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("밀린 하루 용서권(reading_goal_waiver)을 가진 사용자도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withGoalWaiver_succeeds() {
        String email = "waiver@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "용서받은이", "Asia/Seoul", Role.USER));
        waiverRepository.saveAndFlush(ReadingGoalWaiver.create(
                user, LocalDate.now().minusDays(2), LocalDate.now()));

        // reading_goal_waiver.user_id FK가 정리되지 않으면 flush 시 제약 위반(T-029 계열).
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }
}
