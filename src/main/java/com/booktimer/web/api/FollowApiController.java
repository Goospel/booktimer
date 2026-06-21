package com.booktimer.web.api;

import com.booktimer.follow.FollowService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * 팔로우·언팔로우 JSON API (선별 SPA 단계 1a).
 *
 * <p>SSR 폼용 {@link com.booktimer.web.FollowController}(/follow·/unfollow)는 유지(다른 SSR 페이지 공유).
 * 이 API는 fetch 기반 Vue 섬용 — redirect 대신 JSON으로 following 상태 반환.
 * SecurityConfig default-deny로 자동 인증·CSRF 보호.
 */
@RestController
public class FollowApiController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final FollowService followService;
    private final RateLimitService rateLimitService;

    public FollowApiController(UserRepository userRepository,
                               CurrentUserService currentUserService,
                               FollowService followService,
                               RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.followService = followService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/follow")
    public ResponseEntity<FollowResult> follow(@RequestBody FollowRequest request, Principal principal) {
        User me = currentUserService.resolve(principal);
        User target = resolveTarget(request.loginId());
        if (target.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다");
        }
        if (rateLimitService.allow(RateLimitAction.FOLLOW, me.getId())) {
            try {
                followService.follow(me, target);
            } catch (IllegalArgumentException ignored) {
                // 차단·기타 도메인 거부 — 조용히 드롭
            }
        }
        return ResponseEntity.ok(new FollowResult(followService.isFollowing(me, target)));
    }

    @PostMapping("/api/unfollow")
    public ResponseEntity<FollowResult> unfollow(@RequestBody FollowRequest request, Principal principal) {
        User me = currentUserService.resolve(principal);
        User target = resolveTarget(request.loginId());
        if (rateLimitService.allow(RateLimitAction.FOLLOW, me.getId())) {
            followService.unfollow(me, target);
        }
        return ResponseEntity.ok(new FollowResult(followService.isFollowing(me, target)));
    }

    private User resolveTarget(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }

    public record FollowRequest(String loginId) {
    }

    public record FollowResult(boolean following) {
    }
}
