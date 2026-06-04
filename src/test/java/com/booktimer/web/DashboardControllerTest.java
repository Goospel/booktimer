package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 대시보드(홈) 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>로그인 주체(username=email)를 도메인 User로 매핑하고, 접속 시 누적(accrueToToday)을
 * 적용한 뒤 잔여 시간·진행 중 세션을 화면에 싣는지 검증한다. 대시보드 본화면은 온보딩을 마친
 * 사용자만 볼 수 있으므로(첫 진입 게이트), 헬퍼로 온보딩 완료한 사용자를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    /**
     * 대시보드(게이트 통과)를 보려면 온보딩을 마친 사용자가 필요하다 — 등록 후 온보딩 완료 처리한다.
     * (온보딩 게이트 자체는 {@code dashboard_redirectsToOnboardingWhenNotOnboarded}가 검증한다.)
     */
    private User registerOnboarded(String email, String nickname, LocalDate startDate) {
        User user = registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, startDate);
        user.completeOnboarding();
        return userRepository.save(user);
    }

    @Test
    @DisplayName("GET /: 로그인 사용자에게 대시보드를 그리고 잔여 시간을 싣는다")
    void dashboard_rendersForLoggedInUser() throws Exception {
        registerOnboarded("dash@booktimer.com", "책벌레", today());

        mockMvc.perform(get("/").with(user("dash@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("nickname", "책벌레"))
                // 가입 당일 1증가값 시드(1h) — 같은 날 접속이라 추가 누적 없음
                .andExpect(model().attribute("remainingSeconds", 3600L))
                .andExpect(model().attribute("hasActiveSession", false))
                // 대시보드에도 독서 잔디(컨트리뷰션 그래프)를 싣는다
                .andExpect(model().attributeExists("graph"));
    }

    @Test
    @DisplayName("GET /: 온보딩 전 사용자는 온보딩 페이지로 리다이렉트된다 (첫 진입 게이트)")
    void dashboard_redirectsToOnboardingWhenNotOnboarded() throws Exception {
        // 온보딩 완료 처리 없이 가입만 — 게이트에 걸려야 한다
        registrationService.register("fresh@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());

        mockMvc.perform(get("/").with(user("fresh@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"));
    }

    @Test
    @DisplayName("GET /: 접속 시 경과 일수만큼 누적이 적용된다 (시작일 1h 시드 + 2일치 2h = 3h)")
    void dashboard_appliesAccrualOnAccess() throws Exception {
        // 2일 전 시작 → 시드 1h + 접속 시 2일치(1h*2) 누적 = 3h(10800s)
        registerOnboarded("acc@booktimer.com", "독서가", today().minusDays(2));

        mockMvc.perform(get("/").with(user("acc@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 10800L));
    }

    @Test
    @DisplayName("GET /: 진행 중 세션이 있으면 hasActiveSession=true")
    void dashboard_showsActiveSession() throws Exception {
        User user = registerOnboarded("act@booktimer.com", "진행중", today());
        Book book = bookRepository.save(
                Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING));
        sessionService.start(user, clock.instant(), book);

        mockMvc.perform(get("/").with(user("act@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActiveSession", true));
    }

    @Test
    @DisplayName("GET /: 진행 중 세션이 책에 연결돼 있으면 그 책의 누적 독서 시간을 싣는다 (완료 세션 합, 진행 중은 미포함)")
    void dashboard_activeBookTotalSeconds() throws Exception {
        User user = registerOnboarded("booktotal@booktimer.com", "독서가", today());
        Book book = bookRepository.save(
                Book.register(user, "전쟁과 평화", null, null, null, null, null, BookStatus.READING));

        // 과거 완료 세션 2건: 600s + 1200s = 1800s
        Instant base = clock.instant();
        ReadingSession s1 = ReadingSession.start(user, base.minusSeconds(10_000), book);
        s1.end(base.minusSeconds(9_400)); // 600s
        ReadingSession s2 = ReadingSession.start(user, base.minusSeconds(5_000), book);
        s2.end(base.minusSeconds(3_800)); // 1200s
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        // 같은 책으로 진행 중(미종료) 세션 — durationSeconds=0이라 합계에 영향 없어야 함
        sessionService.start(user, base, book);

        mockMvc.perform(get("/").with(user("booktotal@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActiveSession", true))
                .andExpect(model().attribute("activeBookTitle", "전쟁과 평화"))
                .andExpect(model().attribute("activeBookTotalSeconds", 1800L));
    }

    @Test
    @DisplayName("GET /: 누적 잔여가 cap에 도달하면 atCap=true (상한 경고 배지)")
    void dashboard_atCapWhenRemainingHitsCap() throws Exception {
        // 10일 전 시작 → 시드 1h + 10일치 누적이 cap(기본 5h=18000s)으로 클램프 → 잔여 == cap
        registerOnboarded("cap@booktimer.com", "상한", today().minusDays(10));

        mockMvc.perform(get("/").with(user("cap@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 18000L))
                .andExpect(model().attribute("atCap", true));
    }

    @Test
    @DisplayName("GET /: 잔여가 cap 미만이면 atCap=false")
    void dashboard_notAtCapWhenBelowCap() throws Exception {
        registerOnboarded("below@booktimer.com", "여유", today());

        mockMvc.perform(get("/").with(user("below@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("atCap", false));
    }
}
