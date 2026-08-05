package com.booktimer.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TossUserProvisioningService 단위 테스트 (Mockito — OAuthUserProvisioningServiceTest 패턴).
 *
 * <p>토스 신원은 이메일이 아니라 <b>userKey</b>다. 그래서 find 키가 다르고, 무엇보다
 * <b>조회(login)는 계정을 만들지 않는다</b> — 만들어 버리면 미니앱의 "새로 시작 / 기존 계정 연결"
 * 선택권이 사라진다(설계 §2.2). 이메일 자동 연결은 계정 탈취 벡터라 금지 — 충돌·null이면 합성 주소로 폴백한다.
 */
@ExtendWith(MockitoExtension.class)
class TossUserProvisioningServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5); // Asia/Seoul 기준 오늘
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRegistrationService registrationService;

    private TossUserProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new TossUserProvisioningService(userRepository, registrationService, clock);
    }

    private static User tossUser(String email) {
        return User.ofOAuth(email, "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
    }

    // ── login (조회 전용 — 생성 부작용 없음) ──────────────────────────────────

    @Test
    @DisplayName("login: userKey가 이미 있으면 그 User를 그대로 반환하고 새로 만들지 않는다")
    void login_existingUserKey_returnsSameUserNoCreate() {
        User existing = tossUser("t@booktimer.com");
        when(userRepository.findByTossUserKey("uk-1")).thenReturn(Optional.of(existing));

        Optional<User> result = service.login("uk-1");

        assertThat(result).containsSame(existing);
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("login: 미등록 userKey면 User를 만들지 않고 빈 값을 돌려준다 (registered:false의 근거)")
    void login_unknownUserKey_doesNotCreateUser() {
        when(userRepository.findByTossUserKey("uk-new")).thenReturn(Optional.empty());

        Optional<User> result = service.login("uk-new");

        assertThat(result).isEmpty();
        // 조회가 생성 부작용을 가지면 "새로 시작 / 기존 계정 연결" 선택권이 사라진다 — 이 단언이 그 결함을 잡는다.
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any(), anyBoolean());
        verify(userRepository, never()).save(any());
    }

    // ── register (신규 생성) ─────────────────────────────────────────────────

    @Test
    @DisplayName("register: 신규 userKey면 TOSS 계정을 만들고(emailVerified=false) userKey를 붙인다")
    void register_newUserKey_createsTossUserUnverified() {
        when(userRepository.findByTossUserKey("uk-new")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("me@toss.im")).thenReturn(false);
        when(registrationService.registerOAuth(eq("me@toss.im"), any(), eq("Asia/Seoul"),
                eq(AuthProvider.TOSS), eq(TODAY), eq(false)))
                .thenAnswer(inv -> tossUser(inv.getArgument(0)));

        User created = service.register("uk-new", "me@toss.im");

        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.TOSS);
        assertThat(created.isEmailVerified()).isFalse(); // 토스는 이메일 소유를 보증하지 않는다
        assertThat(created.getTossUserKey()).isEqualTo("uk-new");
        // verifyEmail을 부르지 않는 오버로드를 타야 한다(Google 전제의 registerOAuth를 그대로 쓰면 안 됨)
        verify(registrationService).registerOAuth("me@toss.im", "토스유저", "Asia/Seoul",
                AuthProvider.TOSS, TODAY, false);
    }

    @Test
    @DisplayName("register: 토스 email이 null이면 합성 주소(toss-{userKey}@noreply.booktimer.app)로 만든다")
    void register_nullEmail_usesSyntheticAddress() {
        when(userRepository.findByTossUserKey("ABcd-1234")).thenReturn(Optional.empty());
        when(registrationService.registerOAuth(any(), any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> tossUser(inv.getArgument(0)));

        User created = service.register("ABcd-1234", null);

        // 이메일 형식을 깨는 문자는 제거하고 소문자로 정규화한다(users.email은 형식 검증을 통과해야 한다).
        assertThat(created.getEmail()).isEqualTo("toss-abcd1234@noreply.booktimer.app");
        verify(userRepository, never()).existsByEmail(any()); // null이면 충돌 확인 자체가 없다
    }

    @Test
    @DisplayName("register: 토스 email이 기존 계정과 충돌하면 합성 주소로 폴백한다 (이메일 자동 연결 금지)")
    void register_conflictingEmail_fallsBackToSynthetic() {
        when(userRepository.findByTossUserKey("dupkey01")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("owner@booktimer.com")).thenReturn(true);
        when(registrationService.registerOAuth(any(), any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> tossUser(inv.getArgument(0)));

        User created = service.register("dupkey01", "owner@booktimer.com");

        // 기존 계정에 자동으로 붙지 않는다 — 붙이면 계정 탈취 벡터. 안 박으면 uk_users_email 500이 샌다.
        assertThat(created.getEmail()).isEqualTo("toss-dupkey01@noreply.booktimer.app");
        verify(registrationService).registerOAuth(eq("toss-dupkey01@noreply.booktimer.app"), any(),
                any(), eq(AuthProvider.TOSS), any(), eq(false));
    }

    @Test
    @DisplayName("register: 이미 등록된 userKey면 기존 User를 그대로 돌려준다 (중복 계정 생성 없음)")
    void register_existingUserKey_returnsExisting() {
        User existing = tossUser("t@booktimer.com");
        when(userRepository.findByTossUserKey("uk-1")).thenReturn(Optional.of(existing));

        User result = service.register("uk-1", "t@toss.im");

        assertThat(result).isSameAs(existing);
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any(), anyBoolean());
    }
}
