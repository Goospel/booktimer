package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.profile.ProfileService;
import com.booktimer.search.UserRowAssembler;
import com.booktimer.search.UserSearchResult;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
    private ProfileService profileService;
    @Mock
    private StoryLikeRepository storyLikeRepository;
    @Mock
    private UserRowAssembler rowAssembler;

    private StoryService service;

    private User me;

    @BeforeEach
    void setUp() {
        service = new StoryService(storyRepository, bookRepository, rateLimitService,
                profileService, storyLikeRepository, rowAssembler);
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

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null, null, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: bookId 없음 → 400 (여백은 책에 귀속 — 책 없는 글은 만들 수 없다)")
    void create_withoutBookId_throws400() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(me, "문장", null, null, null, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 없는 책·남의 책 → 404 (존재 누설 방지)")
    void create_missingOrOthersBook_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null, null, false))
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

        Story saved = service.create(me, "나만 보는 메모", 5L, null, null, false);

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

        Story saved = service.create(me, "인상 깊은 문장", 5L, "night", "  새는 알에서 나오려고 투쟁한다.  ",
                false);

        assertThat(saved.getText()).isEqualTo("인상 깊은 문장");
        assertThat(saved.getBook()).isSameAs(mine);
        assertThat(saved.getBgCode()).isEqualTo("night");
        // 인용은 그대로 흘려보내고 정규화(strip)는 도메인이 한다 — 서비스가 따로 손대지 않는다
        assertThat(saved.getQuote()).isEqualTo("새는 알에서 나오려고 투쟁한다.");
        assertThat(saved.isShared()).isFalse();
    }

    @Test
    @DisplayName("create: shared=true → 켜진 채 저장된다. 내 PRIVATE 책이어도 막지 않는다(읽기 시점 판정)")
    void create_shared_savesWithFlagRegardlessOfVisibility() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        Book privateBook = Book.register(me, "비공개", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(privateBook));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = service.create(me, "미리 걸어 두는 글", 5L, null, null, true);

        assertThat(saved.isShared()).isTrue();
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

    /**
     * 2026-08-22 — <b>팔로우는 열람 권한에서 빠졌다</b>. 예전엔 비팔로워에게 빈 배열을 줬는데, 「모두의
     * 여백」(책축)이 열린 뒤로는 같은 글을 팔로우 없이 이미 읽을 수 있어 게이트가 절반만 작동했다.
     * 이제 공개 책의 여백은 공개고, 팔로우는 홈 소식 구독에만 남는다.
     */
    @Test
    @DisplayName("marginOf: 비팔로워 → 공개 책이면 목록을 그대로 준다 (팔로우는 열람 권한이 아니다)")
    void marginOf_nonFollower_seesEntries() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "남의 공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        Story theirs = storyWithId(10L, target, "낯선 사람도 읽는 글", NOW.minusSeconds(60), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), any(Pageable.class)))
                .thenReturn(List.of(theirs));

        MarginResponse response = service.marginOf(me, "target", 7L);

        assertThat(response.entries()).extracting(MarginEntry::text).containsExactly("낯선 사람도 읽는 글");
        assertThat(response.self()).isFalse();
        assertThat(response.book().title()).isEqualTo("남의 공개 책");
        assertThat(response.ownerNickname()).isEqualTo("대상");
    }

    @Test
    @DisplayName("marginOf: 남의 공개 책 → 최신순 글 목록")
    void marginOf_othersPublicBook_returnsNewestFirst() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        Story older = storyWithId(10L, target, "먼저 남긴 글", NOW.minusSeconds(600), book);
        Story newer = storyWithId(11L, target, "나중 남긴 글", NOW.minusSeconds(60), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), any(Pageable.class)))
                .thenReturn(List.of(newer, older));

        MarginResponse response = service.marginOf(me, "target", 7L);

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
        assertThat(response.entries()).extracting(MarginEntry::text).containsExactly("내 글");
        assertThat(response.book().coverUrl()).isEqualTo("https://img/cover.jpg");
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

    // --- like / unlike ---

    /** 배치 집계 투영 — 레포 관례가 인터페이스 투영이라(BookStoryRecency 등) 테스트가 익명 구현으로 만든다. */
    private StoryLikeRepository.StoryLikeCount likeCount(long storyId, long count) {
        return new StoryLikeRepository.StoryLikeCount() {
            @Override
            public Long getStoryId() {
                return storyId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    /** 남의 공개 책에 달린 남의 글 — 좋아요 게이트를 통과하기 직전 상태. */
    private Story othersPublicStory(User owner) {
        return storyWithId(10L, owner, "남의 문장", NOW.minusSeconds(60), publicBookOf(owner, "남의 공개 책"));
    }

    @Test
    @DisplayName("like: 레이트리밋 초과 → 429")
    void like_rateLimited_throws429() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.like(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(storyLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("like: 없는 글 → 404")
    void like_missingStory_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(me, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * 자기 글 금지를 걷어낸 자리(2026-08-20). 게이트를 <b>통과</b>시키는 것만으로는 부족해서
     * 「팔로우·공개 검사에 닿지도 않는다」까지 못 박는다 — self가 그 체인에 들어가면 자기 자신을
     * 팔로우하지 않으므로 404가 되고, 내 여백의 하트가 통째로 죽는다.
     */
    @Test
    @DisplayName("like: 내 글 → 저장된다 (자기 좋아요 허용 — 팔로우·공개 검사에 닿지 않는다)")
    void like_ownStory_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));
        when(storyLikeRepository.findByStoryAndUser(mine, me)).thenReturn(Optional.empty());
        when(storyLikeRepository.countByStory(mine)).thenReturn(1L);

        StoryService.LikeState state = service.like(me, 10L);

        assertThat(state.likeCount()).isEqualTo(1L);
        assertThat(state.liked()).isTrue();
        verify(storyLikeRepository).save(any());
    }

    @Test
    @DisplayName("like: 내 PRIVATE 책 글도 눌린다 — 나만 보는 메모라 공개 검사가 닿지 않는다")
    void like_ownPrivateBookStory_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        Book privateBook = Book.register(me, "내 비공개 책", null, null, null, null, null, BookStatus.READING);
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), privateBook);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));
        when(storyLikeRepository.findByStoryAndUser(mine, me)).thenReturn(Optional.empty());
        when(storyLikeRepository.countByStory(mine)).thenReturn(1L);

        assertThat(service.like(me, 10L).liked()).isTrue();
    }

    /**
     * 게이트가 {@code marginOf}와 같은 판정을 재사용함을 못 박는다 — 이게 없으면 안 보이는 글 id에
     * 눌러 보고 200/404로 <b>존재를 알아낼 수 있다</b>.
     */
    @Test
    @DisplayName("like: 가드 실패(차단·ADMIN·핸들 없는 주인) → 404")
    void like_guardFails_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        when(storyRepository.findById(10L)).thenReturn(Optional.of(othersPublicStory(owner)));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("like: 남의 PRIVATE 책 글 → 404 (팔로우 검사에 닿지도 않는다)")
    void like_privateBook_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Book hidden = Book.register(owner, "비공개 책", null, null, null, null, null, BookStatus.READING);
        when(storyRepository.findById(10L))
                .thenReturn(Optional.of(storyWithId(10L, owner, "남의 메모", NOW.minusSeconds(60), hidden)));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.like(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** 팔로우 축 제거(2026-08-22)의 단건 미러 — 팔로우를 게이트로 되살리면 이 테스트가 붉어진다. */
    @Test
    @DisplayName("like: 비팔로워 → 공개 책 글이면 눌린다 (팔로우는 열람 권한이 아니다)")
    void like_nonFollower_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.empty());
        when(storyLikeRepository.countByStory(story)).thenReturn(1L);

        assertThat(service.like(me, 10L).liked()).isTrue();
        verify(storyLikeRepository).save(any());
    }

    @Test
    @DisplayName("like: 팔로워 → 저장하고 갱신된 개수를 돌려준다")
    void like_follower_savesAndReturnsCount() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.empty());
        when(storyLikeRepository.countByStory(story)).thenReturn(4L);

        StoryService.LikeState state = service.like(me, 10L);

        assertThat(state.likeCount()).isEqualTo(4L);
        assertThat(state.liked()).isTrue();
        ArgumentCaptor<StoryLike> captor = ArgumentCaptor.forClass(StoryLike.class);
        verify(storyLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(me);
        assertThat(captor.getValue().getStory()).isSameAs(story);
    }

    /**
     * 모바일에서 타임아웃 뒤 재전송이 흔하다 — POST가 토글이면 그 재시도가 <b>좋아요를 취소</b>한다.
     * 그래서 POST는 멱등이어야 하고, 이 테스트가 그 계약이다.
     */
    @Test
    @DisplayName("like: 이미 눌러 둔 글 → 다시 저장하지 않고 그대로 liked:true (재전송 멱등)")
    void like_alreadyLiked_isIdempotent() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.of(StoryLike.of(me, story)));
        when(storyLikeRepository.countByStory(story)).thenReturn(4L);

        StoryService.LikeState state = service.like(me, 10L);

        assertThat(state.liked()).isTrue();
        assertThat(state.likeCount()).isEqualTo(4L);
        verify(storyLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("unlike: 안 누른 글 → 404 (행의 부재로 수렴 — 존재도 누설하지 않는다)")
    void unlike_notLiked_throws404() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlike(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * <b>취소에는 노출 게이트를 걸지 않는다.</b> 걸면 누른 뒤 언팔한 사람이 자기 좋아요를 되돌릴 수 없게
     * 갇힌다. 행이 있다는 것 자체가 「한때 볼 수 있었다」의 증거라 존재 누설도 없다.
     */
    @Test
    @DisplayName("unlike: 눌러 둔 글 → 팔로우 검사 없이 지운다 (언팔 뒤에도 되돌릴 수 있다)")
    void unlike_liked_deletesWithoutGate() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        StoryLike like = StoryLike.of(me, story);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.of(like));
        when(storyLikeRepository.countByStory(story)).thenReturn(3L);

        StoryService.LikeState state = service.unlike(me, 10L);

        assertThat(state.liked()).isFalse();
        assertThat(state.likeCount()).isEqualTo(3L);
        verify(storyLikeRepository).delete(like);
        verify(profileService, never()).resolveVisibleTarget(any(), any());
    }

    @Test
    @DisplayName("marginOf: 글마다 좋아요 개수와 내가 누른 여부가 실린다 (카드마다 세지 않는다 — 배치 2쿼리)")
    void marginOf_projectsLikeCountAndLiked() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        Story liked = storyWithId(10L, target, "내가 누른 글", NOW.minusSeconds(60), book);
        Story plain = storyWithId(11L, target, "아무도 안 누른 글", NOW.minusSeconds(120), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), any(Pageable.class)))
                .thenReturn(List.of(liked, plain));
        when(storyLikeRepository.countsByStoryIds(List.of(10L, 11L))).thenReturn(List.of(likeCount(10L, 3L)));
        when(storyLikeRepository.likedStoryIds(List.of(10L, 11L), me)).thenReturn(List.of(10L));

        MarginResponse response = service.marginOf(me, "target", 7L);

        assertThat(response.entries()).extracting(MarginEntry::likeCount).containsExactly(3L, 0L);
        assertThat(response.entries()).extracting(MarginEntry::liked).containsExactly(true, false);
    }

    /**
     * 자기 좋아요가 허용되면서 <b>내 여백에서도 liked를 물어야</b> 한다(2026-08-20). 예전엔 자기 글엔 누를
     * 수 없어 답이 구조적으로 비어 있으니 쿼리를 건너뛰었는데, 지금 건너뛰면 방금 누른 하트가 새로고침에
     * 꺼진다 — 「눌렀는데 안 눌린 것처럼 보인다」가 바로 이 지점이다.
     */
    @Test
    @DisplayName("marginOf: 내 여백에서도 내가 누른 하트가 채워진다 (자기 좋아요 허용)")
    void marginOf_self_projectsLiked() {
        when(profileService.resolveVisibleTarget(me, "meuser")).thenReturn(Optional.of(me));
        Book book = publicBookOf(me, "내 공개 책");
        when(bookRepository.findByIdAndUser(7L, me)).thenReturn(Optional.of(book));
        Story mine = storyWithId(10L, me, "내 글", NOW.minusSeconds(60), book);
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(me), eq(book), any(Pageable.class)))
                .thenReturn(List.of(mine));
        when(storyLikeRepository.countsByStoryIds(List.of(10L))).thenReturn(List.of(likeCount(10L, 5L)));
        when(storyLikeRepository.likedStoryIds(List.of(10L), me)).thenReturn(List.of(10L));

        MarginResponse response = service.marginOf(me, "meuser", 7L);

        assertThat(response.entries()).extracting(MarginEntry::likeCount).containsExactly(5L);
        assertThat(response.entries()).extracting(MarginEntry::liked).containsExactly(true);
    }

    @Test
    @DisplayName("marginOf: 글이 없으면 집계 쿼리를 아예 안 던진다 (in () 회피 — recencyByBook과 같은 가드)")
    void marginOf_noEntries_skipsAggregates() {
        User target = visibleTarget("target");
        Book book = publicBookOf(target, "공개 책");
        when(bookRepository.findByIdAndUser(7L, target)).thenReturn(Optional.of(book));
        when(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(eq(target), eq(book), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.marginOf(me, "target", 7L).entries()).isEmpty();

        verify(storyLikeRepository, never()).countsByStoryIds(any());
        verify(storyLikeRepository, never()).likedStoryIds(any(), any());
    }

    @Test
    @DisplayName("delete: 글을 지우면 거기 달린 좋아요도 먼저 지운다 (story_like.story_id FK)")
    void delete_owner_deletesLikesFirst() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        service.delete(me, 10L);

        InOrder order = inOrder(storyLikeRepository, storyRepository);
        order.verify(storyLikeRepository).deleteByStory(mine);
        order.verify(storyRepository).delete(mine);
    }

    /** 좋아요 행 — 최근순 정렬·필터를 계측하려면 실제 엔티티가 필요하다(투영이 아니라 관계를 탄다). */
    private StoryLike likeBy(User user, Story story) {
        return StoryLike.of(user, story);
    }

    @Test
    @DisplayName("likers: 없는 글 → 404")
    void likers_missingStory_throws404() {
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.likers(me, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * 명단은 <b>목록과 같은 판정</b>을 재사용해야 한다 — 어긋나면 안 보이는 글 id로 명단을 열어
     * 「그 글이 있다」와 「누가 눌렀다」를 한꺼번에 알아낼 수 있다.
     */
    @Test
    @DisplayName("likers: 비팔로워 → 공개 책 글이면 열린다 (목록과 같은 판정 — 팔로우 축 제거의 미러)")
    void likers_nonFollower_opens() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(story)).thenReturn(List.of());
        when(rowAssembler.toRows(eq(me), anyList())).thenReturn(List.of());

        assertThat(service.likers(me, 10L)).isEmpty();
    }

    @Test
    @DisplayName("likers: 남의 PRIVATE 책 글 → 404 (팔로우 검사에 닿지도 않는다)")
    void likers_privateBook_throws404() {
        User owner = userWithId(2L, "owner", "주인");
        Book privateBook = Book.register(owner, "남의 비공개 책", null, null, null, null, null, BookStatus.READING);
        when(storyRepository.findById(10L))
                .thenReturn(Optional.of(storyWithId(10L, owner, "남의 문장", NOW.minusSeconds(60), privateBook)));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.likers(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("likers: 남의 공개 글 → 레포가 준 순서(최근순) 그대로 사용자 행으로 조립")
    void likers_othersPublicStory_returnsRowsInOrder() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        User recent = visibleUser(3L, "recent");
        User older = visibleUser(4L, "older");
        when(storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(story))
                .thenReturn(List.of(likeBy(recent, story), likeBy(older, story)));
        List<UserSearchResult> rows = List.of(row("recent"), row("older"));
        when(rowAssembler.toRows(eq(me), anyList())).thenReturn(rows);

        assertThat(service.likers(me, 10L)).isSameAs(rows);
        assertThat(capturedTargets()).extracting(User::getLoginId).containsExactly("recent", "older");
    }

    @Test
    @DisplayName("likers: 내 글 → 팔로우 검사 없이 열린다 (자기 여백의 명단은 내 것이다)")
    void likers_ownStory_opensWithoutGate() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));
        when(storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(mine)).thenReturn(List.of());

        assertThat(service.likers(me, 10L)).isEmpty();

    }

    /**
     * 두 필터를 한 테스트에 둔 것은 <b>같은 실패</b>를 막기 때문이다 — 명단은 「내가 볼 수 있는 사람」만
     * 담아야 한다. 핸들 없는 사람(N-055)은 어느 목록에도 실리지 않고, 차단은 대칭이라 좋아요를 누른 뒤
     * 차단이 걸려도 그 흔적이 남으면 안 된다(차단이 팔로우는 끊지만 이미 눌린 행은 남는다).
     */
    @Test
    @DisplayName("likers: 핸들 없는 사람·차단 관계는 명단에서 뺀다 (N-055 · 차단 대칭)")
    void likers_filtersHandlelessAndBlocked() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = othersPublicStory(owner);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        User visible = visibleUser(3L, "visible");
        User handleless = userWithId(4L, "nohandle", "온보딩 전");
        ReflectionTestUtils.setField(handleless, "loginId", null);
        User blocked = userWithId(5L, "blocked", "차단됨"); // resolveVisibleTarget 미스텁 → 빈 Optional
        when(storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(story))
                .thenReturn(List.of(likeBy(visible, story), likeBy(handleless, story), likeBy(blocked, story)));
        when(rowAssembler.toRows(eq(me), anyList())).thenReturn(List.of(row("visible")));

        service.likers(me, 10L);

        assertThat(capturedTargets()).extracting(User::getLoginId).containsExactly("visible");
    }

    /** 조립기에 실제로 넘어간 사용자들 — 필터·정렬이 이 서비스의 일이라 여기서 계측한다. */
    @SuppressWarnings("unchecked")
    private List<User> capturedTargets() {
        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowAssembler).toRows(eq(me), captor.capture());
        return captor.getValue();
    }

    /** 명단에 남는(=차단 아님·ADMIN 아님) 사용자 — 가드가 그를 통과시키도록 스텁까지 함께 건다. */
    private User visibleUser(long id, String loginId) {
        User u = userWithId(id, loginId, "누른이");
        when(profileService.resolveVisibleTarget(me, loginId)).thenReturn(Optional.of(u));
        return u;
    }

    private UserSearchResult row(String loginId) {
        return new UserSearchResult(loginId, "누른이", 0L, false, false);
    }

    // ── 「모두의 여백」(shared) ────────────────────────────────────────────────
    // 불변식(2026-08-22 개정): 노출 = book.isPublic(). 그게 전부다 — 팔로우도 shared도 게이트가
    // 아니다. shared는 이제 「책축 목록에 실을지」를 정하는 배치 값이고, 열람 권한과 무관하다.
    // 여기 남은 테스트가 지키는 것은 <b>책 게이트가 유일한 방어로 살아 있는가</b> 하나다.

    private Story sharedStoryOf(User owner, Book book) {
        Story story = storyWithId(10L, owner, "함께 건 글", NOW.minusSeconds(60), book);
        story.markShared(true);
        return story;
    }

    @Test
    @DisplayName("like: 낯선 사람 + 함께 걸린 공개 글 → 200 (책축 목록의 미러 — 팔로우 없이도 눌린다)")
    void like_strangerOnSharedPublicStory_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Story story = sharedStoryOf(owner, publicBookOf(owner, "남의 공개 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryAndUser(story, me)).thenReturn(Optional.empty());
        when(storyLikeRepository.countByStory(story)).thenReturn(1L);

        StoryService.LikeState state = service.like(me, 10L);

        assertThat(state.liked()).isTrue();
        verify(storyLikeRepository).save(any());
    }

    @Test
    @DisplayName("like: 함께 걸었어도 책이 PRIVATE면 404 — 책 게이트가 상위 AND (핵심 누출 가드)")
    void like_sharedStoryOnPrivateBook_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_LIKE, 1L)).thenReturn(true);
        User owner = userWithId(2L, "owner", "주인");
        Book hidden = Book.register(owner, "비공개 책", null, null, null, null, null, BookStatus.READING);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(sharedStoryOf(owner, hidden)));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.like(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyLikeRepository, never()).save(any());
    }

    /**
     * 팔로우·shared 두 축이 게이트에서 빠진 뒤, 이 자리에 있던 회귀 가드 둘
     * ({@code like_strangerOnUnsharedStory_throws404} · {@code like_followerOnUnsharedStory_stillSaves})은
     * 지웠다 — 둘 다 「낯선 사람 + 공개 책 글」이라 {@code like_nonFollower_saves}와 같은 돌연변이를
     * 잡는 완전 중복이 됐다. 남은 가드는 위 PRIVATE 404 하나이고, 그게 이제 유일한 방어선이다.
     */

    @Test
    @DisplayName("likers: 낯선 사람도 함께 걸린 공개 글의 명단을 연다 (목록·단건 미러 일치)")
    void likers_strangerOnSharedStory_opens() {
        User owner = userWithId(2L, "owner", "주인");
        Story story = sharedStoryOf(owner, publicBookOf(owner, "남의 공개 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(profileService.resolveVisibleTarget(me, "owner")).thenReturn(Optional.of(owner));
        when(storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(story)).thenReturn(List.of());
        when(rowAssembler.toRows(eq(me), anyList())).thenReturn(List.of());

        assertThat(service.likers(me, 10L)).isEmpty();
    }

    // --- setShared ---

    @Test
    @DisplayName("setShared: 없는 글·남의 글 → 404 (IDOR — 존재 비노출, delete와 같은 필터)")
    void setShared_missingOrOthers_throws404() {
        User other = userWithId(2L, "other", "남");
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());
        when(storyRepository.findById(10L))
                .thenReturn(Optional.of(othersPublicStory(other)));

        assertThatThrownBy(() -> service.setShared(me, 99L, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.setShared(me, 10L, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("setShared: 켜기는 멱등 — 이미 켜진 글을 다시 켜도 true (재전송이 꺼 버리지 않는다)")
    void setShared_on_isIdempotent() {
        Story mine = storyWithId(10L, me, "내 글", NOW.minusSeconds(60), publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        assertThat(service.setShared(me, 10L, true).shared()).isTrue();
        assertThat(service.setShared(me, 10L, true).shared()).isTrue();
        assertThat(mine.isShared()).isTrue();
    }

    @Test
    @DisplayName("setShared: 끄면 shared=false — 내린 글은 다음 조회부터 책축에서 빠진다")
    void setShared_off_turnsFalse() {
        Story mine = sharedStoryOf(me, publicBookOf(me, "내 책"));
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        assertThat(service.setShared(me, 10L, false).shared()).isFalse();
        assertThat(mine.isShared()).isFalse();
    }

    @Test
    @DisplayName("setShared: 내 PRIVATE 책 글도 켤 수 있다 — 쓰기 시점엔 검사하지 않는다(공개하면 그때부터 보인다)")
    void setShared_onPrivateOwnBook_allowed() {
        Book privateBook = Book.register(me, "비공개", null, null, null, null, null, BookStatus.READING);
        Story mine = storyWithId(10L, me, "나만 보는 메모", NOW.minusSeconds(60), privateBook);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        assertThat(service.setShared(me, 10L, true).shared()).isTrue();
    }

    // --- bookMarginOf (책축 목록) ---

    private static final String ISBN = "9791168340084";

    private Book bookWithIsbn(User owner, String title, String isbn, boolean makePublic) {
        Book book = Book.register(owner, title, "저자", isbn, "https://img/cover.jpg", null, null,
                BookStatus.READING);
        if (makePublic) {
            book.makePublic();
        }
        return book;
    }

    @Test
    @DisplayName("bookMarginOf: 하이픈 붙은 isbn도 정규화해 조회한다 (클라 방어)")
    void bookMarginOf_normalizesIsbn() {
        User author = userWithId(2L, "author", "글쓴이");
        Story story = sharedStoryOf(author, bookWithIsbn(author, "이 책", ISBN, true));
        when(storyRepository.sharedByIsbn(eq(ISBN), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(story));
        when(storyRepository.countSharedByIsbn(ISBN, 1L)).thenReturn(1L);
        when(bookRepository.findFirstByUserAndIsbn13(me, ISBN)).thenReturn(Optional.empty());
        when(storyLikeRepository.countsByStoryIds(anyList())).thenReturn(List.of());
        when(storyLikeRepository.likedStoryIds(anyList(), eq(me))).thenReturn(List.of());

        BookMarginResponse response = service.bookMarginOf(me, "979-11-6834-008-4");

        assertThat(response.book().isbn13()).isEqualTo(ISBN);
        assertThat(response.book().title()).isEqualTo("이 책");
    }

    @Test
    @DisplayName("bookMarginOf: 알맹이 없는 isbn → 404 (isbn 없는 책은 책축 자체가 없다)")
    void bookMarginOf_blankIsbn_throws404() {
        assertThatThrownBy(() -> service.bookMarginOf(me, " - "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyRepository, never()).sharedByIsbn(any(), any(), any());
    }

    @Test
    @DisplayName("bookMarginOf: 라벨은 내 책 우선 — myBookId가 채워지고 주인 이름은 어디에도 없다")
    void bookMarginOf_prefersMyBookForLabel() {
        User author = userWithId(2L, "author", "글쓴이");
        Book myBook = bookWithIsbn(me, "내가 가진 판", ISBN, false);
        ReflectionTestUtils.setField(myBook, "id", 42L);
        when(bookRepository.findFirstByUserAndIsbn13(me, ISBN)).thenReturn(Optional.of(myBook));
        when(storyRepository.sharedByIsbn(eq(ISBN), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(sharedStoryOf(author, bookWithIsbn(author, "남의 판", ISBN, true))));
        when(storyRepository.countSharedByIsbn(ISBN, 1L)).thenReturn(1L);
        when(storyLikeRepository.countsByStoryIds(anyList())).thenReturn(List.of());
        when(storyLikeRepository.likedStoryIds(anyList(), eq(me))).thenReturn(List.of());

        BookMarginResponse response = service.bookMarginOf(me, ISBN);

        assertThat(response.myBookId()).isEqualTo(42L);
        assertThat(response.book().title()).isEqualTo("내가 가진 판");
    }

    @Test
    @DisplayName("bookMarginOf: 내 책이 없으면 첫 글의 책이 라벨 — myBookId는 null (담기 안내 분기)")
    void bookMarginOf_fallsBackToFirstStoryBook() {
        User author = userWithId(2L, "author", "글쓴이");
        when(bookRepository.findFirstByUserAndIsbn13(me, ISBN)).thenReturn(Optional.empty());
        when(storyRepository.sharedByIsbn(eq(ISBN), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(sharedStoryOf(author, bookWithIsbn(author, "남의 판", ISBN, true))));
        when(storyRepository.countSharedByIsbn(ISBN, 1L)).thenReturn(1L);
        when(storyLikeRepository.countsByStoryIds(anyList())).thenReturn(List.of());
        when(storyLikeRepository.likedStoryIds(anyList(), eq(me))).thenReturn(List.of());

        BookMarginResponse response = service.bookMarginOf(me, ISBN);

        assertThat(response.myBookId()).isNull();
        assertThat(response.book().title()).isEqualTo("남의 판");
        assertThat(response.entries()).extracting(SharedMarginEntry::authorLoginId).containsExactly("author");
        assertThat(response.entries()).extracting(SharedMarginEntry::authorNickname).containsExactly("글쓴이");
    }

    @Test
    @DisplayName("bookMarginOf: 내 책도 없고 함께 걸린 글도 없으면 404 (그릴 헤더가 없다)")
    void bookMarginOf_nothingToLabel_throws404() {
        when(bookRepository.findFirstByUserAndIsbn13(me, ISBN)).thenReturn(Optional.empty());
        when(storyRepository.sharedByIsbn(eq(ISBN), eq(1L), any(Pageable.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.bookMarginOf(me, ISBN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("bookMarginOf: totalCount는 상한과 무관한 진짜 값 + 목록은 100장 상한")
    void bookMarginOf_totalCountIsUncapped() {
        User author = userWithId(2L, "author", "글쓴이");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(bookRepository.findFirstByUserAndIsbn13(me, ISBN)).thenReturn(Optional.empty());
        when(storyRepository.sharedByIsbn(eq(ISBN), eq(1L), captor.capture()))
                .thenReturn(List.of(sharedStoryOf(author, bookWithIsbn(author, "남의 판", ISBN, true))));
        when(storyRepository.countSharedByIsbn(ISBN, 1L)).thenReturn(137L);
        when(storyLikeRepository.countsByStoryIds(anyList())).thenReturn(List.of());
        when(storyLikeRepository.likedStoryIds(anyList(), eq(me))).thenReturn(List.of());

        BookMarginResponse response = service.bookMarginOf(me, ISBN);

        assertThat(response.totalCount()).isEqualTo(137L);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
