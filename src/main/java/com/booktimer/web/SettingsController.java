package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AccountDeletionConfirmationException;
import com.booktimer.user.AccountService;
import com.booktimer.user.InvalidPasswordException;
import com.booktimer.user.User;
import com.booktimer.user.UserSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final CurrentUserService currentUserService;
    private final ReadingTimerRepository timerRepository;
    private final UserSettingsService settingsService;
    private final AccountService accountService;

    public SettingsController(CurrentUserService currentUserService,
                              ReadingTimerRepository timerRepository,
                              UserSettingsService settingsService,
                              AccountService accountService) {
        this.currentUserService = currentUserService;
        this.timerRepository = timerRepository;
        this.settingsService = settingsService;
        this.accountService = accountService;
    }

    /** 타임존 드롭다운 후보 — GET 폼과 POST 검증 실패 재렌더 모두에 자동으로 실린다. */
    @ModelAttribute("timezones")
    public java.util.List<String> timezones() {
        return TimeZoneOptions.all();
    }

    @GetMapping("/settings")
    public String settingsForm(Principal principal, Model model) {
        User user = currentUser(principal);
        if (!model.containsAttribute("settingsForm")) {
            ReadingTimer timer = timerRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalStateException("no timer for user: " + principal.getName()));

            SettingsForm form = new SettingsForm();
            form.setNickname(user.getNickname());
            form.setTimezone(user.getTimezone());
            form.setIncrementMinutes((int) (timer.getDailyIncrementSeconds() / SECONDS_PER_MINUTE));
            form.setDebtCarryover(timer.isDebtCarryover());
            model.addAttribute("settingsForm", form);
        }
        // 소셜 계정은 비밀번호가 없다 → 비밀번호 변경 카드를 숨기고, 탈퇴는 본인 @핸들(login_id) 재입력으로 확인한다.
        model.addAttribute("localAccount", user.isLocalAccount());
        model.addAttribute("loginId", user.getLoginId());
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
                currentUser(principal).getEmail(), // 서비스는 email로 조회 — principal(login_id)을 User로 해석해 넘긴다
                form.getNickname(),
                form.getTimezone(),
                form.getIncrementMinutes() * (long) SECONDS_PER_MINUTE,
                form.isDebtCarryover());

        redirectAttributes.addFlashAttribute("message", "설정을 저장했습니다.");
        return "redirect:/settings";
    }

    /**
     * 비밀번호 변경. 같은 설정 페이지에 여러 폼이 공존하므로 검증 실패는 필드 에러 대신
     * 플래시 메시지 + 리다이렉트(PRG)로 처리한다. 현재 비밀번호 확인은 {@link AccountService}가 담당.
     */
    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal, RedirectAttributes redirectAttributes) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            redirectAttributes.addFlashAttribute("error", "새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
            return "redirect:/settings";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "새 비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/settings";
        }
        try {
            accountService.changePassword(currentUser(principal).getEmail(), currentPassword, newPassword);
        } catch (InvalidPasswordException e) {
            redirectAttributes.addFlashAttribute("error", "현재 비밀번호가 올바르지 않습니다.");
            return "redirect:/settings";
        }
        redirectAttributes.addFlashAttribute("message", "비밀번호를 변경했습니다.");
        return "redirect:/settings";
    }

    /**
     * 회원 탈퇴. 되돌릴 수 없는 파괴적 동작이라 재확인을 요구한다 — LOCAL은 비밀번호를, 소셜은 비번이 없어
     * 본인 @핸들(login_id) 재입력을 확인한다. 통과하면 계정·연관 데이터를 삭제하고 현재 세션을 무효화한 다음
     * 로그인 화면({@code /login?deleted})으로 보낸다. 실패(비번/핸들 불일치)는 플래시 에러로 설정 화면에 되돌린다.
     */
    @PostMapping("/settings/delete")
    public String deleteAccount(@RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmHandle, Principal principal,
                                HttpServletRequest request, HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        if (user.isLocalAccount()) {
            // LOCAL 계정: 비밀번호 재확인 필수.
            try {
                accountService.deleteAccount(user.getEmail(), password);
            } catch (InvalidPasswordException e) {
                redirectAttributes.addFlashAttribute("error", "비밀번호가 올바르지 않습니다.");
                return "redirect:/settings";
            }
        } else {
            // 소셜 계정: 비밀번호가 없으므로 본인 @핸들 재입력으로 확인(우발적·CSRF성 삭제 방어).
            try {
                accountService.deleteSocialAccount(user.getEmail(), confirmHandle);
            } catch (AccountDeletionConfirmationException e) {
                redirectAttributes.addFlashAttribute("error", "아이디가 일치하지 않습니다. 탈퇴하려면 본인 아이디를 정확히 입력하세요.");
                return "redirect:/settings";
            }
        }
        // 계정이 사라졌으니 현재 인증 세션을 무효화하고 컨텍스트를 비운다.
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?deleted";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
