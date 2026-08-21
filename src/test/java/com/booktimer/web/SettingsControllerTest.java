package com.booktimer.web;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCodeRepository;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import java.security.Principal;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 설정 화면/처리 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>GET은 현재 설정(분 단위로 변환)을 폼에 채워 보여주고, POST는 검증 후
 * {@code UserSettingsService}로 갱신하고 리다이렉트한다. 검증 실패(잘못된 타임존/음수 분)는
 * 화면을 다시 그린다. 분↔초 변환·와이어링을 보고, 도메인 규칙은 하위 테스트에 위임(N-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerTest {

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
    private TossLinkCodeService linkCodeService;

    @Autowired
    private TossLinkCodeRepository linkCodeRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", SEOUL, Role.USER, today());
    }

    private User registerSocial(String email) {
        return registrationService.registerOAuth(email, "구글러", SEOUL, AuthProvider.GOOGLE, today());
    }

    @Autowired
    private SettingsController controller;

    @Test
    @DisplayName("GET /settings: 렌더 전 CSRF 토큰을 선확정한다 — 폼 여러 개 큰 페이지 commit-후-500 방어(T-049 재발)")
    void settingsForm_precommitsCsrfToken() {
        register("csrf-settings@booktimer.com");
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);
        Principal principal = () -> "csrf-settings@booktimer.com";

        controller.settingsForm(request, principal, new ConcurrentModel());

        verify(token).getToken();
    }

    @Test
    @DisplayName("GET /settings: 현재 닉네임/타임존과 분 단위 하루 목표를 폼에 채워 보여준다")
    void getSettings_showsCurrentValues() throws Exception {
        register("get@booktimer.com"); // 기본: 하루 목표 3600s(60분)

        mockMvc.perform(get("/settings").with(user("get@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"))
                .andExpect(model().attributeExists("settingsForm"))
                .andExpect(content().string(containsString("독서가")))
                .andExpect(content().string(containsString("Asia/Seoul")))
                .andExpect(content().string(containsString("60"))); // 하루 목표 분
    }

    @Test
    @DisplayName("GET /settings: 타임존 선택지(드롭다운) 목록을 모델에 싣는다")
    void getSettings_includesTimezoneOptions() throws Exception {
        register("tzopts@booktimer.com");

        mockMvc.perform(get("/settings").with(user("tzopts@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("timezones", hasItem("America/New_York")));
    }

    @Test
    @DisplayName("GET /settings: LOCAL 계정은 localAccount=true, 비밀번호 변경 카드를 보여준다")
    void getSettings_localAccount_showsPasswordCard() throws Exception {
        register("local@booktimer.com");

        mockMvc.perform(get("/settings").with(user("local@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("localAccount", true))
                .andExpect(content().string(containsString("비밀번호 변경")));
    }

    @Test
    @DisplayName("GET /settings: 소셜 계정은 localAccount=false, 비밀번호 변경 카드를 숨긴다")
    void getSettings_socialAccount_hidesPasswordCard() throws Exception {
        registerSocial("social@booktimer.com");

        mockMvc.perform(get("/settings").with(user("social@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("localAccount", false))
                .andExpect(content().string(not(containsString("비밀번호 변경"))));
    }

    @Test
    @DisplayName("GET /settings: 이메일 미검증이면 인증 유도 배너(재발송 버튼)를 보인다")
    void getSettings_unverified_showsVerifyBanner() throws Exception {
        register("unverified@booktimer.com"); // 가입 직후라 emailVerified=false

        mockMvc.perform(get("/settings").with(user("unverified@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("emailVerified", false))
                .andExpect(content().string(containsString("/verify-email/resend"))); // 재발송 버튼 = 배너 노출
    }

    @Test
    @DisplayName("GET /settings: 이메일 검증됐으면 인증 배너를 숨긴다")
    void getSettings_verified_hidesVerifyBanner() throws Exception {
        User user = register("verifiedok@booktimer.com");
        user.verifyEmail();
        userRepository.save(user);

        mockMvc.perform(get("/settings").with(user("verifiedok@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("emailVerified", true))
                .andExpect(content().string(not(containsString("/verify-email/resend"))));
    }

    // 재발송 결과 플래시(verifyResendResult=sent/already/failed)의 화면 표시는 트리비얼한 안내 텍스트 분기라
    // 단위로 누른다(프로젝트 테스트-깊이 규칙). resend가 그 플래시를 남기는 행동은 EmailVerificationControllerTest가 커버.

    @Test
    @DisplayName("POST /settings: 유효하면 프로필·하루 목표를 갱신하고 /settings로 리다이렉트한다")
    void postSettings_valid_updatesAndRedirects() throws Exception {
        register("post@booktimer.com");

        mockMvc.perform(post("/settings").with(user("post@booktimer.com")).with(csrf())
                        .param("nickname", "새닉")
                        .param("timezone", "America/New_York")
                        .param("incrementMinutes", "120"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByEmail("post@booktimer.com").orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("새닉");
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");

        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(120 * 60L);
    }

    @Test
    @DisplayName("POST /settings: 타임존이 유효하지 않으면 필드 에러로 화면을 다시 그린다")
    void postSettings_invalidTimezone_rerenders() throws Exception {
        register("badtz@booktimer.com");

        mockMvc.perform(post("/settings").with(user("badtz@booktimer.com")).with(csrf())
                        .param("nickname", "닉")
                        .param("timezone", "Mars/Phobos")
                        .param("incrementMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "timezone"));
    }

    @Test
    @DisplayName("POST /settings: 하루 목표 분이 음수면 필드 에러로 화면을 다시 그린다")
    void postSettings_negativeMinutes_rerenders() throws Exception {
        register("neg@booktimer.com");

        mockMvc.perform(post("/settings").with(user("neg@booktimer.com")).with(csrf())
                        .param("nickname", "닉")
                        .param("timezone", "Asia/Seoul")
                        .param("incrementMinutes", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "incrementMinutes"));
    }

    // --- 회원 탈퇴 (소셜 계정 @핸들 재확인) ---

    private User registerSocialWithHandle(String email, String handle) {
        User social = registerSocial(email);
        social.assignLoginId(handle);
        return userRepository.save(social);
    }

    @Test
    @DisplayName("GET /settings: 소셜 계정 탈퇴 폼에 @핸들 입력칸과 본인 핸들 안내를 보여준다")
    void getSettings_socialAccount_showsHandleConfirmField() throws Exception {
        registerSocialWithHandle("handlefield@booktimer.com", "handler");

        mockMvc.perform(get("/settings").with(user("handlefield@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loginId", "handler"))
                .andExpect(content().string(containsString("confirmHandle")))
                .andExpect(content().string(containsString("@handler")));
    }

    @Test
    @DisplayName("POST /settings/delete: 소셜 계정은 @핸들이 일치하면 삭제하고 /login?deleted로 보낸다")
    void postDelete_social_handleMatches_deletes() throws Exception {
        registerSocialWithHandle("delok@booktimer.com", "delok");

        mockMvc.perform(post("/settings/delete").with(user("delok@booktimer.com")).with(csrf())
                        .param("confirmHandle", "delok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.findByEmail("delok@booktimer.com")).isEmpty();
    }

    @Test
    @DisplayName("POST /settings/delete: 소셜 계정은 @핸들이 틀리면 삭제하지 않고 설정으로 돌려보낸다")
    void postDelete_social_handleMismatch_doesNotDelete() throws Exception {
        registerSocialWithHandle("delno@booktimer.com", "delno");

        mockMvc.perform(post("/settings/delete").with(user("delno@booktimer.com")).with(csrf())
                        .param("confirmHandle", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        assertThat(userRepository.findByEmail("delno@booktimer.com")).isPresent(); // 삭제 안 됨
    }

    // --- 핸들 없는 소셜 계정(온보딩 전 login_id=null)의 탈퇴 경로 ---
    // 확인 수단을 @핸들에만 두면 이 계정은 화면으로 탈퇴가 불가능하다("@null" 안내 + 입력할 값 없음).
    // 핸들이 없으면 본인 이메일 재입력으로 확인한다.

    @Test
    @DisplayName("GET /settings: 핸들 없는 소셜 계정 탈퇴 폼은 @null 대신 본인 이메일을 확인 값으로 안내한다")
    void getSettings_socialWithoutHandle_showsEmailConfirmField() throws Exception {
        registerSocial("nohandle@booktimer.com"); // 온보딩 전 — login_id=null

        mockMvc.perform(get("/settings").with(user("nohandle@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("deleteConfirmValue", "nohandle@booktimer.com"))
                .andExpect(content().string(containsString("confirmHandle")))
                .andExpect(content().string(not(containsString("@null"))));
    }

    @Test
    @DisplayName("POST /settings/delete: 핸들 없는 소셜 계정은 이메일이 일치하면 삭제한다")
    void postDelete_socialWithoutHandle_emailMatches_deletes() throws Exception {
        registerSocial("delnohandle@booktimer.com");

        mockMvc.perform(post("/settings/delete").with(user("delnohandle@booktimer.com")).with(csrf())
                        .param("confirmHandle", "delnohandle@booktimer.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.findByEmail("delnohandle@booktimer.com")).isEmpty();
    }

    @Test
    @DisplayName("POST /settings/delete: 핸들 없는 소셜 계정도 값이 틀리면 삭제하지 않는다")
    void postDelete_socialWithoutHandle_mismatch_doesNotDelete() throws Exception {
        registerSocial("keepnohandle@booktimer.com");

        mockMvc.perform(post("/settings/delete").with(user("keepnohandle@booktimer.com")).with(csrf())
                        .param("confirmHandle", "wrong@booktimer.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        assertThat(userRepository.findByEmail("keepnohandle@booktimer.com")).isPresent();
    }

    @Test
    @DisplayName("POST /settings/delete: 핸들이 있는 소셜 계정은 이메일 입력으로는 삭제되지 않는다(안내한 값만 통과)")
    void postDelete_socialWithHandle_emailIsNotAccepted() throws Exception {
        registerSocialWithHandle("emailno@booktimer.com", "emailno");

        mockMvc.perform(post("/settings/delete").with(user("emailno@booktimer.com")).with(csrf())
                        .param("confirmHandle", "emailno@booktimer.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        assertThat(userRepository.findByEmail("emailno@booktimer.com")).isPresent();
    }

    // --- 밀린 부채 합산 표시 토글 (debtCarryover) ---

    @Test
    @DisplayName("GET /settings: 밀린 부채 합산 표시 토글(체크박스)을 폼에 싣는다")
    void getSettings_showsDebtCarryoverToggle() throws Exception {
        register("carryget@booktimer.com"); // 기본 ON

        mockMvc.perform(get("/settings").with(user("carryget@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("debtCarryover")));
    }

    @Test
    @DisplayName("POST /settings: 밀린 부채 합산 표시 토글을 끄면(체크박스 해제) 저장된다")
    void postSettings_debtCarryoverOff_saved() throws Exception {
        register("carrypost@booktimer.com"); // 기본 ON

        // 체크박스를 해제하면 debtCarryover 파라미터가 전송되지 않는다 → false로 바인딩되어야 한다.
        mockMvc.perform(post("/settings").with(user("carrypost@booktimer.com")).with(csrf())
                        .param("nickname", "닉")
                        .param("timezone", "Asia/Seoul")
                        .param("incrementMinutes", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByEmail("carrypost@booktimer.com").orElseThrow();
        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.isDebtCarryover()).isFalse();
    }

    @Test
    @DisplayName("POST /settings: 토글을 켜면(체크박스 체크=true 전송) 저장된다")
    void postSettings_debtCarryoverOn_saved() throws Exception {
        register("carryon@booktimer.com");
        // 먼저 OFF로 만들어 둔 뒤(기본 ON과 구분), 체크해서 다시 ON 되는지 본다.
        timerRepository.findByUser(userRepository.findByEmail("carryon@booktimer.com").orElseThrow())
                .orElseThrow().updateSettings(3600L, false);

        mockMvc.perform(post("/settings").with(user("carryon@booktimer.com")).with(csrf())
                        .param("nickname", "닉")
                        .param("timezone", "Asia/Seoul")
                        .param("incrementMinutes", "60")
                        .param("debtCarryover", "true"))
                .andExpect(status().is3xxRedirection());

        User reloaded = userRepository.findByEmail("carryon@booktimer.com").orElseThrow();
        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.isDebtCarryover()).isTrue();
    }

    // --- 알림: 재참여 안내 메일 수신 동의(이메일) — 웹 푸시 토글은 제거됨(네이티브 앱 백로그) ---

    @Test
    @DisplayName("GET /settings: 재참여 안내 메일 수신 동의(marketingEmailConsent) 항목을 보여준다")
    void getSettings_showsMarketingEmailConsent() throws Exception {
        register("mktmail@booktimer.com");

        mockMvc.perform(get("/settings").with(user("mktmail@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("marketingEmailConsent", false))
                .andExpect(content().string(containsString("marketingEmailConsent")))
                .andExpect(content().string(containsString("복귀 안내 메일 받기")));
    }

    @Test
    @DisplayName("GET /settings: 웹 푸시 토글 잔재(푸시 UI·오버레이·notification-settings.js)를 노출하지 않는다(제거 회귀 가드)")
    void getSettings_hasNoPushRemnants() throws Exception {
        register("nopush@booktimer.com");

        mockMvc.perform(get("/settings").with(user("nopush@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("push-reminder-toggle"))))
                .andExpect(content().string(not(containsString("push-marketing-toggle"))))
                .andExpect(content().string(not(containsString("notification-settings.js"))))
                .andExpect(content().string(not(containsString("알람 기능은 아직 개발 중"))));
    }

    // --- 프로필 사진(도감 작가 얼굴) 선택 ---

    @Test
    @DisplayName("GET /settings: 보유 작가 목록(ownedCharacters)을 모델에 싣는다(미보유면 빈 목록)")
    void getSettings_includesOwnedCharacters() throws Exception {
        register("profchar-get@booktimer.com"); // 완독책 없음 → 보유 작가 0(빈 목록)

        mockMvc.perform(get("/settings").with(user("profchar-get@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("ownedCharacters"));
    }

    @Test
    @DisplayName("GET /settings: 현재 선택한 프로필 작가 코드(profileCharacterCode)를 모델에 싣는다")
    void getSettings_includesSelectedProfileCharacter() throws Exception {
        User u = register("profchar-sel@booktimer.com");
        u.selectProfileCharacter("han_gang"); // 엔티티 직접(보유검증 우회) — 모델 전달만 검증
        userRepository.save(u);

        mockMvc.perform(get("/settings").with(user("profchar-sel@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profileCharacterCode", "han_gang"));
    }

    @Test
    @DisplayName("POST /settings/profile-character: 미보유 작가는 거부하고 error 플래시로 되돌린다(IDOR 방어)")
    void postProfileCharacter_unowned_flashErrorAndNotSaved() throws Exception {
        register("profchar-unowned@booktimer.com"); // 완독책 없음 → 어떤 작가도 미보유

        mockMvc.perform(post("/settings/profile-character")
                        .with(user("profchar-unowned@booktimer.com")).with(csrf())
                        .param("characterCode", "han_gang"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));

        User reloaded = userRepository.findByEmail("profchar-unowned@booktimer.com").orElseThrow();
        assertThat(reloaded.getProfileCharacterCode()).isNull(); // 저장 안 됨
    }

    @Test
    @DisplayName("POST /settings/profile-character: 빈 코드면 선택 해제(이니셜 폴백)하고 /settings로 되돌린다")
    void postProfileCharacter_blank_clears() throws Exception {
        User u = register("profchar-clear@booktimer.com");
        u.selectProfileCharacter("han_gang"); // 보유검증 우회(엔티티 직접) — 해제 동작만 검증
        userRepository.save(u);

        mockMvc.perform(post("/settings/profile-character")
                        .with(user("profchar-clear@booktimer.com")).with(csrf())
                        .param("characterCode", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByEmail("profchar-clear@booktimer.com").orElseThrow();
        assertThat(reloaded.getProfileCharacterCode()).isNull();
    }

    // ── 토스 앱 연결 (PR-2) ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /settings: 미연결이면 tossLinked=false — 화면이 발급 버튼을 노출하는 분기")
    void getSettings_notLinked_showsIssueBranch() throws Exception {
        register("toss-unlinked@booktimer.com");

        mockMvc.perform(get("/settings").with(user("toss-unlinked@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tossLinked", false));
    }

    @Test
    @DisplayName("GET /settings: 이미 연결된 계정이면 tossLinked=true — 발급 버튼 대신 연결됨 표시")
    void getSettings_linked_showsLinkedBranch() throws Exception {
        User u = register("toss-linked@booktimer.com");
        u.linkTossUserKey("uk-settings-linked");
        userRepository.save(u);

        mockMvc.perform(get("/settings").with(user("toss-linked@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tossLinked", true));
    }

    @Test
    @DisplayName("POST /settings/toss-link-code: 코드를 발급해 플래시로 한 번만 보여준다(평문은 DB에 없다)")
    void postTossLinkCode_issuesCode() throws Exception {
        User u = register("toss-issue@booktimer.com");

        MvcResult result = mockMvc.perform(post("/settings/toss-link-code")
                        .with(user("toss-issue@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("tossLinkCode"))
                .andReturn();

        String code = (String) result.getFlashMap().get("tossLinkCode");
        assertThat(linkCodeService.consume(code)).map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("POST /settings/toss-link-code: 미인증이면 발급하지 않고 로그인으로 보낸다")
    void postTossLinkCode_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/settings/toss-link-code").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(linkCodeRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /settings/toss-link-code: 이미 연결된 계정은 코드를 발급하지 않고 error 플래시로 되돌린다")
    void postTossLinkCode_alreadyLinked_flashError() throws Exception {
        User u = register("toss-issue-linked@booktimer.com");
        u.linkTossUserKey("uk-settings-issue");
        userRepository.save(u);

        mockMvc.perform(post("/settings/toss-link-code")
                        .with(user("toss-issue-linked@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"))
                .andExpect(flash().attribute("tossLinkCode", (Object) null));

        assertThat(linkCodeRepository.findByUserAndUsedAtIsNull(u)).isEmpty();
    }

    // --- 아이디 변경 (평생 1회) ---

    /** 아이디가 확정된 계정 — principal이 login_id인 실제 로그인 상태를 재현하려면 핸들이 있어야 한다. */
    private User registerWithHandle(String email, String handle) {
        return registrationService.register(email, "rawpw1234", handle, "독서가", SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("POST /settings/login-id: 확인 체크박스를 안 누르면 바꾸지 않고 error 플래시로 되돌린다")
    void changeLoginId_withoutConfirm_doesNothing() throws Exception {
        User u = registerWithHandle("lid-noconfirm@booktimer.com", "lidnoconfirm");

        mockMvc.perform(post("/settings/login-id").with(user("lidnoconfirm")).with(csrf())
                        .param("newLoginId", "lidbrandnew"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));

        assertThat(userRepository.findById(u.getId()).orElseThrow().getLoginId()).isEqualTo("lidnoconfirm");
    }

    @Test
    @DisplayName("POST /settings/login-id: 정상 변경 후에도 옛 아이디 principal의 세션이 살아 있다(브리지)")
    void changeLoginId_success_oldSessionStillResolves() throws Exception {
        User u = registerWithHandle("lid-ok@booktimer.com", "lidoldhandle");

        mockMvc.perform(post("/settings/login-id").with(user("lidoldhandle")).with(csrf())
                        .param("newLoginId", "lidnewhandle")
                        .param("confirmOnce", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("message"));

        User reloaded = userRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("lidnewhandle");
        assertThat(reloaded.getPreviousLoginId()).isEqualTo("lidoldhandle");

        // 이미 열려 있던 세션의 principal은 아직 옛 아이디다 — 여기서 500이 나면 전 기기가 터진 것이다.
        mockMvc.perform(get("/settings").with(user("lidoldhandle")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loginIdChangeUsed", true));
    }

    @Test
    @DisplayName("POST /settings/login-id: 이미 변경권을 쓴 계정은 '평생 1번' 안내로 거부한다")
    void changeLoginId_alreadyUsed_flashError() throws Exception {
        User u = registerWithHandle("lid-used@booktimer.com", "lidusedold");
        u.changeLoginId("lidusednew");
        userRepository.save(u);

        mockMvc.perform(post("/settings/login-id").with(user("lidusednew")).with(csrf())
                        .param("newLoginId", "lidusedthird")
                        .param("confirmOnce", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("평생 1번")));

        assertThat(userRepository.findById(u.getId()).orElseThrow().getLoginId()).isEqualTo("lidusednew");
    }

    @Test
    @DisplayName("POST /settings/login-id: 남이 버린 옛 아이디도 '이미 사용 중'으로 거부한다(어느 컬럼인지는 알리지 않는다)")
    void changeLoginId_takenPreviousHandle_flashError() throws Exception {
        User other = registerWithHandle("lid-other@booktimer.com", "lidabandoned");
        other.changeLoginId("lidothernew");
        userRepository.save(other);
        User me = registerWithHandle("lid-taker@booktimer.com", "lidtaker");

        mockMvc.perform(post("/settings/login-id").with(user("lidtaker")).with(csrf())
                        .param("newLoginId", "lidabandoned")
                        .param("confirmOnce", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("이미 사용 중")));

        assertThat(userRepository.findById(me.getId()).orElseThrow().getLoginId()).isEqualTo("lidtaker");
        assertThat(userRepository.findById(me.getId()).orElseThrow().getPreviousLoginId()).isNull();
    }

    @Test
    @DisplayName("POST /settings/login-id: 형식 위반은 규칙 안내로 거부하고 변경권도 소진하지 않는다")
    void changeLoginId_invalidFormat_flashError() throws Exception {
        User u = registerWithHandle("lid-bad@booktimer.com", "lidbadinput");

        mockMvc.perform(post("/settings/login-id").with(user("lidbadinput")).with(csrf())
                        .param("newLoginId", "bad-handle!")
                        .param("confirmOnce", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("사용할 수 없는")));

        User reloaded = userRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("lidbadinput");
        assertThat(reloaded.getPreviousLoginId()).isNull();
    }

    @Test
    @DisplayName("GET /settings: 온보딩 전(아이디 미설정) 계정엔 아이디 변경 카드를 아예 그리지 않는다")
    void getSettings_noLoginId_hidesChangeCard() throws Exception {
        // 아이디가 없으면 안내가 '@null'로 렌더되고, 제출하면 도메인 ISE가 '이미 사용했어요'라는
        // 거짓 안내로 흡수된다. 아예 안 그려서 그 경로 자체를 없앤다.
        registerSocial("lid-none@booktimer.com");

        mockMvc.perform(get("/settings").with(user("lid-none@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/settings/login-id"))))
                .andExpect(content().string(not(containsString("아이디 변경"))));
    }

    @Test
    @DisplayName("GET /settings: 아직 안 바꾼 계정은 아이디 변경 폼을 보여준다")
    void getSettings_notChangedYet_showsChangeForm() throws Exception {
        registerWithHandle("lid-form@booktimer.com", "lidformuser");

        mockMvc.perform(get("/settings").with(user("lidformuser")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loginIdChangeUsed", false))
                .andExpect(content().string(containsString("/settings/login-id")));
    }
}
