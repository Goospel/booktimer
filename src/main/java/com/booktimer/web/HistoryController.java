package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 독서 기록 화면 셸 (선별 SPA 단계 1b 이후 Vue 섬).
 *
 * <p>데이터 조회는 {@link com.booktimer.web.api.HistoryApiController}(GET /api/history)로 이관.
 * 이 컨트롤러는 얇은 셸 템플릿(history.html)만 반환하고 nickname만 모델에 싣는다.
 */
@Controller
public class HistoryController {

    private final CurrentUserService currentUserService;

    public HistoryController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname());
        return "history";
    }
}
