package com.booktimer.web;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.booktimer.user.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 설정 화면/처리.
 *
 * <p>GET은 현재 설정을 폼에 채워 보여준다 — 도메인은 초로 저장하므로 <b>분으로 변환</b>해 싣는다.
 * POST는 검증(@Valid + IANA 타임존) 후 분→초로 바꿔 {@link UserSettingsService}에 위임하고,
 * 성공 시 플래시 메시지와 함께 {@code /settings}로 리다이렉트한다(PRG 패턴). 검증 실패 시 재렌더.
 */
@Controller
public class SettingsController {

    private static final int SECONDS_PER_MINUTE = 60;

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;
    private final UserSettingsService settingsService;

    public SettingsController(UserRepository userRepository,
                              ReadingTimerRepository timerRepository,
                              UserSettingsService settingsService) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.settingsService = settingsService;
    }

    /** 타임존 드롭다운 후보 — GET 폼과 POST 검증 실패 재렌더 모두에 자동으로 실린다. */
    @ModelAttribute("timezones")
    public java.util.List<String> timezones() {
        return TimeZoneOptions.all();
    }

    @GetMapping("/settings")
    public String settingsForm(Principal principal, Model model) {
        if (!model.containsAttribute("settingsForm")) {
            User user = currentUser(principal);
            ReadingTimer timer = timerRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalStateException("no timer for user: " + principal.getName()));

            SettingsForm form = new SettingsForm();
            form.setNickname(user.getNickname());
            form.setTimezone(user.getTimezone());
            form.setIncrementMinutes((int) (timer.getDailyIncrementSeconds() / SECONDS_PER_MINUTE));
            form.setCapMinutes((int) (timer.getCapSeconds() / SECONDS_PER_MINUTE));
            model.addAttribute("settingsForm", form);
        }
        return "settings";
    }

    @PostMapping("/settings")
    public String update(@Valid @ModelAttribute("settingsForm") SettingsForm form,
                         BindingResult bindingResult, Principal principal,
                         RedirectAttributes redirectAttributes) {
        // 타임존 IANA 유효성(@NotBlank로는 못 잡음) — 실패 시 필드 에러로 변환
        if (form.getTimezone() != null && !form.getTimezone().isBlank()) {
            try {
                ZoneId.of(form.getTimezone());
            } catch (DateTimeException e) {
                bindingResult.rejectValue("timezone", "timezone.invalid", "유효한 타임존이 아닙니다");
            }
        }

        if (bindingResult.hasErrors()) {
            return "settings";
        }

        settingsService.updateSettings(
                principal.getName(),
                form.getNickname(),
                form.getTimezone(),
                form.getIncrementMinutes() * (long) SECONDS_PER_MINUTE,
                form.getCapMinutes() * (long) SECONDS_PER_MINUTE);

        redirectAttributes.addFlashAttribute("message", "설정을 저장했습니다.");
        return "redirect:/settings";
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
