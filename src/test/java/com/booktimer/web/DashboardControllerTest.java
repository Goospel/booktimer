package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.garden.GardenView;
import com.booktimer.quote.Quote;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 대시보드(홈) 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>로그인 주체(username=email)를 도메인 User로 매핑하고, <b>오늘 부채</b>(remainingSeconds)·진행 중
 * 세션을 화면에 싣는지 검증한다. 부채는 7일 윈도우 per-day로 완료 세션에서 유도된다(옛 단일 카운터·
 * accrual·cap 제거). "이번 주 빠뜨린 날"(weeklyShortfall)은 독서 기록 화면으로 옮겨져
 * {@code HistoryControllerTest}가 검증한다. 대시보드 본화면은 온보딩을 마친 사용자만 볼 수 있으므로
 * (첫 진입 게이트), 헬퍼로 온보딩 완료한 사용자를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    // 통합 테스트는 운영 Clock.systemUTC()(실시간)를 쓰는데, "오늘 부채"가 now 기준 세션을 만들어
    // 자정 경계(예: 06-08 00:0x KST)엔 now-30분이 전날로 넘어가 플레이키하게 깨진다. 결정적 날짜로 고정.
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock fixedClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
        }
    }

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
    private ReadingGoalService goalService;

    @Autowired
    private ReadingGoalChangeRepository goalChangeRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    /**
     * 대시보드(게이트 통과)를 보려면 온보딩을 마친 사용자가 필요하다 — 등록 후 온보딩 완료 처리한다.
     * (온보딩 게이트 자체는 {@code dashboard_redirectsToOnboardingWhenNotOnboarded}가 검증한다.)
     *
     * <p>실제 온보딩 경로(OnboardingService)처럼 <b>오늘자 목표 baseline</b>도 남긴다 — 이게 없으면
     * ReadingDebtService가 폴백 목표를 윈도우 7일 전체에 적용해, '안 읽은 갓 가입자'가 윈도우 내내
     * 빚으로 잡힌다(부채 합산 ON 기본에서 헤드라인이 오늘 부채보다 커짐). baseline=오늘이면 어제 이전은
     * '시작 전'으로 제외되어 갓 가입자의 헤드라인이 오늘 부채와 같아진다.
     */
    private User registerOnboarded(String email, String nickname, LocalDate startDate) {
        User user = registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, startDate);
        user.completeOnboarding();
        user = userRepository.save(user);
        goalService.record(user, UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);
        return user;
    }

    @Test
    @DisplayName("GET /: 로그인 사용자에게 대시보드를 그리고 잔여 시간을 싣는다")
    void dashboard_rendersForLoggedInUser() throws Exception {
        registerOnboarded("dash@booktimer.com", "책벌레", today());

        mockMvc.perform(get("/").with(user("dash@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("nickname", "책벌레"))
                // remainingSeconds = 오늘 부채 = 목표(기본 1h) − 오늘 읽은 양(0) = 1h
                .andExpect(model().attribute("remainingSeconds", 3600L))
                .andExpect(model().attribute("hasActiveSession", false))
                // 대시보드에도 독서 잔디(컨트리뷰션 그래프)를 싣는다
                .andExpect(model().attributeExists("graph"));
    }

    @Test
    @DisplayName("GET /: principal이 login_id인 로컬 사용자도 정상 해석된다 (실제 인증 컷오버 경로 — PR-4)")
    void dashboard_resolvesLoginIdPrincipal() throws Exception {
        // 7-arg register = 로컬 가입(login_id 확정) → 로그인 시 principal이 login_id가 된다.
        User u = registrationService.register(
                "loc@booktimer.com", "rawpw1234", "localhandle", "로컬책", SEOUL, Role.USER, today());
        u.completeOnboarding();
        userRepository.save(u);

        mockMvc.perform(get("/").with(user("localhandle"))) // principal = login_id (이메일 아님)
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("nickname", "로컬책"))
                .andExpect(model().attribute("loginId", "localhandle"));
    }

    @Test
    @DisplayName("GET /: ADMIN 역할 사용자는 대시보드 대신 /admin으로 리다이렉트된다 (운영자는 독서 대시보드가 불필요)")
    void dashboard_adminRedirectsToAdmin() throws Exception {
        // 운영자(ADMIN)는 책을 읽는 주체가 아니라 운영 화면으로 직행해야 한다.
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
    @DisplayName("GET /: 온보딩 전 사용자는 온보딩 페이지로 리다이렉트된다 (첫 진입 게이트)")
    void dashboard_redirectsToOnboardingWhenNotOnboarded() throws Exception {
        // 온보딩 완료 처리 없이 가입만 — 게이트에 걸려야 한다
        registrationService.register("fresh@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());

        mockMvc.perform(get("/").with(user("fresh@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"));
    }

    @Test
    @DisplayName("GET /: remainingSeconds는 '오늘 부채' — 오늘 읽은 만큼 줄어든다 (누적·accrual 없음)")
    void dashboard_remainingIsTodayDebt() throws Exception {
        User user = registerOnboarded("todaydebt@booktimer.com", "오늘", today());
        Book book = bookRepository.save(
                Book.register(user, "책", null, null, null, null, null, BookStatus.READING));
        // 오늘 30분 완료 세션 → 오늘 부채 = 목표(1h) − 30m = 30m
        Instant base = clock.instant();
        ReadingSession s = ReadingSession.start(user, base.minusSeconds(1800), book);
        s.end(base);
        sessionRepository.save(s);

        mockMvc.perform(get("/").with(user("todaydebt@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 3600L - 1800L));
    }

    @Test
    @DisplayName("GET /: 빠뜨린 날 섹션과 '빠뜨린 기록' 버튼이 대시보드에서 사라졌다 (독서 기록 화면으로 이전)")
    void dashboard_omitsMissedDaysSectionAndManualButton() throws Exception {
        // 갓 가입해 안 읽은 유저면 빠뜨린 날이 있지만(=옛날엔 대시보드에 떴음), 이제 대시보드엔 안 나와야 한다.
        registerOnboarded("nomissed@booktimer.com", "이전", today());

        mockMvc.perform(get("/").with(user("nomissed@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("이번 주 빠뜨린 날")))) // 섹션 이전됨
                .andExpect(content().string(not(containsString("/sessions/manual")))); // '빠뜨린 기록' 버튼 제거
    }

    @Test
    @DisplayName("GET /: 부채 합산 ON(기본) — 어제까지 밀린 빚을 헤드라인에 합산하고 floor(carriedDebtSeconds)를 싣는다")
    void dashboard_debtCarryover_on_accumulatesWindowDebt() throws Exception {
        User user = registerOnboarded("carryon@booktimer.com", "합산", today());
        // baseline을 이틀 전으로 더 이르게 심어 어제·그제가 윈도우 안 '빠뜨린 날'이 되게 한다(아무 날도 안 읽음).
        goalChangeRepository.save(ReadingGoalChange.of(user, today().minusDays(2), 3600L));

        // 오늘 부채 3600 + (어제·그제) 빚 2×3600=7200 → 헤드라인 10800, floor(carriedDebtSeconds) 7200
        mockMvc.perform(get("/").with(user("carryon@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("remainingSeconds", 3600L + 7200L))
                .andExpect(model().attribute("carriedDebtSeconds", 7200L));
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
    @DisplayName("GET /: 측정 드롭다운용 책을 상태별로 나눠 싣고, 읽고싶음은 제외한다")
    @SuppressWarnings("unchecked")
    void dashboard_splitsReadableBooks_excludesWantToRead() throws Exception {
        User user = registerOnboarded("split@booktimer.com", "분류", today());
        Book reading = bookRepository.save(
                Book.register(user, "읽는중책", null, null, null, null, null, BookStatus.READING));
        Book finished = bookRepository.save(
                Book.register(user, "완독책", null, null, null, null, null, BookStatus.FINISHED));
        // 읽고싶음 책은 측정 대상이 아니므로 드롭다운 어느 그룹에도 없어야 한다
        bookRepository.save(
                Book.register(user, "읽고싶은책", null, null, null, null, null, BookStatus.WANT_TO_READ));

        var result = mockMvc.perform(get("/").with(user("split@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("readingBooks"))
                .andExpect(model().attributeExists("finishedBooks"))
                .andReturn();

        var modelMap = result.getModelAndView().getModel();
        List<Book> readingBooks = (List<Book>) modelMap.get("readingBooks");
        List<Book> finishedBooks = (List<Book>) modelMap.get("finishedBooks");
        assertThat(readingBooks).extracting(Book::getId).containsExactly(reading.getId());
        assertThat(finishedBooks).extracting(Book::getId).containsExactly(finished.getId());
        assertThat(readingBooks).extracting(Book::getStatus).containsOnly(BookStatus.READING);
        assertThat(finishedBooks).extracting(Book::getStatus).containsOnly(BookStatus.FINISHED);
    }

    @Test
    @DisplayName("GET /: 가장 최근에 읽은 책이 recentBookId로 미리 선택된다")
    void dashboard_recentBookId_isMostRecentlyReadBook() throws Exception {
        User user = registerOnboarded("recent@booktimer.com", "최근", today());
        Book a = bookRepository.save(
                Book.register(user, "책A", null, null, null, null, null, BookStatus.READING));
        Book b = bookRepository.save(
                Book.register(user, "책B", null, null, null, null, null, BookStatus.READING));

        // 책A를 먼저, 책B를 더 나중에 읽음 → 가장 최근 읽은 책은 B
        Instant base = clock.instant();
        ReadingSession s1 = ReadingSession.start(user, base.minusSeconds(1000), a);
        s1.end(base.minusSeconds(900));
        ReadingSession s2 = ReadingSession.start(user, base.minusSeconds(500), b);
        s2.end(base.minusSeconds(400));
        sessionRepository.save(s1);
        sessionRepository.save(s2);

        mockMvc.perform(get("/").with(user("recent@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("recentBookId", b.getId()));
    }

    @Test
    @DisplayName("GET /: 이메일 미검증 사용자에겐 인증 유도 배너(재발송 버튼)를 보인다")
    void dashboard_unverified_showsVerifyBanner() throws Exception {
        registerOnboarded("unverified@booktimer.com", "미검증", today()); // 가입 직후라 emailVerified=false

        mockMvc.perform(get("/").with(user("unverified@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("emailVerified", false))
                .andExpect(content().string(containsString("/verify-email/resend"))); // 재발송 버튼 = 배너 노출
    }

    @Test
    @DisplayName("GET /: 이메일 검증된 사용자에겐 인증 배너를 보이지 않는다")
    void dashboard_verified_hidesVerifyBanner() throws Exception {
        User user = registerOnboarded("verifiedok@booktimer.com", "검증완료", today());
        user.verifyEmail();
        userRepository.save(user);

        mockMvc.perform(get("/").with(user("verifiedok@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("emailVerified", true))
                .andExpect(content().string(not(containsString("/verify-email/resend")))); // 배너 숨김
    }

    @Test
    @DisplayName("GET /: 대시보드에 독서 정원(베타) 뷰를 함께 싣는다 (별도 /garden 엔드포인트 없음)")
    void dashboard_includesGardenView() throws Exception {
        registerOnboarded("gardenview@booktimer.com", "정원", today());

        var result = mockMvc.perform(get("/").with(user("gardenview@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("garden"))
                .andReturn();

        Object garden = result.getModelAndView().getModel().get("garden");
        assertThat(garden).isInstanceOf(GardenView.class);
        // 시간축뿐 아니라 장르 수집축 슬롯도 함께 실린다(카탈로그는 V36 시드, 테스트 H2엔 비어 non-null만 확인).
        assertThat(((GardenView) garden).genrePlants()).isNotNull();
        // 트랙 B 레시피축(발견 식물·미스터리 슬롯)도 배선된다(카탈로그는 V37 시드, 테스트 H2엔 비어 non-null만 확인).
        assertThat(((GardenView) garden).recipePlants()).isNotNull();
        assertThat(((GardenView) garden).justDiscovered()).isNotNull();
    }

    @Test
    @DisplayName("GET /: 인사말 자리에 띄울 작가 격언을 모델에 싣는다 (큐레이션 목록 안의 한 줄)")
    void dashboard_includesRandomAuthorQuote() throws Exception {
        registerOnboarded("quote@booktimer.com", "격언", today());

        var result = mockMvc.perform(get("/").with(user("quote@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("quote"))
                .andReturn();

        Quote quote = (Quote) result.getModelAndView().getModel().get("quote");
        assertThat(quote).isNotNull();
        assertThat(quote.getText()).isNotBlank();
        assertThat(quote.getAuthor()).isNotBlank();
    }
}
