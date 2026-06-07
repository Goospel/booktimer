package com.booktimer.web;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AuthProvider;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
}
