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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여백 유스케이스 (sns-design §13) — 글 작성·본인 삭제.
 *
 * <p><b>여백은 책에 딸린 자리다</b>(2026-08-16 재설계). 사람 단위 스트립·전체화면 뷰어·열람 기록은
 * 전부 폐기됐고(V71이 story_view를 드롭), 글은 「책방 격자 → 책 → 그 책의 글 목록」으로만 도달한다.
 * 그래서 {@code bookId}가 선택이 아니라 필수다 — 책 없는 글은 아무 데도 실리지 않는다.
 *
 * <p>게이트 실패를 API 상태코드로 직접 표현해야 해서(레이트리밋 429·미노출 404) 이 서비스는
 * 예외적으로 {@link ResponseStatusException}을 던진다 — 프론트는 상태코드로 분기해 안내한다.
 */
@Service
@Transactional
public class StoryService {

    /**
     * 한 책의 여백에서 한 번에 내려주는 글 수. 레이트리밋이 시간당 10장이라 도달까지 수개월이다.
     * ponytail: 넘치면 헤더의 N이 100에서 멈춘다 — 페이지네이션은 그때 붙인다.
     */
    public static final int MAX_MARGIN_ENTRIES = 100;

    private final StoryRepository storyRepository;
    private final BookRepository bookRepository;
    private final RateLimitService rateLimitService;
    private final ProfileService profileService;
    private final StoryLikeRepository storyLikeRepository;
    private final UserRowAssembler rowAssembler;

    // FollowService 의존성은 2026-08-22에 걷었다 — 여백 노출에서 팔로우 축이 사라지면서 이 서비스가
    // 팔로우를 물어볼 일이 하나도 남지 않았다. 게이트를 되살리려는 손이 있으면 여기서 먼저 막힌다.
    public StoryService(StoryRepository storyRepository,
                        BookRepository bookRepository,
                        RateLimitService rateLimitService,
                        ProfileService profileService,
                        StoryLikeRepository storyLikeRepository,
                        UserRowAssembler rowAssembler) {
        this.storyRepository = storyRepository;
        this.bookRepository = bookRepository;
        this.rateLimitService = rateLimitService;
        this.profileService = profileService;
        this.storyLikeRepository = storyLikeRepository;
        this.rowAssembler = rowAssembler;
    }

