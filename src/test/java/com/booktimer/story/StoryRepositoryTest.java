package com.booktimer.story;

import com.booktimer.block.Block;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.config.JpaConfig;
import com.booktimer.follow.Follow;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
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

    // ── 사람축 전체 목록(recentByUser) — 책방 「여백」 탭 ─────────────────────────
    // 책생글 M1부터 본인만 부른다(StoryService.allMarginsOf — 남에게는 빈 목록). 남이 보는 쿼리(feedRecent ·
    // sharedByIsbn · 배지 집계)는 저장소째 지웠다 — 그 테스트도 함께 걷었다(여백은 본인만 — C3).

    private Book privateBookOf(User owner, String title) {
        return bookRepository.save(
                Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("사람축 목록: 비공개 책 글까지 실리고 책 없는 쪽지는 빠진다 — 옛 화면은 책 라벨 없는 카드를 못 그린다(J64)")
    void recentByUser_includesPrivateBookExcludesBookless() {
        User me = user("me@booktimer.com", "meuser");
        storyAt(me, publicBookOf(me, "공개 책"), "공개 책 글", NOW.minusSeconds(60));
        storyAt(me, privateBookOf(me, "비공개 책"), "나만 보는 메모", NOW.minusSeconds(30));
        storyRepository.save(Story.of(me, "책 없는 쪽지", null, null));
        em.flush();
        em.clear();

        List<Story> entries = storyRepository.recentByUser(me, ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("나만 보는 메모", "공개 책 글");
        // U-9: fetch join 실증 — @DataJpaTest는 트랜잭션 안이라 지연 로딩이 조용히 성공한다.
        assertThat(Hibernate.isInitialized(entries.get(0).getBook())).isTrue();
    }

    @Test
    @DisplayName("사람축 목록: 다른 사람의 글은 섞이지 않는다 — 책 제목이 같아도")
    void recentByUser_isScopedToTarget() {
        User target = user("target@booktimer.com", "target");
        User stranger = user("stranger@booktimer.com", "stranger");
        storyAt(target, publicBookOf(target, "같은 제목"), "대상의 글", NOW.minusSeconds(60));
        storyAt(stranger, publicBookOf(stranger, "같은 제목"), "남의 글", NOW.minusSeconds(30));

        List<Story> entries = storyRepository.recentByUser(target, ALL);

        assertThat(entries).extracting(Story::getText).containsExactly("대상의 글");
    }

    @Test
    @DisplayName("사람축 목록: 최신순 + 동시각 id 내림차순 — 상한은 최신부터 자른다")
    void recentByUser_newestFirstAndLimitTakesNewest() {
        User target = user("target@booktimer.com", "target");
        Book one = publicBookOf(target, "책 하나");
        Book two = publicBookOf(target, "책 둘");
        storyAt(target, one, "오래된", NOW.minusSeconds(300));
        Story olderTie = storyAt(target, two, "동시각 먼저", NOW.minusSeconds(100));
        Story newerTie = storyAt(target, one, "동시각 나중", NOW.minusSeconds(100));

        assertThat(storyRepository.recentByUser(target, ALL))
                .extracting(Story::getId)
                .containsExactly(newerTie.getId(), olderTie.getId(), oldestIdOf(target));

        assertThat(storyRepository.recentByUser(target, PageRequest.of(0, 2)))
                .extracting(Story::getText)
                .containsExactly("동시각 나중", "동시각 먼저");
    }

    /** 위 tie 테스트의 셋째 글 id — 시각이 가장 오래된 하나. */
    private Long oldestIdOf(User target) {
        return storyRepository.recentByUser(target, ALL).stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElseThrow()
                .getId();
    }
}
