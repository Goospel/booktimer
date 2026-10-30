package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.profile.ProfileService;
import com.booktimer.search.UserRowAssembler;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-213(책생글 M1) — 여백은 나만 본다. 「함께 걸기」는 켜지지 않고(share 400), 만들기의 {@code shared = true}는 무시하고
 * 꺼진 채 저장한다 — 동결 미니앱이 체크 값을 몸에 싣기 때문이다(400이면 그 글이 저장되지 않는다, J9). 옛 REQ-94 두 테스트를 대신한다.
 */
@ExtendWith(MockitoExtension.class)
class StoryServiceShareTest {

    private static final String QUOTE = "새는 알에서 나오려고 투쟁한다.";

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private ProfileService profileService;
    @Mock
    private StoryLikeRepository storyLikeRepository;
    @Mock
    private UserRowAssembler rowAssembler;

    private StoryService service;
    private User me;
    private Book book;

    @BeforeEach
    void setUp() {
        service = new StoryService(storyRepository, bookRepository, rateLimitService,
                profileService, storyLikeRepository, rowAssembler, java.time.Clock.systemUTC());
        me = User.of("me@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "나", "Asia/Seoul", Role.USER);
        ReflectionTestUtils.setField(me, "id", 1L);
        book = Book.register(me, "공개 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        lenient().when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        lenient().when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(book));
        lenient().when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    private Story storedStory(String text) {
        Story story = Story.of(me, text, book, null, QUOTE);
        ReflectionTestUtils.setField(story, "id", 10L);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        return story;
    }

    @Test
    @DisplayName("REQ-213 · share 400 — 생각 있는 여백도 켜지지 않는다 · 끄기는 200 그대로")
    void shareIs400() {
        Story story = storedStory("내 생각");
        assertThatThrownBy(() -> service.setShared(me, 10L, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("여백은 나만 봐요");
        assertThat(story.isShared()).isFalse();
        assertThat(service.setShared(me, 10L, false).shared()).isFalse();
    }

    @Test
    @DisplayName("REQ-213 · shared:true로 만들기 200 · 저장은 shared false — 동결 미니앱의 체크 값에 글을 잃지 않는다(J9)")
    void createIgnoresShared() {
        Story withThought = service.create(me, "내 생각", 5L, null, QUOTE, true);
        Story quoteOnly = service.create(me, "  ", 5L, null, QUOTE, true);

        assertThat(withThought.isShared()).isFalse();
        assertThat(quoteOnly.isShared()).isFalse();
        assertThat(quoteOnly.getText()).isEqualTo("");
        verify(storyRepository, times(2)).save(any(Story.class));
    }
}
