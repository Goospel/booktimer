package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.LoginIdAlreadyExistsException;
import com.booktimer.user.OnboardingService;
import com.booktimer.user.User;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 온보딩(첫 진입 초기 설정) 화면/처리.
 *
 * <p>신규 가입자는 {@link DashboardController}의 게이트에 의해 이곳으로 유도된다 — 여기서
 * 타이머 <b>초기값(시작 잔여)+증가값+상한</b>을 직접 정한다. 가입 시 잔여는 증가값으로 자동
 * 시드되지만, 사용자가 원하는 시작값을 고를 수 있게 하는 게 이 단계의 목적이다.
 *
 * <p>GET은 폼을 보여주되 이미 온보딩한 사용자는 대시보드로 돌려보낸다(재온보딩 방지). POST는
 * 검증(@Valid + 초기값 ≤ 상한) 후 분→초로 바꿔 {@link OnboardingService}에 위임하고 대시보드로
 * 리다이렉트한다(PRG). "오늘"(유저 타임존)은 {@link Clock}+유저 TZ로 계산해 넘긴다(N-010).
 */
@Controller
public class OnboardingController {

    private static final int SECONDS_PER_MINUTE = 60;

    private final CurrentUserService currentUserService;
    private final ReadingTimerRepository timerRepository;
    private final OnboardingService onboardingService;
    private final Clock clock;

    public OnboardingController(CurrentUserService currentUserService,
                                ReadingTimerRepository timerRepository,
                                OnboardingService onboardingService,
                                Clock clock) {
        this.currentUserService = currentUserService;
        this.timerRepository = timerRepository;
        this.onboardingService = onboardingService;
        this.clock = clock;
    }

    @GetMapping("/onboarding")
    public String onboardingForm(Principal principal, Model model) {
        User user = currentUser(principal);
        if (user.isOnboarded()) {
            return "redirect:/"; // 이미 마쳤으면 다시 보여주지 않는다
        }
        // login_id를 아직 안 정한 사용자(소셜 로그인)에게만 아이디 입력칸을 보인다. 로컬은 가입에서 받았다(불변).
        model.addAttribute("needsLoginId", user.getLoginId() == null);
        if (!model.containsAttribute("onboardingForm")) {
            // 가입 시 시드된 타이머 값을 분으로 변환해 기본값으로 채운다.
            ReadingTimer timer = timerRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalStateException("no timer for user: " + principal.getName()));
            OnboardingForm form = new OnboardingForm();
            form.setNickname(user.getNickname()); // 자동 배정/가입 닉을 기본값으로 — 사용자가 바꿀 수 있다
            form.setInitialMinutes((int) (timer.getRemainingSeconds() / SECONDS_PER_MINUTE));
            form.setIncrementMinutes((int) (timer.getDailyIncrementSeconds() / SECONDS_PER_MINUTE));
            form.setCapMinutes((int) (timer.getCapSeconds() / SECONDS_PER_MINUTE));
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

        // 초기값은 상한을 넘을 수 없다(넘으면 도메인이 클램프하지만, 의도치 않은 손실을 막게 명시 안내).
        if (form.getInitialMinutes() != null && form.getCapMinutes() != null
                && form.getInitialMinutes() > form.getCapMinutes()) {
            bindingResult.rejectValue("initialMinutes", "initial.aboveCap", "초기값은 상한보다 클 수 없습니다");
        }
        // 소셜 로그인 사용자는 여기서 login_id를 반드시 정해야 한다(로컬은 가입에서 받았으므로 입력칸 자체가 없다).
        if (needsLoginId && (form.getLoginId() == null || form.getLoginId().isBlank())) {
            bindingResult.rejectValue("loginId", "loginId.required", "아이디를 입력해 주세요");
        }

        if (bindingResult.hasErrors()) {
            return "onboarding";
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
        try {
            onboardingService.complete(
                    user,
                    form.getLoginId(),
                    form.getNickname(),
                    form.getInitialMinutes() * (long) SECONDS_PER_MINUTE,
                    form.getIncrementMinutes() * (long) SECONDS_PER_MINUTE,
                    form.getCapMinutes() * (long) SECONDS_PER_MINUTE,
                    today);
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
