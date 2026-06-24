package com.booktimer.web.api;

import com.booktimer.garden.FeedRequest;
import com.booktimer.garden.FeedResult;
import com.booktimer.garden.FeedingService;
import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.GardenService;
import com.booktimer.garden.GardenView;
import com.booktimer.garden.LayoutSaveRequest;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 독서 마을 SPA용 JSON API (S1 — 백엔드 레이어).
 *
 * <p>기존 {@code /village} Thymeleaf 뷰와 병행 제공한다. S4 컷오버 전까지 두 경로가 공존한다.
 * {@code /api/**}는 SecurityConfig default-deny에 의해 자동으로 인증·CSRF 보호된다.
 */
@RestController
@RequestMapping("/api/garden")
public class GardenApiController {

    private final CurrentUserService currentUserService;
    private final GardenService gardenService;
    private final GardenLayoutService gardenLayoutService;
    private final FeedingService feedingService;

    public GardenApiController(CurrentUserService currentUserService,
                               GardenService gardenService,
                               GardenLayoutService gardenLayoutService,
                               FeedingService feedingService) {
        this.currentUserService = currentUserService;
        this.gardenService = gardenService;
        this.gardenLayoutService = gardenLayoutService;
        this.feedingService = feedingService;
    }

    @GetMapping
    public GardenApiResponse getGarden(Principal principal) {
        User user = currentUserService.resolve(principal);
        GardenView view = gardenService.view(user);
        int foodBalance = feedingService.foodBalance(user);
        Map<String, Integer> affectionByCharacter = feedingService.affectionByCharacter(user);
        return GardenApiResponse.of(
                view,
                gardenLayoutService.layoutItemsOf(user),
                GardenLayoutService.WORLD_WIDTH,
                GardenLayoutService.WORLD_HEIGHT,
                user.getNickname(),
                foodBalance,
                affectionByCharacter);
    }

    @PostMapping("/layout")
    public ResponseEntity<Void> saveLayout(Principal principal, @RequestBody LayoutSaveRequest request) {
        User user = currentUserService.resolve(principal);
        gardenLayoutService.saveLayout(user, request);
        return ResponseEntity.ok().build();
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
    public ResponseEntity<String> handleInvalidPlacement(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
