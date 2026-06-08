package com.booktimer.web;

import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingPersonalityService;
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
 * 책BTI(독서 성향) 본인 화면 (책BTI Phase 5, v1).
 *
 * <p>전용 페이지 {@code /personality}에 본인 성향 카드를 그린다 — 캐시를 활용해({@code analyzeCached})
 * 서술이 있으면 보여주고, 책이 부족하면(콜드스타트) 안내, LLM 실패면 폴백 문구를 띄운다. "다시 분석"은
 * 강제 재생성({@code force=true})이다.
 *
 * <p>여기서 <b>책방 노출 opt-in 토글</b>({@code POST /personality/visibility})도 다룬다 — 켜면 공개 책BTI를
 * 즉시 생성해(방문자 조회는 읽기 전용이므로 소유자가 미리 만들어 둠) 책방(공개 프로필)에 실린다. 끄면 노출만
 * 멈춘다(reading-personality-design Phase 6, §7 누출 차단은 공개 책만 집계로 보장).
 */
@Controller
public class PersonalityController {

    private final CurrentUserService currentUserService;
    private final ReadingPersonalityService personalityService;
    private final UserRepository userRepository;

    public PersonalityController(CurrentUserService currentUserService,
                                 ReadingPersonalityService personalityService,
                                 UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.personalityService = personalityService;
        this.userRepository = userRepository;
    }

    @GetMapping("/personality")
    public String personality(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        ReadingPersonality result = personalityService.analyzeCached(user, false);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("view", PersonalityView.from(result, ReadingPersonalityService.COLD_START_MIN_BOOKS));
        model.addAttribute("personalityPublic", user.isPersonalityPublic()); // 책방 노출 토글 상태
        model.addAttribute("loginId", user.getLoginId()); // 내 책방(공개 프로필) 링크용
        return "personality";
    }

    @PostMapping("/personality/refresh")
    public String refresh(Principal principal) {
        User user = currentUserService.resolve(principal);
        personalityService.analyzeCached(user, true); // "다시 분석" = 강제 재생성
        if (user.isPersonalityPublic()) {
            personalityService.analyzePublicCached(user, true); // 노출 중이면 공개 책BTI도 같이 최신화
        }
        return "redirect:/personality";
    }

    /**
     * 책방 노출 opt-in 토글. 켜면(true) 플래그를 세우고 공개 책BTI를 즉시 생성해 둔다(소유자 행동에서만 LLM 호출).
     * 끄면(false) 노출만 멈춘다(공개 캐시는 남겨 둬도 플래그가 노출을 막는다 — 재노출 시 즉시 반영).
     */
    @PostMapping("/personality/visibility")
    public String setVisibility(@RequestParam("public") boolean makePublic, Principal principal) {
        User user = currentUserService.resolve(principal);
        user.setPersonalityPublic(makePublic);
        userRepository.save(user);
        if (makePublic) {
            personalityService.analyzePublicCached(user, true); // 켤 때 공개 책BTI 즉시 생성(방문자는 읽기만 함)
        }
        return "redirect:/personality";
    }
}
