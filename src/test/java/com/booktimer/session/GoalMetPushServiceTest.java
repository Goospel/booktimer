package com.booktimer.session;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "오늘 목표 달성" 푸시 감지 — <b>읽는 도중</b> 누적이 오늘 목표를 넘는 순간 한 통만 나가는지(실제 빈 + H2).
 *
 * <p>이 테스트가 단독으로 잡는 실패:
 * <ul>
 *   <li>날짜 귀속을 깨뜨려 <b>어제 시작해 아직 도는 세션</b>의 경과가 오늘 누적으로 새는 것
 *       (그러면 자정 직후 모두에게 오발송된다 — {@code ReadingHistoryService}의 startedAt 귀속 규칙과 어긋남),</li>
 *   <li>하루 1회 멱등이 깨져 분마다(스케줄 주기) 같은 사람에게 계속 발송되는 것,</li>
 *   <li>목표 미설정(0)·토스 미연동에도 발송해 스팸이 되는 것,</li>
 *   <li>발송 실패인데 마킹해 그날 재시도가 영영 막히는 것,</li>
 *   <li>한 명의 실패가 배치를 멈춰 나머지가 못 받는 것.</li>
 * </ul>
 *
 * <p>시계는 <b>전진 가능한</b> 것을 주입한다 — 자정을 넘겨 "다음 날 다시 발송"을 보려면 {@code Clock.fixed}로는 안 된다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "booktimer.toss.messenger.goal-met-template-code=DAILY_GOAL_MET")
class GoalMetPushServiceTest {

    private static final String SEOUL = "Asia/Seoul";
    /** 2026-06-17 18:00 KST — 자정에서 멀어 하루 안에서 앞뒤로 움직여도 날짜가 안 바뀐다. */
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");
    private static final long GOAL = 3600L;

    /** 전진 가능한 시계 — 다음 날 재발송 검증에 필요(Clock.fixed는 못 움직인다). */
    static class MutableClock extends Clock {
        private Instant now = NOW;

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired GoalMetPushService pushService;
    @Autowired UserRepository userRepository;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingTimerRepository timerRepository;
    @Autowired com.booktimer.config.TossProperties tossProperties;
    @Autowired Clock clock;

    @MockitoBean TossMessengerClient messengerClient;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    /**
     * 시계 빈은 컨텍스트에 공유돼 다음 날 테스트가 전진시킨 값이 <b>다른 테스트로 샌다</b>(실행 순서 의존 =
     * 플레이키). 매 테스트를 같은 "지금"에서 시작시킨다.
     */
    @BeforeEach
    void resetClock() {
        clock().set(NOW);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User user(String email, long goalSeconds, boolean linkToss) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", SEOUL, Role.USER);
        if (linkToss) {
            u.linkTossUserKey("uk-" + email);
        }
        User saved = userRepository.save(u);
        timerRepository.save(ReadingTimer.startFor(saved, goalSeconds));
        return saved;
    }

    private User tossUser(String email) {
        return user(email, GOAL, true);
    }

    /** {@code startedAt}에 시작해 {@code seconds}만큼 읽고 끝난 세션. */
    private void completed(User u, Instant startedAt, long seconds) {
        ReadingSession s = ReadingSession.start(u, startedAt);
        s.end(startedAt.plusSeconds(seconds));
        sessionRepository.save(s);
    }

    /** 아직 도는 세션(타이머 켜 둔 상태). */
    private ReadingSession active(User u, Instant startedAt) {
        return sessionRepository.save(ReadingSession.start(u, startedAt));
    }

    private LocalDate pushedOn(User u) {
        return userRepository.findById(u.getId()).orElseThrow().getGoalMetPushedOn();
    }

    @Test
    @DisplayName("읽는 도중 누적이 목표를 넘으면 푸시 1회 + 오늘로 마킹")
    void goalReachedWhileReading_pushesOnce() {
        User u = tossUser("g1@booktimer.com");
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        completed(u, NOW.minusSeconds(7200), 3000); // 오늘 이미 읽은 50분
        active(u, NOW.minusSeconds(700));           // 지금 11분 40초째 — 합 3700 >= 3600

        pushService.detectAndPush();

        verify(messengerClient, times(1))
                .sendMessage(eq("uk-g1@booktimer.com"), eq("DAILY_GOAL_MET"), any());
        assertThat(pushedOn(u)).isEqualTo(today());
    }

