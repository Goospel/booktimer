package com.booktimer.web.api;

import com.booktimer.search.UserSearchResult;
import com.booktimer.security.CurrentUserService;
import com.booktimer.story.MarginEntry;
import com.booktimer.story.MarginResponse;
import com.booktimer.story.Story;
import com.booktimer.story.StoryService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * 여백 JSON API (sns-design §13.4) — 책별 글 목록·작성·삭제.
 *
 * <p>URL·타입 이름은 {@code story}로 남는다 — 사용자에게 보이는 어휘만 「여백」으로 바꿨고,
 * 경로·테이블까지 개명하면 마이그레이션이 딸려 오는데 그건 사용자가 보지 않는 값이다(2026-08-16).
 *
 * <p>개별 글 GET은 없다 — 본문(≤500자)이 목록 응답에 통째로 실리므로 상세 조회가 불필요하고,
 * 노출 경계 진입점을 최소화한다. 게이트·상태코드(레이트리밋 429·미노출 404)는
 * {@link StoryService}가 담당하고, 여기서는 도메인 검증 실패(IAE)만 400으로 변환한다.
 * SecurityConfig default-deny로 자동 인증·CSRF 보호.
 */
@RestController
public class StoryApiController {

    private final CurrentUserService currentUserService;
    private final StoryService storyService;

    public StoryApiController(CurrentUserService currentUserService, StoryService storyService) {
        this.currentUserService = currentUserService;
        this.storyService = storyService;
    }

    /**
     * 책 하나의 여백 — 그 자리에 쌓인 글 목록. 경로는 「누구의」(loginId) + 「어느 책」(bookId) 두 축이다.
     * 노출 게이트는 전부 {@link StoryService#marginOf}에 있다(차단·IDOR → 404 / <b>남의</b> PRIVATE 책 →
     * 404 / 비팔로워 → 빈 목록). 소유자 본인은 자기 PRIVATE 책의 여백을 읽는다(2026-08-16 결정 2).
     */
    @GetMapping("/api/stories/of/{loginId}")
    public MarginResponse marginOf(@PathVariable String loginId,
                                   @RequestParam Long bookId,
                                   Principal principal) {
        return storyService.marginOf(currentUserService.resolve(principal), loginId, bookId);
    }

    @PostMapping("/api/stories")
    public MarginEntry create(@RequestBody CreateStoryRequest request, Principal principal) {
        User me = currentUserService.resolve(principal);
        try {
            Story story = storyService.create(me, request.text(), request.bookId(), request.bgCode(),
                    request.quote());
            return MarginEntry.of(story);
        } catch (IllegalArgumentException e) {
            // 도메인 검증(문장 길이·팔레트 등) 실패 — 프론트는 상태코드로 분기해 안내한다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "글을 남길 수 없습니다");
        }
    }

    /**
     * 좋아요를 누른다. <b>멱등</b>이라 재전송해도 취소되지 않는다 — 토글 단일 엔드포인트를 쓰지 않은
     * 이유이자, 모바일 타임아웃 재시도가 흔한 경로라서다. 게이트·상태코드는 {@link StoryService#like}.
     */
    @PostMapping("/api/stories/{id}/like")
    public StoryService.LikeState like(@PathVariable Long id, Principal principal) {
        return storyService.like(currentUserService.resolve(principal), id);
    }

    /**
     * 그 글에 좋아요를 누른 사람들 — 카드의 「좋아요 N명」이 여는 명단. 게이트는 목록과 같은 판정이라
     * 안 보이는 글의 명단은 404다({@link StoryService#likers}).
     *
     * <p>목록 응답에 싣지 않고 별도 조회로 둔 이유: 여백 한 장이 글 100개까지 실리므로 글마다 명단을
     * 동봉하면 열지도 않을 명단이 한꺼번에 날아온다.
     */
    @GetMapping("/api/stories/{id}/likes")
    public List<UserSearchResult> likers(@PathVariable Long id, Principal principal) {
        return storyService.likers(currentUserService.resolve(principal), id);
    }

    /** 좋아요를 취소한다. 안 누른 글·없는 글은 404(존재 비노출) — 노출 게이트는 걸지 않는다. */
    @DeleteMapping("/api/stories/{id}/like")
    public StoryService.LikeState unlike(@PathVariable Long id, Principal principal) {
        return storyService.unlike(currentUserService.resolve(principal), id);
    }

    @DeleteMapping("/api/stories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        storyService.delete(currentUserService.resolve(principal), id);
        return ResponseEntity.ok().build();
    }

    /**
     * 글 작성 요청. {@code quote}(책에서 옮긴 문장)는 <b>선택</b>이고, 필드가 아예 없는 옛 클라이언트의
     * 요청도 그대로 받는다(Jackson이 null로 채운다 — 인용 없이 남긴 글).
     */
    public record CreateStoryRequest(String text, String quote, Long bookId, String bgCode) {
    }
}
