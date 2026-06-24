package com.booktimer.web.api;

import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.GardenService;
import com.booktimer.garden.LayoutSaveRequest;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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

    public GardenApiController(CurrentUserService currentUserService,
                               GardenService gardenService,
                               GardenLayoutService gardenLayoutService) {
        this.currentUserService = currentUserService;
        this.gardenService = gardenService;
        this.gardenLayoutService = gardenLayoutService;
    }

    @GetMapping
    public GardenApiResponse getGarden(Principal principal) {
        User user = currentUserService.resolve(principal);
        return GardenApiResponse.of(
                gardenService.view(user),
                gardenLayoutService.layoutItemsOf(user),
                GardenLayoutService.WORLD_WIDTH,
                GardenLayoutService.WORLD_HEIGHT,
                user.getNickname());
    }

    @PostMapping("/layout")
    public ResponseEntity<Void> saveLayout(Principal principal, @RequestBody LayoutSaveRequest request) {
        User user = currentUserService.resolve(principal);
        gardenLayoutService.saveLayout(user, request);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidPlacement(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
