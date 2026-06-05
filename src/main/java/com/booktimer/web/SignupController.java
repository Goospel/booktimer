package com.booktimer.web;

import com.booktimer.user.EmailAlreadyExistsException;
import com.booktimer.user.LoginIdAlreadyExistsException;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 회원가입 화면/처리.
 *
 * <p>GET은 빈 폼을, POST는 검증 후 {@link UserRegistrationService#register}로 사용자를
 * 만들고 로그인 화면으로 리다이렉트한다(가입 후 수동 로그인). 검증 실패 시 화면을 다시 그린다.
 *
 * <p>누적 시작일("오늘")은 여기서 {@link Clock}과 유저 타임존으로 계산해 넘긴다 — 절대 시점은
 * 시계가, "오늘"은 유저 TZ가 결정(N-010). 등록 서비스는 평문을 받아 해싱한다.
 */
@Controller
public class SignupController {

    private final UserRegistrationService registrationService;
    private final Clock clock;

    public SignupController(UserRegistrationService registrationService, Clock clock) {
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /** 타임존 드롭다운 후보 — GET 폼과 POST 검증 실패 재렌더 모두에 자동으로 실린다. */
    @ModelAttribute("timezones")
    public java.util.List<String> timezones() {
        return TimeZoneOptions.all();
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm", new SignupForm());
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form,
                         BindingResult bindingResult) {
        // 타임존 형식 검증(@NotBlank로는 못 잡는 IANA 유효성) — 실패 시 필드 에러로 변환
        ZoneId zone = null;
        if (form.getTimezone() != null && !form.getTimezone().isBlank()) {
            try {
                zone = ZoneId.of(form.getTimezone());
            } catch (DateTimeException e) {
                bindingResult.rejectValue("timezone", "timezone.invalid", "유효한 타임존이 아닙니다");
            }
        }

        if (bindingResult.hasErrors()) {
            return "signup";
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        try {
            registrationService.register(
                    form.getEmail(), form.getPassword(), form.getLoginId(), form.getNickname(),
                    form.getTimezone(), Role.USER, today);
        } catch (EmailAlreadyExistsException | DataIntegrityViolationException e) {
            // 계정 열거 완화: 이메일은 비공개 속성이라 "이미 가입됨"을 응답으로 드러내면 열거가 된다.
            // 그래서 가입 성공과 "동일한 응답"으로 흡수한다 — 계정은 만들어지지 않았고(예외가 저장 전/플러시에 발생),
            // 응답만 성공과 같아 이메일 존재 여부를 구분할 수 없다. (동시 가입 레이스의 DB 제약 위반도 같은 처리 →
            // 500 방지 + login_id 레이스조차 안전 측 success. 이메일 발송 인프라가 없어 "조용히 수락+메일 통지" 대신
            // 동일 리다이렉트로 갈음 — 잊고 재가입한 사용자는 로그인 단계에서 알게 되는 비용은 감수.)
            return "redirect:/login?registered";
        } catch (LoginIdAlreadyExistsException e) {
            // 이미 쓰이는 로그인 아이디 — 다른 아이디를 받도록 필드 에러로 안내(생성 없음)
            bindingResult.rejectValue("loginId", "loginId.duplicate", "이미 사용 중인 아이디입니다");
            return "signup";
        } catch (IllegalArgumentException e) {
            // 예약어 등 @Pattern으로 못 거른 형식/규칙 위반(도메인 검증) — 필드 에러로 안내
            bindingResult.rejectValue("loginId", "loginId.invalid", "사용할 수 없는 아이디입니다");
            return "signup";
        }

        return "redirect:/login?registered";
    }
}
