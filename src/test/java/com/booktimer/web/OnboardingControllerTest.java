package com.booktimer.web;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import jakarta.servlet.http.HttpSession;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import java.security.Principal;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 온보딩(첫 진입 초기 설정) 화면/처리 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>GET은 초기 설정 폼을 보여주되 이미 온보딩한 사용자는 대시보드로 돌려보낸다. POST는 하루 목표를
 * 분→초로 변환해 {@code OnboardingService}로 위임한다(옛 초기 잔여·cap은 7일 윈도우 부채 모델로 사라짐).
 * 분↔초 변환·와이어링을 보고, 도메인 규칙은 하위 테스트에 위임(N-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingTimerRepository timerRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", SEOUL, Role.USER, today());
    }

    @Autowired
    private OnboardingController controller;

    @Test
    @DisplayName("GET /onboarding: 렌더 전 CSRF 토큰을 선확정한다 — SSR 폼 commit-후-500 방어(T-049 재발)")
    void onboardingForm_precommitsCsrfToken() {
        register("csrf-onb@booktimer.com");
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);
        Principal principal = () -> "csrf-onb@booktimer.com";

        controller.onboardingForm(request, principal, new ConcurrentModel());

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /onboarding: 온보딩 전 사용자에게 초기 설정 폼을 보여준다")
    void getOnboarding_showsFormForNewUser() throws Exception {
        register("new@booktimer.com");

        mockMvc.perform(get("/onboarding").with(user("new@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"))
                .andExpect(model().attributeExists("onboardingForm"));
    }

    @Test
    @DisplayName("GET /onboarding: 이미 온보딩한 사용자는 대시보드로 돌려보낸다 (재온보딩 방지)")
    void getOnboarding_alreadyOnboarded_redirectsHome() throws Exception {
        User u = register("done@booktimer.com");
        u.completeOnboarding();
        userRepository.save(u);

        mockMvc.perform(get("/onboarding").with(user("done@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /onboarding: 닉네임·하루 목표(분)를 적용하고 온보딩 완료 후 대시보드로 리다이렉트")
    void postOnboarding_appliesAndRedirects() throws Exception {
        User u = register("apply@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("apply@booktimer.com")).with(csrf())
                        .param("loginId", "apply_user")
                        .param("nickname", "직접정한닉")
                        .param("incrementMinutes", "90"))  // 하루 목표 90분
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User reloaded = userRepository.findByEmail("apply@booktimer.com").orElseThrow();
        assertThat(reloaded.isOnboarded()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("직접정한닉");
        assertThat(reloaded.getLoginId()).isEqualTo("apply_user");
        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(5400L); // 90분
    }

    @Test
    @DisplayName("POST /onboarding: 남이 쓰는 닉네임이어도 온보딩이 완료된다 (nickname 중복 허용)")
    void postOnboarding_duplicateNickname_allowed() throws Exception {
        registrationService.register("taken@booktimer.com", "rawpw1234", "선점된닉", SEOUL, Role.USER, today());
        register("duptry@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("duptry@booktimer.com")).with(csrf())
                        .param("loginId", "duptry_user")
                        .param("nickname", "선점된닉")
                        .param("incrementMinutes", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User onboarded = userRepository.findByEmail("duptry@booktimer.com").orElseThrow();
        assertThat(onboarded.isOnboarded()).isTrue();
        assertThat(onboarded.getNickname()).isEqualTo("선점된닉");
    }

    @Test
    @DisplayName("POST /onboarding: 이미 쓰이는 아이디(login_id)면 필드 에러로 화면을 다시 그리고 온보딩되지 않는다")
    void postOnboarding_duplicateLoginId_rerenders() throws Exception {
        User owner = register("lidowner@booktimer.com");
        owner.assignLoginId("grabbed_id");
        userRepository.save(owner);
        register("lidtaker@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("lidtaker@booktimer.com")).with(csrf())
                        .param("loginId", "grabbed_id")
                        .param("nickname", "닉")
                        .param("incrementMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"))
                .andExpect(model().attributeHasFieldErrors("onboardingForm", "loginId"));

        assertThat(userRepository.findByEmail("lidtaker@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("POST /onboarding: 닉네임이 비면 검증 실패로 화면을 다시 그린다")
    void postOnboarding_blankNickname_rerenders() throws Exception {
        register("blanknick@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("blanknick@booktimer.com")).with(csrf())
                        .param("loginId", "blank_user")
                        .param("nickname", "")
                        .param("incrementMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"));

        assertThat(userRepository.findByEmail("blanknick@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("POST /onboarding: 음수 목표(분)면 검증 실패로 화면을 다시 그린다")
    void postOnboarding_negativeGoal_rerenders() throws Exception {
        register("neg@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("neg@booktimer.com")).with(csrf())
                        .param("loginId", "neg_user")
                        .param("nickname", "음수닉")
                        .param("incrementMinutes", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"));

        assertThat(userRepository.findByEmail("neg@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("POST /onboarding: 0분 목표는 검증 실패로 화면을 다시 그린다 (하루 목표는 최소 1분 — 발견 10)")
    void postOnboarding_zeroGoal_rerenders() throws Exception {
        register("zero@booktimer.com");

        mockMvc.perform(post("/onboarding").with(user("zero@booktimer.com")).with(csrf())
                        .param("loginId", "zero_user")
                        .param("nickname", "제로닉")
                        .param("incrementMinutes", "0")) // 0분 = "안 읽어도 됨"이라 목표의 의미가 사라짐
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"))
                .andExpect(model().attributeHasFieldErrors("onboardingForm", "incrementMinutes"));

        assertThat(userRepository.findByEmail("zero@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    // 세션 신호(§6.4)는 컨트롤러를 직접 호출해 mock 세션으로 검증한다 — 보안 필터체인을 거치면
    // MockMvc가 세션을 갈아끼워(session fixation) 컨트롤러가 심은 세션을 관측할 수 없기 때문.
    // 템플릿(data-just-onboarded)·환영 배너 렌더는 프리뷰 실 브라우저 + 프론트 vitest가 커버한다.

    @Test
    @DisplayName("POST /onboarding: 온보딩 완료 시 세션에 justOnboarded 신호를 심는다 — 대시보드 1회 환영 유도 (§6.4)")
    void postOnboarding_setsJustOnboardedSessionSignal() {
        register("welcome@booktimer.com");
        OnboardingForm form = new OnboardingForm();
        form.setLoginId("welcome_user");
        form.setNickname("환영닉");
        form.setIncrementMinutes(30);
        BindingResult br = new BeanPropertyBindingResult(form, "onboardingForm");
        Principal principal = () -> "welcome@booktimer.com";
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(req.getSession()).thenReturn(session);

        String view = controller.onboarding(form, br, principal, new ConcurrentModel(), req);

        assertThat(view).isEqualTo("redirect:/");
        verify(session).setAttribute("justOnboarded", Boolean.TRUE);
    }

    @Test
    @DisplayName("POST /onboarding: 검증 실패로 재렌더될 땐 세션 신호를 심지 않는다 (완료된 경우만 환영) (§6.4)")
    void postOnboarding_validationError_noSignal() {
        register("noflash@booktimer.com");
        OnboardingForm form = new OnboardingForm();
        form.setLoginId("noflash_user");
        form.setNickname("닉있음");
        form.setIncrementMinutes(30);
        BindingResult br = new BeanPropertyBindingResult(form, "onboardingForm");
        br.reject("forced", "검증 실패 강제(직접 호출이라 @Valid가 안 돎)");
        Principal principal = () -> "noflash@booktimer.com";
        HttpServletRequest req = mock(HttpServletRequest.class);

        String view = controller.onboarding(form, br, principal, new ConcurrentModel(), req);

        assertThat(view).isEqualTo("onboarding");
        verify(req, never()).getSession(); // 신호 심기 코드에 진입하지 않았다
    }
}
