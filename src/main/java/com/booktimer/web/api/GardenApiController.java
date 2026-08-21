package com.booktimer.web.api;

import com.booktimer.garden.FeedRequest;
import com.booktimer.garden.FeedResult;
import com.booktimer.garden.FeedingService;
import com.booktimer.garden.GardenService;
import com.booktimer.garden.GardenView;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 서재 SPA용 JSON API (S1 — 백엔드 레이어).
 *
 * <p>기존 {@code /village} Thymeleaf 뷰와 병행 제공한다. {@code /api/**}는 SecurityConfig
 * default-deny에 의해 자동으로 인증·CSRF 보호된다.
 *
 * <p>배치/편집 엔진 은퇴(PR-2): 좌표 저장({@code POST /layout})이 사라졌다 — 보기 전용 서재는
 * 도감·배회 캐릭터·먹이주기만 제공한다.
 */
@RestController
@RequestMapping("/api/garden")
public class GardenApiController {

    private final CurrentUserService currentUserService;
    private final GardenService gardenService;
    private final FeedingService feedingService;

    public GardenApiController(CurrentUserService currentUserService,
                               GardenService gardenService,
                               FeedingService feedingService) {
        this.currentUserService = currentUserService;
        this.gardenService = gardenService;
        this.feedingService = feedingService;
    }

    @GetMapping
    public GardenApiResponse getGarden(Principal principal) {
        User user = currentUserService.resolve(principal);
        GardenView view = gardenService.view(user);
        int foodBalance = feedingService.foodBalance(user);
        Map<String, Integer> affectionByCharacter = feedingService.affectionByCharacter(user);
        return GardenApiResponse.of(view, user.getNickname(), foodBalance, affectionByCharacter);
    }

    /**
     * 보유 작가 캐릭터에게 먹이기 — 먹이 1 소비, affection++.
     *
     * @return 200 {@link FeedResult} / 400 미보유·먹이없음
     */
    @PostMapping("/feed")
    public FeedResult feed(Principal principal, @RequestBody FeedRequest request) {
        User user = currentUserService.resolve(principal);
        return feedingService.feed(user, request.characterCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
