package com.booktimer.web;

import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.LayoutSaveRequest;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

/**
 * 독서 마을 셸 + 배치 저장 컨트롤러.
 *
 * <p>S4 컷오버: GET /village는 얇은 셸(nickname만 모델)만 반환한다. 도감·배치·월드 데이터는
 * {@code /api/garden}(JSON API)이 단일 출처로 제공하고, Vue SPA가 마운트 후 fetch한다.
 * 배치 저장({@code POST /village/layout})·레거시 리다이렉트·예외 핸들러는 불변.
 */
@Controller
public class GardenController {

    private final CurrentUserService currentUserService;
    private final GardenLayoutService gardenLayoutService;
    private final String vapidPublicKey;

    public GardenController(CurrentUserService currentUserService,
                            GardenLayoutService gardenLayoutService,
                            @Value("${booktimer.push.vapid.public-key:not-configured}") String vapidPublicKey) {
        this.currentUserService = currentUserService;
        this.gardenLayoutService = gardenLayoutService;
        this.vapidPublicKey = vapidPublicKey;
    }

    @GetMapping("/village")
    public String village(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("vapidPublicKey", vapidPublicKey);
        return "garden";
    }

    /** 레거시 리다이렉트 — 옛 북마크·외부 링크를 새 URL로 안내한다 (302). */
    @GetMapping("/garden")
    public String gardenRedirect() {
        return "redirect:/village";
    }

    /**
     * 캔버스 배치 저장 — 편집 모드 "저장"이 식물+소품 전체를 JSON({@link LayoutSaveRequest})으로 보낸다.
     * 한 트랜잭션에서 본인 범위를 교체 저장한다.
     */
    @PostMapping("/village/layout")
    @ResponseBody
    public ResponseEntity<Void> saveLayout(Principal principal, @RequestBody LayoutSaveRequest request) {
        User user = currentUserService.resolve(principal);
        gardenLayoutService.saveLayout(user, request);
        return ResponseEntity.ok().build();
    }

    /** 잘못된 배치 요청(미보유·미지 소품·범위 밖·중복·개수 초과)은 400으로 돌려준다. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> handleInvalidPlacement(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
