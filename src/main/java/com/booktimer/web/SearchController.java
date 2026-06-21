package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * search 페이지 얇은 셸 컨트롤러 (선별 SPA 단계 1a).
 *
 * <p>검색 로직·추천·레이트리밋은 {@link com.booktimer.web.api.SearchApiController}(JSON API)로 이관.
 * 이 컨트롤러는 Vue 섬이 마운트할 셸 HTML만 내려준다 — model에는 초기 q·myLoginId만 담는다.
 */
@Controller
public class SearchController {

    private final CurrentUserService currentUserService;

    public SearchController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String q,
                         Principal principal, Model model) {
        User me = currentUserService.resolve(principal);
        model.addAttribute("q", q);
        model.addAttribute("myLoginId", me.getLoginId());
        return "search";
    }
}
