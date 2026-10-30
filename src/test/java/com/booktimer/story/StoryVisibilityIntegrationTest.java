package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-95 → REQ-212(책생글 M1) — 여백은 본인만이다. 생각 빈 여백뿐 아니라 생각 있는 여백도 남에게는 어느 읽기 경로로도
 * 안 보이고(옛 REQ-95를 넓혔다 — C3), 본인에게는 그대로 보인다.
 * 실 H2 + 실제 빈(쿼리 술어를 목으로는 못 잰다). 컨텍스트는 {@code StoryFkIntegrationTest}와 같은 맨
 * {@code @SpringBootTest} 세계를 공유한다(T-235 — 새 세계를 띄우지 않는다).
 *
 * <p>같은 공개 책에 A(생각 빈 여백)와 B(생각 있는 여백)를 나란히 둔다 — 양성 대조는 본인 테스트(둘 다 보인다)가 맡는다.
 */
@SpringBootTest
@Transactional
class StoryVisibilityIntegrationTest {

    private static final String QUOTE = "새는 알에서 나오려고 투쟁한다.";

    @Autowired
    private StoryService storyService;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FollowRepository followRepository;

    private User owner;
    private User stranger;
    private Book book;
    private Story quoteOnly;
    private Story withThought;

    private User user(String loginId) {
        User u = User.of(loginId + "@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", loginId, "Asia/Seoul",
                Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    @BeforeEach
    void setUp() {
        owner = user("visowner");
        stranger = user("visstranger");
        followRepository.saveAndFlush(Follow.of(stranger, owner)); // 소식(feedRecent)은 팔로우한 사람 글만
        Book b = Book.register(owner, "공개 책", null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        book = bookRepository.saveAndFlush(b);
        quoteOnly = storyRepository.saveAndFlush(Story.of(owner, "  ", book, null, QUOTE));
        withThought = storyRepository.saveAndFlush(Story.of(owner, "내 생각", book, null, QUOTE));
    }

    private static HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("REQ-212 · 생각 빈 여백도 생각 있는 여백도 남의 책방 · 여백 목록에 없고 좋아요는 404다")
    void margins_areHiddenFromOthers() {
        assertThat(quoteOnly.getText()).isEqualTo("");

        assertThat(storyService.allMarginsOf(stranger, "visowner")).isEmpty();
        assertThat(storyService.marginOf(stranger, "visowner", book.getId()).entries()).isEmpty();

        assertThatThrownBy(() -> storyService.like(stranger, quoteOnly.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> storyService.likers(stranger, quoteOnly.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> storyService.like(stranger, withThought.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("REQ-212 · 본인은 생각 빈 여백 · 생각 있는 여백을 그대로 본다(양성 대조)")
    void quoteOnly_isVisibleToOwner() {
        assertThat(storyService.allMarginsOf(owner, "visowner"))
                .extracting(ProfileMarginEntry::id)
                .containsExactlyInAnyOrder(quoteOnly.getId(), withThought.getId());

        assertThat(storyService.marginOf(owner, "visowner", book.getId()).entries())
                .extracting(MarginEntry::id)
                .containsExactlyInAnyOrder(quoteOnly.getId(), withThought.getId());

        assertThat(storyService.like(owner, quoteOnly.getId()).liked()).isTrue();
    }
}