    @Test
    @DisplayName("아직 목표에 못 미치면 발송하지 않는다")
    void belowGoal_doesNotPush() {
        User u = tossUser("g2@booktimer.com");
        active(u, NOW.minusSeconds(600)); // 10분째

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
        assertThat(pushedOn(u)).isNull();
    }

    @Test
    @DisplayName("같은 날 두 번 돌아도 한 통 — 분당 폴링이 스팸이 되지 않는다")
    void alreadyPushedToday_doesNotPushAgain() {
        User u = tossUser("g3@booktimer.com");
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        active(u, NOW.minusSeconds(GOAL + 60));

        pushService.detectAndPush();
        pushService.detectAndPush();

        verify(messengerClient, times(1)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("다음 날이 되면 다시 발송 가능 — 마킹은 '그날'에만 유효하다")
    void nextDay_pushesAgain() {
        User u = tossUser("g4@booktimer.com");
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        ReadingSession yesterdaySession = active(u, NOW.minusSeconds(GOAL + 60));
        pushService.detectAndPush();

        clock().set(NOW.plusSeconds(86400)); // 다음 날 같은 시각(KST 18:00)
        yesterdaySession.end(clock.instant().minusSeconds(80000)); // 어제 세션은 끝났다
        completed(u, clock.instant().minusSeconds(7200), 3000);
        active(u, clock.instant().minusSeconds(700));

        pushService.detectAndPush();

        verify(messengerClient, times(2)).sendMessage(anyString(), anyString(), any());
        assertThat(pushedOn(u)).isEqualTo(today());
    }

    @Test
    @DisplayName("어제 시작해 아직 도는 세션은 오늘 누적에 안 들어간다 — 세션은 startedAt의 날짜에 귀속")
    void activeSessionStartedYesterday_notCountedToday() {
        User u = tossUser("g5@booktimer.com");
        active(u, NOW.minusSeconds(22 * 3600)); // 어제 20:00 KST 시작, 22시간째 도는 중

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
        assertThat(pushedOn(u)).isNull();
    }

    @Test
    @DisplayName("토스 미연동(userKey null) 사용자에겐 보내지 않는다")
    void noTossUserKey_doesNotPush() {
        User u = user("g6@booktimer.com", GOAL, false);
        active(u, NOW.minusSeconds(GOAL + 60));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("목표가 0이면 보내지 않는다 — 목표 미설정자에게 '달성' 알림은 스팸")
    void zeroGoal_doesNotPush() {
        User u = user("g7@booktimer.com", 0L, true);
        active(u, NOW.minusSeconds(600));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("발송 실패(false)면 마킹하지 않는다 — 다음 틱에 재시도된다")
    void sendFailure_doesNotMark() {
        User u = tossUser("g8@booktimer.com");
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(false);
        active(u, NOW.minusSeconds(GOAL + 60));

        pushService.detectAndPush();
        assertThat(pushedOn(u)).isNull();

        pushService.detectAndPush();
        verify(messengerClient, times(2)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("한 명이 터져도 나머지는 계속 처리된다 (per-user 격리)")
    void oneUserFailing_doesNotStopBatch() {
        User boom = tossUser("g9-boom@booktimer.com");
        User ok = tossUser("g9-ok@booktimer.com");
        when(messengerClient.sendMessage(eq("uk-g9-boom@booktimer.com"), anyString(), any()))
                .thenThrow(new RuntimeException("toss down"));
        when(messengerClient.sendMessage(eq("uk-g9-ok@booktimer.com"), anyString(), any()))
                .thenReturn(true);
        active(boom, NOW.minusSeconds(GOAL + 60));
        active(ok, NOW.minusSeconds(GOAL + 60));

        pushService.detectAndPush();

        assertThat(pushedOn(ok)).isEqualTo(today());
        assertThat(pushedOn(boom)).isNull();
    }

    @Test
    @DisplayName("템플릿 코드가 비면 발송하지 않는다 — 검수 전 다크런치에서 조용히 마킹만 되는 것 방지")
    void blankTemplateCode_doesNotPushOrMark() {
        User u = tossUser("g10@booktimer.com");
        active(u, NOW.minusSeconds(GOAL + 60));

        String saved = tossProperties.getMessenger().getGoalMetTemplateCode();
        tossProperties.getMessenger().setGoalMetTemplateCode("");
        try {
            pushService.detectAndPush();
        } finally {
            tossProperties.getMessenger().setGoalMetTemplateCode(saved);
        }

        verify(messengerClient, never()).sendMessage(anyString(), anyString(), any());
        assertThat(pushedOn(u)).isNull();
    }
}
