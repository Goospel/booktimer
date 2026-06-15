package com.booktimer.web;

import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.GardenService;
import com.booktimer.garden.PlacedPlant;
import com.booktimer.garden.PlacementRequest;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
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
import java.util.List;

/**
 * 독서 정원 도감 + 꾸미기(배치) 페이지({@code /garden}).
 *
 * <p>대시보드 잔디 카드 안 경량 요약({@code fragments/garden :: summary})에서 "정원 도감 →" 링크로 들어온다.
 * 대시보드와 <b>같은</b> {@link GardenService#view}를 재사용해 4축(시간·장르·다양성·레시피) 카탈로그 전체를
 * 싣고, 그 위에 보유 식물을 격자에 배치한 "내 정원"({@link GardenLayoutService})을 얹는다 — 도감은 읽기,
 * 배치는 사용자 꾸미기 의도(저장)다.
 *
 * <p>배치 저장은 {@link #saveLayout}({@code POST /garden/layout}, JSON)으로 한다 — 편집 모드에서 "저장" 시
 * 캔버스 전체를 보낸다. 보유 검증·격자·중복 실패는 {@link IllegalArgumentException}으로 올라와 400으로 매핑한다.
 * {@code /garden**}은 default-deny 정책에서 permitAll 목록에 없어 자동으로 인증·CSRF 보호된다(SecurityConfig).
 */
@Controller
public class GardenController {

    private final CurrentUserService currentUserService;
    private final GardenService gardenService;
    private final GardenLayoutService gardenLayoutService;

    public GardenController(CurrentUserService currentUserService,
                            GardenService gardenService,
                            GardenLayoutService gardenLayoutService) {
        this.currentUserService = currentUserService;
        this.gardenService = gardenService;
        this.gardenLayoutService = gardenLayoutService;
    }

    @GetMapping("/garden")
    public String garden(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname()); // brand 헤더 인사용(books/dashboard 관례)
        model.addAttribute("garden", gardenService.view(user)); // 대시보드와 동일 view 재사용(팔레트는 garden.ownedPlants())
        // 내 정원 — 저장된 배치 ∩ 현재 보유(zOrder 오름차순). JS 편집·no-JS 정적 렌더가 같은 좌표 소스를 본다.
        List<PlacedPlant> placed = gardenLayoutService.layoutOf(user);
        model.addAttribute("placedPlants", placed);
        // 정원 월드 종횡비/픽셀 — 프론트가 정규화 좌표(0~1)를 실제 픽셀로 환산하고 카메라를 핏하는 기준(설계 §2.3).
        model.addAttribute("worldWidth", GardenLayoutService.WORLD_WIDTH);
        model.addAttribute("worldHeight", GardenLayoutService.WORLD_HEIGHT);
        return "garden";
    }

    /** 캔버스 배치 저장 — 편집 모드 "저장"이 현재 배치 전체를 JSON으로 보낸다. 본인 범위 교체 저장(설계 §2.4). */
    @PostMapping("/garden/layout")
    @ResponseBody
    public ResponseEntity<Void> saveLayout(Principal principal, @RequestBody List<PlacementRequest> requests) {
        User user = currentUserService.resolve(principal);
        gardenLayoutService.save(user, requests);
        return ResponseEntity.ok().build();
    }

    /** 보유하지 않은 식물·좌표 범위 밖·같은 식물 중복 등 잘못된 배치 요청은 400으로 돌려준다(설계 §4). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> handleInvalidPlacement(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
