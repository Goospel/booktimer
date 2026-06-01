package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 설정 변경 오케스트레이션 테스트 (실제 빈·H2).
 *
 * <p>설정은 두 엔티티에 걸쳐 있다 — 프로필(User: 닉네임/타임존)과 타이머 설정
 * (ReadingTimer: 증가값/cap). 이 서비스가 한 트랜잭션에서 둘을 함께 갱신하는지 본다.
 * 값 검증·클램프 규칙 자체는 도메인 단위 테스트가 이미 덮으므로(N-009), 여기선 와이어링.
 */
@SpringBootTest
@Transactional
class UserSettingsServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserSettingsService settingsService;

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

    @Test
    @DisplayName("updateSettings: 닉네임·타임존(User)과 증가값·cap(Timer)을 함께 갱신한다")
    void updateSettings_changesProfileAndTimer() {
        registrationService.register("set@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        settingsService.updateSettings("set@booktimer.com", "새닉", "America/New_York", 7200L, 36000L);

        User reloaded = userRepository.findByEmail("set@booktimer.com").orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("새닉");
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");

        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(7200L);
        assertThat(timer.getCapSeconds()).isEqualTo(36000L);
    }

    @Test
    @DisplayName("updateSettings: 타임존이 유효하지 않으면 예외 (도메인 검증 위임)")
    void updateSettings_invalidTimezone_throws() {
        registrationService.register("badtz@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        assertThatThrownBy(() ->
                settingsService.updateSettings("badtz@booktimer.com", "닉", "Mars/Phobos", 3600L, 18000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
