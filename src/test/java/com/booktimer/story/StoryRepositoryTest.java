package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.config.JpaConfig;
import com.booktimer.follow.Follow;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화 — 없으면 INSERT 시 NOT NULL 위반
class StoryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
    private static final Pageable ALL = PageRequest.of(0, 100);

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private Book publicBookOf(User owner, String title) {
        Book book = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        return bookRepository.save(book);
    }

    /** 원하는 생성 시각의 글 — @CreatedDate가 저장 시각으로 채우므로 벌크 갱신으로 되돌린다. */
    private Story storyAt(User author, Book book, String text, Instant createdAt) {
        Story story = storyRepository.save(Story.of(author, text, book, null));
        em.createQuery("update Story s set s.createdAt = :t where s.id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", story.getId())
                .executeUpdate();
        em.clear();
        return story;
    }

    @Test
    @DisplayName("책별 목록: 그 책의 여백만 — 같은 사람의 다른 책 글은 섞이지 않는다")
    void marginList_isScopedToOneBook() {
        User me = user("me@booktimer.com", "meuser");
        Book target = publicBookOf(me, "이 책");
        Book other = publicBookOf(me, "저 책");
        storyAt(me, target, "이 책의 글", NOW.minusSeconds(60));
        storyAt(me, other, "저 책의 글", NOW.minusSeconds(30));

        List<Story> entries = storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(me, target, ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("이 책의 글");
    }

    @Test
    @DisplayName("책별 목록: 최신순 — 30일 전 글도 남는다(시간 만료 없음)")
    void marginList_isNewestFirst() {
        User me = user("me@booktimer.com", "meuser");
        Book book = publicBookOf(me, "책");
        storyAt(me, book, "옛것", NOW.minusSeconds(2_592_000L)); // 30일 전
        storyAt(me, book, "최신", NOW.minusSeconds(10));
        storyAt(me, book, "중간", NOW.minusSeconds(600));

        List<Story> entries = storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(me, book, ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("최신", "중간", "옛것");
    }

    @Test
    @DisplayName("책별 목록: 같은 시각이면 id 내림차순으로 갈린다 — 상한 경계가 호출마다 흔들리지 않게")
    void marginList_tieBreaksById() {
        User me = user("me@booktimer.com", "meuser");
        Book book = publicBookOf(me, "책");
        Story older = storyAt(me, book, "먼저 저장", NOW.minusSeconds(60));
        Story newer = storyAt(me, book, "나중 저장", NOW.minusSeconds(60)); // 같은 createdAt

        List<Story> entries = storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(me, book, ALL);

        assertThat(entries).extracting(Story::getId).containsExactly(newer.getId(), older.getId());
    }

    // ── feedRecent 가시성 (§5-1 ⓒ) ────────────────────────────────────────────
    // 앵커(반전 아님): 쿼리가 이미 표시 시점에 b.visibility=PUBLIC을 재검사한다. 비공개 책에도 글을
    // 쓸 수 있게 된 뒤로는 이 재검사가 「백업」이 아니라 소식 피드의 <b>주 방어</b>다 — 회귀하면 남의
    // 비공개 메모가 팔로워 홈에 실린다.

    private void follows(User follower, User followee) {
        em.persist(Follow.of(follower, followee));
        em.flush();
    }

    @Test
    @DisplayName("소식 피드: 글을 남긴 뒤 책을 비공개로 돌리면 빠진다 — 공개 책 글은 남는다(양성 대조군)")
    void feedRecent_excludesStoriesOnBooksTurnedPrivate() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User followee = user("followee@booktimer.com", "followee");
        follows(viewer, followee);
        Book stayPublic = publicBookOf(followee, "공개로 남는 책");
        Book turnedPrivate = publicBookOf(followee, "나중에 비공개가 될 책");
        storyAt(followee, stayPublic, "보여야 하는 글", NOW.minusSeconds(60));
        storyAt(followee, turnedPrivate, "새면 안 되는 글", NOW.minusSeconds(30));
        bookRepository.findById(turnedPrivate.getId()).orElseThrow().makePrivate();
        em.flush();
        em.clear();

        List<Story> feed = storyRepository.feedRecent(viewer, NOW.minusSeconds(3600));

        assertThat(feed).extracting(Story::getText).containsExactly("보여야 하는 글");
    }

    @Test
    @DisplayName("소식 피드: 처음부터 비공개인 책의 글도 빠진다 — 비공개 책 여백은 소유자 전용 메모")
    void feedRecent_excludesStoriesBornOnPrivateBooks() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User followee = user("followee@booktimer.com", "followee");
        follows(viewer, followee);
        Book secret = bookRepository.save(
                Book.register(followee, "비공개 책", null, null, null, null, null, BookStatus.READING));
        storyAt(followee, secret, "나만 보는 메모", NOW.minusSeconds(30));

        List<Story> feed = storyRepository.feedRecent(viewer, NOW.minusSeconds(3600));

        assertThat(feed).isEmpty();
    }

    @Test
    @DisplayName("책별 목록: Pageable 상한이 최신부터 자른다")
    void marginList_limitTakesNewest() {
        User me = user("me@booktimer.com", "meuser");
        Book book = publicBookOf(me, "책");
        storyAt(me, book, "오래된", NOW.minusSeconds(300));
        storyAt(me, book, "중간", NOW.minusSeconds(200));
        storyAt(me, book, "최신", NOW.minusSeconds(100));

        List<Story> entries = storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(
                me, book, PageRequest.of(0, 2));

        assertThat(entries).extracting(Story::getText).containsExactly("최신", "중간");
    }
}
