package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 책BTI(독서 성향) 페이지 셸 컨트롤러 (선별 SPA 단계 1c 이후).
 *
 * <p>Vue 섬 마운트에 필요한 최소 속성(nickname·loginId)만 SSR로 제공한다.
 * 뮤테이션(refresh·select)과 조회 데이터는 {@link com.booktimer.web.api.PersonalityApiController}(JSON)가 담당.
 */
@Controller
public class PersonalityController {

    private final CurrentUserService currentUserService;

    public PersonalityController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/personality")
    public String personality(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("loginId", user.getLoginId());
        return "personality";
    }
}
