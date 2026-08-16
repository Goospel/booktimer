package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.follow.FollowService;
import com.booktimer.profile.ProfileService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
    private final FollowService followService;
    private final ProfileService profileService;

    public StoryService(StoryRepository storyRepository,
                        BookRepository bookRepository,
                        RateLimitService rateLimitService,
                        FollowService followService,
                        ProfileService profileService) {
        this.storyRepository = storyRepository;
        this.bookRepository = bookRepository;
        this.rateLimitService = rateLimitService;
        this.followService = followService;
        this.profileService = profileService;
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
     */
    public Story create(User author, String text, Long bookId, String bgCode) {
        if (!rateLimitService.allow(RateLimitAction.STORY_CREATE, author.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "글을 너무 자주 남겼습니다");
        }
        if (bookId == null) {
            // 여백은 책에 귀속 — 진입점이 이미 책이므로 정상 클라는 여기 오지 않는다(직접 호출 방어)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 지정해야 합니다");
        }
        Book book = bookRepository.findByIdAndUser(bookId, author)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
        return storyRepository.save(Story.of(author, text, book, bgCode));
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
     *   <li>비팔로워(본인 아님) → {@code entries} 빈 배열 + {@code following:false}. 404가 아닌 이유:
     *       공개 책은 격자에 이미 보이므로 감출 것이 책이 아니라 <b>글</b>이다(글 유무 정보도 안 샘)</li>
     * </ol>
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
        boolean following = !self && followService.isFollowing(viewer, target);
        List<MarginEntry> entries = (self || following)
                ? storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(
                        target, book, PageRequest.of(0, MAX_MARGIN_ENTRIES)).stream()
                        .map(MarginEntry::of)
                        .toList()
                : List.of();
        return new MarginResponse(MarginBook.of(book), target.getNickname(), self, following, entries);
    }

    /** 본인 글 즉시 삭제(실수 게시 회수 — §13.6). 없거나 타인 것이면 404(IDOR — 존재 비노출). */
    public void delete(User actor, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .filter(s -> isSameUser(s.getUser(), actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        storyRepository.delete(story);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
