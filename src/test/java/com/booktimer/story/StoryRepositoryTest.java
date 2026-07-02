package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.config.JpaConfig;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화 — 없으면 INSERT 시 NOT NULL 위반
class StoryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private BookRepository bookRepository;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    /** 공개 핸들 미설정(login_id null) — OAuth 온보딩 전 정상 존재하는 null-state 유저 (N-055). */
    private User userWithoutLoginId(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "온보딩전", "Asia/Seoul", Role.USER));
    }

    private User admin(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "운영자", "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void follow(User follower, User followee) {
        followRepository.save(Follow.of(follower, followee));
    }

    /** 원하는 생성 시각의 스토리 — @CreatedDate가 저장 시각으로 채우므로 벌크 갱신으로 되돌린다. */
    private Story storyAt(User author, String text, Instant createdAt) {
        Story story = storyRepository.save(Story.of(author, text, null, null));
        em.createQuery("update Story s set s.createdAt = :t where s.id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", story.getId())
                .executeUpdate();
        em.clear();
        return story;
    }

    @Test
    @DisplayName("feedOf: 팔로우한 작성자의 스토리만 — 비팔로우·내 스토리는 제외")
    void feedOf_returnsOnlyFollowedAuthors() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User followed = user("followed@booktimer.com", "followed");
        User stranger = user("stranger@booktimer.com", "stranger");
        follow(viewer, followed);
        storyAt(followed, "팔로잉 문장", NOW.minusSeconds(60));
        storyAt(stranger, "남 문장", NOW.minusSeconds(60));
        storyAt(viewer, "내 문장", NOW.minusSeconds(60));

        List<Story> feed = storyRepository.feedOf(viewer, CUTOFF);

        assertThat(feed).extracting(Story::getText).containsExactly("팔로잉 문장");
    }

    @Test
    @DisplayName("feedOf 24h 경계: 정확히 24h 지난 스토리는 제외, 24h−1s는 포함")
    void feedOf_expiryBoundary() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        follow(viewer, author);
        storyAt(author, "정확히 24h", CUTOFF);                  // 나이 = 정확히 24h → 만료
        storyAt(author, "24h-1s", CUTOFF.plusSeconds(1));       // 나이 = 24h−1s → 활성

        List<Story> feed = storyRepository.feedOf(viewer, CUTOFF);

        assertThat(feed).extracting(Story::getText).containsExactly("24h-1s");
    }

    @Test
    @DisplayName("feedOf: ADMIN·login_id null 작성자의 스토리는 팔로우 중이어도 제외 (노출 불변식, N-055)")
    void feedOf_excludesAdminAndNullLoginIdAuthors() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User adminAuthor = admin("admin@booktimer.com", "adminid");
        User nullHandle = userWithoutLoginId("pending@booktimer.com");
        follow(viewer, adminAuthor);
        follow(viewer, nullHandle);
        storyAt(adminAuthor, "운영자 문장", NOW.minusSeconds(60));
        storyAt(nullHandle, "온보딩전 문장", NOW.minusSeconds(60));

        List<Story> feed = storyRepository.feedOf(viewer, CUTOFF);

        assertThat(feed).isEmpty();
    }

    @Test
    @DisplayName("feedOf 정렬: 작성자 id asc → 그 안에서 작성순 asc")
    void feedOf_ordersByAuthorIdThenCreatedAtAsc() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User first = user("first@booktimer.com", "first");   // 먼저 저장 → 더 작은 id
        User second = user("second@booktimer.com", "second");
        follow(viewer, first);
        follow(viewer, second);
        storyAt(second, "b-늦음", NOW.minusSeconds(10));
        storyAt(first, "a-늦음", NOW.minusSeconds(20));
        storyAt(first, "a-이름", NOW.minusSeconds(300));
        storyAt(second, "b-이름", NOW.minusSeconds(400));

        List<Story> feed = storyRepository.feedOf(viewer, CUTOFF);

        assertThat(feed).extracting(Story::getText)
                .containsExactly("a-이름", "a-늦음", "b-이름", "b-늦음");
    }

    // --- fetch 초기화 검증 (flush/clear 필수 — 1차 캐시가 살아있으면 가짜 GREEN) ---
    // 피드·책방 조립이 카드마다 book.isPublic()·제목·표지를 읽으므로(책 라벨 표시 시점 재검사, §13.2),
    // book이 LAZY로 남으면 홈 진입마다 스토리 수만큼 SELECT가 추가된다(N+1 — 핫패스).

    @Test
    @DisplayName("feedOf: 작성자·첨부 책이 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void feedOf_initializesAuthorAndBook() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        follow(viewer, author);
        Book book = Book.register(author, "첨부 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);
        storyRepository.save(Story.of(author, "책 첨부 문장", book, null));

        em.flush();
        em.clear();

        List<Story> feed = storyRepository.feedOf(viewer, CUTOFF);

        assertThat(feed).hasSize(1);
        assertThat(Hibernate.isInitialized(feed.get(0).getUser())).isTrue();
        assertThat(Hibernate.isInitialized(feed.get(0).getBook())).isTrue();
    }

    @Test
    @DisplayName("findByUserAndCreatedAtAfterOrderByCreatedAtAsc: 첨부 책이 즉시 초기화된다 (N+1 없음)")
    void myActiveStories_initializesBook() {
        User me = user("me@booktimer.com", "meuser");
        Book book = Book.register(me, "내 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);
        storyRepository.save(Story.of(me, "책 첨부 문장", book, null));

        em.flush();
        em.clear();

        List<Story> mine = storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF);

        assertThat(mine).hasSize(1);
        assertThat(Hibernate.isInitialized(mine.get(0).getBook())).isTrue();
    }

    @Test
    @DisplayName("findByUserAndCreatedAtAfterOrderByCreatedAtAsc: 내 활성 스토리만 작성순으로 — 만료 제외")
    void myActiveStories_excludesExpired_ordersAsc() {
        User me = user("me@booktimer.com", "meuser");
        storyAt(me, "만료됨", CUTOFF.minusSeconds(1));
        storyAt(me, "둘째", NOW.minusSeconds(10));
        storyAt(me, "첫째", NOW.minusSeconds(600));

        List<Story> mine = storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF);

        assertThat(mine).extracting(Story::getText).containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("countByUserAndCreatedAtAfter: 활성분만 센다 — 활성 상한 20 게이트용")
    void activeCount_excludesExpired() {
        User me = user("me@booktimer.com", "meuser");
        storyAt(me, "만료됨", CUTOFF.minusSeconds(1));
        storyAt(me, "활성1", NOW.minusSeconds(10));
        storyAt(me, "활성2", NOW.minusSeconds(20));

        long count = storyRepository.countByUserAndCreatedAtAfter(me, CUTOFF);

        assertThat(count).isEqualTo(2);
    }
}
