package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportService;
import com.booktimer.session.ReadingGoalWaiver;
import com.booktimer.session.ReadingGoalWaiverRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.story.StoryView;
import com.booktimer.story.StoryViewRepository;
import com.booktimer.timer.ReadingGoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private StoryViewRepository storyViewRepository;
    @Autowired
    private FollowRepository followRepository;
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

    /**
     * 미니앱 탈퇴(토스 재인증 경로)의 FK 스모크 — 자식이 여러 갈래로 달린 계정을 실제로 지워 본다.
     *
     * <p>확인 게이트 자체는 mock 단위테스트가 덮는다. 여기서 재는 것은 <b>게이트를 통과한 뒤의 purge가
     * 실 스키마에서 끝까지 간다</b>는 것 하나다 — 세션·책·스토리·열람기록·팔로우가 전부 users(또는 서로)를
     * FK 참조하므로, 순서가 하나만 틀려도 flush 시점에 제약 위반으로 터진다. mock은 FK를 모른다(T-023·T-029).
     */
    @Test
    @DisplayName("미니앱 탈퇴: 세션·책·스토리·팔로우를 가진 토스 계정도 FK 위반 없이 전부 삭제된다")
    void deleteTossVerifiedAccount_withChildren_succeeds() {
        User me = userRepository.saveAndFlush(
                withTossKey(User.ofOAuth("toss-quit@noreply.booktimer.app", "토스유저", "Asia/Seoul",
                        Role.USER, AuthProvider.TOSS), "uk-quit"));
        User friend = userRepository.saveAndFlush(
                User.of("friend@booktimer.com", passwordEncoder.encode("rawpw1234"), "친구", "Asia/Seoul", Role.USER));

        Book book = Book.register(me, "탈퇴 전에 읽던 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic(); // 스토리는 공개 책에만 붙는다(Story.of의 불변식)
        bookRepository.saveAndFlush(book);
        // 세션은 book을 FK 참조한다 — 책보다 뒤에 지우면 위반이다.
        sessionRepository.saveAndFlush(ReadingSession.start(me, Instant.now().minusSeconds(3600), book));
        // 스토리도 book 참조 + 열람기록이 스토리를 참조한다(3단 사슬).
        Story story = storyRepository.saveAndFlush(Story.of(me, "오늘로 마지막 기록.", book, "paper"));
        storyViewRepository.saveAndFlush(StoryView.of(story, friend));
        // 팔로우는 양방향 — 내가 건 것과 남이 나에게 건 것 둘 다 정리돼야 한다.
        followRepository.saveAndFlush(Follow.of(me, friend));
        followRepository.saveAndFlush(Follow.of(friend, me));

        assertThatCode(() -> {
            accountService.deleteTossVerifiedAccount(me, "uk-quit");
            assertThat(userRepository.findByTossUserKey("uk-quit")).isEmpty();
        }).doesNotThrowAnyException();

        // 남의 계정은 그대로 — 탈퇴가 이웃까지 쓸어가지 않는다.
        assertThat(userRepository.findByEmail("friend@booktimer.com")).isPresent();
    }

    /** {@code toss_user_key}는 생성자로 못 넣는다 — 웹 연결과 같은 진입점({@code linkTossUserKey})으로 채운다. */
    private User withTossKey(User user, String userKey) {
        user.linkTossUserKey(userKey);
        return user;
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
