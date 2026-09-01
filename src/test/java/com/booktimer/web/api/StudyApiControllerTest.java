package com.booktimer.web.api;

import com.booktimer.session.ReadingSessionService;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/study/start|stop 통합 테스트 (H2).
 *
 * <p>계약은 독서({@code /api/sessions/*})와 같은 모양이다 — 200 + 상태 / 409 중복·무세션 / 미인증 차단.
 * 여기서만 보는 것은 <b>격리</b>다: 공부 세션을 심어도 잔디·기록이 0으로 남는지를 회귀 테스트로 고정한다.
 * "절대 안 섞인다"는 별도 테이블이라는 <b>구조</b>가 보장하지만, 그 구조가 깨졌을 때 울릴 계측기가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudySessionRepository studyRepository;
    @Autowired ReadingSessionService readingSessionService;
    @Autowired Clock clock;

    private User register(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "닉네임_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /**
     * <b>오늘 정오(KST)</b> — 「오늘 안이지만 경계에서 가장 먼 시각」이다.
     *
     * <p>당일 누적을 재는 테스트가 {@code now - 1시간} 같은 <b>상대 좌표</b>로 세션을 심으면
     * KST 00:00~01:00에 그 시각이 어제로 넘어가 CI가 날짜에 따라 붉어진다. 정오는 어느 쪽 경계와도
     * 12시간 떨어져 있어 그 창이 사라진다(경계 자체를 재는 것은 {@code todaySeconds_excludesYesterday}의 몫).
     */
    private Instant todayNoon() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL))
                .atTime(12, 0)
                .atZone(ZoneId.of(SEOUL))
                .toInstant();
    }

    /** 완료된 공부 세션 한 건을 그 시각에 심는다(집계·격리 검증용). */
    private void completedStudy(User user, Instant startedAt, Duration length) {
        StudySession session = StudySession.start(user, startedAt);
        session.end(startedAt.plus(length));
        studyRepository.save(session);
    }

    // ── 인증 경계 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/start: 미인증 → 로그인으로 차단(인증 없이 원장에 못 쓴다)")
    void start_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/api/study/start").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── start ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/start: 200 + hasActiveSession=true")
    void start_returnsActiveState() throws Exception {
        register("study-start@a.com", "studystart");

        mockMvc.perform(post("/api/study/start").with(user("studystart")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(true))
                .andExpect(jsonPath("$.activeStartedAt").isNotEmpty())
                .andExpect(jsonPath("$.todaySeconds").value(0));
    }

    @Test
    @DisplayName("POST /api/study/start: 이미 진행 중이면 409")
    void start_duplicate_conflicts() throws Exception {
        register("study-dup@a.com", "studydup");

        mockMvc.perform(post("/api/study/start").with(user("studydup")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/start").with(user("studydup")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/study/start: 독서 측정 중이면 409 — 두 원장이 같은 시간을 이중으로 세지 않는다")
    void start_whileReading_conflicts() throws Exception {
        User u = register("study-reading@a.com", "studyreading");
        readingSessionService.start(u, clock.instant(), null);

        mockMvc.perform(post("/api/study/start").with(user("studyreading")).with(csrf()))
                .andExpect(status().isConflict());
    }

    // ── stop ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/study/stop: 진행 중 세션이 없으면 409")
    void stop_withoutSession_conflicts() throws Exception {
        register("study-nostop@a.com", "studynostop");

        mockMvc.perform(post("/api/study/stop").with(user("studynostop")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/study/stop: 종료하면 오늘 누적에 합산된다")
    void stop_accumulatesToday() throws Exception {
        User u = register("study-sum@a.com", "studysum");
        completedStudy(u, todayNoon(), Duration.ofMinutes(25));

        mockMvc.perform(post("/api/study/start").with(user("studysum")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/stop").with(user("studysum")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(false))
                .andExpect(jsonPath("$.activeStartedAt").doesNotExist())
                // 방금 끝낸 0초짜리 + 앞서 심은 25분
                .andExpect(jsonPath("$.todaySeconds").value(greaterThanOrEqualTo(1500)));
    }

    @Test
    @DisplayName("todaySeconds: 어제 시작한 세션은 빠진다(유저 타임존 하루 경계)")
    void todaySeconds_excludesYesterday() throws Exception {
        User u = register("study-tz@a.com", "studytz");
        completedStudy(u, clock.instant().minus(Duration.ofDays(2)), Duration.ofHours(1));

        mockMvc.perform(post("/api/study/start").with(user("studytz")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySeconds").value(0));
    }

    // ── 격리 (핵심) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("격리: 공부 기록은 잔디에 0건이다 — 독서 집계가 study_session을 아예 모른다")
    void studyDoesNotLeakIntoContributionGraph() throws Exception {
        User u = register("study-iso-graph@a.com", "studyisograph");
        completedStudy(u, clock.instant().minus(Duration.ofMinutes(90)), Duration.ofHours(1));

        mockMvc.perform(get("/api/dashboard").with(user("studyisograph")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.totalSeconds").value(0))
                .andExpect(jsonPath("$.graph.activeDays").value(0));
    }

    @Test
    @DisplayName("격리: 공부 기록은 /api/history 목록에도 0건이다")
    void studyDoesNotLeakIntoHistory() throws Exception {
        register("study-iso-hist@a.com", "studyisohist");
        completedStudy(userRepository.findByLoginId("studyisohist").orElseThrow(),
                clock.instant().minus(Duration.ofMinutes(90)), Duration.ofHours(1));

        mockMvc.perform(get("/api/history").with(user("studyisohist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").isEmpty());
    }

    // ── 대시보드 동봉 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard: 진행 중 공부 세션이 그대로 실린다 — 재진입해도 측정이 이어진다")
    void dashboard_carriesActiveStudySession() throws Exception {
        register("study-resume@a.com", "studyresume");

        mockMvc.perform(post("/api/study/start").with(user("studyresume")).with(csrf()))
                .andExpect(status().isOk());

        // 다시 들어온 앱이 보는 것과 같은 응답 — 여기에 진행 중 사실이 없으면 재진입 시 측정이 사라진다.
        mockMvc.perform(get("/api/dashboard").with(user("studyresume")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.hasActiveSession").value(true))
                .andExpect(jsonPath("$.study.activeStartedAt").isNotEmpty())
                // 독서는 그대로 쉬고 있다 — 두 원장이 서로의 상태를 물들이지 않는다.
                .andExpect(jsonPath("$.hasActiveSession").value(false));
    }

    @Test
    @DisplayName("GET /api/dashboard: study 블록을 동봉한다(미니앱이 초기 모드·누적을 여기서 받는다)")
    void dashboard_includesStudyBlock() throws Exception {
        User u = register("study-dash@a.com", "studydash");
        completedStudy(u, todayNoon(), Duration.ofMinutes(10));

        mockMvc.perform(get("/api/dashboard").with(user("studydash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.study.hasActiveSession").value(false))
                .andExpect(jsonPath("$.study.todaySeconds").value(600));
    }
}
