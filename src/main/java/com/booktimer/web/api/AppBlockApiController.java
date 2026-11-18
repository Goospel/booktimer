package com.booktimer.web.api;

import com.booktimer.blog.BlogBlockService;
import com.booktimer.blog.BlogViews.BlockedUser;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

/**
 * 앱 「책생글」 차단(B2b · 계획 §4.7 · REQ-254) — 블로그 대상(글 · 댓글)으로 차단 · 목록 · 풀기. loginId 없는 앱 가입자(Apple · 카카오)도
 * 받는다(웹 {@code /api/block}은 loginId로 받는다 · 그쪽은 그대로).
 *
 * <p>{@code AppBlogApiController}에 두지 않는다 — REQ-244 경로 집합 시험은 {@code QuoteBoxService} · {@code BlogAccess}를 쥔
 * 컨트롤러가 두 접두 밖({@code /api/app/blocks})에 쓰기 문을 열면 빨갛다(§4.9). 이 컨트롤러는 둘 다 쥐지 않는다 · 허용 목록과 무관.
 */
@RestController
public class AppBlockApiController {

    private final CurrentUserService currentUserService;
    private final BlogBlockService blockService;

    public AppBlockApiController(CurrentUserService currentUserService, BlogBlockService blockService) {
        this.currentUserService = currentUserService;
        this.blockService = blockService;
    }

    /**
     * @return 200 {@code {userId, name}}(이미 차단 · 동시 두 번 · 운영자 글도 같음) / 400 나 자신 · 종류 / 404 공개 대상 아님(이름 0)
     *
     * <p>두 번 누름이 같은 순간 겹치면 {@code uk_block} 유일 위반 — 서비스 트랜잭션 <b>밖</b>인 여기서 잡아 한 번 더 부른다(두 번째는
     * 이미 있는 차단을 보고 멱등으로 끝난다).
     */
    @PostMapping("/api/app/blog/blocks")
    public BlockedUser block(@RequestBody BlockRequest request, Principal principal) {
        User user = currentUserService.resolve(principal);
        long id = request.targetId() == null ? 0 : request.targetId();
        try {
            return blockService.blockAuthorOf(user, AppBlogApiController.target(request.targetType()), id);
        } catch (DataIntegrityViolationException e) {
            return blockService.blockAuthorOf(user, AppBlogApiController.target(request.targetType()), id);
        }
    }

    /** 설정 「차단한 사람」 — 최근 차단 순. */
    @GetMapping("/api/app/blocks")
    public List<BlockedUser> list(Principal principal) {
        return blockService.list(currentUserService.resolve(principal));
    }

    /** @return 204(멱등 — 관계가 없거나 없는 id여도) */
    @DeleteMapping("/api/app/blocks/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable long userId, Principal principal) {
        blockService.unblock(currentUserService.resolve(principal), userId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return AppAuthApiController.plainTextError(e);
    }

    public record BlockRequest(String targetType, Long targetId) {}
}