    /**
     * 여백에 글을 남긴다. 게이트 순서: 레이트리밋(429 — FOLLOW의 무음 드롭과 달리 안내한다: 작성은
     * 콘텐츠 소실이라 사용자가 원인을 알아야 한다, §13.5) → 책 검증(없는 bookId는 400, 남의 책·미존재는
     * 404로 존재 누설 방지).
     *
     * <p><b>공개 여부는 묻지 않는다</b>(2026-08-16 결정 2) — 비공개 책의 여백은 나만 보는 메모다.
     * 남에게 안 새게 하는 책임은 읽기 쪽({@link #marginOf} · {@code feedRecent})이 전부 진다.
     *
     * <p>개수 상한은 없다 — 도배 방어는 레이트리밋(시간당 10)이 맡고, 목록 폭주는 읽기 쪽 상한이 맡는다.
     *
     * <p>{@code quote}(책에서 옮긴 문장)는 선택이다 — 길이·공백 정규화는 전부 {@link Story#of}가 한다.
     *
     * <p>{@code shared}(「함께 걸기」)는 <b>기본 꺼짐</b>이고 켜도 여기서는 아무것도 검사하지 않는다 —
     * 비공개 책에서 켜 두는 것도 유효하고, 그 글은 책이 공개되는 순간부터만 보인다({@link Story} 불변식).
     */
    public Story create(User author, String text, Long bookId, String bgCode, String quote,
                        boolean shared) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "글을 너무 자주 남겼습니다");
        }
        if (bookId == null) {
            // 여백은 책에 귀속 — 진입점이 이미 책이므로 정상 클라는 여기 오지 않는다(직접 호출 방어)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 지정해야 합니다");
        }
        Book book = bookRepository.findByIdAndUser(bookId, author)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
        Story story = Story.of(author, text, book, bgCode, quote);
        story.markShared(shared);
        return storyRepository.save(story);
    }

    /**
     * 책 하나의 여백 — 그 자리에 쌓인 글 목록(최신순). 진입로는 책방 격자와 홈 소식 둘이라
     * 응답이 자기완결이다({@link MarginResponse}).
     *
     * <p>게이트 순서(하나라도 어긋나면 <b>존재를 감춘다</b>):
     * <ol>
     *   <li>{@link ProfileService#resolveVisibleTarget} — 차단·ADMIN·미존재 → 404 (프로필과 같은 가드 공유)</li>
     *   <li>책이 target 소유가 아니거나 없음 → 404. 남의 책 id를 다른 핸들에 끼워 넣는 IDOR를 여기서 막는다</li>
     *   <li>책이 PRIVATE <b>이고 본인이 아니면</b> → 404. 남에게는 오늘과 동일하다(존재조차 안 샌다).
     *       <b>소유자만 예외</b>다(2026-08-16 결정 2 — 비공개 책 여백 = 나만 보는 메모): 팔로워라고
     *       열리지 않으므로 {@code self} 판정은 반드시 이 게이트 <i>앞</i>에 있어야 한다</li>
     * </ol>
     *
     * <p><b>팔로우 게이트는 없다</b>(2026-08-22). 예전엔 비팔로워에게 빈 배열을 줬는데, 「모두의 여백」
     * (책축)이 열린 뒤로 같은 글을 팔로우 없이 이미 읽을 수 있어 게이트가 절반만 작동했다 — 낯선
     * 사람의 글을 읽고 그 사람 책방으로 넘어오면 「팔로우하면 볼 수 있어요」를 만나는 모순이었다.
     * 이제 <b>책 가시성이 유일한 방어</b>고, 팔로우는 홈 소식 구독({@code feedRecent})에만 남는다.
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
        List<Story> stories = storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(
                target, book, PageRequest.of(0, MAX_MARGIN_ENTRIES));
        return new MarginResponse(MarginBook.of(book), target.getNickname(), self,
                withLikes(stories, viewer));
    }

    /**
     * 글 목록에 좋아요 집계를 붙인다 — 카드마다 세지 않고 <b>배치 2쿼리</b>로 (N+1 금지,
     * {@code recencyByBook}과 같은 관례).
     *
     * <p>빈 목록이면 쿼리를 아예 안 던진다({@code in ()}은 DB마다 취급이 다르고, 물어볼 것도 없다).
     *
     * <p><b>자기 여백에서도 「내가 눌렀는가」를 묻는다</b>(2026-08-20). 예전엔 자기 글엔 누를 수 없어
     * 답이 구조적으로 비어 있으니 건너뛰었는데, 자기 좋아요가 허용된 지금 건너뛰면 방금 누른 하트가
     * 새로고침에 꺼진다.
     */
    private List<MarginEntry> withLikes(List<Story> stories, User viewer) {
        if (stories.isEmpty()) {
            return List.of();
        }
        Likes likes = likesOf(stories, viewer);
        return stories.stream()
                .map(s -> MarginEntry.of(s, likes.countOf(s), likes.likedBy(s)))
                .toList();
    }

    /** 배치 2쿼리의 결과 — 사람축({@link MarginEntry})·책축({@link SharedMarginEntry})이 같은 것을 쓴다. */
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

    /**
     * 「이 책의 여백」 — isbn13 하나에 <b>함께 걸린</b> 글 전부(최신순). 사람 좌표 없이 책만으로 도달한다.
     *
     * <p>노출 게이트는 통째로 {@link StoryRepository#sharedByIsbn} 쿼리에 있다(책 PUBLIC ∧ shared ∧
     * 차단 아님 ∧ ADMIN 아님 ∧ 핸들 있음) — 행마다 {@code assertVisible}을 부르면 N+1이라 쿼리가 같은
     * 술어를 진다. 둘은 미러이므로 술어가 늘면 양쪽을 같이 고친다({@link Story} 불변식).
     *
     * <p>경로 변수 isbn은 서버가 한 번 더 정규화한다({@link Isbn#normalize}) — 클라가 하이픈을 붙여
     * 보내도 같은 책에 도달한다. 알맹이가 없으면 404(isbn 없는 책은 책축 좌표 자체가 없다).
     *
     * <p>헤더 라벨은 ① viewer 본인 책 행 → ② 첫 공유 글의 책 행 → ③ 둘 다 없으면 <b>404</b>.
     * 셋째는 「그릴 헤더가 없다」는 뜻이지 권한 실패가 아니지만, 화면 입장에선 구분할 이유가 없다.
     */
    @Transactional(readOnly = true)
    public BookMarginResponse bookMarginOf(User viewer, String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        if (isbn == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다");
        }
        Optional<Book> myBook = bookRepository.findFirstByUserAndIsbn13(viewer, isbn);
        List<Story> stories = storyRepository.sharedByIsbn(isbn, viewer.getId(),
                PageRequest.of(0, MAX_MARGIN_ENTRIES));
        Book label = myBook.orElseGet(() -> stories.stream().findFirst().map(Story::getBook)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다")));
        Likes likes = stories.isEmpty() ? new Likes(Map.of(), Set.of()) : likesOf(stories, viewer);
        List<SharedMarginEntry> entries = stories.stream()
                .map(s -> SharedMarginEntry.of(s, likes.countOf(s), likes.likedBy(s)))
                .toList();
        return new BookMarginResponse(BookMarginLabel.of(label), myBook.map(Book::getId).orElse(null),
                storyRepository.countSharedByIsbn(isbn, viewer.getId()), entries);
    }

    /**
     * 「함께 걸기」를 켜거나 끈다 — <b>본인 글만</b>({@link #delete}와 같은 필터: 없거나 타인 것이면
     * 404로 존재를 감춘다).
     *
     * <p><b>멱등</b>하다. POST/DELETE 쌍으로 둔 것도 {@link #like}와 같은 이유다 — 토글 단일
     * 엔드포인트면 모바일 타임아웃 뒤 재전송이 방금 켠 것을 꺼 버린다.
     *
     * <p>책 공개 여부는 검사하지 않는다: 비공개 책에서 미리 켜 두는 것도 유효하고, 그 글은 책이
     * 공개되는 순간부터만 보인다(가시성은 읽기 시점 판정 — {@link Story} 불변식).
     */
    public ShareState setShared(User actor, Long storyId, boolean shared) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        story.markShared(shared);
        return new ShareState(story.isShared());
    }

    /** 켜기·끄기 직후의 상태 — 클라가 추측하지 않게 서버가 읽은 값을 그대로 준다({@link LikeState} 관례). */
    public record ShareState(boolean shared) {
    }

    /**
     * 글에 좋아요를 누른다. 게이트는 {@link #marginOf}와 <b>같은 판정</b>을 재사용한다 — 그러지 않으면
     * 안 보이는 글 id에 눌러 보고 200/404로 <b>글의 존재를 알아낼 수 있다</b>.
     *
     * <p><b>멱등하다</b>: 이미 눌러 둔 글이면 저장하지 않고 현재 상태를 그대로 돌려준다. POST를 토글로
     * 두지 않은 것도 같은 이유다 — 모바일에서 타임아웃 뒤 재전송이 흔한데, 토글이면 그 재시도가
     * 좋아요를 <b>취소</b>해 버린다. 진짜 동시 요청의 중복은 DB 유니크({@code uk_story_like})가 막는다.
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

    /**
     * 좋아요를 취소한다. <b>노출 게이트를 걸지 않는다</b> — 걸면 누른 뒤 언팔했거나 상대가 책을 비공개로
     * 돌린 사람이 자기 좋아요를 <b>되돌릴 수 없게 갇힌다</b>. 행이 있다는 것 자체가 「한때 볼 수 있었다」의
     * 증거이므로 게이트 없이도 안전하고, 없으면 404로 수렴시켜 존재도 누설하지 않는다.
     */
    public LikeState unlike(User viewer, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        StoryLike like = storyLikeRepository.findByStoryAndUser(story, viewer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyLikeRepository.delete(like);
        return new LikeState(storyLikeRepository.countByStory(story), false);
    }

    /**
     * 「이 글이 viewer에게 보이는가」 — {@code marginOf}의 목록 게이트와 <b>같은 판정을 순서까지 맞춰</b>
     * 재사용한다. 여기가 목록과 어긋나는 순간, 목록에 안 뜨는 글에 좋아요가 달리거나 그 명단이 열린다.
     *
     * <p>좋아요({@link #like})와 명단({@link #likers})이 이것 하나를 공유한다 — 갈라 두면 한쪽만 고치는
     * 날이 온다.
     *
     * <p><b>자기 글은 즉시 통과</b>한다. 뒤 체인에 넣으면 내 비공개 책(나만 보는 메모)이 공개 검사에
     * 걸려 내 여백의 하트가 통째로 죽는다.
     *
     * <p>핸들 없는 주인({@code loginId == null})은 {@code resolveVisibleTarget}이 걸러 낸다 —
     * 그들의 글은 애초에 어느 목록에도 실리지 않는다(N-055).
     *
     * <p><b>책 게이트가 마지막이자 유일한 방어</b>다(2026-08-22 팔로우 축 제거). 예전엔 그 뒤에
     * 「팔로워이거나 shared이거나」가 한 겹 더 있었는데, 둘 다 게이트에서 걷었다 — 팔로우는 열람
     * 권한이 아니고({@code marginOf} 참조), {@code shared}는 책축 목록에 실을지를 정하는 배치 값이다.
     * 이중 방어가 사라졌으므로 <b>이 공개 검사를 지우면 곧바로 비공개 메모가 샌다</b>({@link Story}
     * 불변식). 목록 쪽 미러는 {@link StoryRepository#sharedByIsbn}이고, 둘은 같이 고친다.
     */
    private void assertVisible(User viewer, Story story) {
        User owner = story.getUser();
        if (isSameUser(owner, viewer)) {
            return;
        }
        profileService.resolveVisibleTarget(viewer, owner.getLoginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        if (!story.getBook().isPublic()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
    }

    /**
     * 그 글에 좋아요를 누른 사람들 — 카드의 「좋아요 N명」이 여는 명단. 최근순.
     *
     * <p>게이트는 {@link #assertVisible} — 목록과 같은 판정이라, 안 보이는 글의 명단은 404다.
     * 어긋나면 임의의 글 id로 명단을 열어 「그 글이 있다」와 「누가 눌렀다」를 한꺼번에 알아낼 수 있다.
     *
     * <p>명단에서 <b>두 부류를 뺀다</b>: 핸들 없는 사람(N-055 — 어느 목록에도 실리지 않는다)과
     * 차단 관계인 사람. 차단은 팔로우를 양방향으로 끊지만 <b>이미 눌린 행은 남으므로</b>, 안 거르면
     * 차단한 사람의 활동이 이 명단으로 샌다. 판정은 프로필과 같은 {@code resolveVisibleTarget}을
     * 재사용한다(가드를 갈라 두지 않는다 — ADMIN 제외도 공짜로 따라온다).
     *
     * <p>개수({@code likeCount})는 걸러 내지 않는다 — 명단과 한둘 어긋날 수 있지만 눈에 띄지 않고,
     * 집계 쿼리까지 관계를 태우면 목록 응답이 비싸진다.
     *
     * <p>ponytail: 행마다 가드 1~2쿼리다. 명단이 짧아 지금은 충분하고, 길어지면 배치 조회로 바꾼다.
     */
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

    /** 본인 글 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyLikeRepository.deleteByStory(story); // story_like.story_id FK — 글보다 먼저
        storyRepository.delete(story);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
