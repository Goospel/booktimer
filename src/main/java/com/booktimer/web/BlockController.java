package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 차단 목록 Vue 셸 (SNS 5단계, sns-design §7.5).
 *
 * <p>GET /me/blocks — Vue 섬 셸. 목록 데이터는 {@link com.booktimer.web.api.BlockApiController}가 제공.
 * POST /block·/unblock은 SPA 전환 후 BlockApiController(/api/block·/api/unblock)가 담당.
 */
@Controller
public class BlockController {

    private final CurrentUserService currentUserService;

    public BlockController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/blocks")
    public String blocks(Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("myLoginId", me.getLoginId());
        return "block-list";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
