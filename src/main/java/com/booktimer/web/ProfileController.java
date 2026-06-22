package com.booktimer.web;

import com.booktimer.profile.ProfileService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * 공개 프로필 페이지 셸 (선별 SPA 단계 2d).
 *
 * <p>Vue 섬을 마운트할 빈 셸만 반환. 실제 데이터는 {@link com.booktimer.web.api.ProfileApiController}(JSON)가 담당.
 * 선가드({@link ProfileService#profileOf})로 차단·ADMIN·미존재를 SSR 단계에서도 404로 막는다 — API와 이중 가드.
 */
@Controller
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;

    public ProfileController(ProfileService profileService, CurrentUserService currentUserService) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/u/{loginId}")
    public String profile(@PathVariable String loginId, Principal principal, Model model) {
        User viewer = currentUserService.resolve(principal);
        profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        model.addAttribute("loginId", loginId);
        model.addAttribute("myLoginId", viewer.getLoginId());
        return "profile";
    }
}
