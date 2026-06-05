package com.booktimer.web;

import com.booktimer.follow.FollowListService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 본인 팔로워/팔로잉 목록 — {@code /me/**}.
 *
 * <p>경로에 닉네임이 없고 항상 인증된 본인 기준이라, 남의 목록을 볼 수 있는 경로 자체가 없다(보안 경계 자동).
 * 프로필 페이지의 팔로워/팔로잉 카운트는 본인일 때만 이 페이지로 링크한다(남의 프로필은 카운트만).
 */
@Controller
public class FollowListController {

    private final UserRepository userRepository;
    private final FollowListService followListService;

    public FollowListController(UserRepository userRepository, FollowListService followListService) {
        this.userRepository = userRepository;
        this.followListService = followListService;
    }

    @GetMapping("/me/followers")
    public String followers(Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("loginId", me.getLoginId()); // "내 프로필" 링크(/u/{loginId})용
        model.addAttribute("listType", "followers");
        model.addAttribute("heading", "내 팔로워");
        model.addAttribute("emptyMessage", "아직 나를 팔로우한 사람이 없습니다.");
        model.addAttribute("users", followListService.followers(me));
        return "follow-list";
    }

    @GetMapping("/me/following")
    public String following(Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("loginId", me.getLoginId()); // "내 프로필" 링크(/u/{loginId})용
        model.addAttribute("listType", "following");
        model.addAttribute("heading", "내 팔로잉");
        model.addAttribute("emptyMessage", "아직 팔로우한 사람이 없습니다.");
        model.addAttribute("users", followListService.following(me));
        return "follow-list";
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("인증 사용자를 찾을 수 없습니다: " + principal.getName()));
    }
}
