package com.booktimer.story;

import com.booktimer.block.BlockRepository;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private StoryViewRepository storyViewRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private FollowService followService;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private ProfileService profileService;

    private StoryService service;

    private User me;

    @BeforeEach
    void setUp() {
        service = new StoryService(Clock.fixed(NOW, ZoneOffset.UTC), storyRepository, storyViewRepository,
                bookRepository, followService, blockRepository, rateLimitService, profileService);
        me = userWithId(1L, "meuser", "나");
    }

    private User userWithId(long id, String loginId, String nickname) {
        User u = User.of(loginId + "@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Story storyWithId(long id, User author, String text, Instant createdAt, Book book, String bgCode) {
        Story s = Story.of(author, text, book, bgCode);
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

        assertThatThrownBy(() -> service.create(me, "문장", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 활성 20장 도달 → 400")
    void create_activeCapReached_throws400() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(storyRepository.countByUserAndCreatedAtAfter(me, CUTOFF)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(me, "문장", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 없는 책·남의 책 → 404 (존재 누설 방지)")
    void create_missingOrOthersBook_throws404() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(storyRepository.countByUserAndCreatedAtAfter(me, CUTOFF)).thenReturn(0L);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("create: 내 책인데 PRIVATE → 400 (공개 책만 첨부)")
    void create_privateOwnBook_throws400() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(storyRepository.countByUserAndCreatedAtAfter(me, CUTOFF)).thenReturn(0L);
        Book privateBook = Book.register(me, "비공개", null, null, null, null, null, BookStatus.READING);
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(privateBook));

        assertThatThrownBy(() -> service.create(me, "문장", 5L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create: 정상 — 공개 자기 책 첨부 저장")
    void create_valid_saves() {
        when(rateLimitService.allow(RateLimitAction.STORY_CREATE, 1L)).thenReturn(true);
        when(storyRepository.countByUserAndCreatedAtAfter(me, CUTOFF)).thenReturn(19L);
        Book mine = publicBookOf(me, "내 공개 책");
        when(bookRepository.findByIdAndUser(5L, me)).thenReturn(Optional.of(mine));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = service.create(me, "인상 깊은 문장", 5L, "night");

        assertThat(saved.getText()).isEqualTo("인상 깊은 문장");
        assertThat(saved.getBook()).isSameAs(mine);
        assertThat(saved.getBgCode()).isEqualTo("night");
    }

    // --- feed ---

    @Test
    @DisplayName("feed: 작성자별 그룹핑 + mine 분리 + 미열람 작성자 우선(최신 desc) + allViewed 계산")
    void feed_groupsSortsAndSeparatesMine() {
        User allRead = userWithId(2L, "allread", "다읽음");
        User freshNew = userWithId(3L, "freshnew", "신선");
        User freshOld = userWithId(4L, "freshold", "덜신선");
        Story a1 = storyWithId(10L, allRead, "a-1", NOW.minusSeconds(7200), null, null);
        Story a2 = storyWithId(11L, allRead, "a-2", NOW.minusSeconds(3600), null, null);
        Story b1 = storyWithId(12L, freshNew, "b-1", NOW.minusSeconds(60), null, null);
        Story c1 = storyWithId(13L, freshOld, "c-1", NOW.minusSeconds(600), null, null);
        when(storyRepository.feedOf(me, CUTOFF)).thenReturn(List.of(a1, a2, b1, c1));
        Story m1 = storyWithId(20L, me, "내 문장", NOW.minusSeconds(30), null, null);
        when(storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF)).thenReturn(List.of(m1));
        when(storyViewRepository.findViewedStoryIds(eq(me), anyCollection())).thenReturn(List.of(10L, 11L));

        StoryFeedResponse feed = service.feed(me);

        assertThat(feed.mine()).isNotNull();
        assertThat(feed.mine().loginId()).isEqualTo("meuser");
        assertThat(feed.mine().allViewed()).isTrue();
        assertThat(feed.mine().stories()).extracting(StoryCard::text).containsExactly("내 문장");
        assertThat(feed.mine().stories()).allSatisfy(card -> assertThat(card.viewed()).isTrue());
        // 미열람 있는 작성자(최신 desc): freshNew(60s 전) → freshOld(600s 전) → 전부 열람: allRead
        assertThat(feed.groups()).extracting(AuthorStories::loginId)
                .containsExactly("freshnew", "freshold", "allread");
        assertThat(feed.groups().get(0).allViewed()).isFalse();
        assertThat(feed.groups().get(2).allViewed()).isTrue();
        // 그룹 내부 작성순 asc + viewed 플래그
        assertThat(feed.groups().get(2).stories()).extracting(StoryCard::text).containsExactly("a-1", "a-2");
        assertThat(feed.groups().get(2).stories()).allSatisfy(card -> assertThat(card.viewed()).isTrue());
        assertThat(feed.groups().get(0).stories().get(0).viewed()).isFalse();
    }

    @Test
    @DisplayName("feed: 스토리가 하나도 없으면 mine=null, groups=[] — 열람 배치 조회도 안 탄다")
    void feed_empty_returnsNullMineAndEmptyGroups() {
        when(storyRepository.feedOf(me, CUTOFF)).thenReturn(List.of());
        when(storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF)).thenReturn(List.of());

        StoryFeedResponse feed = service.feed(me);

        assertThat(feed.mine()).isNull();
        assertThat(feed.groups()).isEmpty();
        verify(storyViewRepository, never()).findViewedStoryIds(any(), anyCollection());
    }

    @Test
    @DisplayName("feed 책 라벨 표시 시점 재검사: 첨부 후 PRIVATE 전환 → 라벨만 숨고 문장 유지 (§13.2)")
    void feed_rechecksBookVisibilityAtDisplayTime() {
        User author = userWithId(2L, "author", "작가");
        Book turnedPrivate = publicBookOf(author, "한때 공개");
        Story withHidden = storyWithId(10L, author, "라벨 숨을 문장", NOW.minusSeconds(120), turnedPrivate, null);
        turnedPrivate.makePrivate(); // 첨부 후 비공개 전환
        Book stillPublic = publicBookOf(author, "여전히 공개");
        Story withLabel = storyWithId(11L, author, "라벨 보일 문장", NOW.minusSeconds(60), stillPublic, null);
        when(storyRepository.feedOf(me, CUTOFF)).thenReturn(List.of(withHidden, withLabel));
        when(storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF)).thenReturn(List.of());
        when(storyViewRepository.findViewedStoryIds(eq(me), anyCollection())).thenReturn(List.of());

        StoryFeedResponse feed = service.feed(me);

        List<StoryCard> cards = feed.groups().get(0).stories();
        assertThat(cards.get(0).text()).isEqualTo("라벨 숨을 문장");
        assertThat(cards.get(0).bookTitle()).isNull();
        assertThat(cards.get(0).bookCoverUrl()).isNull();
        assertThat(cards.get(1).bookTitle()).isEqualTo("여전히 공개");
        assertThat(cards.get(1).bookCoverUrl()).isEqualTo("https://img/cover.jpg");
    }

    // --- storiesOf ---

    @Test
    @DisplayName("storiesOf: 가드 실패(차단·ADMIN·미존재) → 404 (프로필 가드와 동일 경로)")
    void storiesOf_guardFails_throws404() {
        when(profileService.resolveVisibleTarget(me, "hidden")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.storiesOf(me, "hidden"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("storiesOf: 비팔로워 → 200 + 빈 배열 (404 아님 — 스토리 유무 정보도 안 샘)")
    void storiesOf_nonFollower_returnsEmpty() {
        User target = userWithId(2L, "target", "대상");
        when(profileService.resolveVisibleTarget(me, "target")).thenReturn(Optional.of(target));
        when(followService.isFollowing(me, target)).thenReturn(false);

        List<StoryCard> cards = service.storiesOf(me, "target");

        assertThat(cards).isEmpty();
        verify(storyRepository, never()).findByUserAndCreatedAtAfterOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("storiesOf: 팔로워 → 활성 목록 + viewed 플래그")
    void storiesOf_follower_returnsActiveCards() {
        User target = userWithId(2L, "target", "대상");
        when(profileService.resolveVisibleTarget(me, "target")).thenReturn(Optional.of(target));
        when(followService.isFollowing(me, target)).thenReturn(true);
        Story s1 = storyWithId(10L, target, "첫째", NOW.minusSeconds(600), null, null);
        Story s2 = storyWithId(11L, target, "둘째", NOW.minusSeconds(60), null, null);
        when(storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(target, CUTOFF)).thenReturn(List.of(s1, s2));
        when(storyViewRepository.findViewedStoryIds(eq(me), anyCollection())).thenReturn(List.of(10L));

        List<StoryCard> cards = service.storiesOf(me, "target");

        assertThat(cards).extracting(StoryCard::text).containsExactly("첫째", "둘째");
        assertThat(cards.get(0).viewed()).isTrue();
        assertThat(cards.get(1).viewed()).isFalse();
    }

    @Test
    @DisplayName("storiesOf: 본인 → 활성 목록 (팔로우 검사 없이, viewed=true)")
    void storiesOf_self_returnsOwnActive() {
        when(profileService.resolveVisibleTarget(me, "meuser")).thenReturn(Optional.of(me));
        Story s1 = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(me, CUTOFF)).thenReturn(List.of(s1));

        List<StoryCard> cards = service.storiesOf(me, "meuser");

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).viewed()).isTrue();
        verify(followService, never()).isFollowing(any(), any());
    }

    // --- delete ---

    @Test
    @DisplayName("delete: 없는 스토리·타인 스토리 → 404 (IDOR)")
    void delete_missingOrOthers_throws404() {
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(me, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));

        User other = userWithId(2L, "other", "타인");
        Story others = storyWithId(10L, other, "남의 문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(others));
        assertThatThrownBy(() -> service.delete(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 본인 스토리 — view 자식 먼저 지우고 스토리 삭제 (FK 순서)")
    void delete_owner_deletesViewsFirst() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        service.delete(me, 10L);

        InOrder order = inOrder(storyViewRepository, storyRepository);
        order.verify(storyViewRepository).deleteByStory(mine);
        order.verify(storyRepository).delete(mine);
    }

    // --- markViewed ---

    @Test
    @DisplayName("markViewed: 팔로워 첫 열람 → 기록 저장")
    void markViewed_follower_recordsView() {
        User author = userWithId(2L, "author", "작가");
        Story story = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(blockRepository.existsBetween(me, author)).thenReturn(false);
        when(followService.isFollowing(me, author)).thenReturn(true);
        when(storyViewRepository.existsByStoryAndViewer(story, me)).thenReturn(false);

        service.markViewed(me, 10L);

        verify(storyViewRepository).save(any(StoryView.class));
    }

    @Test
    @DisplayName("markViewed: 이미 열람 → 중복 저장 없이 성공 (멱등)")
    void markViewed_alreadyViewed_isIdempotent() {
        User author = userWithId(2L, "author", "작가");
        Story story = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(blockRepository.existsBetween(me, author)).thenReturn(false);
        when(followService.isFollowing(me, author)).thenReturn(true);
        when(storyViewRepository.existsByStoryAndViewer(story, me)).thenReturn(true);

        assertThatCode(() -> service.markViewed(me, 10L)).doesNotThrowAnyException();
        verify(storyViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("markViewed: 유니크 경합(동시 요청) → DIV를 삼키지 않고 전파 (rollback-only 함정 — 컨트롤러가 멱등 변환)")
    void markViewed_racingDuplicate_propagatesViolation() {
        User author = userWithId(2L, "author", "작가");
        Story story = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(blockRepository.existsBetween(me, author)).thenReturn(false);
        when(followService.isFollowing(me, author)).thenReturn(true);
        when(storyViewRepository.existsByStoryAndViewer(story, me)).thenReturn(false);
        when(storyViewRepository.save(any(StoryView.class))).thenThrow(new DataIntegrityViolationException("uk_story_view"));

        // 트랜잭션 안에서 삼키면 이미 rollback-only라 커밋에서 500 — 경계 밖(컨트롤러)에서 처리해야 한다
        assertThatThrownBy(() -> service.markViewed(me, 10L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("markViewed: 본인 스토리 → 기록 없이 성공 no-op (인스타 동일)")
    void markViewed_self_noopWithoutRecord() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));

        assertThatCode(() -> service.markViewed(me, 10L)).doesNotThrowAnyException();
        verify(storyViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("markViewed: 만료(정확히 24h) → 404 — stale id 재검사")
    void markViewed_expired_throws404() {
        User author = userWithId(2L, "author", "작가");
        Story expired = storyWithId(10L, author, "문장", CUTOFF, null, null); // 나이 = 정확히 24h → 만료
        when(storyRepository.findById(10L)).thenReturn(Optional.of(expired));
        when(blockRepository.existsBetween(me, author)).thenReturn(false);
        when(followService.isFollowing(me, author)).thenReturn(true);

        assertThatThrownBy(() -> service.markViewed(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("markViewed: 비팔로워 → 404 (존재 누설 금지)")
    void markViewed_nonFollower_throws404() {
        User author = userWithId(2L, "author", "작가");
        Story story = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(blockRepository.existsBetween(me, author)).thenReturn(false);
        when(followService.isFollowing(me, author)).thenReturn(false);

        assertThatThrownBy(() -> service.markViewed(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("markViewed: 차단 관계 → 404 (양방향)")
    void markViewed_blocked_throws404() {
        User author = userWithId(2L, "author", "작가");
        Story story = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
        when(blockRepository.existsBetween(me, author)).thenReturn(true);

        assertThatThrownBy(() -> service.markViewed(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storyViewRepository, never()).save(any());
    }

    // --- viewers ---

    @Test
    @DisplayName("viewers: 작성자 본인이 아니면 404")
    void viewers_notAuthor_throws404() {
        User author = userWithId(2L, "author", "작가");
        Story others = storyWithId(10L, author, "문장", NOW.minusSeconds(60), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.viewers(me, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("viewers: 차단 관계 열람자는 목록에서 제외, 나머지는 열람 시각과 함께")
    void viewers_excludesBlockedViewers() {
        Story mine = storyWithId(10L, me, "내 문장", NOW.minusSeconds(600), null, null);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(mine));
        User friendly = userWithId(2L, "friendly", "친구");
        User hostile = userWithId(3L, "hostile", "차단됨");
        StoryView v1 = StoryView.of(mine, friendly);
        ReflectionTestUtils.setField(v1, "createdAt", NOW.minusSeconds(60));
        StoryView v2 = StoryView.of(mine, hostile);
        ReflectionTestUtils.setField(v2, "createdAt", NOW.minusSeconds(30));
        when(storyViewRepository.findByStoryOrderByCreatedAtDesc(mine)).thenReturn(List.of(v2, v1));
        when(blockRepository.existsBetween(me, friendly)).thenReturn(false);
        when(blockRepository.existsBetween(me, hostile)).thenReturn(true);

        List<StoryViewerEntry> viewers = service.viewers(me, 10L);

        assertThat(viewers).hasSize(1);
        assertThat(viewers.get(0).loginId()).isEqualTo("friendly");
        assertThat(viewers.get(0).nickname()).isEqualTo("친구");
        assertThat(viewers.get(0).viewedAt()).isEqualTo(NOW.minusSeconds(60));
    }
}
