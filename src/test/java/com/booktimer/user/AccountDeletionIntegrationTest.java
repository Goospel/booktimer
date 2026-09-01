package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.garden.AuthorAffection;
import com.booktimer.garden.AuthorAffectionRepository;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportService;
import com.booktimer.session.ReadingGoalWaiver;
import com.booktimer.session.ReadingGoalWaiverRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.StudyDailyCheck;
import com.booktimer.session.StudyDailyCheckRepository;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.timer.ReadingGoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
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
    private StudyBookRepository studyBookRepository;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ReadingGoalService goalService;
    @Autowired
    private ReadingGoalWaiverRepository waiverRepository;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private StudySessionRepository studySessionRepository;
    @Autowired
    private StudyDailyCheckRepository studyDailyCheckRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthorAffectionRepository affectionRepository;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

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
    @DisplayName("미니앱 탈퇴: 세션·책·여백 글·팔로우를 가진 토스 계정도 FK 위반 없이 전부 삭제된다")
    void deleteTossVerifiedAccount_withChildren_succeeds() {
        User me = userRepository.saveAndFlush(
                withTossKey(User.ofOAuth("toss-quit@noreply.booktimer.app", "토스유저", "Asia/Seoul",
                        Role.USER, AuthProvider.TOSS), "uk-quit"));
        User friend = userRepository.saveAndFlush(
                User.of("friend@booktimer.com", passwordEncoder.encode("rawpw1234"), "친구", "Asia/Seoul", Role.USER));

        Book book = Book.register(me, "탈퇴 전에 읽던 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic(); // 여백은 공개 책에만 열린다(Story.of의 불변식)
        bookRepository.saveAndFlush(book);
        // 세션은 book을 FK 참조한다 — 책보다 뒤에 지우면 위반이다.
        sessionRepository.saveAndFlush(ReadingSession.start(me, Instant.now().minusSeconds(3600), book));
        // 여백의 글도 book을 FK 참조한다 — 책보다 먼저 지워야 한다.
        storyRepository.saveAndFlush(Story.of(me, "오늘로 마지막 기록.", book, "paper"));
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

    /**
     * 공부 원장(study_session)도 users를 FK 참조한다 — 독서와 <b>다른 테이블</b>이라 purge가 따로
     * 지워야 하고, 빠지면 <b>공부 기록을 가진 사람만</b> 탈퇴가 실패한다. 그 부류가 정확히
     * {@code author_affection} 누락으로 운영 27명 중 2명이 탈퇴 불가였던 자리다(2026-08-15).
     *
     * <p>mock 단위테스트는 FK를 모른다(T-023·T-029) — 실 스키마로만 잡힌다.
     */
    @Test
    @DisplayName("공부 기록(study_session)을 가진 사용자도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withStudySession_succeeds() {
        String email = "studyquit@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "공부하던이", "Asia/Seoul", Role.USER));
        StudySession session = StudySession.start(user, Instant.now().minusSeconds(3600));
        session.end(Instant.now().minusSeconds(1800));
        studySessionRepository.saveAndFlush(session);

        // study_session.user_id FK가 정리되지 않으면 flush 시 제약 위반.
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }

    /**
     * 공부 <b>일정 체크</b>(study_daily_check)도 같은 부류다 — 세션과 별개 테이블이라 purge에 한 줄이
     * 더 필요하고, 빠지면 「달력을 한 번이라도 눌러 본 사람만」 탈퇴가 실패한다.
     *
     * <p>mock으로는 영영 못 잡는다(FK를 모른다 — T-023·T-029). 1차 리뷰 W-3의 교훈 그대로 실 H2다.
     */
    @Test
    @DisplayName("공부 일정 체크(study_daily_check)를 가진 사용자도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withStudyDailyCheck_succeeds() {
        String email = "studycheckquit@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "체크하던이", "Asia/Seoul", Role.USER));
        studyDailyCheckRepository.saveAndFlush(
                StudyDailyCheck.of(user, LocalDate.now().minusDays(1), true));

        // study_daily_check.user_id FK가 정리되지 않으면 flush 시 제약 위반.
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }

    /**
     * 공부 <b>서재</b>(study_book)도 같은 부류다 — 독서 책({@code book})과 별개 테이블이라 purge에 한 줄이
     * 더 필요하고, 빠지면 「공부 책을 한 권이라도 담은 사람만」 탈퇴가 실패한다.
     *
     * <p>mock으로는 영영 못 잡는다(FK를 모른다 — T-023·T-029). study_session·study_daily_check와 같은 규율.
     */
    @Test
    @DisplayName("공부 책(study_book)을 가진 사용자도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withStudyBook_succeeds() {
        String email = "studybookquit@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "수험생", "Asia/Seoul", Role.USER));
        studyBookRepository.saveAndFlush(
                StudyBook.register(user, "정보처리기사 실기", null, null, null, null, null));

        // study_book.user_id FK가 정리되지 않으면 flush 시 제약 위반.
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

    @Test
    @DisplayName("작가에게 먹이를 준 사용자(author_affection)도 FK 위반 없이 탈퇴된다")
    void deleteAccount_withAuthorAffection_succeeds() {
        String email = "feeder@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "사육사", "Asia/Seoul", Role.USER));
        affectionRepository.saveAndFlush(AuthorAffection.create(user, "author-001"));

        // author_affection.user_id FK가 정리되지 않으면 flush 시 제약 위반.
        // 운영 실측(2026-08-15)에서 실제로 이 테이블 때문에 27명 중 2명이 탈퇴 불가였다.
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("탈퇴하면 그 사용자의 로그인 세션도 사라진다 — 계정 없는 유령 세션을 남기지 않는다")
    void deleteAccount_removesLoginSessions() {
        String email = "ghost@booktimer.com";
        User user = User.of(email, passwordEncoder.encode("rawpw1234"), "유령", "Asia/Seoul", Role.USER);
        user.assignLoginId("ghostid");
        userRepository.saveAndFlush(user);
        String sessionId = createSession(sessions, "ghostid");

        accountService.deleteAccount(email, "rawpw1234");

        // 세션은 users와 FK로 묶여 있지 않아 계정을 지워도 남는다 — 남으면 계정 없는 principal이
        // 인증된 채로 떠다닌다(운영 실측: testid 삭제 후 616건 잔존).
        assertThat(sessions.findById(sessionId)).isNull();
    }

    /** principal 이름으로 인덱싱된 세션을 실 저장소에 하나 만든다. */
    private static <S extends Session> String createSession(SessionRepository<S> repo, String principalName) {
        S session = repo.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
        repo.save(session);
        return session.getId();
    }
}
