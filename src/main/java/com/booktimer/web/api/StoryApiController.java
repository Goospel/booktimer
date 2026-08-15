package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryCard;
import com.booktimer.story.StoryFeedResponse;
import com.booktimer.story.StoryService;
import com.booktimer.story.StoryViewerEntry;
import com.booktimer.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * 여백 JSON API (sns-design §13.4) — 홈 스트립·풀스크린 뷰어·작성 모달용.
 *
 * <p>URL·타입 이름은 {@code story}로 남는다 — 사용자에게 보이는 어휘만 「여백」으로 바꿨고,
 * 경로·테이블까지 개명하면 마이그레이션이 딸려 오는데 그건 사용자가 보지 않는 값이다(2026-08-16).
 *
 * <p>개별 여백 GET은 없다 — 본문(≤500자)이 피드 응답에 통째로 실리므로 상세 조회가 불필요하고,
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

    @GetMapping("/api/stories/feed")
    public StoryFeedResponse feed(Principal principal) {
        return storyService.feed(currentUserService.resolve(principal));
    }

    @GetMapping("/api/stories/of/{loginId}")
    public List<StoryCard> storiesOf(@PathVariable String loginId, Principal principal) {
        return storyService.storiesOf(currentUserService.resolve(principal), loginId);
    }

    @PostMapping("/api/stories")
    public StoryCard create(@RequestBody CreateStoryRequest request, Principal principal) {
        User me = currentUserService.resolve(principal);
        try {
            Story story = storyService.create(me, request.text(), request.bookId(), request.bgCode());
            return StoryCard.of(story, true); // 본인 카드는 항상 viewed 취급 (피드 mine과 동일)
        } catch (IllegalArgumentException e) {
            // 도메인 검증(문장 길이·팔레트 등) 실패 — 프론트는 상태코드로 분기해 안내한다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여백을 남길 수 없습니다");
        }
    }

    @DeleteMapping("/api/stories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        storyService.delete(currentUserService.resolve(principal), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/stories/{id}/view")
    public ResponseEntity<Void> markViewed(@PathVariable Long id, Principal principal) {
        try {
            storyService.markViewed(currentUserService.resolve(principal), id);
        } catch (DataIntegrityViolationException ignored) {
            // 동시 열람 경합 — uk_story_view 승자 행이 이미 커밋됐으므로 멱등 성공.
            // 서비스(@Transactional) 안에서 잡으면 rollback-only 마킹 때문에 커밋에서 다시 터진다 —
            // 그래서 트랜잭션 경계 밖인 여기서 변환한다.
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/stories/{id}/viewers")
    public List<StoryViewerEntry> viewers(@PathVariable Long id, Principal principal) {
        return storyService.viewers(currentUserService.resolve(principal), id);
    }

    public record CreateStoryRequest(String text, Long bookId, String bgCode) {
    }
}
