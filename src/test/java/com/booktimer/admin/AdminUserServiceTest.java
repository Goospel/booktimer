package com.booktimer.admin;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 데이터 조회 서비스 테스트 (admin-data-lookup-design §4).
 *
 * <p>전부 <b>읽기 전용</b>: 사용자 목록(페이징·login_id/nickname 검색·email 마스킹)과 드릴다운
 * (타이머 설정·최근 N세션·책장 요약)을 조립한다. 비밀번호 해시는 DTO에 담지 않는다.
 */
@SpringBootTest
@Transactional
class AdminUserServiceTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private ReadingTimerRepository timerRepository;
    @Autowired
    private Clock clock;

    private User user(String email, String loginId, String nickname) {
        User u = User.of(email, "hash", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        u.completeOnboarding();
        return userRepository.save(u);
    }

    private Book book(User owner, String title, BookStatus status, boolean isPublic) {
        Book b = Book.register(owner, title, null, null, null, null, null, status);
        if (isPublic) {
            b.makePublic();
        }
        return bookRepository.save(b);
    }

    private void session(User owner, Book b, Instant startedAt, long durationSec) {
        ReadingSession s = ReadingSession.start(owner, startedAt, b);
        s.end(startedAt.plusSeconds(durationSec));
        sessionRepository.save(s);
    }

    // --- 목록 ---

    @Test
    @DisplayName("목록: email은 마스킹되어 실리고, 원문도 함께 담긴다(토글용). 비밀번호 해시는 없다")
    void list_masksEmail() {
        user("goospel@gmail.com", "goospel", "구스펠");

        Page<AdminUserRow> page = adminUserService.listUsers(null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        AdminUserRow row = page.getContent().get(0);
        assertThat(row.loginId()).isEqualTo("goospel");
        assertThat(row.emailMasked()).isEqualTo("g***@gmail.com");
        assertThat(row.email()).isEqualTo("goospel@gmail.com");
        assertThat(row.provider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    @DisplayName("목록 검색: q가 login_id 또는 nickname 부분일치하는 사용자만(대소문자 무시), email로는 검색 안 함")
    void list_searchByLoginIdOrNickname() {
        user("alpha@booktimer.com", "alphauser", "독서가");
        user("beta@booktimer.com", "betauser", "구스펠");
        user("gamma@booktimer.com", "gamma", "다른이");

        // login_id 부분일치
        Page<AdminUserRow> byLoginId = adminUserService.listUsers("user", PageRequest.of(0, 20));
        assertThat(byLoginId.getContent()).extracting(AdminUserRow::loginId)
                .containsExactlyInAnyOrder("alphauser", "betauser");

        // nickname 부분일치
        Page<AdminUserRow> byNickname = adminUserService.listUsers("구스", PageRequest.of(0, 20));
        assertThat(byNickname.getContent()).extracting(AdminUserRow::loginId)
                .containsExactly("betauser");

        // email로는 검색되지 않음
        Page<AdminUserRow> byEmail = adminUserService.listUsers("gamma@booktimer", PageRequest.of(0, 20));
        assertThat(byEmail.getContent()).isEmpty();
    }

    @Test
    @DisplayName("목록 페이징: size를 넘으면 페이지로 잘린다")
    void list_paging() {
        for (int i = 0; i < 5; i++) {
            user("u" + i + "@booktimer.com", "useridx" + i, "닉" + i);
        }

        Page<AdminUserRow> first = adminUserService.listUsers(null, PageRequest.of(0, 2));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalPages()).isEqualTo(3);
    }

    // --- 드릴다운 ---

    @Test
    @DisplayName("드릴다운: 타이머 설정 + 최근 N세션 + 책장 요약을 조립한다")
    void detail_assembles() {
        User u = user("reader@booktimer.com", "readerone", "독서왕");

        ReadingTimer timer = ReadingTimer.startFor(u, 3600, 36000, today());
        timerRepository.save(timer);

        Book b1 = book(u, "읽는책", BookStatus.READING, true);
        book(u, "원하는책", BookStatus.WANT_TO_READ, false);
        book(u, "완독책", BookStatus.FINISHED, false);

        Instant now = clock.instant();
        session(u, b1, now.minus(Duration.ofDays(1)), 600);
        session(u, b1, now.minus(Duration.ofHours(2)), 300);

        AdminUserDetail detail = adminUserService.userDetail("readerone").orElseThrow();

        assertThat(detail.loginId()).isEqualTo("readerone");
        assertThat(detail.emailMasked()).isEqualTo("r***@booktimer.com");
        assertThat(detail.timer()).isNotNull();
        assertThat(detail.timer().dailyIncrementSeconds()).isEqualTo(3600);
        assertThat(detail.recentSessions()).hasSize(2);
        // 책장 요약
        assertThat(detail.bookshelf().total()).isEqualTo(3);
        assertThat(detail.bookshelf().wantToRead()).isEqualTo(1);
        assertThat(detail.bookshelf().reading()).isEqualTo(1);
        assertThat(detail.bookshelf().finished()).isEqualTo(1);
        assertThat(detail.bookshelf().publicCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("드릴다운 최근 세션은 startedAt 내림차순으로 최대 N개만(N 초과분은 잘림)")
    void detail_recentSessionsLimited() {
        User u = user("reader@booktimer.com", "readerone", "독서왕");
        Book b = book(u, "책", BookStatus.READING, false);

        Instant now = clock.instant();
        // 한도(10)보다 많은 12개 — 가장 최근 10개만 남아야 함
        for (int i = 0; i < 12; i++) {
            session(u, b, now.minus(Duration.ofHours(i + 1)), 60);
        }

        AdminUserDetail detail = adminUserService.userDetail("readerone").orElseThrow();

        assertThat(detail.recentSessions()).hasSize(AdminUserService.RECENT_SESSION_LIMIT);
        // 첫 원소가 가장 최근(1시간 전), 내림차순
        assertThat(detail.recentSessions().get(0).startedAt())
                .isAfter(detail.recentSessions().get(1).startedAt());
    }

    @Test
    @DisplayName("드릴다운: 타이머가 없으면(온보딩 전 등) timer는 null로 폴백한다")
    void detail_noTimer() {
        user("notimer@booktimer.com", "notimer", "무타이머");

        AdminUserDetail detail = adminUserService.userDetail("notimer").orElseThrow();

        assertThat(detail.timer()).isNull();
        assertThat(detail.recentSessions()).isEmpty();
        assertThat(detail.bookshelf().total()).isEqualTo(0);
    }

    @Test
    @DisplayName("드릴다운: 없는 loginId면 빈 Optional(컨트롤러가 404로 변환)")
    void detail_unknown_empty() {
        Optional<AdminUserDetail> detail = adminUserService.userDetail("nosuchid");
        assertThat(detail).isEmpty();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
    }
}
