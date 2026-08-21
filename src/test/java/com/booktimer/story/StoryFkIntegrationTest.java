package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 여백 FK 연쇄 통합 테스트 (실제 빈 + H2 — mock 금지, T-023·T-029 재발 방지).
 *
 * <p>story는 users·book을, story_like는 story·users를 FK 참조한다(cascade 없음). 부모 삭제 경로 둘(회원 탈퇴·책 삭제)이 자식을
 * 먼저 정리하지 않으면 제약 위반으로 500이 난다 — mock 단위테스트는 FK 제약을 검증하지 못하므로
 * 실 스키마로 못 박는다. 특히 {@code story.book_id}가 NOT NULL이 된 뒤로는 「첨부만 풀기」가 불가능해
 * 책 삭제가 글 삭제를 <b>반드시</b> 동반해야 한다(2026-08-16 재설계 — 옛 unlinkBook 경로 폐기).
 */
@SpringBootTest
@Transactional
class StoryFkIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private BookService bookService;

    @Autowired
    private StoryService storyService;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private StoryLikeRepository storyLikeRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user(String email, String loginId) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    private Book publicBookOf(User owner, String title) {
        Book book = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        return bookRepository.saveAndFlush(book);
    }

    @Test
    @DisplayName("여백에 글을 남긴 회원이 탈퇴해도 FK 위반 없이 정리된다 (글 → 책 순서)")
    void deleteAccount_withMarginStories_succeeds() {
        User author = user("story-author@booktimer.com", "storyauthor");
        Book book = publicBookOf(author, "여백이 열린 책");
        storyRepository.saveAndFlush(Story.of(author, "탈퇴할 사람의 문장", book, null));

        // story.user_id가 users를, story.book_id가 book을 FK 참조 —
        // purge가 글을 책보다 먼저 지우지 않으면 여기서 제약 위반이 난다(쿼리 시 flush 강제).
        assertThatCode(() -> {
            accountService.deleteAccount("story-author@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("story-author@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("책을 삭제하면 그 책 여백의 글도 함께 사라진다 — 여백은 책에 딸린 자리(FK 위반 없음)")
    void deleteBook_withMarginStories_deletesThemToo() {
        User author = user("book-owner@booktimer.com", "bookowner");
        Book book = publicBookOf(author, "삭제될 책");
        Book keeper = publicBookOf(author, "남을 책");
        Story doomed = storyRepository.saveAndFlush(Story.of(author, "책과 함께 사라질 문장", book, null));
        Story survivor = storyRepository.saveAndFlush(Story.of(author, "다른 책의 문장", keeper, null));

        assertThatCode(() -> bookService.delete(author, book.getId())).doesNotThrowAnyException();

        assertThat(storyRepository.findById(doomed.getId())).isEmpty();
        assertThat(storyRepository.findById(survivor.getId())).isPresent(); // 다른 책의 여백은 무사
        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }

    @Test
    @DisplayName("본인이 글을 지우면 책은 남고 글만 사라진다")
    void deleteStory_keepsBook() {
        User author = user("del-author@booktimer.com", "delauthor");
        Book book = publicBookOf(author, "책은 남는다");
        Story story = storyRepository.saveAndFlush(Story.of(author, "지울 문장", book, null));

        assertThatCode(() -> {
            storyService.delete(author, story.getId());
            storyRepository.flush();
            assertThat(storyRepository.findById(story.getId())).isEmpty();
            assertThat(bookRepository.findById(book.getId())).isPresent();
        }).doesNotThrowAnyException();
    }

    // ── 좋아요(story_like) 연쇄 ──
    // story_like는 story와 users를 <b>둘 다</b> FK 참조한다. 그래서 부모 삭제 경로가 넷이고
    // (책 삭제 · 글 삭제 · 글쓴이 탈퇴 · 누른 사람 탈퇴), 넷 다 좋아요를 먼저 지워야 한다.
    // 넷을 따로 세우는 이유: 하나를 빠뜨려도 나머지 셋은 그대로 통과해 결함이 안 보인다.

    private StoryLike like(User liker, Story story) {
        return storyLikeRepository.saveAndFlush(StoryLike.of(liker, story));
    }

    @Test
    @DisplayName("좋아요 달린 글이 있는 책을 지워도 FK 위반이 없다 (좋아요 → 글 → 책 순서)")
    void deleteBook_withLikedStories_succeeds() {
        User author = user("liked-book@booktimer.com", "likedbook");
        User liker = user("book-liker@booktimer.com", "booklikera");
        Book book = publicBookOf(author, "좋아요가 달린 책");
        Story story = storyRepository.saveAndFlush(Story.of(author, "누군가 좋아한 문장", book, null));
        StoryLike given = like(liker, story);

        assertThatCode(() -> bookService.delete(author, book.getId())).doesNotThrowAnyException();

        assertThat(storyLikeRepository.findById(given.getId())).isEmpty();
        assertThat(storyRepository.findById(story.getId())).isEmpty();
    }

    @Test
    @DisplayName("좋아요 달린 글을 본인이 지워도 FK 위반이 없다")
    void deleteStory_withLikes_succeeds() {
        User author = user("liked-story@booktimer.com", "likedstory");
        User liker = user("story-liker@booktimer.com", "storylikera");
        Book book = publicBookOf(author, "책은 남는다");
        Story story = storyRepository.saveAndFlush(Story.of(author, "지울 인기 문장", book, null));
        StoryLike given = like(liker, story);

        assertThatCode(() -> {
            storyService.delete(author, story.getId());
            storyRepository.flush();
        }).doesNotThrowAnyException();

        assertThat(storyLikeRepository.findById(given.getId())).isEmpty();
        assertThat(bookRepository.findById(book.getId())).isPresent();
    }

    @Test
    @DisplayName("좋아요를 받은 사람이 탈퇴해도 FK 위반이 없다 — 남이 누른 행이 내 글에 매달려 있다")
    void deleteAccount_ofAuthorWithLikes_succeeds() {
        User author = user("popular@booktimer.com", "popularone");
        User liker = user("fan@booktimer.com", "fanaccount");
        Book book = publicBookOf(author, "인기 있는 책");
        Story story = storyRepository.saveAndFlush(Story.of(author, "많이 눌린 문장", book, null));
        StoryLike given = like(liker, story);

        assertThatCode(() -> {
            accountService.deleteAccount("popular@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("popular@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();

        assertThat(storyLikeRepository.findById(given.getId())).isEmpty();
        assertThat(userRepository.findByEmail("fan@booktimer.com")).isPresent(); // 누른 쪽은 무사
    }

    @Test
    @DisplayName("좋아요를 누른 사람이 탈퇴해도 FK 위반이 없다 — 글은 남고 좋아요만 사라진다")
    void deleteAccount_ofLiker_succeeds() {
        User author = user("author-stays@booktimer.com", "authorstays");
        User liker = user("leaving@booktimer.com", "leavingone");
        Book book = publicBookOf(author, "남을 책");
        Story story = storyRepository.saveAndFlush(Story.of(author, "남을 문장", book, null));
        StoryLike given = like(liker, story);

        assertThatCode(() -> {
            accountService.deleteAccount("leaving@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("leaving@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();

        assertThat(storyLikeRepository.findById(given.getId())).isEmpty();
        assertThat(storyRepository.findById(story.getId())).isPresent(); // 글은 남는다
    }
}
