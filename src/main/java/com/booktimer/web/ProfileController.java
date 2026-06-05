package com.booktimer.web;

import com.booktimer.profile.ProfileService;
import com.booktimer.profile.ProfileView;
import com.booktimer.report.ReportReason;
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
 * 개인 공개 프로필 페이지 (SNS 2·3단계, sns-design §7.2·§7.3).
 *
 * <p>{@code GET /u/{loginId}} — login_id(공개 @핸들)로 공개 프로필(PUBLIC 책장 + 잔디 + 팔로우 카운트)을 SSR로 그린다.
 * 이 페이지는 "남에게 보이는 공개 프로필"이라 viewer를 가리지 않는다(본인이 봐도 PUBLIC만, {@link ProfileService}).
 * 팔로우 버튼만 viewer 기준으로 분기한다(내가 팔로우 중인지 / 본인이면 버튼 없음).
 *
 * <p>접근 제어: 로그인 사용자만(비로그인은 SecurityConfig {@code anyRequest().authenticated()}로 차단).
 * 없는 아이디는 <b>404</b>(존재 누설 회피 §5.3).
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
        User viewer = currentUser(principal);
        ProfileView profile = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));

        model.addAttribute("loginId", profile.loginId());
        model.addAttribute("nickname", profile.nickname());
        model.addAttribute("books", profile.books());
        model.addAttribute("bookTimes", profile.bookTimes());
        model.addAttribute("graph", profile.graph());
        model.addAttribute("followerCount", profile.followerCount());
        model.addAttribute("followingCount", profile.followingCount());
        model.addAttribute("following", profile.following());
        model.addAttribute("self", profile.self());
        model.addAttribute("reportReasons", ReportReason.values());
        return "profile";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
