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
 * 스토리 FK 자식 정리 통합 테스트 (실제 빈 + H2 — mock 금지, T-023·T-029 재발 방지).
 *
 * <p>story는 users·book을, story_view는 story·users를 FK 참조한다(cascade 없음). 부모 삭제 경로
 * 세 곳(회원 탈퇴·책 삭제·스토리 본인 삭제)이 자식을 먼저 정리하지 않으면 제약 위반으로 500이 난다 —
 * mock 단위테스트는 FK 제약을 검증하지 못하므로 실 스키마로 못 박는다.
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
    private StoryViewRepository storyViewRepository;

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
    @DisplayName("스토리(책 첨부)+열람 기록을 가진 회원이 탈퇴해도 FK 위반 없이 정리된다")
    void deleteAccount_withStoriesAndViews_succeeds() {
        User author = user("story-author@booktimer.com", "storyauthor");
        User viewer = user("story-viewer@booktimer.com", "storyviewer");
        Book book = publicBookOf(author, "첨부 책");
        Story story = storyRepository.saveAndFlush(Story.of(author, "탈퇴할 사람의 문장", book, null));
        storyViewRepository.saveAndFlush(StoryView.of(story, viewer));               // 내 스토리에 달린 열람
        Story viewersStory = storyRepository.saveAndFlush(Story.of(viewer, "남의 문장", null, null));
        storyViewRepository.saveAndFlush(StoryView.of(viewersStory, author));        // 내가 남긴 열람

        // story.user_id·story_view.viewer_id가 users를, story.book_id가 book을 FK 참조 —
        // purge가 정리하지 않으면 여기서 제약 위반이 난다(쿼리 시 flush 강제).
        assertThatCode(() -> {
            accountService.deleteAccount("story-author@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("story-author@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("스토리가 첨부한 책을 삭제하면 첨부만 풀리고(book_id null) 문장은 보존된다")
    void deleteBook_withAttachedStory_unlinksAndKeepsStory() {
        User author = user("book-owner@booktimer.com", "bookowner");
        Book book = publicBookOf(author, "삭제될 책");
        Story story = storyRepository.saveAndFlush(Story.of(author, "책이 사라져도 남을 문장", book, null));

        assertThatCode(() -> bookService.delete(author, book.getId())).doesNotThrowAnyException();

        Story reloaded = storyRepository.findById(story.getId()).orElseThrow();
        assertThat(reloaded.getBook()).isNull();
        assertThat(reloaded.getText()).isEqualTo("책이 사라져도 남을 문장");
        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }

    @Test
    @DisplayName("열람 기록이 달린 스토리를 본인이 삭제해도 FK 위반 없이 지워진다")
    void deleteStory_withViews_succeeds() {
        User author = user("del-author@booktimer.com", "delauthor");
        User viewer = user("del-viewer@booktimer.com", "delviewer");
        Story story = storyRepository.saveAndFlush(Story.of(author, "지울 문장", null, null));
        storyViewRepository.saveAndFlush(StoryView.of(story, viewer));

        assertThatCode(() -> {
            storyService.delete(author, story.getId());
            storyRepository.flush();
            assertThat(storyRepository.findById(story.getId())).isEmpty();
        }).doesNotThrowAnyException();
    }
}
