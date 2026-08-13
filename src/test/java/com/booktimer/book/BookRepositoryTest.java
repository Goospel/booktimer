package com.booktimer.book;

import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.config.JpaConfig;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * BookRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>{@link BookRepository#countPublicByUsers}의 배치 집계를 검증한다 — 사용자 행 목록 조립의
 * 행당 {@code countByUserAndVisibility} N+1을 단일 group by로 대체한 쿼리. 특히 <b>공개책 0권
 * 사용자는 결과 행이 없다</b>는 의미를 못 박는다(호출부의 0-디폴트 경로가 이 누락에 의존).
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private BlockRepository blockRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    /** 공개 핸들(login_id)을 가진 일반 사용자 — 추천 후보 적격(login_id null 가드 통과). */
    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(b);
    }

    private void privateBook(User owner, String title) { // 기본 visibility=PRIVATE
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    /** isbn·상태·공개범위를 지정해 책을 저장(같은 책 추천 신호 테스트용). */
    private void book(User owner, String isbn, BookStatus status, BookVisibility visibility) {
        Book b = Book.register(owner, "책-" + isbn, null, isbn, null, null, null, status);
        b.changeVisibility(visibility);
        bookRepository.save(b);
    }

    @Test
    @DisplayName("countPublicByUsers: 여러 사용자의 PUBLIC 책 수를 한 번에 group 집계한다")
    void groupsPublicCountsPerUser() {
        User a = persistedUser("a@booktimer.com");
        User b = persistedUser("b@booktimer.com");
        publicBook(a, "공개1");
        publicBook(a, "공개2");
        publicBook(b, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(a.getId(), b.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId, UserPublicBookCount::getPublicCount)
                .containsExactlyInAnyOrder(tuple(a.getId(), 2L), tuple(b.getId(), 1L));
    }

    @Test
    @DisplayName("countPublicByUsers: 공개책 0권 사용자는 결과 행이 없다 (0-디폴트가 이 누락에 의존)")
    void zeroPublicBookUserIsAbsent() {
        User a = persistedUser("a@booktimer.com");
        User c = persistedUser("c@booktimer.com"); // 공개책 없음
        publicBook(a, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(a.getId(), c.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId).doesNotContain(c.getId());
    }

    @Test
    @DisplayName("countPublicByUsers: PRIVATE 책만 가진 사용자는 집계에서 제외된다")
    void excludesPrivateBooks() {
        User d = persistedUser("d@booktimer.com");
        privateBook(d, "비공개1");
        privateBook(d, "비공개2");
        publicBook(d, "공개1");

        List<UserPublicBookCount> rows =
                bookRepository.countPublicByUsers(List.of(d.getId()));

        assertThat(rows).extracting(UserPublicBookCount::getUserId, UserPublicBookCount::getPublicCount)
                .containsExactly(tuple(d.getId(), 1L)); // PRIVATE 2권은 안 세고 PUBLIC 1권만
    }

    // --- 친구 추천: 내 isbn (findReadIsbnsByUser) ---

    @Test
    @DisplayName("findReadIsbnsByUser: 읽는중∪완독 책의 isbn만 (읽고싶음·null isbn 제외, distinct)")
    void readIsbns_onlyReadWithIsbn() {
        User u = user("u@booktimer.com", "uureader");
        book(u, "9788900000001", BookStatus.FINISHED, BookVisibility.PRIVATE); // 내 책이라 PRIVATE도 포함
        book(u, "9788900000002", BookStatus.READING, BookVisibility.PUBLIC);
        book(u, "9788900000003", BookStatus.WANT_TO_READ, BookVisibility.PUBLIC); // 읽고싶음 → 제외
        bookRepository.save( // isbn null → 동일성 키 없음 → 제외
                Book.register(u, "isbn없는책", null, null, null, null, null, BookStatus.FINISHED));

        assertThat(bookRepository.findReadIsbnsByUser(u.getId()))
                .containsExactlyInAnyOrder("9788900000001", "9788900000002");
    }

    // --- 친구 추천: 같은 책 (findCoReadCandidates) ---

    @Test
    @DisplayName("findCoReadCandidates: 내 isbn과 겹치는 PUBLIC 읽음 책 보유자를 겹친 권수 desc로")
    void coRead_bySharedBookCountDesc() {
        User viewer = user("v@booktimer.com", "viewer");
        book(viewer, "ISBNA", BookStatus.FINISHED, BookVisibility.PRIVATE); // 내 입력은 PRIVATE도 OK
        book(viewer, "ISBNB", BookStatus.READING, BookVisibility.PUBLIC);

        User two = user("t@booktimer.com", "twomatch"); // A·B 둘 다 PUBLIC → 겹침 2
        book(two, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        book(two, "ISBNB", BookStatus.READING, BookVisibility.PUBLIC);
        User one = user("o@booktimer.com", "onematch"); // A만 → 겹침 1
        book(one, "ISBNA", BookStatus.READING, BookVisibility.PUBLIC);

        List<CoReadCount> rows = bookRepository.findCoReadCandidates(
                viewer.getId(), List.of("ISBNA", "ISBNB"), PageRequest.of(0, 10));

        assertThat(rows).extracting(CoReadCount::getUserId, CoReadCount::getSharedBookCount)
                .containsExactly(tuple(two.getId(), 2L), tuple(one.getId(), 1L));
    }

    @Test
    @DisplayName("findCoReadCandidates 누수 가드: PRIVATE 겹침·읽고싶음만·login_id null·차단(양방향)·본인 제외")
    void coRead_guards() {
        User viewer = user("v@booktimer.com", "viewer");
        book(viewer, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);

        User privateOnly = user("p@booktimer.com", "privonly"); // 겹친 책이 PRIVATE만 → 비공개 누수 가드
        book(privateOnly, "ISBNA", BookStatus.FINISHED, BookVisibility.PRIVATE);
        User wantOnly = user("w@booktimer.com", "wantonly"); // 겹친 책이 읽고싶음만 → 제외
        book(wantOnly, "ISBNA", BookStatus.WANT_TO_READ, BookVisibility.PUBLIC);
        User nullUser = persistedUser("n@booktimer.com"); // login_id null → 제외(N-055)
        book(nullUser, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        User blocked = user("bk@booktimer.com", "blockedmatch"); // viewer가 차단
        book(blocked, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        User blocker = user("br@booktimer.com", "blockermatch"); // viewer를 차단(역방향)
        book(blocker, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        blockRepository.save(Block.of(viewer, blocked));
        blockRepository.save(Block.of(blocker, viewer));
        User ok = user("o@booktimer.com", "okmatch"); // 적격
        book(ok, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);

        List<CoReadCount> rows = bookRepository.findCoReadCandidates(
                viewer.getId(), List.of("ISBNA"), PageRequest.of(0, 10));

        assertThat(rows).extracting(CoReadCount::getUserId)
                .containsExactly(ok.getId()) // 본인(viewer)도 self 제외라 안 잡힘
                .doesNotContain(privateOnly.getId(), wantOnly.getId(), nullUser.getId(),
                        blocked.getId(), blocker.getId(), viewer.getId());
    }

    @Test
    @DisplayName("findCoReadCandidates: 내가 이미 팔로우한 사람은 제외 (새 사람만 추천)")
    void coRead_excludesAlreadyFollowed() {
        User viewer = user("v@booktimer.com", "viewer");
        book(viewer, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        User followed = user("f@booktimer.com", "followedmatch");
        book(followed, "ISBNA", BookStatus.FINISHED, BookVisibility.PUBLIC);
        followRepository.save(Follow.of(viewer, followed));

        List<CoReadCount> rows = bookRepository.findCoReadCandidates(
                viewer.getId(), List.of("ISBNA"), PageRequest.of(0, 10));

        assertThat(rows).extracting(CoReadCount::getUserId).doesNotContain(followed.getId());
    }

    // --- 홈 소식 피드 (feedFinished / feedStarted) ---
    // StoryRepository.feedOf 미러: 팔로우 theta 조인 · PUBLIC only · ADMIN 제외 · login_id null 제외(N-055).
    // 차단 필터는 쿼리에 없다 — "팔로우 존재 ⇒ 차단 없음" write-시점 불변식이 보장하고
    // BookFeedBlockInvariantTest가 행동으로 못 박는다.

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(14));
    private static final Instant RECENT = NOW.minus(Duration.ofDays(1));
    private static final Instant OLDER = NOW.minus(Duration.ofDays(5));
    private static final Instant STALE = NOW.minus(Duration.ofDays(15)); // 창 밖

    /** 시작·완독 시각을 스탬프한 책. startedAt/finishedAt이 null이면 그 전이를 밟지 않는다. */
    private Book stampedBook(User owner, String title, BookVisibility visibility,
                             Instant startedAt, Instant finishedAt) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.WANT_TO_READ);
        b.changeVisibility(visibility);
        if (startedAt != null) {
            b.changeStatus(BookStatus.READING, startedAt);
        }
        if (finishedAt != null) {
            b.changeStatus(BookStatus.FINISHED, finishedAt);
        }
        return bookRepository.save(b);
    }

    private User followedBy(User viewer, String email, String loginId) {
        User followee = user(email, loginId);
        followRepository.save(Follow.of(viewer, followee));
        return followee;
    }

    @Test
    @DisplayName("feedFinished: 팔로우한 사람의 PRIVATE 완독 책은 피드에 안 실린다 (PUBLIC-only 불변식)")
    void feedFinished_excludesPrivateBooks() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "공개완독", BookVisibility.PUBLIC, null, RECENT);
        stampedBook(followee, "비공개완독", BookVisibility.PRIVATE, null, RECENT);

        assertThat(bookRepository.feedFinished(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("공개완독");
    }

    @Test
    @DisplayName("feedFinished: 비팔로우·ADMIN·login_id null(N-055) 소유자의 PUBLIC 완독 책은 제외된다")
    void feedFinished_excludesNonFollowedAdminAndNullLoginId() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "적격", BookVisibility.PUBLIC, null, RECENT);

        User stranger = user("b@booktimer.com", "bravo"); // 팔로우 안 함
        stampedBook(stranger, "비팔로우", BookVisibility.PUBLIC, null, RECENT);

        User admin = User.of("admin@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv",
                "관리자", "Asia/Seoul", Role.ADMIN);
        admin.assignLoginId("adminuser");
        admin = userRepository.save(admin);
        followRepository.save(Follow.of(viewer, admin));
        stampedBook(admin, "관리자", BookVisibility.PUBLIC, null, RECENT);

        User noHandle = persistedUser("n@booktimer.com"); // login_id null — 온보딩 전
        followRepository.save(Follow.of(viewer, noHandle));
        stampedBook(noHandle, "핸들없음", BookVisibility.PUBLIC, null, RECENT);

        assertThat(bookRepository.feedFinished(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("적격");
    }

    @Test
    @DisplayName("feedFinished: 창(cutoff) 밖 완독은 제외하고, 최신 완독 순(desc)으로 준다")
    void feedFinished_windowAndOrder() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "예전", BookVisibility.PUBLIC, null, OLDER);
        stampedBook(followee, "최근", BookVisibility.PUBLIC, null, RECENT);
        stampedBook(followee, "창밖", BookVisibility.PUBLIC, null, STALE);

        assertThat(bookRepository.feedFinished(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("최근", "예전");
    }

    @Test
    @DisplayName("feedStarted: 시작 시각이 없는 기존 책(백필 안 함)은 시작 피드에 안 뜬다")
    void feedStarted_excludesUnstampedLegacyBooks() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "스탬프됨", BookVisibility.PUBLIC, RECENT, null);
        // 시작 시각 없이 READING으로 바로 등록된 레거시 책
        Book legacy = Book.register(followee, "레거시", null, null, null, null, null, BookStatus.READING);
        legacy.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(legacy);

        assertThat(bookRepository.feedStarted(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("스탬프됨");
    }

    @Test
    @DisplayName("feedStarted: PRIVATE 제외 · 창 밖 제외 · 최신 시작 순(desc)")
    void feedStarted_publicOnlyWindowAndOrder() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "예전시작", BookVisibility.PUBLIC, OLDER, null);
        stampedBook(followee, "최근시작", BookVisibility.PUBLIC, RECENT, null);
        stampedBook(followee, "비공개시작", BookVisibility.PRIVATE, RECENT, null);
        stampedBook(followee, "창밖시작", BookVisibility.PUBLIC, STALE, null);

        assertThat(bookRepository.feedStarted(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("최근시작", "예전시작");
    }

    @Test
    @DisplayName("feedStarted: 완독한 책도 시작 이벤트는 남는다 (시작→완독 두 줄이 시간순 공존)")
    void feedStarted_keepsFinishedBooks() {
        User viewer = user("v@booktimer.com", "viewer");
        User followee = followedBy(viewer, "a@booktimer.com", "alpha");
        stampedBook(followee, "시작후완독", BookVisibility.PUBLIC, OLDER, RECENT);

        assertThat(bookRepository.feedStarted(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("시작후완독");
        assertThat(bookRepository.feedFinished(viewer, CUTOFF))
                .extracting(Book::getTitle)
                .containsExactly("시작후완독");
    }
}
