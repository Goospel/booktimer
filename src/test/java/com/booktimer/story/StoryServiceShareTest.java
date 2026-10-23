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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-94 — 생각 빈 여백(인용만 남긴 글)은 「모두의 여백」에 올릴 수 없다. 쓰기 두 문(create·setShared)이
 * 같이 막아야 {@code shared = true}를 지는 쿼리들(sharedRecent·sharedByIsbn·개수)을 안 고쳐도 된다.
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
                profileService, storyLikeRepository, rowAssembler);
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
    @DisplayName("REQ-94 · 생각 빈 여백은 함께 걸 수 없다")
    void quoteOnlyStory_cannotBeShared() {
        // create(shared=true) — 400, 저장도 안 한다
        assertThatThrownBy(() -> service.create(me, "  ", 5L, null, QUOTE, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("생각을 적은 여백만 모두의 여백에 올릴 수 있어요");
        verify(storyRepository, never()).save(any());

        // setShared(true) — 400, 값은 그대로 꺼짐
        Story quoteOnly = storedStory("");
        assertThatThrownBy(() -> service.setShared(me, 10L, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(quoteOnly.isShared()).isFalse();

        // 끄기는 막지 않는다
        assertThat(service.setShared(me, 10L, false).shared()).isFalse();
        // shared=false 생성도 막지 않는다 — 나만 보는 여백으로 저장된다
        assertThat(service.create(me, "  ", 5L, null, QUOTE, false).getText()).isEqualTo("");
    }

    @Test
    @DisplayName("REQ-94 · 양성 대조 — 생각을 적은 여백은 create·setShared 둘 다 켜진다")
    void storyWithThought_canBeShared() {
        assertThat(service.create(me, "내 생각", 5L, null, QUOTE, true).isShared()).isTrue();

        storedStory("내 생각");
        assertThat(service.setShared(me, 10L, true).shared()).isTrue();
    }
}
