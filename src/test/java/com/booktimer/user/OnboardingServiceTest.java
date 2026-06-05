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
 *
 * <p><b>login_id</b>는 아직 없을 때(=소셜 로그인)만 온보딩에서 확정한다 — 여기 헬퍼는 login_id 미설정
 * 사용자({@code register} 6-arg 오버로드, login_id=null)를 만들어 그 캡처 경로를 검증한다. 로컬 가입자(login_id
 * 보유)는 온보딩이 login_id를 건드리지 않는다(불변) — 마지막 테스트가 그걸 본다.
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

    /** login_id 미설정(null) 사용자 생성 — 소셜 로그인처럼 온보딩에서 login_id를 정하는 경로. */
    private User newUserWithoutLoginId(String email, String nickname) {
        return registrationService.register(email, "rawpw1234", nickname, SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("complete: 닉네임·초기값·증가값·cap을 적용하고 사용자를 온보딩 완료로 표시한다")
    void complete_appliesNicknameAndTimerSetupAndMarksOnboarded() {
        User user = newUserWithoutLoginId("ob@booktimer.com", "독서가");

        // 닉네임 변경 + 초기값 2h, 증가값 90분, cap 10h
        onboardingService.complete(user, "reader_one", "내가정한닉", 7200L, 5400L, 36000L, today());

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
        User user = newUserWithoutLoginId("obcap@booktimer.com", "독서가");

        onboardingService.complete(user, "cap_reader", "닉캡", 36000L, 3600L, 18000L, today()); // 초기 10h > cap 5h

        ReadingTimer timer = timerRepository.findByUser(
                userRepository.findByEmail("obcap@booktimer.com").orElseThrow()).orElseThrow();
        assertThat(timer.getRemainingSeconds()).isEqualTo(18000L); // cap으로 클램프
    }

    @Test
    @DisplayName("complete: 음수 값이면 예외 (도메인 검증 위임)")
    void complete_negative_throws() {
        User user = newUserWithoutLoginId("obneg@booktimer.com", "독서가");

        assertThatThrownBy(() ->
                onboardingService.complete(user, "neg_reader", "닉음수", -1L, 3600L, 18000L, today()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("complete: 남이 쓰는 닉네임으로도 온보딩이 완료된다 (nickname 중복 허용 — 표시 이름일 뿐)")
    void complete_duplicateNickname_allowed() {
        newUserWithoutLoginId("obowner@booktimer.com", "선점닉");
        User newer = newUserWithoutLoginId("obnew@booktimer.com", "임시닉");

        onboardingService.complete(newer, "new_reader", "선점닉", 3600L, 3600L, 18000L, today());

        User onboarded = userRepository.findByEmail("obnew@booktimer.com").orElseThrow();
        assertThat(onboarded.isOnboarded()).isTrue();
        assertThat(onboarded.getNickname()).isEqualTo("선점닉"); // 중복이어도 그대로 확정
    }

    @Test
    @DisplayName("complete: login_id가 없으면 입력 아이디를 정규화(소문자)해 불변 login_id로 확정한다")
    void complete_assignsNormalizedImmutableLoginId() {
        User user = newUserWithoutLoginId("oblid@booktimer.com", "독서가");

        onboardingService.complete(user, "Reader_CAP", "닉", 3600L, 3600L, 18000L, today());

        assertThat(userRepository.findByEmail("oblid@booktimer.com").orElseThrow().getLoginId())
                .isEqualTo("reader_cap"); // 대문자 입력 → 소문자 정규화
    }

    @Test
    @DisplayName("complete: 로컬 가입자(이미 login_id 보유)는 온보딩이 login_id를 바꾸지 않는다 (불변 — 인자 무시)")
    void complete_localUserWithLoginId_keepsLoginId() {
        // 7-arg register = 로컬 가입(login_id 확정). 온보딩에서 다른 아이디를 넘겨도 무시되어야 한다.
        User local = registrationService.register(
                "local@booktimer.com", "rawpw1234", "myhandle", "로컬", SEOUL, Role.USER, today());

        onboardingService.complete(local, "tryingtochange", "로컬닉", 3600L, 3600L, 18000L, today());

        User reloaded = userRepository.findByEmail("local@booktimer.com").orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("myhandle"); // 가입 때 값 그대로 (불변)
        assertThat(reloaded.isOnboarded()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("로컬닉");
    }

    @Test
    @DisplayName("complete: 이미 쓰이는 login_id면 거부하고 온보딩되지 않는다 (유니크 사전 확인)")
    void complete_duplicateLoginId_rejected() {
        User owner = newUserWithoutLoginId("lidown@booktimer.com", "주인");
        User newer = newUserWithoutLoginId("lidnew@booktimer.com", "신규");
        onboardingService.complete(owner, "taken_id", "주인닉", 3600L, 3600L, 18000L, today());

        assertThatThrownBy(() ->
                onboardingService.complete(newer, "taken_id", "신규닉", 3600L, 3600L, 18000L, today()))
                .isInstanceOf(LoginIdAlreadyExistsException.class);

        assertThat(userRepository.findByEmail("lidnew@booktimer.com").orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("complete: 예약어 아이디(admin 등)는 거부한다 (도메인 검증)")
    void complete_reservedLoginId_rejected() {
        User user = newUserWithoutLoginId("lidres@booktimer.com", "예약");

        assertThatThrownBy(() ->
                onboardingService.complete(user, "admin", "예약닉", 3600L, 3600L, 18000L, today()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
