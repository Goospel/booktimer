package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private RateLimitService rateLimitService;

    private StoryService service;

    private User me;

    @BeforeEach
    void setUp() {
        service = new StoryService(storyRepository, bookRepository, rateLimitService);
        me = userWithId(1L, "meuser", "나");
    }

    private User userWithId(long id, String loginId, String nickname) {
        User u = User.of(loginId + "@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Story storyWithId(long id, User author, String text, Instant createdAt, Book book) {
        Story s = Story.of(author, text, book, null);
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "createdAt", createdAt);
        return s;
    }

    private Book publicBookOf(User owner, String title) {
        Book book = Book.register(owner, title, null, null, "https://img/cover.jpg", null, null, BookStatus.READING);
        book.makePublic();
        return book;
    }

    private static HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    // --- create ---

    @Test
    @DisplayName("create: 레이트리밋 초과 → 429 (무음 드롭 금지 — 작성은 콘텐츠 소실이라 안내)")
    void create_rateLimited_throws429() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: bookId 없음 → 400 (여백은 책에 귀속 — 책 없는 글은 만들 수 없다)")
    void create_withoutBookId_throws400() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(me, "문장", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 없는 책·남의 책 → 404 (존재 누설 방지)")
    void create_missingOrOthersBook_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("create: 내 책인데 PRIVATE → 400 (공개 책에만 여백이 열린다)")
    void create_privateOwnBook_throws400() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        Book privateBook = Book.register(me, "비공개", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(privateBook));

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create: 정상 — 내 공개 책의 여백에 저장")
    void create_valid_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        Book mine = publicBookOf(me, "내 공개 책");
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(mine));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = service.create(me, "인상 깊은 문장", 5L, "night");

        assertThat(saved.getText()).isEqualTo("인상 깊은 문장");
        assertThat(saved.getBook()).isSameAs(mine);
        assertThat(saved.getBgCode()).isEqualTo("night");
    }

    // --- delete ---

    @Test
    @DisplayName("delete: 없는 글·타인 글 → 404 (IDOR)")
    void delete_missingOrOthers_throws404() {
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(me, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));

        User other = userWithId(2L, "other", "타인");
        Story others = storyWithId(10L, other, "남의 문장", NOW.minusSeconds(60), publicBookOf(other, "남의 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(others));
        assertThatThrownBy(() -> service.delete(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 본인 글 → 삭제된다")
    void delete_owner_deletes() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        service.delete(me, 10L);

        verify(storyRepository).delete(mine);
    }
}
