package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingGoalService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;

/**
 * 대시보드 라우팅·셸 통합 테스트.
 *
 * <p>Vue 전환 이후 SSR 모델 속성(nickname/remainingSeconds 등)은 제거됐다 — 콘텐츠는
 * {@link com.booktimer.web.api.DashboardApiController}가 JSON으로, Vue 섬이 클라이언트에서 렌더한다.
 * 이 테스트는 ① 라우팅(비로그인·ADMIN·온보딩 미완) ② 얇은 셸 구조만 검증한다.
 * 비즈니스 로직(부채·진행 세션·잔디 등)은 {@link com.booktimer.web.api.DashboardApiControllerTest}에 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock fixedClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
        }
    }

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingSessionService sessionService;
    @Autowired ReadingGoalService goalService;
    @Autowired ReadingGoalChangeRepository goalChangeRepository;
    @Autowired Clock clock;
    @Autowired DashboardController dashboardController;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User registerOnboarded(String email, String nickname, LocalDate startDate) {
        User user = registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, startDate);
        user.completeOnboarding();
        user = userRepository.save(user);
        goalService.record(user, UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);
        return user;
    }

    // ── 라우팅 게이트 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /: 온보딩 완료 사용자 → 200 dashboard 셸")
    void dashboard_rendersForLoggedInUser() throws Exception {
        registerOnboarded("dash@booktimer.com", "책벌레", today());

        mockMvc.perform(get("/").with(user("dash@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    @DisplayName("GET /: login_id principal(로컬 로그인)도 정상 처리됨")
    void dashboard_resolvesLoginIdPrincipal() throws Exception {
        User u = registrationService.register(
                "loc@booktimer.com", "rawpw1234", "localhandle", "로컬책", SEOUL, Role.USER, today());
        u.completeOnboarding();
        userRepository.save(u);

        mockMvc.perform(get("/").with(user("localhandle")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    @DisplayName("GET /: ADMIN → /admin 리다이렉트")
    void dashboard_adminRedirectsToAdmin() throws Exception {
        User admin = registrationService.register(
                "admin@booktimer.com", "rawpw1234", "운영자", SEOUL, Role.ADMIN, today());
        admin.assignLoginId("adminhandle");
        admin.completeOnboarding();
        userRepository.save(admin);

        mockMvc.perform(get("/").with(user("adminhandle")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("GET /: 온보딩 미완 → /onboarding 리다이렉트")
    void dashboard_redirectsToOnboardingWhenNotOnboarded() throws Exception {
        registrationService.register("fresh@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());

        mockMvc.perform(get("/").with(user("fresh@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"));
    }

    // ── PWA 콜드 런치 별칭: /dashboard (동결된 구 install 구제) ─────────────────
    // 이미 설치된 PWA는 start_url을 설치 시점에 동결한다. #473이 manifest를 /dashboard→/로
    // 고쳤어도, 구 install은 여전히 콜드 런치마다 /dashboard를 쏜다. 서버가 이를 /와 동일하게
    // 받아 404를 막는다(별칭이 단순 정적 페이지가 아니라 / 의 분기 로직을 그대로 공유함을 박는다).

    @Test
    @DisplayName("GET /dashboard: 온보딩 완료 사용자 → 200 dashboard 셸 (/ 별칭)")
    void dashboardAlias_rendersForLoggedInUser() throws Exception {
        registerOnboarded("dashalias@booktimer.com", "별칭책벌레", today());

        mockMvc.perform(get("/dashboard").with(user("dashalias@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    @DisplayName("GET /dashboard: ADMIN → /admin 리다이렉트 (/ 와 동일 분기)")
    void dashboardAlias_adminRedirectsToAdmin() throws Exception {
        User admin = registrationService.register(
                "adminalias@booktimer.com", "rawpw1234", "별칭운영자", SEOUL, Role.ADMIN, today());
        admin.assignLoginId("adminaliashandle");
        admin.completeOnboarding();
        userRepository.save(admin);

        mockMvc.perform(get("/dashboard").with(user("adminaliashandle")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    // ── 셸 버퍼 안전 게이트 (CSRF 선확정 제거 전제조건) ──────────────────────

    @Test
    @DisplayName("GET /: 얇은 셸 — grass-grid 없음 + dashboard-app 마운트 포인트 있음")
    void dashboardShell_smallResponse_noBufferCommit() throws Exception {
        registerOnboarded("shell@booktimer.com", "셸", today());

        mockMvc.perform(get("/").with(user("shell@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("grass-grid"))))
                .andExpect(content().string(containsString("dashboard-app")));
    }

    // ── 가입완료 환영 신호 (§6.4): 세션 → 모델(justOnboarded) → 셸 data 속성, 읽고 삭제 = show-once ──
    // 컨트롤러를 직접 호출해 mock 세션으로 검증한다 — MockMvc 보안 필터체인은 세션을 갈아끼워
    // 컨트롤러가 읽는 세션을 결정적으로 주입할 수 없다. 템플릿 렌더(data-just-onboarded="true")는 프리뷰가 커버.

    @Test
    @DisplayName("GET /: 세션에 justOnboarded 신호가 있으면 모델에 담고 즉시 제거한다 (show-once, §6.4)")
    void dashboard_withJustOnboardedSignal_addsModelAndConsumes() {
        registerOnboarded("welcomesig@booktimer.com", "환영", today());
        Principal principal = () -> "welcomesig@booktimer.com";
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute("justOnboarded")).thenReturn(Boolean.TRUE);
        Model model = new ConcurrentModel();

        String view = dashboardController.dashboard(principal, req, model);

        assertThat(view).isEqualTo("dashboard");
        assertThat(model.getAttribute("justOnboarded")).isEqualTo(true);
        verify(session).removeAttribute("justOnboarded"); // 읽은 즉시 소비(1회성)
    }

    @Test
    @DisplayName("GET /: 세션 신호가 없으면 모델에 justOnboarded가 없다 (일반 로드엔 환영 배너 없음) (§6.4)")
    void dashboard_withoutSignal_noModelAttr() {
        registerOnboarded("nosig@booktimer.com", "일반", today());
        Principal principal = () -> "nosig@booktimer.com";
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getSession(false)).thenReturn(null); // 세션 자체가 없음
        Model model = new ConcurrentModel();

        String view = dashboardController.dashboard(principal, req, model);

        assertThat(view).isEqualTo("dashboard");
        assertThat(model.getAttribute("justOnboarded")).isNull();
    }
}
