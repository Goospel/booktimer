package com.booktimer.web;

import com.booktimer.garden.DecorationOption;
import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.GardenService;
import com.booktimer.garden.LayoutSaveRequest;
import com.booktimer.garden.PlacedItem;
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
        var garden = gardenService.view(user);
        model.addAttribute("garden", garden); // 대시보드와 동일 view 재사용(팔레트는 garden.ownedPlants())
        // 내 정원 — 저장된 식물+소품 배치를 z 통합 병합 정렬로(설계 §2 결정 ③). JS 편집·no-JS 정적 렌더가 같은 단일 소스를 본다.
        List<PlacedItem> placed = gardenLayoutService.layoutItemsOf(user);
        model.addAttribute("placedItems", placed);
        // 소품 카탈로그 — 보유 무관이라 전체가 팔레트(식물 팔레트는 garden.ownedPlants()). 편집 진입 시 부트스트랩으로 쓰인다.
        List<DecorationOption> decorations = gardenLayoutService.decorationCatalog();
        model.addAttribute("decorations", decorations);
        // C2 배회 캐릭터 — 보유 AUTHOR만 배회용으로 노출(팔레트·저장 경로와 완전 분리).
        model.addAttribute("characters", garden.ownedCharacters());
        // 정원 월드 종횡비/픽셀 — 프론트가 정규화 좌표(0~1)를 실제 픽셀로 환산하고 카메라를 핏하는 기준(설계 §2.3).
        model.addAttribute("worldWidth", GardenLayoutService.WORLD_WIDTH);
        model.addAttribute("worldHeight", GardenLayoutService.WORLD_HEIGHT);
        return "garden";
    }

    /**
     * 캔버스 배치 저장 — 편집 모드 "저장"이 식물+소품 전체를 JSON({@link LayoutSaveRequest})으로 보낸다.
     * 한 트랜잭션에서 본인 범위를 교체 저장한다(설계 §2 결정 ②).
     */
    @PostMapping("/garden/layout")
    @ResponseBody
    public ResponseEntity<Void> saveLayout(Principal principal, @RequestBody LayoutSaveRequest request) {
        User user = currentUserService.resolve(principal);
        gardenLayoutService.saveLayout(user, request);
        return ResponseEntity.ok().build();
    }

    /** 미보유 식물·미지 소품·좌표 범위 밖·같은 식물 중복·소품 개수 초과 등 잘못된 배치 요청은 400으로 돌려준다(설계 §4·§1). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> handleInvalidPlacement(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
