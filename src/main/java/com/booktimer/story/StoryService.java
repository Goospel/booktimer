package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.Isbn;
import com.booktimer.profile.ProfileService;
import com.booktimer.search.UserRowAssembler;
import com.booktimer.search.UserSearchResult;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여백 유스케이스 — 쪽지 쓰기 · 고치기 · 지우기 · 본인 읽기 (sns-design §13 · 책생글 「여백 + 블로그」 §5.1).
 *
 * <p><b>여백은 본인만의 공간이다</b>(2026-10-30 책생글 M1 — D1 · C3). 남이 부르는 읽기는 전부 빈 목록 · 404다 — 공개는 블로그가 맡는다.
 * 옛 경로({@code /api/stories/**} — 웹 · 동결 미니앱)의 모양은 그대로 두고 내용만 비운다: 응답 모양을 바꾸면 동결 미니앱이 깨진다.
 *
 * <p>게이트 실패를 API 상태코드로 직접 표현해야 해서(레이트리밋 429 · 미노출 404 · 낡은 판 409) 이 서비스는
 * 예외적으로 {@link ResponseStatusException}을 던진다 — 도메인 검증(길이 등)은 {@code IllegalArgumentException}으로 둔다.
 */
@Service
@Transactional
public class StoryService {

    /**
     * 웹 여백 한 장에서 한 번에 내려주는 글 수.
     * ponytail: 넘치면 헤더의 N이 100에서 멈춘다 — 페이지네이션은 그때 붙인다.
     */
    public static final int MAX_MARGIN_ENTRIES = 100;

    /** 앱 책 없는 쪽지 목록 상한 — {@code WritingService.MARGINS_MAX}(책 한 권 200)와 같다. ponytail: 넘으면 페이지를 붙인다. */
    public static final int MAX_NOTES = 200;

    /** 옛 API(웹 · 동결 미니앱)가 그릴 줄 아는 생각 길이 — 넘는 쪽지는 잘라 준다(J85). */
    static final int LEGACY_TEXT_MAX = 500;

    private final StoryRepository storyRepository;
    private final BookRepository bookRepository;
    private final RateLimitService rateLimitService;
    private final ProfileService profileService;
    private final StoryLikeRepository storyLikeRepository;
    private final UserRowAssembler rowAssembler;
    private final Clock clock;

    public StoryService(StoryRepository storyRepository,
                        BookRepository bookRepository,
                        RateLimitService rateLimitService,
                        ProfileService profileService,
                        StoryLikeRepository storyLikeRepository,
                        UserRowAssembler rowAssembler,
                        Clock clock) {
        this.storyRepository = storyRepository;
        this.bookRepository = bookRepository;
        this.rateLimitService = rateLimitService;
        this.profileService = profileService;
        this.storyLikeRepository = storyLikeRepository;
        this.rowAssembler = rowAssembler;
        this.clock = clock;
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────────────

    /**
     * 웹 · 동결 미니앱의 여백 글 남기기({@code POST /api/stories}). 게이트 순서: 레이트리밋(429) → 책(없으면 400, 남의 책·미존재 404).
     * <b>책은 여전히 필수다</b> — 엔티티가 책 없는 쪽지를 받게 됐으므로 이 문이 진다(REQ-219). 책 없는 쪽지는 앱 경로({@link #createNote})에서만 생긴다.
     *
     * <p>{@code shared}는 <b>무시하고 꺼진 채 저장한다</b>(REQ-213 · J9) — 동결 미니앱이 「함께 걸기」 체크 값을 몸에 싣는다.
     * 400이면 그 글이 저장되지 않는다.
     */
    public Story create(User author, String text, Long bookId, String bgCode, String quote, boolean shared) {
        checkCreateRate(author);
        if (bookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 지정해야 합니다");
        }
        Book book = bookRepository.findByIdAndUser(bookId, author)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
        return storyRepository.save(Story.of(author, text, book, bgCode, quote));
    }

    /**
     * 앱 쪽지 만들기({@code POST /api/app/margins}) — 첫 저장 때만 행이 생긴다(빈 행 0 — J6). {@code bookId}가 없으면 책 없는 쪽지(C9).
     *
     * @throws ResponseStatusException  429 시간당 상한 · 404 남의 · 없는 · 공부 서재 책
     * @throws IllegalArgumentException 길이 · 빈 쪽지 · 책 없는 옮긴 문장(→ 400)
     */
    public Story createNote(User author, Long bookId, String quote, String text) {
        checkCreateRate(author);
        Book book = bookId == null ? null : bookRepository.findByIdAndUser(bookId, author)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없어요"));
        return storyRepository.save(Story.of(author, text, book, null, quote));
    }

    /** 만들기 상한(시간당 30 — J7). 고치기는 만들기가 아니라 걸리지 않는다. */
    private void checkCreateRate(User author) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "글을 너무 자주 남겼어요 — 잠시 뒤 다시 남겨 주세요");
        }
    }

    /**
     * 앱 쪽지 고치기({@code PUT /api/app/margins/{id}}) — 소유 판정(404)이 검증(400) · 판 대조(409)보다 먼저다.
     *
     * @param exported {@code true}면 처음 한 번만 내보낸 시각을 박는다(C8 · REQ-227)
     */
    public Story editNote(User actor, Long storyId, String quote, String text, int revision, Boolean exported) {
        Story story = note(actor, storyId);
        try {
            story.edit(quote, text, revision);
        } catch (Story.StaleMarginException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 기기에서 이 쪽지가 바뀌었어요 — 그쪽을 불러와 주세요");
        }
        if (Boolean.TRUE.equals(exported)) {
            story.markExported(clock.instant());
        }
        return story;
    }

    /** 본인 글 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyLikeRepository.deleteByStory(story); // story_like.story_id FK — 글보다 먼저
        storyRepository.delete(story);
    }

    // ── 본인 읽기 ────────────────────────────────────────────────────────────

    /** 본인 쪽지 하나 — 없거나 남의 것이면 404(존재 비노출). */
    @Transactional(readOnly = true)
    public Story note(User owner, Long storyId) {
        return storyRepository.findWithBook(storyId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쪽지를 찾을 수 없어요"));
    }

    /** 본인의 책 없는 쪽지 — 최근순(C9). */
    @Transactional(readOnly = true)
    public List<Story> booklessNotes(User owner) {
        return storyRepository.findByUserAndBookIsNullOrderByCreatedAtDescIdDesc(owner, PageRequest.of(0, MAX_NOTES));
    }

    /**
     * 책 하나의 여백(웹 · 동결 미니앱 {@code /api/stories/of/{loginId}?bookId}). 게이트(차단 · ADMIN · 미존재 · 남의 PRIVATE 책 →
     * 404)는 그대로 두고, <b>본인이 아니면 글이 0건</b>이다(C3 — 머리만 나간다, 응답 모양은 동결 미니앱 호환).
     */
    @Transactional(readOnly = true)
    public MarginResponse marginOf(User viewer, String loginId, Long bookId) {
        User target = profileService.resolveVisibleTarget(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        Book book = bookRepository.findByIdAndUser(bookId, target)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        boolean self = isSameUser(target, viewer);
        if (!book.isPublic() && !self) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
        List<Story> stories = self ? legacyVisible(storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(
                target, book, PageRequest.of(0, MAX_MARGIN_ENTRIES))) : List.of();
        return new MarginResponse(MarginBook.of(book), target.getNickname(), self, withLikes(stories, viewer));
    }

    /**
     * 한 사람의 여백 전체(웹 책방 「여백」 탭 · 동결 미니앱 「내 여백 전체」). 게이트(차단 · ADMIN · 미존재 → 404)는 그대로,
     * <b>본인이 아니면 빈 목록</b>(C3). 책 없는 쪽지는 빠진다(쿼리가 inner join — J64).
     */
    @Transactional(readOnly = true)
    public List<ProfileMarginEntry> allMarginsOf(User viewer, String loginId) {
        User target = profileService.resolveVisibleTarget(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        if (!isSameUser(target, viewer)) {
            return List.of();
        }
        List<Story> stories = legacyVisible(storyRepository.recentByUser(target, PageRequest.of(0, MAX_MARGIN_ENTRIES)));
        Likes likes = stories.isEmpty() ? new Likes(Map.of(), Set.of()) : likesOf(stories, viewer);
        return stories.stream()
                .map(s -> ProfileMarginEntry.of(s, likes.countOf(s), likes.likedBy(s)))
                .toList();
    }

    /**
     * 「이 책의 여백」(책축 — 웹 · 동결 미니앱 {@code /api/stories/book/{isbn}}). 「모두의 여백」이 걷혀 <b>글은 늘 0건</b>이다(C3).
     * 머리는 viewer 본인 책 행이고, 없으면 404(그릴 머리가 없다).
     */
    @Transactional(readOnly = true)
    public BookMarginResponse bookMarginOf(User viewer, String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        Book label = isbn == null ? null : bookRepository.findFirstByUserAndIsbn13(viewer, isbn).orElse(null);
        if (label == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다");
        }
        return new BookMarginResponse(BookMarginLabel.of(label), label.getId(), 0, List.of());
    }

    /**
     * 옛 API가 그릴 수 있는 쪽지만 — 생각 · 옮긴 문장이 다 빈 쪽지(질문 기록만 남은 옮긴 쪽지 · B1부터 건 블로그 문장만 있는 쪽지)는
     * 뺀다. 옛 화면은 빈 카드를 그릴 줄 모르고 고치기 경로가 없다(J85). 긴 생각 자르기는 응답 DTO가 한다({@link #legacyText}).
     */
    private static List<Story> legacyVisible(List<Story> stories) {
        return stories.stream().filter(s -> !s.getText().isEmpty() || s.getQuote() != null).toList();
    }

    /** 옛 API 응답의 생각 — {@value #LEGACY_TEXT_MAX}자를 넘으면 잘라 끝에 「…」(J85 — 옛 상한 안으로). */
    static String legacyText(String text) {
        return text.length() <= LEGACY_TEXT_MAX ? text : text.substring(0, LEGACY_TEXT_MAX - 1) + "…";
    }

    // ── 함께 걸기 · 좋아요(옛 경로 — 본인만) ────────────────────────────────────

    /**
     * 「함께 걸기」 — <b>켜기는 400</b>(C3 · REQ-213: 여백은 나만 본다). 끄기는 본인 글이면 200 그대로(없거나 남의 것이면 404).
     */
    public ShareState setShared(User actor, Long storyId, boolean shared) {
        if (shared) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여백은 나만 봐요 — 함께 걸 수 없어요");
        }
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        story.markShared(false);
        return new ShareState(false);
    }

    /** 켜기·끄기 직후의 상태 — 클라가 추측하지 않게 서버가 읽은 값을 그대로 준다({@link LikeState} 관례). */
    public record ShareState(boolean shared) {
    }

    /**
     * 좋아요 — <b>내 쪽지에만</b>({@link #assertVisible}). 멱등: 이미 눌러 둔 글이면 저장하지 않는다(모바일 재전송이 취소로 뒤집히지 않게).
     */
    public LikeState like(User viewer, Long storyId) {
        if (!rateLimitService.allow(RateLimitAction.STORY_LIKE, viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "좋아요를 너무 자주 눌렀습니다");
        }
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        assertVisible(viewer, story);
        if (storyLikeRepository.findByStoryAndUser(story, viewer).isEmpty()) {
            storyLikeRepository.save(StoryLike.of(viewer, story));
        }
        return new LikeState(storyLikeRepository.countByStory(story), true);
    }

    /** 좋아요 취소 — 남의 여백은 404(C3). M1 전에 남이 눌러 둔 행이 있어도 열리지 않는다(V104가 행을 지운다). */
    public LikeState unlike(User viewer, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        assertVisible(viewer, story);
        StoryLike like = storyLikeRepository.findByStoryAndUser(story, viewer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyLikeRepository.delete(like);
        return new LikeState(storyLikeRepository.countByStory(story), false);
    }

    /**
     * 「이 쪽지가 viewer에게 보이는가」 — <b>본인만</b>(C3 · {@link Story} 불변식). 책을 읽지 않는다 — 책 없는 쪽지도 같은 판정이다.
     * 좋아요 · 명단 · 좋아요 취소가 이것 하나를 공유한다.
     */
    private static void assertVisible(User viewer, Story story) {
        if (!isSameUser(story.getUser(), viewer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
    }

    /** 내 쪽지에 좋아요를 누른 사람들 — 남의 쪽지는 404({@link #assertVisible}). 핸들 없는 · 차단 관계인 사람은 뺀다. */
    @Transactional(readOnly = true)
    public List<UserSearchResult> likers(User viewer, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        assertVisible(viewer, story);
        List<User> targets = storyLikeRepository.findByStoryOrderByCreatedAtDescIdDesc(story).stream()
                .map(StoryLike::getUser)
                .filter(u -> u.getLoginId() != null)
                .filter(u -> profileService.resolveVisibleTarget(viewer, u.getLoginId()).isPresent())
                .toList();
        return rowAssembler.toRows(viewer, targets);
    }

    /** 누르기·취소 직후의 상태 — 클라가 개수를 추측하지 않게 서버가 센 값을 그대로 준다. */
    public record LikeState(long likeCount, boolean liked) {
    }

    /** 글 목록에 좋아요 집계를 붙인다 — 배치 2쿼리(N+1 금지). 빈 목록이면 쿼리를 아예 안 던진다. */
    private List<MarginEntry> withLikes(List<Story> stories, User viewer) {
        if (stories.isEmpty()) {
            return List.of();
        }
        Likes likes = likesOf(stories, viewer);
        return stories.stream()
                .map(s -> MarginEntry.of(s, likes.countOf(s), likes.likedBy(s)))
                .toList();
    }

    private record Likes(Map<Long, Long> counts, Set<Long> liked) {
        long countOf(Story story) {
            return counts.getOrDefault(story.getId(), 0L);
        }

        boolean likedBy(Story story) {
            return liked.contains(story.getId());
        }
    }

    private Likes likesOf(List<Story> stories, User viewer) {
        List<Long> ids = stories.stream().map(Story::getId).toList();
        Map<Long, Long> counts = storyLikeRepository.countsByStoryIds(ids).stream()
                .collect(Collectors.toMap(StoryLikeRepository.StoryLikeCount::getStoryId,
                        StoryLikeRepository.StoryLikeCount::getCount));
        return new Likes(counts, Set.copyOf(storyLikeRepository.likedStoryIds(ids, viewer)));
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
