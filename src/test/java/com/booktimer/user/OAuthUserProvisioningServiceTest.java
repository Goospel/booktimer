package com.booktimer.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private OAuthUserProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new OAuthUserProvisioningService(userRepository, registrationService, clock);
    }

    @Test
    @DisplayName("provision: 이미 있는 이메일이면 기존 사용자를 그대로 반환, 새로 만들지 않는다")
    void provision_existingEmail_returnsExistingNoCreate() {
        User existing = User.ofOAuth("g@booktimer.com", "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        when(userRepository.findByEmail("g@booktimer.com")).thenReturn(Optional.of(existing));

        User result = service.provision("g@booktimer.com", "구글러", true);

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

        User result = service.provision("new@booktimer.com", "새사람", true);

        assertThat(result).isSameAs(created);
        verify(registrationService).registerOAuth("new@booktimer.com", "새사람",
                "Asia/Seoul", AuthProvider.GOOGLE, LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("provision: 표시 이름이 비어있으면 이메일 local part를 닉네임으로 쓴다")
    void provision_blankName_usesEmailLocalPart() {
        when(userRepository.findByEmail("noname@booktimer.com")).thenReturn(Optional.empty());
        when(registrationService.registerOAuth(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> User.ofOAuth(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), Role.USER, inv.getArgument(3)));

        User result = service.provision("noname@booktimer.com", "  ", true);

        assertThat(result.getNickname()).isEqualTo("noname");
        verify(registrationService).registerOAuth(eq("noname@booktimer.com"), eq("noname"),
                eq("Asia/Seoul"), eq(AuthProvider.GOOGLE), any());
    }

    @Test
    @DisplayName("provision: 이메일 미검증(email_verified=false)이면 거부하고 아무 사용자도 만들지/조회하지 않는다")
    void provision_unverifiedEmail_rejected() {
        assertThatThrownBy(() -> service.provision("attacker@booktimer.com", "공격자", false))
                .isInstanceOf(OAuth2AuthenticationException.class);

        // 자동 계정 연결(find-or-create) 자체에 도달하지 않아야 한다 — 조회/생성 모두 없음.
        verify(userRepository, never()).findByEmail(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("provision: email_verified 클레임이 없으면(null) 검증 안 된 것으로 보고 거부한다")
    void provision_nullVerified_rejected() {
        assertThatThrownBy(() -> service.provision("attacker@booktimer.com", "공격자", null))
                .isInstanceOf(OAuth2AuthenticationException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(registrationService, never()).registerOAuth(any(), any(), any(), any(), any());
    }
}
