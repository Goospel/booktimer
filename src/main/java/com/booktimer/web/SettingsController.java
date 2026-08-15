package com.booktimer.web;

import com.booktimer.garden.GardenService;
import com.booktimer.garden.ProfileCharacterService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.SessionInvalidator;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AccountDeletionConfirmationException;
import com.booktimer.user.AccountService;
import com.booktimer.user.InvalidPasswordException;
import com.booktimer.user.LoginIdAlreadyExistsException;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.TossLinkConflictException;
import com.booktimer.user.User;
import com.booktimer.user.UserSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final GardenService gardenService;
    private final ProfileCharacterService profileCharacterService;
    private final TossLinkCodeService linkCodeService;
    private final SessionInvalidator sessionInvalidator;

    public SettingsController(CurrentUserService currentUserService,
                              ReadingTimerRepository timerRepository,
                              UserSettingsService settingsService,
                              AccountService accountService,
                              GardenService gardenService,
                              ProfileCharacterService profileCharacterService,
                              TossLinkCodeService linkCodeService,
                              SessionInvalidator sessionInvalidator) {
        this.currentUserService = currentUserService;
        this.timerRepository = timerRepository;
        this.settingsService = settingsService;
        this.accountService = accountService;
        this.gardenService = gardenService;
        this.profileCharacterService = profileCharacterService;
        this.linkCodeService = linkCodeService;
        this.sessionInvalidator = sessionInvalidator;
    }

    /** 타임존 드롭다운 후보 — GET 폼과 POST 검증 실패 재렌더 모두에 자동으로 실린다. */
    @ModelAttribute("timezones")
    public java.util.List<String> timezones() {
        return TimeZoneOptions.all();
    }

    @GetMapping("/settings")
    public String settingsForm(HttpServletRequest request, Principal principal, Model model) {
        CsrfTokenUtil.precommit(request); // 폼 렌더 전 세션 선확정 — 큰 페이지 commit-후-500 방어(T-049)
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
        // 아이디 변경은 평생 1회 — 소진했으면 폼 대신 "이미 사용했어요" 정적 표기를 낸다.
        model.addAttribute("loginIdChangeUsed", user.getPreviousLoginId() != null);
        // 미검증이면 인증 유도 배너를 띄운다(정책 ③). 재발송 버튼은 POST /verify-email/resend로 이 화면에 결과를 남긴다.
        model.addAttribute("emailVerified", user.isEmailVerified());
        model.addAttribute("marketingEmailConsent", user.isMarketingEmailConsent());
        // 프로필 사진(도감 작가 얼굴) — 보유(완독)한 작가만 고를 수 있다. 현재 선택 코드도 함께 싣는다.
        model.addAttribute("ownedCharacters", gardenService.view(user).ownedCharacters());
        model.addAttribute("profileCharacterCode", user.getProfileCharacterCode());
        // 토스 앱 연결 — 연결됐으면 상태만 보이고, 아니면 일회용 코드 발급 버튼을 낸다(설계 §2.2).
        model.addAttribute("tossLinked", user.getTossUserKey() != null);
        return "settings";
    }

    /**
     * 토스 앱 연결 코드 발급 — 웹에 로그인해 있다는 사실 자체가 "이 계정의 주인" 증명이라, 그 상태에서 발급한
     * 일회용 코드를 미니앱에 입력하면 같은 계정에 토스 신원이 붙는다(설계 §2.2 ⓐ).
     *
     * <p>평문 코드는 저장하지 않으므로 <b>플래시로 한 번만</b> 보여준다 — 새로고침하면 사라지고, 다시 필요하면
     * 재발급(직전 코드는 무효화)한다. 이미 연결된 계정의 발급은 서비스가 거부하며 여기선 error 플래시로 옮긴다.
     */
    @PostMapping("/settings/toss-link-code")
    public String issueTossLinkCode(Principal principal, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("tossLinkCode", linkCodeService.issue(currentUser(principal)));
        } catch (TossLinkConflictException e) {
            redirectAttributes.addFlashAttribute("error", "이미 토스 앱에 연결된 계정이에요.");
        }
        return "redirect:/settings";
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
     *
     * <p>성공하면 <b>이 창을 뺀 나머지 세션을 끊는다</b>({@link SessionInvalidator}) — 비번을 바꾸는 이유가
     * 대개 "샜을지도 모른다"인데, 세션은 DB에 따로 살아 있어 해시만 바꿔서는 침입자가 안 쫓겨난다.
     * 세션 id는 서비스 계층이 알 수 없어(웹 개념) 이 호출만 컨트롤러에 둔다.
     */
    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal, HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            redirectAttributes.addFlashAttribute("error", "새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
            return "redirect:/settings";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "새 비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/settings";
        }
        User user = currentUser(principal);
        try {
            accountService.changePassword(user.getEmail(), currentPassword, newPassword);
        } catch (InvalidPasswordException e) {
            // 실패한 시도로는 아무 세션도 끊지 않는다(비번을 모르는 쪽이 남을 로그아웃시킬 수 없게).
            redirectAttributes.addFlashAttribute("error", "현재 비밀번호가 올바르지 않습니다.");
            return "redirect:/settings";
        }
        // 지금 이 창은 남긴다 — 방금 현재 비번을 증명한 본인이고, 아래 플래시 메시지도 이 세션에 실린다.
        HttpSession current = request.getSession(false);
        sessionInvalidator.invalidate(user, current == null ? null : current.getId());
        redirectAttributes.addFlashAttribute("message", "비밀번호를 변경했습니다. 다른 기기의 로그인은 해제됩니다.");
        return "redirect:/settings";
    }

    /**
     * 아이디(공개 @핸들) 변경 — <b>평생 1회</b>. 되돌릴 수 없으므로 새 아이디 입력에 더해 확인 체크박스를
     * 서버에서 다시 검증한다(비밀번호 재확인은 두지 않는다 — 공개 핸들이지 비밀이 아니고, 소셜 계정은
     * 비밀번호가 없어 경로가 갈라진다). 세션은 끊지 않는다 — {@code CurrentUserService}가 옛 아이디로
     * 브리지하므로 전 기기의 열린 세션이 그대로 살아 있다.
     *
     * <p>실패는 모두 같은 화면으로 되돌리되 문구를 갈라 안내한다: 소진(ISE) / 이미 사용 중(사전 검사 또는
     * 경합 시 DB UNIQUE) / 형식·현재와 동일(IAE). {@code IllegalStateException}에는 login_id 미설정도
     * 포함되지만, 웹에 들어온 계정은 가입·온보딩에서 아이디가 확정돼 있어 실무상 도달하지 않는다.
     */
    @PostMapping("/settings/login-id")
    public String changeLoginId(@RequestParam String newLoginId,
                                @RequestParam(name = "confirmOnce", defaultValue = "false") boolean confirmOnce,
                                Principal principal, RedirectAttributes redirectAttributes) {
        if (!confirmOnce) {
            redirectAttributes.addFlashAttribute("error", "되돌릴 수 없음 확인에 체크해야 아이디를 바꿀 수 있어요.");
            return "redirect:/settings";
        }
        User user = currentUser(principal);
        try {
            accountService.changeLoginId(user, newLoginId);
            redirectAttributes.addFlashAttribute("message",
                    "아이디를 @" + user.getLoginId() + "(으)로 바꿨어요. 다음 로그인부터 새 아이디를 쓰세요.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "아이디 변경은 평생 1번이에요. 이미 사용했어요.");
        } catch (LoginIdAlreadyExistsException | DataIntegrityViolationException e) {
            // 현행 핸들인지 남이 버린 옛 핸들인지는 구분해 알리지 않는다 — 구분하면 "저 사람이 아이디를
            // 바꿨구나"라는 부수 정보가 샌다.
            redirectAttributes.addFlashAttribute("error", "이미 사용 중인 아이디예요. 다른 아이디를 입력해 주세요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자, 현재 아이디와 달라야 해요.");
        }
        return "redirect:/settings";
    }

    /**
     * 마케팅(재참여 넛지) 수신 동의 토글. 체크박스는 미체크 시 파라미터가 아예 안 실리므로 기본 false로 받는다
     * (없음=꺼짐). 동의/철회를 {@link UserSettingsService}에 위임하고 PRG로 설정 화면에 되돌린다.
     */
    @PostMapping("/settings/marketing")
    public String updateMarketingConsent(
            @RequestParam(name = "marketingEmailConsent", defaultValue = "false") boolean marketingEmailConsent,
            Principal principal, RedirectAttributes redirectAttributes) {
        settingsService.updateMarketingConsent(currentUser(principal).getEmail(), marketingEmailConsent);
        redirectAttributes.addFlashAttribute("message",
                marketingEmailConsent ? "소식·알림 메일 수신을 켰습니다." : "소식·알림 메일 수신을 껐습니다.");
        return "redirect:/settings";
    }

    /**
     * 프로필 사진(도감 작가 얼굴) 선택/해제. 빈 코드는 선택 해제(이니셜 폴백)다. 보유(해금) 검증은
     * {@link ProfileCharacterService}가 담당 — 미보유 작가 위조(IDOR)는 {@link IllegalArgumentException}으로
     * 거부되어 error 플래시로 변환된다. 마케팅 토글과 동일한 PRG로 설정 화면에 되돌린다.
     */
    @PostMapping("/settings/profile-character")
    public String updateProfileCharacter(
            @RequestParam(name = "characterCode", required = false) String characterCode,
            Principal principal, RedirectAttributes redirectAttributes) {
        try {
            profileCharacterService.select(currentUser(principal), characterCode);
            boolean cleared = characterCode == null || characterCode.isBlank();
            redirectAttributes.addFlashAttribute("message",
                    cleared ? "프로필 사진을 기본(이니셜)으로 되돌렸어요." : "프로필 사진을 바꿨어요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "보유하지 않은 작가는 프로필 사진으로 설정할 수 없어요.");
        }
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
