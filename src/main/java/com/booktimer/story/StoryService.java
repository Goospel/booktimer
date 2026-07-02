package com.booktimer.story;

import com.booktimer.block.BlockRepository;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.follow.FollowService;
import com.booktimer.profile.ProfileService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독서 스토리 유스케이스 (sns-design §13) — 작성·피드·열람 기록·열람자 목록·본인 삭제.
 *
 * <p>노출 경계(§13.2)가 가장 위험한 지점이다: 피드는 쿼리 자체가 게이트(팔로우 theta 조인 + 만료 필터 +
 * ADMIN·공개핸들 미설정 제외), id를 직접 받는 진입점(열람 기록)은 {@link #canView} 전체 게이트를 재검사한다
 * (IDOR·stale id 방어). 만료는 물리 삭제가 아니라 표시 필터(§13.6) — {@code createdAt} 기준 24h,
 * Instant 절대시간이라 사용자 타임존·자정 경계와 무관하다.
 *
 * <p>게이트 실패를 API 상태코드로 직접 표현해야 해서(레이트리밋 429·상한 400·미노출 404) 이 서비스는
 * 예외적으로 {@link ResponseStatusException}을 던진다 — 프론트는 상태코드로 분기해 안내한다.
 */
@Service
@Transactional
public class StoryService {

    /** 활성(미만료) 스토리 상한 — 뷰어 진행바 UI 보호 + 도배 방지 (§13.5). */
    public static final int MAX_ACTIVE_STORIES = 20;

    /** 스토리 수명 — 이 나이(정확히 24h)부터 표시에서 제외된다(§13.8 경계). */
    private static final Duration LIFETIME = Duration.ofHours(24);

    private final Clock clock;
    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final BookRepository bookRepository;
    private final FollowService followService;
    private final BlockRepository blockRepository;
    private final RateLimitService rateLimitService;
    private final ProfileService profileService;

    public StoryService(Clock clock,
                        StoryRepository storyRepository,
                        StoryViewRepository storyViewRepository,
                        BookRepository bookRepository,
                        FollowService followService,
                        BlockRepository blockRepository,
                        RateLimitService rateLimitService,
                        ProfileService profileService) {
        this.clock = clock;
        this.storyRepository = storyRepository;
        this.storyViewRepository = storyViewRepository;
        this.bookRepository = bookRepository;
        this.followService = followService;
        this.blockRepository = blockRepository;
        this.rateLimitService = rateLimitService;
        this.profileService = profileService;
    }

    /**
     * 스토리를 작성한다. 게이트 순서: 레이트리밋(429 — FOLLOW의 무음 드롭과 달리 안내한다: 작성은
     * 콘텐츠 소실이라 사용자가 원인을 알아야 한다, §13.5) → 활성 상한(400) → 책 검증(남의 책·없음은
     * 404로 존재 누설 방지, 내 책인데 비공개면 400 — 공개 책만 첨부 가능, §13.2).
     *
     * <p>활성 상한은 count-then-insert라 동시 작성 경합 시 순간 초과가 가능한 <b>soft 상한</b>이다 —
     * UI 보호용이라 실해가 없고, 초과 폭은 레이트리밋(시간당 10)이 묶는다. 엄격한 계약이 필요해지면
     * 사용자 행 잠금으로 보강한다.
     */
    public Story create(User author, String text, Long bookId, String bgCode) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "스토리 작성이 너무 잦습니다");
        }
        if (storyRepository.countByUserAndCreatedAtAfter(author, cutoff()) >= MAX_ACTIVE_STORIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "활성 스토리는 최대 " + MAX_ACTIVE_STORIES + "장입니다");
        }
        Book book = null;
        if (bookId != null) {
            book = bookRepository.findByIdAndUser(bookId, author)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
            if (!book.isPublic()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공개 책만 첨부할 수 있습니다");
            }
        }
        return storyRepository.save(Story.of(author, text, book, bgCode));
    }

    /**
     * 홈 스트립 피드 — 내 활성 스토리(별도 필드) + 팔로잉 작성자별 그룹.
     * 정렬: 미열람 있는 작성자(최신 스토리 desc) → 전부 열람(최신 desc), 그룹 내부는 작성순 asc(§13.4).
     */
    @Transactional(readOnly = true)
    public StoryFeedResponse feed(User viewer) {
        Instant cutoff = cutoff();
        List<Story> followed = storyRepository.feedOf(viewer, cutoff);
        List<Story> myActive = storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(viewer, cutoff);

        Set<Long> viewedIds = followed.isEmpty() ? Set.of()
                : Set.copyOf(storyViewRepository.findViewedStoryIds(viewer,
                        followed.stream().map(Story::getId).toList()));

        Map<Long, List<Story>> byAuthor = new LinkedHashMap<>(); // feedOf 정렬(작성자 id asc, 작성순 asc) 보존
        for (Story story : followed) {
            byAuthor.computeIfAbsent(story.getUser().getId(), id -> new ArrayList<>()).add(story);
        }
        List<AuthorStories> groups = new ArrayList<>();
        for (List<Story> stories : byAuthor.values()) {
            groups.add(toAuthorStories(stories.get(0).getUser(), stories, viewedIds));
        }
        groups.sort(Comparator.comparing(AuthorStories::allViewed)
                .thenComparing(StoryService::latestCreatedAt, Comparator.reverseOrder()));

        AuthorStories mine = myActive.isEmpty() ? null : toOwnStories(viewer, myActive);
        return new StoryFeedResponse(mine, groups);
    }

    /**
     * 책방(/u/{loginId})의 그 사람 활성 스토리. 프로필과 동일한 소셜 가시성 가드
     * ({@link ProfileService#resolveVisibleTarget} — 차단·ADMIN·미존재 → 404)를 공유하고,
     * 비팔로워에겐 404가 아니라 <b>빈 배열</b>을 준다 — 스토리 유무 정보도 안 샘(§13.2).
     */
    @Transactional(readOnly = true)
    public List<StoryCard> storiesOf(User viewer, String loginId) {
        User target = profileService.resolveVisibleTarget(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        boolean self = isSameUser(target, viewer);
        if (!self && !followService.isFollowing(viewer, target)) {
            return List.of();
        }
        List<Story> stories = storyRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(target, cutoff());
        if (stories.isEmpty()) {
            return List.of();
        }
        if (self) {
            return stories.stream().map(story -> StoryCard.of(story, true)).toList();
        }
        Set<Long> viewedIds = Set.copyOf(storyViewRepository.findViewedStoryIds(viewer,
                stories.stream().map(Story::getId).toList()));
        return stories.stream().map(story -> StoryCard.of(story, viewedIds.contains(story.getId()))).toList();
    }

    /** 본인 스토리 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스토리를 찾을 수 없습니다"));
        storyViewRepository.deleteByStory(story); // FK 자식 먼저 정리 (T-023 클래스)
        storyRepository.delete(story);
    }

    /**
     * 열람 기록. id 직접 진입점이라 {@link #canView} 전체 게이트를 재검사한다 — 차단·비팔로워·만료·미존재는
     * 전부 404(존재 누설 금지). 본인 열람은 기록하지 않고 성공 no-op(인스타 동일, §13.4).
     *
     * <p>멱등: 사전 존재 검사 + {@code uk_story_view}. 동시 요청 경합의
     * {@link DataIntegrityViolationException}은 <b>여기서 잡지 않는다</b> — INSERT 실패 시점에
     * 트랜잭션이 이미 rollback-only로 마킹돼 삼켜도 커밋에서 터지므로(UnexpectedRollbackException),
     * 트랜잭션 경계 밖인 컨트롤러가 잡아 멱등 성공으로 변환한다.
     */
    public void markViewed(User viewer, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> canView(viewer, s))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스토리를 찾을 수 없습니다"));
        if (isSameUser(story.getUser(), viewer)) {
            return;
        }
        if (storyViewRepository.existsByStoryAndViewer(story, viewer)) {
            return;
        }
        storyViewRepository.save(StoryView.of(story, viewer));
    }

    /** 열람자 목록 — 작성자 본인만(아니면 404). 차단 관계 열람자는 목록에서 제외(§13.4). */
    @Transactional(readOnly = true)
    public List<StoryViewerEntry> viewers(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스토리를 찾을 수 없습니다"));
        return storyViewRepository.findByStoryOrderByCreatedAtDesc(story).stream()
                // 열람자 수십 규모라 행별 차단 검사 허용 — 커지면 배치화 후속
                .filter(view -> !blockRepository.existsBetween(actor, view.getViewer()))
                .map(view -> new StoryViewerEntry(view.getViewer().getLoginId(), view.getViewer().getNickname(),
                        view.getViewer().getProfileCharacterCode(), view.getCreatedAt()))
                .toList();
    }

    /**
     * canViewStory 전체 게이트(§13.2) — 본인 허용 / ADMIN·공개핸들(login_id) 미설정 작성자 미노출(N-055) /
     * 차단(양방향) 미노출 / 비팔로워 미노출 / 만료(정확히 24h부터) 미노출.
     */
    private boolean canView(User viewer, Story story) {
        User author = story.getUser();
        if (isSameUser(author, viewer)) {
            return true;
        }
        if (author.getRole() == Role.ADMIN || author.getLoginId() == null) {
            return false;
        }
        if (blockRepository.existsBetween(viewer, author)) {
            return false;
        }
        if (!followService.isFollowing(viewer, author)) {
            return false;
        }
        return story.getCreatedAt().isAfter(cutoff());
    }

    private AuthorStories toAuthorStories(User author, List<Story> stories, Set<Long> viewedIds) {
        List<StoryCard> cards = stories.stream()
                .map(story -> StoryCard.of(story, viewedIds.contains(story.getId())))
                .toList();
        boolean allViewed = cards.stream().allMatch(StoryCard::viewed);
        return new AuthorStories(author.getLoginId(), author.getNickname(),
                author.getProfileCharacterCode(), allViewed, cards);
    }

    /** 본인 열람은 기록하지 않으므로(markViewed no-op) 내 카드는 항상 viewed — 내 링엔 미열람 강조 없음. */
    private AuthorStories toOwnStories(User author, List<Story> stories) {
        List<StoryCard> cards = stories.stream().map(story -> StoryCard.of(story, true)).toList();
        return new AuthorStories(author.getLoginId(), author.getNickname(),
                author.getProfileCharacterCode(), true, cards);
    }

    private static Instant latestCreatedAt(AuthorStories group) {
        List<StoryCard> stories = group.stories();
        return stories.get(stories.size() - 1).createdAt(); // 작성순 asc라 마지막이 최신
    }

    private Instant cutoff() {
        return clock.instant().minus(LIFETIME);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
