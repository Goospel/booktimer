package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미니앱 경량 온보딩 API — {@code POST /api/miniapp/goal} (설계 §2.4).
 *
 * <p>미니앱 온보딩은 <b>하루 목표만</b> 정한다. 핵심 단언은 "{@code onboarded}·{@code login_id}가 그대로다" —
 * 여기서 {@code completeOnboarding()}을 부르면 핸들이 없는 미니앱 계정이 DB CHECK
 * ({@code onboarded ⟹ login_id IS NOT NULL})를 위반한다. 그 회귀를 단독으로 잡는 계측기가 이 클래스다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappGoalApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ReadingTimerRepository timerRepository;
    @Autowired ReadingGoalChangeRepository goalRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    /** 미니앱에서 시작한 신규 계정 — login_id 없이·onboarded=false로 산다(§2.4). */
    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)), false);
    }

    private org.springframework.test.web.servlet.ResultActions setGoal(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/miniapp/goal")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("하루 목표를 바꾸면 타이머 값과 오늘자 목표 이력이 함께 기록된다")
    void setGoal_updatesTimerAndRecordsHistory() throws Exception {
        User u = tossUser("miniapp-goal@noreply.booktimer.app");
        String token = apiTokenService.issue(u);

        setGoal(token, "{\"dailyIncrementSeconds\":1800}").andExpect(status().isOk());

        assertThat(timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds()).isEqualTo(1800);
        assertThat(goalRepository.findByUserOrderByEffectiveDateAsc(u))
                .singleElement()
                .satisfies(change -> assertThat(change.getGoalSeconds()).isEqualTo(1800));
    }

    @Test
    @DisplayName("목표 설정은 onboarded·login_id를 건드리지 않는다 — CHECK 불변식(onboarded ⟹ login_id) 회귀 가드")
    void setGoal_leavesOnboardingUntouched() throws Exception {
        User u = tossUser("miniapp-invariant@noreply.booktimer.app");
        String token = apiTokenService.issue(u);
        assertThat(u.isOnboarded()).isFalse();
        assertThat(u.getLoginId()).isNull();

        setGoal(token, "{\"dailyIncrementSeconds\":2400}").andExpect(status().isOk());

        User reloaded = userRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.isOnboarded()).isFalse();
        assertThat(reloaded.getLoginId()).isNull();
    }

    @Test
    @DisplayName("음수 목표는 400 — 도메인 검증(updateSettings)이 그대로 걸리고 값은 안 바뀐다")
    void setGoal_negative_rejected() throws Exception {
        User u = tossUser("miniapp-negative@noreply.booktimer.app");
        long before = timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds();
        String token = apiTokenService.issue(u);

        setGoal(token, "{\"dailyIncrementSeconds\":-1}").andExpect(status().isBadRequest());

        assertThat(timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds()).isEqualTo(before);
    }

    @Test
    @DisplayName("0(목표 없음)은 도메인이 허용하는 값이라 그대로 저장된다 — 경계")
    void setGoal_zero_allowed() throws Exception {
        User u = tossUser("miniapp-zero@noreply.booktimer.app");
        String token = apiTokenService.issue(u);

        setGoal(token, "{\"dailyIncrementSeconds\":0}").andExpect(status().isOk());

        assertThat(timerRepository.findByUser(u).orElseThrow().getDailyIncrementSeconds()).isZero();
    }

    @Test
    @DisplayName("Bearer 토큰 없이는 401 — 미니앱 체인의 인증이 이 경로에도 걸린다")
    void setGoal_withoutToken_401() throws Exception {
        mockMvc.perform(post("/api/miniapp/goal")
                        .header("Authorization", "Bearer 지어낸토큰")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyIncrementSeconds\":600}"))
                .andExpect(status().isUnauthorized());
    }
}
