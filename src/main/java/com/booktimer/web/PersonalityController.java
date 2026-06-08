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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 책BTI(독서 성향) 본인 화면 (책BTI Phase 5, v1).
 *
 * <p>전용 페이지 {@code /personality}에 본인 성향 카드를 그린다 — 캐시를 활용해({@code analyzeCached})
 * 서술이 있으면 보여주고, 책이 부족하면(콜드스타트) 안내, LLM 실패면 폴백 문구를 띄운다. "다시 분석"은
 * 강제 재생성({@code force=true})이며, 악의적 반복 클릭(LLM 남용) 방어로 하루 횟수 제한을 둔다.
 *
 * <p>책BTI는 <b>공개(PUBLIC)+완독 책만으로</b> 뽑혀 항상 책방(공개 프로필)에 노출된다(공개/비공개 분기 폐지
 * 2026-06-08). 그래서 본인 페이지가 보는 성향과 책방에 실리는 성향이 같다(단일 캐시).
 */
@Controller
public class PersonalityController {

    private final CurrentUserService currentUserService;
    private final ReadingPersonalityService personalityService;
    private final UserRepository userRepository;
    private final Clock clock;

    public PersonalityController(CurrentUserService currentUserService,
                                 ReadingPersonalityService personalityService,
                                 UserRepository userRepository,
                                 Clock clock) {
        this.currentUserService = currentUserService;
        this.personalityService = personalityService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @GetMapping("/personality")
    public String personality(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);
        ReadingPersonality result = personalityService.analyzeCached(user, false);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("view", PersonalityView.from(result, ReadingPersonalityService.COLD_START_MIN_BOOKS));
        model.addAttribute("loginId", user.getLoginId()); // 내 책방(공개 프로필) 링크용 — 책BTI는 항상 책방에 노출됨
        // "다시 분석" 일일 잔여 횟수(버튼 비활성·안내용) — 읽기만(상태 불변)
        model.addAttribute("refreshRemaining", user.remainingPersonalityRefreshes(todayFor(user)));
        model.addAttribute("refreshLimit", User.DAILY_PERSONALITY_REFRESH_LIMIT);
        return "personality";
    }

    @PostMapping("/personality/refresh")
    public String refresh(Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUserService.resolve(principal);
        // 악의적 반복 클릭(LLM 남용) 방어 — 하루 한도를 소비 시도. 초과면 LLM 호출 없이 차단한다.
        if (!user.tryConsumePersonalityRefresh(todayFor(user))) {
            redirectAttributes.addFlashAttribute("refreshLimited", true);
            return "redirect:/personality";
        }
        userRepository.save(user); // 소비한 카운트(또는 자정 롤오버 리셋)를 영속화
        personalityService.analyzeCached(user, true); // "다시 분석" = 강제 재생성(공개 책 기반 단일 캐시 → 책방도 같이 최신화)
        return "redirect:/personality";
    }

    /** 사용자 타임존 기준 오늘 날짜(일일 한도의 자정 경계 계산용). */
    private LocalDate todayFor(User user) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
    }
}
