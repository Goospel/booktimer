package com.booktimer.web;

import com.booktimer.block.BlockService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * 차단/언차단 액션 + 본인 차단 목록 (SNS 5단계, sns-design §7.5).
 *
 * <p>차단하면 상대 프로필이 대칭으로 404가 되므로(거기서 해제 불가), 차단·언차단은 본인 차단 목록
 * {@code /me/blocks}로 돌아온다 — 해제는 이 목록에서 한다. 자기 차단·존재 누설은 조용히 무시한다.
 */
@Controller
public class BlockController {

    private final UserRepository userRepository;
    private final BlockService blockService;

    public BlockController(UserRepository userRepository, BlockService blockService) {
        this.userRepository = userRepository;
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
        model.addAttribute("loginId", me.getLoginId()); // "내 프로필" 링크(/u/{loginId})용
        model.addAttribute("blocked", blockService.blockedUsers(me));
        return "block-list";
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
