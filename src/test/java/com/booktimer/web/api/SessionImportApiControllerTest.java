package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.session.ContributionDay;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/sessions/import} — 미니앱이 로그인 전에 잰 체험 세션을 로그인 직후 올리는 경로.
 *
 * <p>새로 생기는 <b>쓰기 엔드포인트</b>라 입력 검증 4종(시간 역전·미래·너무 오래됨·파싱 실패)이 핵심 경계다.
 * 마지막 통합 케이스는 「저장된 행을 부채·잔디 유도가 실제로 읽는가」의 양성 대조군 —
 * 204만 보고 끝내면 아무 데도 안 닿는 행을 저장해도 초록이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionImportApiControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired ApiTokenService apiTokenService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingContributionService contributionService;
    @Autowired Clock clock;

    /** 미니앱에서 시작한 계정 — login_id 없이·onboarded=false로 산다. */
    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "체험유저", SEOUL.getId(), AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), SEOUL), false);
    }

    private ResultActions importSession(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/sessions/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(Instant startedAt, Instant endedAt) {
        return "{\"startedAt\":\"" + startedAt + "\",\"endedAt\":\"" + endedAt + "\"}";
    }

    /**
     * 유저 TZ <b>오늘</b> 안에 통째로 들어가는 {@code seconds}초 구간의 시작 시각.
     *
     * <p>그냥 {@code now-seconds}로 잡으면 자정 직후에 돌린 실행에서 구간이 어제로 걸쳐
     * 자정 분할이 일어나 행이 2건이 된다 — 실제로 전체 스위트가 KST 자정을 넘겨 돌다 깨졌다.
     * 길이는 항상 정확히 {@code seconds}이고, 끝은 {@code now+5분} 허용폭 안이다({@code seconds ≤ 300}).
     */
    private Instant startedWithinToday(long seconds) {
        Instant now = clock.instant();
        Instant midnight = LocalDate.ofInstant(now, SEOUL).atStartOfDay(SEOUL).toInstant();
        Instant started = now.minusSeconds(seconds);
        return started.isBefore(midnight) ? midnight : started;
    }

    /**
     * ⚠️ <b>음성 판정 전용</b> — 엔드포인트가 아예 없어도 이 단언은 통과한다(체인이 먼저 401을 낸다).
     * 「엔드포인트가 산다」의 양성 대조군은 아래 204 케이스다.
     */
    @Test
    @DisplayName("Bearer 토큰이 유효하지 않으면 401 — 미니앱 체인의 인증이 이 경로에도 걸린다")
    void import_withoutValidToken_401() throws Exception {
        Instant now = clock.instant();
        importSession("지어낸토큰", body(now.minusSeconds(120), now))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 구간은 204이고 책 없는 실측 완료 행 1건이 남는다")
    void import_valid_204_andStoresRealSession() throws Exception {
        User u = tossUser("trial-ok@noreply.booktimer.app");
        Instant started = startedWithinToday(240);

        importSession(apiTokenService.issue(u), body(started, started.plusSeconds(240)))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findByUser(u)).singleElement().satisfies(s -> {
            assertThat(s.getDurationSeconds()).isEqualTo(240L);
            assertThat(s.isManualEntry()).isFalse();
            assertThat(s.getBook()).isNull();
        });
    }

    @Test
    @DisplayName("같은 startedAt으로 두 번 올려도 행이 늘지 않는다 — 업로드 재시도 멱등")
    void import_twice_isIdempotent() throws Exception {
        User u = tossUser("trial-dup@noreply.booktimer.app");
        String token = apiTokenService.issue(u);
        Instant started = startedWithinToday(240);
        String payload = body(started, started.plusSeconds(240));

        importSession(token, payload).andExpect(status().isNoContent());
        importSession(token, payload).andExpect(status().isNoContent());

        assertThat(sessionRepository.findByUser(u)).hasSize(1);
    }

    @Test
    @DisplayName("같은 startedAt의 진행 중 세션이 있어도 완료 행을 새로 만든다 — 멱등 키는 「완료된 행」만이다")
    void import_sameStartedAtAsActiveSession_stillStores() throws Exception {
        User u = tossUser("trial-active@noreply.booktimer.app");
        // 밀리초로 내려야 진짜 충돌이다 — 서비스가 멱등 키를 밀리초로 자르므로, 나노초가 남은 값으로
        // 진행 중 행을 심으면 키가 애초에 안 겹쳐 이 테스트가 아무것도 재지 않는다(실제로 그렇게 통과했다).
        Instant started = startedWithinToday(240).truncatedTo(ChronoUnit.MILLIS);
        // 로그인 직후 미니앱이 새 타이머를 켠 채로 체험 구간을 올리면, 두 startedAt이 같은 밀리초에
        // 떨어질 수 있다. 진행 중 행(endedAt=null)이 멱등 키에 걸리면 체험 기록이 조용히 사라진다.
        sessionRepository.save(ReadingSession.start(u, started));

        importSession(apiTokenService.issue(u), body(started, started.plusSeconds(240)))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findByUser(u)).hasSize(2);
        assertThat(sessionRepository.findByUser(u)).filteredOn(s -> s.getEndedAt() != null)
                .singleElement().satisfies(s -> assertThat(s.getDurationSeconds()).isEqualTo(240L));
    }

    /**
     * 멱등 키의 <b>user 술어</b>를 잠근다 — 「음성 판정 전용」이 아니라, 술어가 빠지면 뒤에 올린 유저의
     * 행이 통째로 사라져 {@code hasSize(2)}가 1로 무너진다. 양성 대조군은 위
     * {@link #import_twice_isIdempotent} — 같은 유저 두 번이면 1건이다.
     */
    @Test
    @DisplayName("서로 다른 유저가 같은 startedAt을 올리면 행은 유저마다 1건씩 남는다 — 멱등은 유저 안에서만")
    void import_sameStartedAtDifferentUsers_storesBoth() throws Exception {
        User a = tossUser("trial-cross-a@noreply.booktimer.app");
        User b = tossUser("trial-cross-b@noreply.booktimer.app");
        Instant started = startedWithinToday(240);
        String payload = body(started, started.plusSeconds(240));

        importSession(apiTokenService.issue(a), payload).andExpect(status().isNoContent());
        importSession(apiTokenService.issue(b), payload).andExpect(status().isNoContent());

        assertThat(sessionRepository.findByUser(a)).hasSize(1);
        assertThat(sessionRepository.findByUser(b)).hasSize(1);
    }

    @Test
    @DisplayName("종료가 시작보다 이르거나 같으면 400이고 아무 행도 안 남는다")
    void import_endedNotAfterStarted_400() throws Exception {
        User u = tossUser("trial-reversed@noreply.booktimer.app");
        String token = apiTokenService.issue(u);
        Instant now = clock.instant();

        importSession(token, body(now, now)).andExpect(status().isBadRequest());
        importSession(token, body(now, now.minusSeconds(60))).andExpect(status().isBadRequest());

        assertThat(sessionRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("종료가 now+5분을 넘으면 400 — 기기 시계가 미래인 값 거부")
    void import_endedTooFarInFuture_400() throws Exception {
        User u = tossUser("trial-future@noreply.booktimer.app");
        Instant now = clock.instant();

        importSession(apiTokenService.issue(u), body(now.minusSeconds(60), now.plusSeconds(600)))
                .andExpect(status().isBadRequest());

        assertThat(sessionRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("시작이 now-7일 이전이면 400 — 오래 묵은 체험 거부")
    void import_startedTooOld_400() throws Exception {
        User u = tossUser("trial-stale@noreply.booktimer.app");
        Instant started = clock.instant().minus(Duration.ofDays(8));

        importSession(apiTokenService.issue(u), body(started, started.plusSeconds(600)))
                .andExpect(status().isBadRequest());

        assertThat(sessionRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("시각 문자열이 ISO가 아니면 400 — 파싱 실패는 500이 아니다")
    void import_unparsableInstant_400() throws Exception {
        User u = tossUser("trial-garbage@noreply.booktimer.app");

        importSession(apiTokenService.issue(u), "{\"startedAt\":\"어제 저녁\",\"endedAt\":\"방금\"}")
                .andExpect(status().isBadRequest());

        assertThat(sessionRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("통합: import한 시간이 대시보드의 오늘 읽은 양과 잔디 오늘 칸에 나타난다(테두리 없음)")
    void import_showsUpInDashboardAndGraph() throws Exception {
        User u = tossUser("trial-joins@noreply.booktimer.app");
        String token = apiTokenService.issue(u);
        Instant started = startedWithinToday(120);

        importSession(token, body(started, started.plusSeconds(120))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayReadSeconds").value(120));

        LocalDate today = LocalDate.ofInstant(started, SEOUL);
        ContributionDay todayCell = contributionService.contributionGraph(u).weeks().stream()
                .flatMap(List::stream)
                .filter(d -> today.equals(d.date()))
                .findFirst().orElseThrow();
        assertThat(todayCell.totalSeconds()).isEqualTo(120L);
        assertThat(todayCell.level()).isGreaterThan(0);
        assertThat(todayCell.manual()).isFalse(); // 실측이라 잔디에 「손으로 채운 날」 테두리가 붙으면 안 된다
    }
}
