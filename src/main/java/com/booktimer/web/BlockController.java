package com.booktimer.web;

import com.booktimer.block.BlockService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * 차단/언차단 SSR 액션 + 차단 목록 Vue 셸 (SNS 5단계, sns-design §7.5).
 *
 * <p>POST /block·/unblock은 프로필 SSR 폼이 사용하므로 2d까지 유지한다.
 * GET /me/blocks는 Vue 섬 셸 — 목록 데이터는 {@link com.booktimer.web.api.BlockApiController}가 제공.
 */
@Controller
public class BlockController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BlockService blockService;

    public BlockController(UserRepository userRepository, CurrentUserService currentUserService,
                           BlockService blockService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.blockService = blockService;
    }

    @PostMapping("/block")
    public String block(@RequestParam String loginId, Principal principal) {
        User me = currentUser(principal);
        userRepository.findByLoginId(loginId).ifPresent(target -> {
            try {
                blockService.block(me, target);
            } catch (IllegalArgumentException ignored) {
                // 자기 자신 차단 등 — 조용히 무시(버튼이 애초에 안 떠야 정상)
            }
        });
        return "redirect:/me/blocks";
    }

    @PostMapping("/unblock")
    public String unblock(@RequestParam String loginId, Principal principal) {
        User me = currentUser(principal);
        userRepository.findByLoginId(loginId).ifPresent(target -> blockService.unblock(me, target));
        return "redirect:/me/blocks";
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
