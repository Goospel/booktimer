package com.booktimer.web;

import com.booktimer.follow.FollowService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * 팔로우/언팔로우 액션 (SNS 3단계, sns-design §7.3).
 *
 * <p>{@code POST /follow}, {@code POST /unfollow} — 대상 login_id(공개 @핸들)로 관계를 만들고/지운다(CSRF, PRG).
 * 자기 자신 팔로우·존재 누설은 조용히 무시하고(예외를 화면에 노출하지 않음) 원래 화면으로 돌아간다.
 * 도메인 규칙(자기 팔로우 금지·멱등)은 {@link FollowService}가 강제한다.
 *
 * <p>스팸 팔로우 방어로 사용자별 레이트리밋({@link RateLimitAction#FOLLOW})을 건다 — 한도 초과 시 액션을
 * 건너뛰고(조용히 드롭) 원래 화면으로 돌아간다(§7.5·§9).
 */
@Controller
public class FollowController {

    private final UserRepository userRepository;
    private final FollowService followService;
    private final RateLimitService rateLimitService;

    public FollowController(UserRepository userRepository, FollowService followService,
                            RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.followService = followService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/follow")
    public String follow(@RequestParam String loginId,
                         @RequestParam(value = "redirect", required = false) String redirect,
                         Principal principal) {
        User me = currentUser(principal);
        if (rateLimitService.allow(RateLimitAction.FOLLOW, me.getId())) {
            userRepository.findByLoginId(loginId).ifPresent(target -> {
                try {
                    followService.follow(me, target);
                } catch (IllegalArgumentException ignored) {
                    // 자기 자신 팔로우 등 — 조용히 무시(버튼이 애초에 안 떠야 정상)
                }
            });
        }
        return "redirect:" + safeRedirect(redirect, loginId);
    }

    @PostMapping("/unfollow")
    public String unfollow(@RequestParam String loginId,
                           @RequestParam(value = "redirect", required = false) String redirect,
                           Principal principal) {
        User me = currentUser(principal);
        if (rateLimitService.allow(RateLimitAction.FOLLOW, me.getId())) {
            userRepository.findByLoginId(loginId).ifPresent(target -> followService.unfollow(me, target));
        }
        return "redirect:" + safeRedirect(redirect, loginId);
    }

    /**
     * 돌아갈 경로를 정한다 — <b>내부 상대경로만</b> 허용(오픈 리다이렉트 방어).
     * "/"로 시작하고 "//"(프로토콜-상대 외부)가 아니면 그대로, 아니면 대상 프로필로.
     */
    private String safeRedirect(String redirect, String loginId) {
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            return redirect;
        }
        return "/u/" + loginId;
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
