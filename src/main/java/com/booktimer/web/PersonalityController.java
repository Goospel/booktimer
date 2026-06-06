package com.booktimer.web;

import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingPersonalityService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

/**
 * 책BTI(독서 성향) 본인 화면 (책BTI Phase 5, v1).
 *
 * <p>전용 페이지 {@code /personality}에 본인 성향 카드를 그린다 — 캐시를 활용해({@code analyzeCached})
 * 서술이 있으면 보여주고, 책이 부족하면(콜드스타트) 안내, LLM 실패면 폴백 문구를 띄운다. "다시 분석"은
 * 강제 재생성({@code force=true})이다. 결과는 본인만 본다(v1 비노출 — 누출 없음).
 */
@Controller
public class PersonalityController {

    private final CurrentUserService currentUserService;
    private final ReadingPersonalityService personalityService;

    public PersonalityController(CurrentUserService currentUserService,
                                 ReadingPersonalityService personalityService) {
        this.currentUserService = currentUserService;
        this.personalityService = personalityService;
    }

    @GetMapping("/personality")
    public String personality(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        ReadingPersonality result = personalityService.analyzeCached(user, false);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("view", PersonalityView.from(result, ReadingPersonalityService.COLD_START_MIN_BOOKS));
        return "personality";
    }

    @PostMapping("/personality/refresh")
    public String refresh(Principal principal) {
        User user = currentUserService.resolve(principal);
        personalityService.analyzeCached(user, true); // "다시 분석" = 강제 재생성
        return "redirect:/personality";
    }
}
