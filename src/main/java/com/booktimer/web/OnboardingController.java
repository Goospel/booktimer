package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.LoginIdAlreadyExistsException;
import com.booktimer.user.OnboardingService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

/**
 * 온보딩(첫 진입 초기 설정) 화면/처리.
 *
 * <p>신규 가입자는 {@link DashboardController}의 게이트에 의해 이곳으로 유도된다 — 여기서
 * <b>하루 목표</b>(+소셜 로그인이면 login_id·닉네임)를 정한다. 7일 윈도우 부채 모델로 전환한 뒤로는
 * "초기 잔여"·"누적 상한"을 정하지 않는다(시작 부채 개념이 없고, 상한은 윈도우가 대체).
 *
 * <p>GET은 폼을 보여주되 이미 온보딩한 사용자는 대시보드로 돌려보낸다(재온보딩 방지). POST는
 * 검증(@Valid) 후 분→초로 바꿔 {@link OnboardingService}에 위임하고 대시보드로 리다이렉트한다(PRG).
 */
@Controller
public class OnboardingController {

    private static final int SECONDS_PER_MINUTE = 60;

    private final CurrentUserService currentUserService;
    private final ReadingTimerRepository timerRepository;
    private final OnboardingService onboardingService;

    public OnboardingController(CurrentUserService currentUserService,
                                ReadingTimerRepository timerRepository,
                                OnboardingService onboardingService) {
        this.currentUserService = currentUserService;
        this.timerRepository = timerRepository;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/onboarding")
    public String onboardingForm(HttpServletRequest request, Principal principal, Model model) {
        CsrfTokenUtil.precommit(request); // 폼 렌더 전 세션 선확정 — commit-후-500 방어(T-049)
        User user = currentUser(principal);
        if (user.isOnboarded()) {
            return "redirect:/"; // 이미 마쳤으면 다시 보여주지 않는다
        }
        // login_id를 아직 안 정한 사용자(소셜 로그인)에게만 아이디 입력칸을 보인다. 로컬은 가입에서 받았다(불변).
        model.addAttribute("needsLoginId", user.getLoginId() == null);
        if (!model.containsAttribute("onboardingForm")) {
            // 가입 시 시드된 하루 목표를 분으로 변환해 기본값으로 채운다.
            ReadingTimer timer = timerRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalStateException("no timer for user: " + principal.getName()));
            OnboardingForm form = new OnboardingForm();
            form.setNickname(user.getNickname()); // 자동 배정/가입 닉을 기본값으로 — 사용자가 바꿀 수 있다
            form.setIncrementMinutes((int) (timer.getDailyIncrementSeconds() / SECONDS_PER_MINUTE));
            model.addAttribute("onboardingForm", form);
        }
        return "onboarding";
    }

    @PostMapping("/onboarding")
    public String onboarding(@Valid @ModelAttribute("onboardingForm") OnboardingForm form,
                             BindingResult bindingResult, Principal principal, Model model) {
        User user = currentUser(principal);
        if (user.isOnboarded()) {
            return "redirect:/";
        }
        boolean needsLoginId = user.getLoginId() == null;
        model.addAttribute("needsLoginId", needsLoginId);

        // 소셜 로그인 사용자는 여기서 login_id를 반드시 정해야 한다(로컬은 가입에서 받았으므로 입력칸 자체가 없다).
        if (needsLoginId && (form.getLoginId() == null || form.getLoginId().isBlank())) {
            bindingResult.rejectValue("loginId", "loginId.required", "아이디를 입력해 주세요");
        }

        if (bindingResult.hasErrors()) {
            return "onboarding";
        }

        try {
            onboardingService.complete(
                    user,
                    form.getLoginId(),
                    form.getNickname(),
                    form.getIncrementMinutes() * (long) SECONDS_PER_MINUTE);
        } catch (LoginIdAlreadyExistsException e) {
            // 이미 쓰이는 공개 핸들 — 다른 아이디를 받도록 필드 에러로 알리고 재렌더(온보딩 미완료 유지).
            bindingResult.rejectValue("loginId", "loginId.duplicate", "이미 사용 중인 아이디입니다");
            return "onboarding";
        } catch (IllegalArgumentException e) {
            // 예약어 등 @Pattern으로 못 거른 형식/규칙 위반(도메인 검증) — 필드 에러로 안내.
            bindingResult.rejectValue("loginId", "loginId.invalid", "사용할 수 없는 아이디입니다");
            return "onboarding";
        }

        return "redirect:/";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
