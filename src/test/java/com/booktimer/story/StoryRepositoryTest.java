package com.booktimer.story;

import com.booktimer.block.Block;
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
import static org.assertj.core.api.Assertions.tuple;

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

    // ── 책축(isbn13) 목록 — 「함께 걸기」 개방의 노출 게이트 (T-R1~R3·R8) ──────────
    // 이 쿼리는 assertVisible의 미러다: 노출 = book PUBLIC ∧ (팔로워 ∨ shared). 팔로우를 안 보는
    // 대신 shared를 보므로, 「책 상위 AND」가 깨지는 순간 남의 비공개 메모가 낯선 사람에게 샌다.

    private static final String ISBN = "9791168340084";

    private User adminUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "관리자", "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    /** 온보딩 전 사용자 — 공개 핸들이 없다(N-055 null-state 픽스처). */
    private User handlelessUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "익명", "Asia/Seoul", Role.USER));
    }

    private Book bookWithIsbn(User owner, String title, String isbn, boolean makePublic) {
        Book book = Book.register(owner, title, null, isbn, null, null, null, BookStatus.READING);
        if (makePublic) {
            book.makePublic();
        }
        return bookRepository.save(book);
    }

    private Story sharedStoryAt(User author, Book book, String text, Instant createdAt) {
        Story story = Story.of(author, text, book, null);
        story.markShared(true);
        storyRepository.save(story);
        em.createQuery("update Story s set s.createdAt = :t where s.id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", story.getId())
                .executeUpdate();
        em.clear();
        return story;
    }

    private void blocks(User blocker, User blocked) {
        em.persist(Block.of(blocker, blocked));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("책축 목록: 비공개 책에서 함께 걸린 글은 빠진다 — 책 게이트가 상위 AND (핵심 누출 가드)")
    void sharedByIsbn_excludesSharedStoriesOnPrivateBooks() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        Book secret = bookWithIsbn(author, "비공개 책", ISBN, false);
        Book open = bookWithIsbn(author, "공개 책", ISBN, true);
        sharedStoryAt(author, secret, "새면 안 되는 메모", NOW.minusSeconds(30));
        sharedStoryAt(author, open, "보여도 되는 글", NOW.minusSeconds(60));

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("보여도 되는 글");
    }

    @Test
    @DisplayName("책축 목록: shared=false 글은 빠지고 shared=true 공개 글만 남는다 — 기본 꺼짐 = 소급 노출 0")
    void sharedByIsbn_onlySharedStories() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        Book book = bookWithIsbn(author, "공개 책", ISBN, true);
        storyAt(author, book, "안 건 글", NOW.minusSeconds(30));
        sharedStoryAt(author, book, "함께 건 글", NOW.minusSeconds(60));

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("함께 건 글");
    }

    @Test
    @DisplayName("책축 목록: 팔로우와 무관하게 남의 글이 실린다 — 이번 개방의 본체")
    void sharedByIsbn_ignoresFollowRelation() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User stranger = user("stranger@booktimer.com", "stranger");
        sharedStoryAt(stranger, bookWithIsbn(stranger, "공개 책", ISBN, true), "낯선 이의 글",
                NOW.minusSeconds(30));

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("낯선 이의 글");
    }

    @Test
    @DisplayName("책축 목록: 다른 isbn13의 글은 섞이지 않는다 — 같은 책 = 같은 isbn13")
    void sharedByIsbn_isScopedToOneIsbn() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        sharedStoryAt(author, bookWithIsbn(author, "이 책", ISBN, true), "이 책의 글", NOW.minusSeconds(30));
        sharedStoryAt(author, bookWithIsbn(author, "저 책", "9788954699914", true), "저 책의 글",
                NOW.minusSeconds(20));

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("이 책의 글");
    }

    @Test
    @DisplayName("책축 목록: 최신순 + 같은 시각은 id 내림차순 + Pageable 상한이 최신부터 자른다")
    void sharedByIsbn_newestFirstWithTieAndLimit() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        Book book = bookWithIsbn(author, "공개 책", ISBN, true);
        sharedStoryAt(author, book, "옛것", NOW.minusSeconds(300));
        Story tieOlder = sharedStoryAt(author, book, "동시각 먼저", NOW.minusSeconds(100));
        Story tieNewer = sharedStoryAt(author, book, "동시각 나중", NOW.minusSeconds(100));

        List<Story> capped = storyRepository.sharedByIsbn(ISBN, viewer.getId(), PageRequest.of(0, 2));

        assertThat(capped).extracting(Story::getId).containsExactly(tieNewer.getId(), tieOlder.getId());
        assertThat(storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL))
                .extracting(Story::getText).containsExactly("동시각 나중", "동시각 먼저", "옛것");
    }

    @Test
    @DisplayName("책축 목록: 차단은 양방향으로 뺀다 — 팔로우 불변식이 없어 쿼리가 직접 진다")
    void sharedByIsbn_excludesBlockedBothDirections() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User iBlocked = user("iblocked@booktimer.com", "iblocked");
        User blockedMe = user("blockedme@booktimer.com", "blockedme");
        User neutral = user("neutral@booktimer.com", "neutral");
        sharedStoryAt(iBlocked, bookWithIsbn(iBlocked, "책", ISBN, true), "내가 차단한 이의 글",
                NOW.minusSeconds(30));
        sharedStoryAt(blockedMe, bookWithIsbn(blockedMe, "책", ISBN, true), "나를 차단한 이의 글",
                NOW.minusSeconds(20));
        sharedStoryAt(neutral, bookWithIsbn(neutral, "책", ISBN, true), "무관한 이의 글",
                NOW.minusSeconds(10));
        blocks(viewer, iBlocked);
        blocks(blockedMe, viewer);

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("무관한 이의 글");
    }

    @Test
    @DisplayName("책축 목록: 핸들 없는 작성자(login_id=null)·ADMIN 작성자의 글은 빠진다 (N-055)")
    void sharedByIsbn_excludesHandlelessAndAdminAuthors() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User handleless = handlelessUser("handleless@booktimer.com");
        User admin = adminUser("admin@booktimer.com", "adminuser");
        User normal = user("normal@booktimer.com", "normal");
        sharedStoryAt(handleless, bookWithIsbn(handleless, "책", ISBN, true), "핸들 없는 이의 글",
                NOW.minusSeconds(30));
        sharedStoryAt(admin, bookWithIsbn(admin, "책", ISBN, true), "관리자의 글", NOW.minusSeconds(20));
        sharedStoryAt(normal, bookWithIsbn(normal, "책", ISBN, true), "보통 사람의 글",
                NOW.minusSeconds(10));

        List<Story> entries = storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("보통 사람의 글");
    }

    @Test
    @DisplayName("책축 카운트: 목록과 같은 술어 — 상한과 무관한 진짜 값이고 비공개·미공유는 안 센다")
    void countSharedByIsbn_mirrorsListPredicate() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        Book open = bookWithIsbn(author, "공개 책", ISBN, true);
        sharedStoryAt(author, open, "센다 1", NOW.minusSeconds(30));
        sharedStoryAt(author, open, "센다 2", NOW.minusSeconds(20));
        storyAt(author, open, "안 건 글", NOW.minusSeconds(15));
        sharedStoryAt(author, bookWithIsbn(author, "비공개 책", ISBN, false), "비공개 메모",
                NOW.minusSeconds(10));

        assertThat(storyRepository.countSharedByIsbn(ISBN, viewer.getId())).isEqualTo(2L);
        assertThat(storyRepository.sharedByIsbn(ISBN, viewer.getId(), PageRequest.of(0, 1)))
                .hasSize(1); // 상한은 목록만 자른다 — 카운트는 그대로 2
    }

    @Test
    @DisplayName("배지 집계: isbn별 group by — 비공개·미공유·핸들 없음·ADMIN은 안 센다")
    void sharedCountsByIsbn_groupsAndFilters() {
        User author = user("author@booktimer.com", "author");
        User handleless = handlelessUser("handleless@booktimer.com");
        String other = "9788954699914";
        Book open = bookWithIsbn(author, "이 책", ISBN, true);
        sharedStoryAt(author, open, "센다 1", NOW.minusSeconds(30));
        sharedStoryAt(author, open, "센다 2", NOW.minusSeconds(20));
        storyAt(author, open, "안 건 글", NOW.minusSeconds(15));
        sharedStoryAt(author, bookWithIsbn(author, "이 책 비공개", ISBN, false), "비공개 메모",
                NOW.minusSeconds(10));
        sharedStoryAt(handleless, bookWithIsbn(handleless, "저 책", other, true), "핸들 없는 이의 글",
                NOW.minusSeconds(5));
        sharedStoryAt(author, bookWithIsbn(author, "저 책", other, true), "저 책의 글",
                NOW.minusSeconds(4));

        List<StoryRepository.IsbnStoryCount> counts =
                storyRepository.sharedCountsByIsbn(List.of(ISBN, other));

        assertThat(counts).extracting(StoryRepository.IsbnStoryCount::getIsbn13,
                        StoryRepository.IsbnStoryCount::getCount)
                .containsExactlyInAnyOrder(tuple(ISBN, 2L), tuple(other, 1L));
    }

    @Test
    @DisplayName("배지 집계·목록: 「함께 걸기」를 끄면 그 즉시 둘 다에서 빠진다 (opt-out 반영)")
    void unsharing_removesFromCountsAndList() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        Book open = bookWithIsbn(author, "공개 책", ISBN, true);
        Story story = sharedStoryAt(author, open, "곧 내릴 글", NOW.minusSeconds(30));
        assertThat(storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL)).hasSize(1);

        storyRepository.findById(story.getId()).orElseThrow().markShared(false);
        em.flush();
        em.clear();

        assertThat(storyRepository.sharedByIsbn(ISBN, viewer.getId(), ALL)).isEmpty();
        assertThat(storyRepository.countSharedByIsbn(ISBN, viewer.getId())).isZero();
        assertThat(storyRepository.sharedCountsByIsbn(List.of(ISBN))).isEmpty();
    }
}
