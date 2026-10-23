package com.booktimer.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuthUserProvisioningService 단위 테스트 (Mockito).
 *
 * <p>소셜 로그인 성공 시 "이 이메일의 사용자가 있으면 그대로, 없으면 새로 만든다"(find-or-create).
 * 신규 생성은 비밀번호 없는 OAuth 사용자({@link UserRegistrationService#registerOAuth})로,
 * 타임존은 기본값(Asia/Seoul, 추후 설정에서 변경)으로 만든다. "오늘"은 주입된 Clock + 기본 TZ로 계산.
 */
@ExtendWith(MockitoExtension.class)
class OAuthUserProvisioningServiceTest {

    // 2026-06-02T01:00Z == Asia/Seoul 2026-06-02 10:00 → "오늘"은 6/2
    private static final Instant NOW = Instant.parse("2026-06-02T01:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRegistrationService registrationService;
    @Mock
    private AccountService accountService;

    private OAuthUserProvisioningService service;
    private final AtomicReference<User> registered = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new OAuthUserProvisioningService(userRepository, registrationService, accountService,
                new GoogleAccountResolver(userRepository), clock);
        // 구글 신원 고정(REQ-115): OPEN · 새 계정은 조건부 UPDATE로 고정된다 — 기본은 1행(성공). 0행 · 유니크 위반은 GooglePinning.
        lenient().when(userRepository.pinGoogleSubIfEmpty(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("provision: 이미 있는 이메일이면 기존 사용자를 그대로 반환, 새로 만들지 않는다")
    void provision_existingEmail_returnsExistingNoCreate() {
        User existing = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(existing));

        reloads(existing); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "g@booktimer.com", null, "구글러", true);

        assertThat(result).isSameAs(existing);
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("provision: 없는 이메일이면 GOOGLE 사용자를 기본 타임존(Asia/Seoul)으로 새로 만든다")
    void provision_newEmail_registersGoogleUser() {
        when(userRepository.findByEmail("new@booktimer.com")).thenReturn(Optional.empty());
        User created = User.ofOAuth("new@booktimer.com", "새사람", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(registrationService.registerOAuth(eq("new@booktimer.com"), eq("새사람"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), eq(LocalDate.of(2026, 6, 2))))
                .thenReturn(created);

        reloads(created); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "new@booktimer.com", null, "새사람", true);

        assertThat(result).isSameAs(created);
        verify(registrationService).registerOAuth("new@booktimer.com", "새사람",
                "Asia/Seoul", AuthProvider.GOOGLE, LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("provision: 표시 이름이 비어있으면 이메일 local part를 닉네임으로 쓴다")
    void provision_blankName_usesEmailLocalPart() {
        when(userRepository.findByEmail("noname@booktimer.com")).thenReturn(Optional.empty());
        when(registrationService.registerOAuth(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> registered.updateAndGet(ignored -> User.ofOAuth(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), Role.USER, inv.getArgument(3))));

        when(userRepository.findById(any())).thenAnswer(inv -> Optional.of(registered.get()));
        User result = service.provision("google-sub-1", "noname@booktimer.com", null, "  ", true);

        assertThat(result.getNickname()).isEqualTo("noname");
        verify(registrationService).registerOAuth(eq("noname@booktimer.com"), eq("noname"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any());
    }

    @Test
    @DisplayName("provision: 표시 이름이 이미 쓰여도 그대로 닉네임으로 쓴다 (nickname 중복 허용 — 자동 유일화 없음)")
    void provision_duplicateDisplayName_usedAsIs() {
        when(userRepository.findByEmail("dupname@booktimer.com")).thenReturn(Optional.empty());
        when(registrationService.registerOAuth(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> registered.updateAndGet(ignored -> User.ofOAuth(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), Role.USER, inv.getArgument(3))));

        when(userRepository.findById(any())).thenAnswer(inv -> Optional.of(registered.get()));
        User result = service.provision("google-sub-1", "dupname@booktimer.com", null, "구글러", true);

        // 중복 확인(existsByNickname) 없이 provider가 준 이름을 그대로 쓴다.
        assertThat(result.getNickname()).isEqualTo("구글러");
        verify(registrationService).registerOAuth(eq("dupname@booktimer.com"), eq("구글러"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any());
    }

    @Test
    @DisplayName("pre-hijacking: 같은 이메일의 미검증 LOCAL 계정이 있으면 폐기하고 OAuth 신규로 만든다")
    void provision_unverifiedLocalAccount_isPurgedAndReprovisioned() {
        // 공격자가 피해자 이메일로 미검증 로컬 계정을 선점한 상황 — provider가 소유를 보증한 OAuth가 진짜 주인.
        User unverifiedLocal = User.of("victim@booktimer.com", "hash", "선점자", "Asia/Seoul", Role.USER);
        // emailVerified 기본 false (미검증)
        when(userRepository.findByEmail("victim@booktimer.com")).thenReturn(Optional.of(unverifiedLocal));
        User created = User.ofOAuth("victim@booktimer.com", "진짜주인", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(registrationService.registerOAuth(eq("victim@booktimer.com"), any(),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any())).thenReturn(created);

        reloads(created); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "victim@booktimer.com", null, "진짜주인", true);

        assertThat(result).isSameAs(created); // 새 OAuth 계정
        verify(accountService).purgeUnverifiedLocalAccount(unverifiedLocal); // 선점 계정 폐기
        verify(registrationService).registerOAuth(eq("victim@booktimer.com"), eq("진짜주인"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any());
    }

    @Test
    @DisplayName("토스를 연결한 미검증 LOCAL 계정은 폐기하지 않고 이메일만 재배정한다(미니앱 기록 보존)")
    void provision_unverifiedLocalAccountLinkedToToss_isReassignedNotPurged() {
        // 웹에서 LOCAL로 가입(미검증)한 뒤 미니앱을 연결한 사용자 — 폐기하면 그 사람의 미니앱 기록이 사라진다.
        // provider가 LOCAL이어도 toss_user_key가 있으면 정책 ②(재배정)로 보낸다.
        User local = User.of("victim@booktimer.com", "hash", "선점자", "Asia/Seoul", Role.USER);
        local.linkTossUserKey("UKlocal-01");
        when(userRepository.findByEmail("victim@booktimer.com")).thenReturn(Optional.of(local));
        User created = User.ofOAuth("victim@booktimer.com", "진짜주인", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(registrationService.registerOAuth(eq("victim@booktimer.com"), eq("진짜주인"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any())).thenReturn(created);

        reloads(created); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "victim@booktimer.com", null, "진짜주인", true);

        assertThat(result).isSameAs(created);
        verify(accountService).reassignUnverifiedTossEmail(local, "toss-uklocal01@noreply.booktimer.app");
        verify(accountService, never()).purgeUnverifiedLocalAccount(any()); // 기록 보존 — 폐기 아님
    }

    @Test
    @DisplayName("미검증 GOOGLE 계정에 토스가 연결돼 있어도 흡수한다 — 구글이 준 이메일은 같은 사람의 것이다")
    void provision_unverifiedGoogleAccountLinkedToToss_isAbsorbed() {
        // 현재 도달 불가(구글 가입은 emailVerified=true)지만, 미래에 미검증 구글 경로가 생기면 정당한 구글
        // 사용자를 합성 주소로 밀어내면 안 된다 — 재배정 대상은 TOSS 가입 또는 토스를 연결한 LOCAL뿐이다.
        User google = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        google.linkTossUserKey("UKgoogle-01");
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(google));

        reloads(google); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "g@booktimer.com", null, "구글러", true);

        assertThat(result).isSameAs(google);
        verify(accountService, never()).reassignUnverifiedTossEmail(any(), any());
        verify(accountService, never()).purgeUnverifiedLocalAccount(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("pre-hijacking: 검증된 LOCAL 계정은 폐기하지 않고 기존대로 연결한다(정당한 소유자)")
    void provision_verifiedLocalAccount_isLinkedNotPurged() {
        User verifiedLocal = User.of("owner@booktimer.com", "hash", "주인", "Asia/Seoul", Role.USER);
        verifiedLocal.verifyEmail(); // 검증됨 = 이메일 소유 증명
        when(userRepository.findByEmail("owner@booktimer.com")).thenReturn(Optional.of(verifiedLocal));

        User result = service.provision("google-sub-1", "owner@booktimer.com", null, "주인", true);

        assertThat(result).isSameAs(verifiedLocal); // 기존 계정 그대로
        verify(accountService, never()).purgeUnverifiedLocalAccount(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("pre-hijacking: 기존 OAuth 계정은 미검증이어도 폐기하지 않는다(LOCAL 선점 벡터만 대상)")
    void provision_existingOAuthAccount_notPurgedEvenIfUnverified() {
        User existingOAuth = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        // OAuth 계정은 emailVerified 기본 false지만 LOCAL이 아니라 폐기 대상이 아니다
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(existingOAuth));

        reloads(existingOAuth); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "g@booktimer.com", null, "구글러", true);

        assertThat(result).isSameAs(existingOAuth);
        verify(accountService, never()).purgeUnverifiedLocalAccount(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("미검증 TOSS 계정과 이메일이 겹치면 그 계정의 이메일을 합성 주소로 재배정하고 구글 계정을 새로 만든다(흡수 아님·폐기 아님)")
    void provision_existingUnverifiedTossAccount_reassignedNotAbsorbed() {
        // 결정 변경(사용자 2026-09-12): 토스는 이메일 소유를 보증하지 않으므로(TossUserProvisioningService#register)
        // 흡수는 pre-hijacking 벡터다. 그렇다고 폐기하면 그 토스 사용자의 기록이 사라지니, 이메일만 비켜 준다.
        User existingToss = User.ofOAuth("toss@booktimer.com", "토스러", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        existingToss.linkTossUserKey("UKsquat-01"); // 이론상 TOSS 계정엔 항상 있다
        when(userRepository.findByEmail("toss@booktimer.com")).thenReturn(Optional.of(existingToss));
        User created = User.ofOAuth("toss@booktimer.com", "구글이름", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(registrationService.registerOAuth(eq("toss@booktimer.com"), eq("구글이름"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any())).thenReturn(created);

        reloads(created); // 고정 뒤 다시 읽기(clear 뒤라 새 인스턴스) — 같은 행
        User result = service.provision("google-sub-1", "toss@booktimer.com", null, "구글이름", true);

        assertThat(result).isSameAs(created); // 반환은 새 구글 계정 — 기존 토스 계정이 아니다
        // 재배정 자체는 AccountService가 한다(세션 무효화 → 이메일 변경 → flush 순서가 거기 있다).
        verify(accountService).reassignUnverifiedTossEmail(existingToss, "toss-uksquat01@noreply.booktimer.app");
        assertThat(existingToss.getTossUserKey()).isEqualTo("UKsquat-01"); // userKey는 불변
        verify(accountService, never()).purgeUnverifiedLocalAccount(any()); // 폐기 아님 — 기록 보존
    }

    @Test
    @DisplayName("TOSS 계정인데 toss_user_key가 없으면 ISE — 합성 주소를 만들 키가 없는데 조용히 흡수로 빠지지 않는다")
    void provision_tossAccountWithoutUserKey_throws() {
        // 이론상 불가(TOSS 계정엔 항상 userKey가 있다). 그 전제가 깨지면 흡수 = pre-hijacking이므로 드러낸다.
        User keyless = User.ofOAuth("keyless@booktimer.com", "토스러", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        when(userRepository.findByEmail("keyless@booktimer.com")).thenReturn(Optional.of(keyless));

        assertThatThrownBy(() -> service.provision("google-sub-1", "keyless@booktimer.com", null, "구글이름", true))
                .isInstanceOf(IllegalStateException.class);

        verify(accountService, never()).reassignUnverifiedTossEmail(any(), any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TOSS 계정이라도 이메일이 검증됐으면 흡수한다(소유 증명이 있으니 같은 사람)")
    void provision_existingVerifiedTossAccount_isAbsorbed() {
        User verifiedToss = User.ofOAuth("vtoss@booktimer.com", "토스러", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        verifiedToss.linkTossUserKey("uk-verified");
        verifiedToss.verifyEmail(); // 그 토스 사용자가 웹에서 이메일 인증을 통과한 경우
        when(userRepository.findByEmail("vtoss@booktimer.com")).thenReturn(Optional.of(verifiedToss));

        User result = service.provision("google-sub-1", "vtoss@booktimer.com", null, "구글이름", true);

        assertThat(result).isSameAs(verifiedToss);
        assertThat(verifiedToss.getEmail()).isEqualTo("vtoss@booktimer.com"); // 재배정 없음
        verify(accountService, never()).reassignUnverifiedTossEmail(any(), any());
        verify(accountService, never()).purgeUnverifiedLocalAccount(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("provision: 이메일 미검증(email_verified=false)이면 거부하고 아무 사용자도 만들지/조회하지 않는다")
    void provision_unverifiedEmail_rejected() {
        assertThatThrownBy(() -> service.provision("google-sub-1", "attacker@booktimer.com", null, "공격자", false))
                .isInstanceOf(OAuth2AuthenticationException.class);

        // 자동 계정 연결(find-or-create) 자체에 도달하지 않아야 한다 — 조회/생성 모두 없음.
        verify(userRepository, never()).findByEmail(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    /** 고정한 뒤 provision은 그 행을 다시 읽는다 — 다시 읽기가 같은 계정을 돌려주게 한다. */
    private void reloads(User user) {
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class GooglePinning {

        @Test
        @DisplayName("REQ-116 · 첫 고정·불일치 로그에 sub와 이메일이 없다")
        void pinAndMismatchLogs_carryUserIdOnly(CapturedOutput output) {
            User first = withId(google("first-log@booktimer.com"), 42L);
            when(userRepository.findByEmail("first-log@booktimer.com")).thenReturn(Optional.of(first));
            when(userRepository.pinGoogleSubIfEmpty(42L, "sub-LOG-111")).thenReturn(1);
            when(userRepository.findById(42L)).thenReturn(Optional.of(first));

            service.provision("sub-LOG-111", "first-log@booktimer.com", null, "구글러", true);

            User pinnedElsewhere = withId(google("taken-log@booktimer.com"), 43L);
            ReflectionTestUtils.setField(pinnedElsewhere, "googleSub", "sub-OWNER-999");
            when(userRepository.findByEmail("taken-log@booktimer.com")).thenReturn(Optional.of(pinnedElsewhere));

            assertThatThrownBy(() -> service.provision("sub-LOG-222", "taken-log@booktimer.com", null, "침입자", true))
                    .isInstanceOf(OAuth2AuthenticationException.class);

            assertThat(output).contains("google sub pinned userId=42");
            assertThat(output).contains("google sub mismatch userId=43");
            assertThat(output).doesNotContain("sub-LOG-111", "sub-LOG-222", "sub-OWNER-999",
                    "first-log@booktimer.com", "taken-log@booktimer.com");
        }

        @Test
        @DisplayName("REQ-115 · 고정이 0행이면 아무것도 바꾸지 않고 불일치로 거부한다")
        void pinZeroRows_rejectsAsMismatch_changesNothing(CapturedOutput output) {
            // 판정은 고정 없는 GOOGLE(OPEN)이었는데, 고정 전에 다른 요청이 그 행을 고정했다.
            User raced = withId(google("raced@booktimer.com"), 44L);
            when(userRepository.findByEmail("raced@booktimer.com")).thenReturn(Optional.of(raced));
            when(userRepository.pinGoogleSubIfEmpty(44L, "sub-late")).thenReturn(0);

            assertThatThrownBy(() -> service.provision("sub-late", "raced@booktimer.com", null, "구글러", true))
                    .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                            e -> assertThat(e.getError().getErrorCode()).isEqualTo("google_account_mismatch"));

            assertThat(output).doesNotContain("google sub pinned");
            verify(accountService, never()).purgeUnverifiedLocalAccount(any());
            verify(accountService, never()).reassignUnverifiedTossEmail(any(), any());
            verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("REQ-115 · 고정의 유니크 위반은 500이 아니라 불일치다")
        void pinUniqueViolation_isMismatchAuthenticationFailure() {
            // 다른 계정이 그새 같은 sub를 가졌다 — 문 안에서 바로 나는 예외라 잡아 인증 실패로 바꾼다(웹은 /login?error).
            User open = withId(google("dup-sub@booktimer.com"), 45L);
            when(userRepository.findByEmail("dup-sub@booktimer.com")).thenReturn(Optional.of(open));
            when(userRepository.pinGoogleSubIfEmpty(45L, "sub-dup"))
                    .thenThrow(new DataIntegrityViolationException("uk_users_google_sub"));

            assertThatThrownBy(() -> service.provision("sub-dup", "dup-sub@booktimer.com", null, "구글러", true))
                    .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                            e -> assertThat(e.getError().getErrorCode()).isEqualTo("google_account_mismatch"));
        }

        private User google(String email) {
            User u = User.ofOAuth(email, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
            u.verifyEmail();
            return u;
        }

        private User withId(User user, long id) {
            ReflectionTestUtils.setField(user, "id", id);
            return user;
        }
    }

    @Test
    @DisplayName("provision: email_verified 클레임이 없으면(null) 검증 안 된 것으로 보고 거부한다")
    void provision_nullVerified_rejected() {
        assertThatThrownBy(() -> service.provision("google-sub-1", "attacker@booktimer.com", null, "공격자", null))
                .isInstanceOf(OAuth2AuthenticationException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }
}
