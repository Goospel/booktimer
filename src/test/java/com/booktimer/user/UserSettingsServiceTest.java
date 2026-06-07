package com.booktimer.user;

import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
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
 * <p>설정은 두 엔티티에 걸쳐 있다 — 프로필(User: 닉네임/타임존)과 타이머 하루 목표
 * (ReadingTimer). 이 서비스가 한 트랜잭션에서 둘을 함께 갱신하는지 본다.
 * 값 검증 규칙 자체는 도메인 단위 테스트가 이미 덮으므로(N-009), 여기선 와이어링.
 * (옛 cap은 7일 윈도우 부채 모델로 설정에서 사라졌다.)
 */
@SpringBootTest
@Transactional
class UserSettingsServiceTest {

    // 통합 테스트는 운영 Clock.systemUTC()(실시간)를 쓰는데, "오늘 목표 이력" 판정이 날짜에 의존해
    // 자정 경계(예: 06-08 00:0x KST)에 플레이키하게 깨진다. 결정적 날짜로 고정한다(TimeConfig javadoc 지침).
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock fixedClock() {
            // 18:00 KST = 05:00 EDT — SEOUL·America/New_York 둘 다 같은 날짜(06-17)라 tz 변경 테스트도 결정적.
            return java.time.Clock.fixed(java.time.Instant.parse("2026-06-17T09:00:00Z"), java.time.ZoneOffset.UTC);
        }
    }

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
    private ReadingGoalChangeRepository goalChangeRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("updateSettings: 닉네임·타임존(User)과 하루 목표(Timer)를 함께 갱신한다")
    void updateSettings_changesProfileAndTimer() {
        registrationService.register("set@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        settingsService.updateSettings("set@booktimer.com", "새닉", "America/New_York", 7200L);

        User reloaded = userRepository.findByEmail("set@booktimer.com").orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("새닉");
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");

        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(7200L);

        // 하루 목표가 바뀌었으니 오늘자 변경 이력이 기록된다 (그날 목표로 과거 판정).
        ReadingGoalChange goalRow = goalChangeRepository.findByUserAndEffectiveDate(reloaded, today()).orElseThrow();
        assertThat(goalRow.getGoalSeconds()).isEqualTo(7200L);
    }

    @Test
    @DisplayName("updateSettings: 하루 목표가 그대로면 변경 이력을 남기지 않는다 (불필요한 행 방지)")
    void updateSettings_goalUnchanged_noHistoryRow() {
        registrationService.register("samegoal@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());
        User user = userRepository.findByEmail("samegoal@booktimer.com").orElseThrow();
        long currentGoal = timerRepository.findByUser(user).orElseThrow().getDailyIncrementSeconds();

        // 닉네임만 바꾸고 목표는 현재값 그대로
        settingsService.updateSettings("samegoal@booktimer.com", "닉만바꿈", SEOUL, currentGoal);

        assertThat(goalChangeRepository.findByUserAndEffectiveDate(user, today())).isEmpty();
    }

    @Test
    @DisplayName("updateSettings: 타임존이 유효하지 않으면 예외 (도메인 검증 위임)")
    void updateSettings_invalidTimezone_throws() {
        registrationService.register("badtz@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        assertThatThrownBy(() ->
                settingsService.updateSettings("badtz@booktimer.com", "닉", "Mars/Phobos", 3600L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateSettings: 남이 이미 쓰는 닉네임으로도 바꿀 수 있다 (nickname 중복 허용 — 표시 이름일 뿐)")
    void updateSettings_duplicateNickname_allowed() {
        registrationService.register("owner@booktimer.com", "rawpw1234", "이미쓰는닉", SEOUL, Role.USER, today());
        registrationService.register("changer@booktimer.com", "rawpw1234", "내닉", SEOUL, Role.USER, today());

        settingsService.updateSettings("changer@booktimer.com", "이미쓰는닉", SEOUL, 3600L);

        // 중복이어도 변경이 적용된다
        assertThat(userRepository.findByEmail("changer@booktimer.com").orElseThrow().getNickname())
                .isEqualTo("이미쓰는닉");
    }

    @Test
    @DisplayName("updateSettings: 자기 현재 닉네임은 그대로 두고 다른 설정만 바꿀 수 있다 (본인 것은 충돌 아님)")
    void updateSettings_keepingOwnNickname_allowed() {
        registrationService.register("keep@booktimer.com", "rawpw1234", "내닉", SEOUL, Role.USER, today());

        settingsService.updateSettings("keep@booktimer.com", "내닉", "America/New_York", 7200L);

        User reloaded = userRepository.findByEmail("keep@booktimer.com").orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("내닉");
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");
    }
}
