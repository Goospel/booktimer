package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 본인 팔로워/팔로잉 목록 SSR 셸 — {@code /me/**}.
 *
 * <p>Vue 섬 전환 후 얇은 셸: URL 보존·인증 게이트·초기 탭 힌트만. 목록 데이터는 Vue가
 * {@code GET /api/follow-list} 로 로드한다.
 */
@Controller
public class FollowListController {

    private final CurrentUserService currentUserService;

    public FollowListController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/followers")
    public String followers(Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("myLoginId", me.getLoginId());
        model.addAttribute("initialTab", "followers");
        return "follow-list";
    }

    @GetMapping("/me/following")
    public String following(Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("myLoginId", me.getLoginId());
        model.addAttribute("initialTab", "following");
        return "follow-list";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
