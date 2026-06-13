package com.booktimer.web;

import com.booktimer.garden.GardenService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 독서 정원 도감 전용 페이지({@code /garden}) — 수집 도감의 본진.
 *
 * <p>대시보드 잔디 카드 안의 경량 요약({@code fragments/garden :: summary})에서 "정원 도감 →" 링크로 들어온다.
 * 대시보드와 <b>같은</b> {@link GardenService#view}를 재사용해 3축(시간·장르·레시피) 카탈로그 전체를
 * 한 번에 싣고, 화면은 축 탭·보유/미보유 필터를 클라이언트(Alpine)에서 토글한다 — 데이터가 이미 다 내려와
 * 있어 서버 왕복이 필요 없다.
 *
 * <p>{@code view}는 트랙 B 레시피 발견을 저장(쓰기)하지만 {@code uk + existsBy}로 멱등이라, 대시보드 진입과
 * {@code /garden} 진입이 같은 발견을 중복 저장하지 않는다. {@code /garden}은 default-deny 정책에서
 * permitAll 목록에 없어 자동으로 인증 보호된다(SecurityConfig).
 */
@Controller
public class GardenController {

    private final CurrentUserService currentUserService;
    private final GardenService gardenService;

    public GardenController(CurrentUserService currentUserService, GardenService gardenService) {
        this.currentUserService = currentUserService;
        this.gardenService = gardenService;
    }

    @GetMapping("/garden")
    public String garden(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        model.addAttribute("nickname", user.getNickname()); // brand 헤더 인사용(books/dashboard 관례)
        model.addAttribute("garden", gardenService.view(user)); // 대시보드와 동일 view 재사용
        return "garden";
    }
}
