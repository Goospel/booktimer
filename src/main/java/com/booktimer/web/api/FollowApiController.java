package com.booktimer.web.api;

import com.booktimer.follow.FollowService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
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
        User target = resolveNewFolloweeTarget(request.loginId());
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

    /**
     * 관계를 <b>만드는</b> 쪽의 대상 조회 — 운영자(ADMIN)는 없는 것으로 본다.
     *
     * <p>운영자는 소셜 노출 대상이 아니라 프로필·검색·여백·추천이 모두 {@code role <> ADMIN}으로 거른다
     * ({@link com.booktimer.profile.ProfileService#resolveVisibleTarget}과 같은 불변식). 여기만 날것으로
     * 조회하면 운영자 핸들을 아는 사람이 이 API의 200/404 차이로 <b>존재를 확인</b>할 수 있고(그 누설을
     * 막으려고 만든 가드다) 팔로우 엣지까지 만들어진다.
     *
     * <p><b>푸는 쪽(unfollow)에는 걸지 않는다</b> — 상대가 나중에 운영자로 승격되면 이미 만들어진 팔로우를
     * 영영 끊을 수 없게 된다. 이 비대칭은 회귀 테스트가 못 박는다.
     */
    private User resolveNewFolloweeTarget(String loginId) {
        return notFoundIfAdmin(resolveTarget(loginId));
    }

    private User resolveTarget(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }

    private static User notFoundIfAdmin(User target) {
        if (target.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다");
        }
        return target;
    }

    public record FollowRequest(String loginId) {
    }

    public record FollowResult(boolean following) {
    }
}
