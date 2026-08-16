package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
import com.booktimer.profile.ProfileService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private FollowService followService;
    @Mock
    private ProfileService profileService;

    private StoryService service;

    private User me;

    @BeforeEach
    void setUp() {
        service = new StoryService(storyRepository, bookRepository, rateLimitService,
                followService, profileService);
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

    /** <b>기대 반전</b>(2026-08-16, 결정 2) — 옛 단언은 「내 PRIVATE 책 → 400」이었다. */
    @Test
    @DisplayName("create: 내 책이 PRIVATE여도 저장된다 — 비공개 책 여백 = 나만의 메모(결정 2)")
    void create_privateOwnBook_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        Book privateBook = Book.register(me, "비공개", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(privateBook));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = service.create(me, "나만 보는 메모", 5L, null);

        assertThat(saved.getText()).isEqualTo("나만 보는 메모");
        assertThat(saved.getBook()).isSameAs(privateBook);
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

    // --- marginOf (책 하나의 글 목록) ---

    /** 대상 사용자를 프로필 가드 통과 상태로 세팅 — 여백 게이트는 프로필과 같은 가드를 공유한다. */
    private User visibleTarget(String loginId) {
        User target = userWithId(2L, loginId, "대상");
        when(profileService.resolveVisibleTarget(me, loginId)).thenReturn(Optional.of(target));
        return target;
    }

    @Test
    @DisplayName("marginOf: 가드 실패(차단·ADMIN·미존재) → 404 (프로필 가드와 동일 경로)")
    void marginOf_guardFails_throws404() {
        when(profileService.resolveVisibleTarget(me, "hidden")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marginOf(me, "hidden", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("marginOf: 남의 책 id를 다른 사람 핸들에 끼워 넣으면 → 404 (IDOR — 존재 비노출)")
    void marginOf_bookNotOwnedByTarget_throws404() {
        User target = visibleTarget("target");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.empty()); // 남의 책·없는 책

        assertThatThrownBy(() -> service.marginOf(me, "target", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("marginOf: 남의 PRIVATE 책 → 404 (격자에 안 보이는 책 = 존재 비노출)")
    void marginOf_privateBook_throws404() {
        User target = visibleTarget("target");
        Book hidden = Book.register(target, "비공개 책", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.marginOf(me, "target", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * §5-1 ⓐ — 완화의 예외는 <b>소유자 하나뿐</b>임을 못 박는다. 팔로워는 공개 책 여백을 볼 수 있는
     * 사이라서, 「팔로우했으니 비공개 책도」로 새어나가는 것이 가장 그럴듯한 회귀다.
     */
    @Test
    @DisplayName("marginOf: 팔로워여도 남의 PRIVATE 책은 404 — 예외는 소유자뿐(팔로우 검사에 닿지도 않는다)")
    void marginOf_followerOfPrivateBook_throws404() {
        User target = visibleTarget("target");
        Book hidden = Book.register(target, "비공개 책", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.marginOf(me, "target", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(followService, never()).isFollowing(any(), any());
        verify(storyRepository, never()).findByUserAndBookOrderByCreatedAtDescIdDesc(any(), any(), any());
    }

    /** <b>기대 반전</b>(2026-08-16, 결정 2) — 옛 단언은 「내 PRIVATE 책도 404」였다. */
    @Test
    @DisplayName("marginOf: 내 PRIVATE 책은 열린다 — self:true + 내 글 목록(결정 2)")
    void marginOf_ownPrivateBook_returnsOwnEntries() {
        when(profileService.resolveVisibleTarget(me, "meuser")).thenReturn(Optional.of(me));
        Book hidden = Book.register(me, "내 비공개 책", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(7L, me)).thenReturn(Optional.of(hidden));
        Story memo = storyWithId(10L, me, "나만 보는 메모", NOW.minusSeconds(60), hidden);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(me), eq(hidden), any(Pageable.class)))
                .thenReturn(List.of(memo));

        MarginResponse response = service.marginOf(me, "meuser", 7L);

        assertThat(response.self()).isTrue();
        assertThat(response.entries()).extracting(MarginEntry::text).containsExactly("나만 보는 메모");
        assertThat(response.book().title()).isEqualTo("내 비공개 책");
    }

    @Test
    @DisplayName("marginOf: 비팔로워 → 책 라벨은 주되 entries는 빈 배열 (글 유무 정보도 안 샘)")
    void marginOf_nonFollower_returnsEmptyEntriesWithBookLabel() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "남의 공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        when(followService.isFollowing(me, target)).thenReturn(false);

        MarginResponse response = service.marginOf(me, "target", 7L);

        assertThat(response.entries()).isEmpty();
        assertThat(response.following()).isFalse();
        assertThat(response.self()).isFalse();
        assertThat(response.book().title()).isEqualTo("남의 공개 책"); // 화면이 "팔로우하면 볼 수 있어요"를 그린다
        assertThat(response.ownerNickname()).isEqualTo("대상");
        verify(storyRepository, never()).findByUserAndBookOrderByCreatedAtDescIdDesc(any(), any(), any());
    }

    @Test
    @DisplayName("marginOf: 팔로워 → 최신순 글 목록 + following:true")
    void marginOf_follower_returnsNewestFirst() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        when(followService.isFollowing(me, target)).thenReturn(true);
        Story older = storyWithId(10L, target, "먼저 남긴 글", NOW.minusSeconds(600), book);
        Story newer = storyWithId(11L, target, "나중 남긴 글", NOW.minusSeconds(60), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), any(Pageable.class)))
                .thenReturn(List.of(newer, older));

        MarginResponse response = service.marginOf(me, "target", 7L);

        assertThat(response.following()).isTrue();
        assertThat(response.self()).isFalse();
        assertThat(response.entries()).extracting(MarginEntry::text)
                .containsExactly("나중 남긴 글", "먼저 남긴 글");
        assertThat(response.entries().get(0).createdAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("marginOf: 목록 상한 100장을 레포에 그대로 넘긴다 (페이지네이션 대신)")
    void marginOf_capsAtHundred() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        when(followService.isFollowing(me, target)).thenReturn(true);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), captor.capture()))
                .thenReturn(List.of());

        service.marginOf(me, "target", 7L);

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("marginOf: 본인 → 팔로우 검사 없이 목록 + self:true")
    void marginOf_self_returnsOwnEntries() {
        when(profileService.resolveVisibleTarget(me, "meuser")).thenReturn(Optional.of(me));
        Book book = publicBookOf(me, "내 공개 책");
        when(bookRepository.findByIdAndUser(7L, me)).thenReturn(Optional.of(book));
        Story mine = storyWithId(10L, me, "내 글", NOW.minusSeconds(60), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(me), eq(book), any(Pageable.class)))
                .thenReturn(List.of(mine));

        MarginResponse response = service.marginOf(me, "meuser", 7L);

        assertThat(response.self()).isTrue();
        assertThat(response.following()).isFalse(); // 자기 자신은 팔로우 대상이 아니다
        assertThat(response.entries()).extracting(MarginEntry::text).containsExactly("내 글");
        assertThat(response.book().coverUrl()).isEqualTo("https://img/cover.jpg");
        verify(followService, never()).isFollowing(any(), any());
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
