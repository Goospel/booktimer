package com.booktimer;

import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가입 → 세션 start → stop → 차감 happy path 통합 테스트.
 *
 * <p>실제 빈·H2·트랜잭션으로 서비스들이 맞물리는지 실증한다(테스트 피라미드 꼭대기).
 * 단위/슬라이스가 각 조각을 이미 검증했고, 여기선 <b>와이어링</b>을 확인한다.
 * 시간 의존을 피하려고 누적 사전조건은 도메인 메서드에 명시적 날짜를 줘서 만든다.
 */
@SpringBootTest
@Transactional
class ReadingFlowIntegrationTest {

    private static final long HOUR = 3600L;
    private static final LocalDate DAY0 = LocalDate.of(2026, 6, 1);
    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private ReadingSessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingTimerRepository timerRepository;

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Test
    @DisplayName("가입하면 User와 ReadingTimer가 함께 영속화된다 (기본 설정)")
    void register_persistsUserAndTimer() {
        User user = registrationService.register(
                "newbie@booktimer.com", "rawpw1234", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        assertThat(userRepository.findByEmail("newbie@booktimer.com")).isPresent();
        // 평문은 저장되지 않고 BCrypt 해시로 보관된다
        assertThat(user.getPasswordHash()).isNotEqualTo("rawpw1234").startsWith("$2");
        Optional<ReadingTimer> timer = timerRepository.findByUser(user);
        assertThat(timer).isPresent();
        assertThat(timer.get().getRemainingSeconds()).isZero();
        assertThat(timer.get().getDailyIncrementSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);
        assertThat(timer.get().getCapSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_CAP_SECONDS);
    }

    @Test
    @DisplayName("세션 stop 시 측정량이 영속화된 타이머 잔여에서 차감된다")
    void startThenStop_deductsFromPersistedTimer() {
        User user = registrationService.register(
                "reader@booktimer.com", "rawpw1234", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        // 사전조건: 누적 부채 2시간 만들기 (도메인 메서드에 명시적 날짜 — 시간 의존 없음)
        ReadingTimer timer = timerRepository.findByUser(user).orElseThrow();
        timer.accrueUntil(DAY0.plusDays(2)); // +2h (increment 1h), cap 5h 이하
        timerRepository.save(timer);
        assertThat(timer.getRemainingSeconds()).isEqualTo(2 * HOUR);

        // start → stop (30분 측정)
        sessionService.start(user, T0);
        ReadingSession stopped = sessionService.stop(user, T0.plusSeconds(1800));

        // 세션이 종료·영속화되고
        assertThat(stopped.isActive()).isFalse();
        assertThat(stopped.getDurationSeconds()).isEqualTo(1800L);
        assertThat(sessionRepository.findByUserAndEndedAtIsNull(user)).isEmpty();
        // 타이머 잔여가 측정량만큼 줄었다 (2h - 30m = 1h30m)
        assertThat(timerRepository.findByUser(user).orElseThrow().getRemainingSeconds())
                .isEqualTo(2 * HOUR - 1800L);
    }

    @Test
    @DisplayName("이미 진행 중 세션이 있으면 start는 거부된다 (영속 상태 기반)")
    void start_whenActiveExists_rejected() {
        User user = registrationService.register(
                "busy@booktimer.com", "rawpw1234", "책벌레", "Asia/Seoul", Role.USER, DAY0);
        sessionService.start(user, T0);

        assertThatThrownBy(() -> sessionService.start(user, T0.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
