package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.search.UserSearchResult;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * 차단 JSON API (선별 SPA 단계 2c).
 *
 * <p>SSR 폼용 {@link com.booktimer.web.BlockController}(/block·/unblock)는 2d까지 유지.
 * 이 API는 fetch 기반 Vue 섬용 — BlockResult.blocked는 단방향(hasBlocked)만 사용(isBlockedBetween 금지).
 */
@RestController
public class BlockApiController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BlockService blockService;

    public BlockApiController(UserRepository userRepository,
                              CurrentUserService currentUserService,
                              BlockService blockService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.blockService = blockService;
    }

    @GetMapping("/api/blocks")
    public BlockListResponse blocks(Principal principal) {
        User me = currentUserService.resolve(principal);
        return new BlockListResponse(blockService.blockedUsers(me), me.getLoginId());
    }

    @PostMapping("/api/block")
    public ResponseEntity<BlockResult> block(@RequestBody BlockRequest req, Principal principal) {
        User me = currentUserService.resolve(principal);
        User target = resolveTarget(req.loginId());
        if (target.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다");
        }
        try {
            blockService.block(me, target);
        } catch (IllegalArgumentException ignored) {
            // 도메인 거부(자기차단 등) — 위에서 먼저 잡으므로 여기에 도달하지 않음
        }
        return ResponseEntity.ok(new BlockResult(blockService.hasBlocked(me, target)));
    }

    @PostMapping("/api/unblock")
    public ResponseEntity<BlockResult> unblock(@RequestBody BlockRequest req, Principal principal) {
        User me = currentUserService.resolve(principal);
        User target = resolveTarget(req.loginId());
        blockService.unblock(me, target);
        return ResponseEntity.ok(new BlockResult(blockService.hasBlocked(me, target)));
    }

    private User resolveTarget(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }

    public record BlockRequest(String loginId) {}

    /** "내가(me) 이 사람을 차단 중인가" — 단방향. 상대의 차단 여부는 포함하지 않는다. */
    public record BlockResult(boolean blocked) {}

    public record BlockListResponse(List<UserSearchResult> blocked, String myLoginId) {}
}
