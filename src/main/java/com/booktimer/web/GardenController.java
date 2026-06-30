package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 독서 마을 셸 컨트롤러.
 *
 * <p>S4 컷오버: GET /village는 얇은 셸(nickname만 모델)만 반환한다. 도감·월드 데이터는
 * {@code /api/garden}(JSON API)이 단일 출처로 제공하고, Vue SPA가 마운트 후 fetch한다.
 *
 * <p>배치/편집 엔진 은퇴(PR-2): 좌표 저장({@code POST /village/layout})이 사라졌다 — 보기 전용 마을.
 */
@Controller
public class GardenController {

    private final CurrentUserService currentUserService;

    public GardenController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/village")
    public String village(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname());
        return "garden";
    }

    /** 레거시 리다이렉트 — 옛 북마크·외부 링크를 새 URL로 안내한다 (302). */
    @GetMapping("/garden")
    public String gardenRedirect() {
        return "redirect:/village";
    }
}
