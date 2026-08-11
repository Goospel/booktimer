package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.session.ReadingGoalWaiver;
import com.booktimer.session.ReadingGoalWaiverRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.WeeklyDebtCalculator;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미니앱 리워드 광고 보상 API — {@code POST /api/miniapp/debt-waiver} + 대시보드 {@code debtWaiverAvailable}.
 *
 * <p>요청 body가 없다는 것이 계약의 핵심이다 — 대상 날짜를 서버가 고르므로 클라이언트에 조작 표면이 없다.
 * 응답에 갱신된 {@code timer}를 실어 버튼 노출/숨김이 재조회 없이 갱신된다.
 *
 * <p>상태코드: 200 성공 / 400 지울 날 없음 / 409 오늘 이미 사용 / 401 미인증(미니앱 Bearer 체인이 처리).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DebtWaiverApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final long GOAL = 3600L;

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingGoalWaiverRepository waiverRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS, today(), false);
    }

    /** 윈도우 7일을 목표 달성으로 채우고 missedOffsets만 안 읽은 날로 남긴다. */
    private void readMetExcept(User user, int... missedOffsets) {
        for (int i = 0; i < WeeklyDebtCalculator.WINDOW_DAYS; i++) {
            final int offset = i;
            if (Arrays.stream(missedOffsets).anyMatch(o -> o == offset)) {
                continue;
            }
            Instant start = today().minusDays(i).atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
            ReadingSession session = ReadingSession.start(user, start);
            session.end(start.plusSeconds(GOAL));
            sessionRepository.save(session);
        }
    }

    private ResultActions waive(String token) throws Exception {
        return mockMvc.perform(post("/api/miniapp/debt-waiver").header("Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("성공: 200 + waivedDate·waivedSeconds + 갱신된 timer(방금 썼으므로 available=false)")
    void waive_success() throws Exception {
        User u = tossUser("waiver-ok@noreply.booktimer.app");
        readMetExcept(u, 3);
        String token = apiTokenService.issue(u);

        waive(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waivedDate").value(today().minusDays(3).toString()))
                .andExpect(jsonPath("$.waivedSeconds").value(GOAL))
                .andExpect(jsonPath("$.timer.debtWaiverAvailable").value(false))
                .andExpect(jsonPath("$.timer.carriedDebtSeconds").value(0)); // 유일한 빠뜨린 날이 지워졌다
    }

    @Test
    @DisplayName("지울 밀린 날이 없으면 400")
    void waive_noMissedDay_400() throws Exception {
        User u = tossUser("waiver-none@noreply.booktimer.app");
        readMetExcept(u);
        String token = apiTokenService.issue(u);

        waive(token).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("오늘 이미 사용했으면 409")
    void waive_alreadyUsedToday_409() throws Exception {
        User u = tossUser("waiver-dup@noreply.booktimer.app");
        readMetExcept(u, 2, 4);
        waiverRepository.saveAndFlush(ReadingGoalWaiver.create(u, today().minusDays(2), today()));
        String token = apiTokenService.issue(u);

        waive(token).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Bearer 토큰이 무효면 401 — 미니앱 체인의 인증이 이 경로에도 걸린다")
    void waive_invalidToken_401() throws Exception {
        mockMvc.perform(post("/api/miniapp/debt-waiver").header("Authorization", "Bearer 지어낸토큰"))
                .andExpect(status().isUnauthorized());
    }

    // --- /api/dashboard 의 debtWaiverAvailable 3분기 ---

    @Test
    @DisplayName("대시보드: 빠뜨린 날 있음 + 미사용 → debtWaiverAvailable=true")
    void dashboard_available_true() throws Exception {
        User u = tossUser("dash-avail@noreply.booktimer.app");
        readMetExcept(u, 2);
        String token = apiTokenService.issue(u);

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtWaiverAvailable").value(true));
    }

    @Test
    @DisplayName("대시보드: 빠뜨린 날 없으면 debtWaiverAvailable=false")
    void dashboard_noMissedDay_false() throws Exception {
        User u = tossUser("dash-nomissed@noreply.booktimer.app");
        readMetExcept(u);
        String token = apiTokenService.issue(u);

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtWaiverAvailable").value(false));
    }

    @Test
    @DisplayName("대시보드: 오늘 이미 사용했으면 debtWaiverAvailable=false")
    void dashboard_usedToday_false() throws Exception {
        User u = tossUser("dash-used@noreply.booktimer.app");
        readMetExcept(u, 2, 4);
        waiverRepository.saveAndFlush(ReadingGoalWaiver.create(u, today().minusDays(2), today()));
        String token = apiTokenService.issue(u);

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtWaiverAvailable").value(false));
    }
}
