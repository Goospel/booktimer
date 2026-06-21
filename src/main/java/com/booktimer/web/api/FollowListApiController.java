package com.booktimer.web.api;

import com.booktimer.follow.FollowListService;
import com.booktimer.search.UserSearchResult;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * GET /api/follow-list — 본인 팔로워/팔로잉 목록 JSON API (Vue 섬용).
 *
 * <p>뮤테이션은 기존 {@code /api/follow}·{@code /api/unfollow}를 그대로 재사용한다(신설 없음).
 */
@RestController
public class FollowListApiController {

    private final CurrentUserService currentUserService;
    private final FollowListService followListService;

    public FollowListApiController(CurrentUserService currentUserService,
                                   FollowListService followListService) {
        this.currentUserService = currentUserService;
        this.followListService = followListService;
    }

    @GetMapping("/api/follow-list")
    public FollowListResponse list(@RequestParam(value = "type", required = false) String type,
                                   Principal principal) {
        User me = currentUserService.resolve(principal);
        boolean isFollowers = !"following".equals(type); // 기본 followers (관대 파싱)
        List<UserSearchResult> users = isFollowers
                ? followListService.followers(me)
                : followListService.following(me);
        return new FollowListResponse(isFollowers ? "followers" : "following", users, me.getLoginId());
    }

    public record FollowListResponse(String type, List<UserSearchResult> users, String myLoginId) {}
}
