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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 여백 유스케이스 (sns-design §13) — 작성·피드·열람 기록·열람자 목록·본인 삭제.
 *
 * <p>노출 경계(§13.2)가 가장 위험한 지점이다: 피드는 쿼리 자체가 게이트(팔로우 theta 조인 +
 * ADMIN·공개핸들 미설정 제외), id를 직접 받는 진입점(열람 기록)은 {@link #canView} 전체 게이트를 재검사한다
 * (IDOR·stale id 방어).
 *
 * <p><b>여백은 24시간 뒤 사라지던 「스토리」였다</b>(2026-08-16 전환). 인상 깊은 문장을 남기는 자리인데
 * 하루 만에 지워지는 게 의도와 어긋나 시간 만료를 걷어냈다. 그 자리를 <b>{@link #MAX_VISIBLE_STORIES}
 * 표시 상한</b>이 대신한다 — 저장은 영구지만 스트립·뷰어에 실리는 건 최근 N장이다(진행바가 카드 수만큼
 * 세그먼트를 그려서, 무제한 노출은 UI가 못 버틴다). 상한을 넘은 옛 여백은 DB에 남아 있고, 링 UI를 목록으로
 * 바꾸는 후속 작업에서 다시 드러난다.
 *
 * <p>게이트 실패를 API 상태코드로 직접 표현해야 해서(레이트리밋 429·미노출 404) 이 서비스는
 * 예외적으로 {@link ResponseStatusException}을 던진다 — 프론트는 상태코드로 분기해 안내한다.
 */
@Service
@Transactional
public class StoryService {

    /** 한 사람당 화면에 실리는 여백 수 — 뷰어 진행바 UI 보호. 저장은 이 값에 묶이지 않는다. */
    public static final int MAX_VISIBLE_STORIES = 20;

    /**
     * 피드 한 번에 훑는 최대 행 수. 작성자 여럿이 섞여 최신순으로 오므로, 이 창 안에서 작성자별로
     * 나눠 담는다 — 오래 안 쓴 작성자가 창 밖으로 밀리는 건 최신순 스트립의 정상 동작이다
     * (그 사람 여백 전체는 책방에서 본다).
     */
    private static final int FEED_SCAN_LIMIT = 200;

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final BookRepository bookRepository;
    private final FollowService followService;
    private final BlockRepository blockRepository;
    private final RateLimitService rateLimitService;
    private final ProfileService profileService;

    public StoryService(StoryRepository storyRepository,
                        StoryViewRepository storyViewRepository,
                        BookRepository bookRepository,
                        FollowService followService,
                        BlockRepository blockRepository,
                        RateLimitService rateLimitService,
                        ProfileService profileService) {
        this.storyRepository = storyRepository;
        this.storyViewRepository = storyViewRepository;
        this.bookRepository = bookRepository;
        this.followService = followService;
        this.blockRepository = blockRepository;
        this.rateLimitService = rateLimitService;
        this.profileService = profileService;
    }

    /**
     * 여백을 남긴다. 게이트 순서: 레이트리밋(429 — FOLLOW의 무음 드롭과 달리 안내한다: 작성은
     * 콘텐츠 소실이라 사용자가 원인을 알아야 한다, §13.5) → 책 검증(남의 책·없음은
     * 404로 존재 누설 방지, 내 책인데 비공개면 400 — 공개 책만 첨부 가능, §13.2).
     *
     * <p>개수 상한은 없다 — 예전 20장 게이트는 "24시간 창 안의 개수"였고, 만료가 사라진 지금은
     * 그대로 두면 21번째부터 영영 못 쓰는 잠금이 된다. 도배 방어는 레이트리밋(시간당 10)이 맡고,
     * UI 보호는 표시 상한({@link #MAX_VISIBLE_STORIES})이 읽기 쪽에서 맡는다.
     */
    public Story create(User author, String text, Long bookId, String bgCode) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "여백을 너무 자주 남겼습니다");
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
     * 홈 스트립 피드 — 내 여백(별도 필드) + 팔로잉 작성자별 그룹.
     * 정렬: 미열람 있는 작성자(최신 여백 desc) → 전부 열람(최신 desc), 그룹 내부는 작성순 asc(§13.4).
     *
     * <p>레포가 전체 최신순으로 주므로 작성자 묶기와 재생순(asc) 복원이 여기 있다. 열람 배치 조회는
     * 스캔한 200장이 아니라 <b>실제로 실릴 카드</b>만 대상으로 한다 — 상한에 잘려 안 보일 여백의
     * 열람 여부를 물어봐야 답이 안 쓰인다.
     */
    @Transactional(readOnly = true)
    public StoryFeedResponse feed(User viewer) {
        List<Story> followed = storyRepository.feedOf(viewer, PageRequest.of(0, FEED_SCAN_LIMIT));
        List<Story> myRecent = storyRepository.findByUserOrderByCreatedAtDescIdDesc(
                viewer, PageRequest.of(0, MAX_VISIBLE_STORIES));

        Map<Long, List<Story>> byAuthor = new LinkedHashMap<>(); // 최신순 스캔 — 먼저 만난 작성자가 더 최신
        for (Story story : followed) {
            List<Story> bucket = byAuthor.computeIfAbsent(story.getUser().getId(), id -> new ArrayList<>());
            if (bucket.size() < MAX_VISIBLE_STORIES) {
                bucket.add(story);
            }
        }

        List<Long> visibleIds = byAuthor.values().stream().flatMap(List::stream).map(Story::getId).toList();
        Set<Long> viewedIds = visibleIds.isEmpty() ? Set.of()
                : Set.copyOf(storyViewRepository.findViewedStoryIds(viewer, visibleIds));

        List<AuthorStories> groups = new ArrayList<>();
        for (List<Story> newestFirst : byAuthor.values()) {
            List<Story> stories = oldestFirst(newestFirst);
            groups.add(toAuthorStories(stories.get(0).getUser(), stories, viewedIds));
        }
        groups.sort(Comparator.comparing(AuthorStories::allViewed)
                .thenComparing(StoryService::latestCreatedAt, Comparator.reverseOrder()));

        AuthorStories mine = myRecent.isEmpty() ? null : toOwnStories(viewer, oldestFirst(myRecent));
        return new StoryFeedResponse(mine, groups);
    }

    /**
     * 책방(/u/{loginId})의 그 사람 여백. 프로필과 동일한 소셜 가시성 가드
     * ({@link ProfileService#resolveVisibleTarget} — 차단·ADMIN·미존재 → 404)를 공유하고,
     * 비팔로워에겐 404가 아니라 <b>빈 배열</b>을 준다 — 여백 유무 정보도 안 샘(§13.2).
     */
    @Transactional(readOnly = true)
    public List<StoryCard> storiesOf(User viewer, String loginId) {
        User target = profileService.resolveVisibleTarget(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        boolean self = isSameUser(target, viewer);
        if (!self && !followService.isFollowing(viewer, target)) {
            return List.of();
        }
        List<Story> recent = storyRepository.findByUserOrderByCreatedAtDescIdDesc(
                target, PageRequest.of(0, MAX_VISIBLE_STORIES));
        if (recent.isEmpty()) {
            return List.of();
        }
        List<Story> stories = oldestFirst(recent);
        if (self) {
            return stories.stream().map(story -> StoryCard.of(story, true)).toList();
        }
        Set<Long> viewedIds = Set.copyOf(storyViewRepository.findViewedStoryIds(viewer,
                stories.stream().map(Story::getId).toList()));
        return stories.stream().map(story -> StoryCard.of(story, viewedIds.contains(story.getId()))).toList();
    }

    /** 본인 여백 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여백을 찾을 수 없습니다"));
        storyViewRepository.deleteByStory(story); // FK 자식 먼저 정리 (T-023 클래스)
        storyRepository.delete(story);
    }

    /**
     * 열람 기록. id 직접 진입점이라 {@link #canView} 전체 게이트를 재검사한다 — 차단·비팔로워·미존재는
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여백을 찾을 수 없습니다"));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여백을 찾을 수 없습니다"));
        return storyViewRepository.findByStoryOrderByCreatedAtDesc(story).stream()
                // 열람자 수십 규모라 행별 차단 검사 허용 — 커지면 배치화 후속
                .filter(view -> !blockRepository.existsBetween(actor, view.getViewer()))
                .map(view -> new StoryViewerEntry(view.getViewer().getLoginId(), view.getViewer().getNickname(),
                        view.getViewer().getProfileCharacterCode(), view.getCreatedAt()))
                .toList();
    }

    /**
     * canViewStory 전체 게이트(§13.2) — 본인 허용 / ADMIN·공개핸들(login_id) 미설정 작성자 미노출(N-055) /
     * 차단(양방향) 미노출 / 비팔로워 미노출.
     *
     * <p>나이는 더 이상 게이트가 아니다 — 표시 상한에 잘려 스트립엔 안 보이는 옛 여백이라도, 링크·stale id로
     * 닿았다면 그건 원래 볼 수 있던 사람이다. 여기서 막으면 "안 사라진다"는 약속과 어긋난다.
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
        return followService.isFollowing(viewer, author);
    }

    /** 레포는 최신순으로 준다 — 뷰어 재생 순서(오래된 것부터)로 뒤집는다. */
    private static List<Story> oldestFirst(List<Story> newestFirst) {
        List<Story> copy = new ArrayList<>(newestFirst);
        Collections.reverse(copy);
        return copy;
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

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
