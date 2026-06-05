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
 * 온보딩(첫 진입 초기 설정) 오케스트레이션 테스트 (실제 빈·H2).
 *
 * <p>온보딩은 두 엔티티에 걸쳐 있다 — 타이머 초기값/증가값/상한(ReadingTimer)과 완료 플래그(User).
 * 이 서비스가 한 트랜잭션에서 둘을 함께 갱신하는지 본다. 값 검증·클램프는 도메인 단위 테스트가
 * 이미 덮으므로(N-009), 여기선 와이어링.
 */
@SpringBootTest
@Transactional
class OnboardingServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private OnboardingService onboardingService;

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
    @DisplayName("complete: 닉네임·초기값·증가값·cap을 적용하고 사용자를 온보딩 완료로 표시한다")
    void complete_appliesNicknameAndTimerSetupAndMarksOnboarded() {
        registrationService.register("ob@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        // 닉네임 변경 + 초기값 2h, 증가값 90분, cap 10h
        onboardingService.complete("ob@booktimer.com", "reader_one", "내가정한닉", 7200L, 5400L, 36000L, today());

        User reloaded = userRepository.findByEmail("ob@booktimer.com").orElseThrow();
        assertThat(reloaded.isOnboarded()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("내가정한닉");
        assertThat(reloaded.getLoginId()).isEqualTo("reader_one");

        ReadingTimer timer = timerRepository.findByUser(reloaded).orElseThrow();
        assertThat(timer.getRemainingSeconds()).isEqualTo(7200L); // 사용자가 정한 초기값
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(5400L);
        assertThat(timer.getCapSeconds()).isEqualTo(36000L);
        assertThat(timer.getLastAccrualDate()).isEqualTo(today());
    }

    @Test
    @DisplayName("complete: 초기값이 cap을 넘으면 cap으로 클램프된다 (도메인 위임)")
    void complete_clampsInitialToCap() {
        registrationService.register("obcap@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        onboardingService.complete("obcap@booktimer.com", "cap_reader", "닉캡", 36000L, 3600L, 18000L, today()); // 초기 10h > cap 5h

        ReadingTimer timer = timerRepository.findByUser(
                userRepository.findByEmail("obcap@booktimer.com").orElseThrow()).orElseThrow();
        assertThat(timer.getRemainingSeconds()).isEqualTo(18000L); // cap으로 클램프
    }

    @Test
    @DisplayName("complete: 음수 값이면 예외 (도메인 검증 위임)")
    void complete_negative_throws() {
        registrationService.register("obneg@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        assertThatThrownBy(() ->
                onboardingService.complete("obneg@booktimer.com", "neg_reader", "닉음수", -1L, 3600L, 18000L, today()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("complete: 남이 쓰는 닉네임으로도 온보딩이 완료된다 (nickname 중복 허용 — 표시 이름일 뿐)")
    void complete_duplicateNickname_allowed() {
        registrationService.register("obowner@booktimer.com", "rawpw1234", "선점닉", SEOUL, Role.USER, today());
        registrationService.register("obnew@booktimer.com", "rawpw1234", "임시닉", SEOUL, Role.USER, today());

        onboardingService.complete("obnew@booktimer.com", "new_reader", "선점닉", 3600L, 3600L, 18000L, today());

        User onboarded = userRepository.findByEmail("obnew@booktimer.com").orElseThrow();
        assertThat(onboarded.isOnboarded()).isTrue();
        assertThat(onboarded.getNickname()).isEqualTo("선점닉"); // 중복이어도 그대로 확정
    }

    @Test
    @DisplayName("complete: 입력 아이디를 정규화(소문자)해 불변 login_id로 확정한다")
    void complete_assignsNormalizedImmutableLoginId() {
        registrationService.register("oblid@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        onboardingService.complete("oblid@booktimer.com", "Reader_CAP", "닉", 3600L, 3600L, 18000L, today());

        assertThat(userRepository.findByEmail("oblid@booktimer.com").orElseThrow().getLoginId())
                .isEqualTo("reader_cap"); // 대문자 입력 → 소문자 정규화
    }

    @Test
    @DisplayName("complete: 이미 쓰이는 login_id면 거부하고 온보딩되지 않는다 (유니크 사전 확인)")
    void complete_duplicateLoginId_rejected() {
        registrationService.register("lidown@booktimer.com", "rawpw1234", "주인", SEOUL, Role.USER, today());
        registrationService.register("lidnew@booktimer.com", "rawpw1234", "신규", SEOUL, Role.USER, today());
        onboardingService.complete("lidown@booktimer.com", "taken_id", "주인닉", 3600L, 3600L, 18000L, today());

        assertThatThrownBy(() ->
                onboardingService.complete("lidnew@booktimer.com", "taken_id", "신규닉", 3600L, 3600L, 18000L, today()))
                .isInstanceOf(LoginIdAlreadyExistsException.class);

        assertThat(userRepository.findByEmail("lidnew@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("complete: 예약어 아이디(admin 등)는 거부한다 (도메인 검증)")
    void complete_reservedLoginId_rejected() {
        registrationService.register("lidres@booktimer.com", "rawpw1234", "예약", SEOUL, Role.USER, today());

        assertThatThrownBy(() ->
                onboardingService.complete("lidres@booktimer.com", "admin", "예약닉", 3600L, 3600L, 18000L, today()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
