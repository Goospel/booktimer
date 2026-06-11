package com.booktimer.email;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PasswordResetService 단위 테스트 (Mockito) — 재설정 메일 발송(열거완화)과 토큰 검증→비밀번호 교체.
 *
 * <p>핵심 보안 불변식: {@code requestReset}은 계정 존재/부재/소셜 여부와 <b>무관하게 동일하게 반환</b>한다
 * (열거완화 N-052) — 차이는 내부 발송 여부뿐이다. 발송은 LOCAL(비밀번호 보유) 계정에만 일어난다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String BASE_URL = "https://booktimer.app";

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTokenService tokenService;
    @Mock
    private EmailDispatcher emailDispatcher;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private PasswordResetService service() {
        return new PasswordResetService(userRepository, tokenService, emailDispatcher, passwordEncoder, BASE_URL);
    }

    private User localUser() {
        User u = User.of("reader@booktimer.com", "oldhash", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader01");
        return u;
    }

    private User socialUser() {
        User u = User.ofOAuth("social@booktimer.com", "소셜러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        u.assignLoginId("social01");
        return u;
    }

    @Test
    @DisplayName("requestReset: LOCAL 계정이면 PASSWORD_RESET 토큰을 발급해 재설정 링크 메일을 보낸다")
    void requestReset_localAccount_issuesTokenAndSendsLink() {
        User user = localUser();
        when(userRepository.findByEmail("reader@booktimer.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user, EmailTokenType.PASSWORD_RESET)).thenReturn("RESETRAW123");

        service().requestReset("reader@booktimer.com");

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailDispatcher).dispatch(to.capture(), any(), body.capture());
        assertThat(to.getValue()).isEqualTo("reader@booktimer.com");
        assertThat(body.getValue()).contains(BASE_URL + "/password/reset?token=RESETRAW123");
    }

    @Test
    @DisplayName("requestReset: 계정이 없으면 발송하지 않는다(열거완화 — 외부 응답은 동일)")
    void requestReset_absentAccount_doesNotSend() {
        when(userRepository.findByEmail("nobody@booktimer.com")).thenReturn(Optional.empty());

        service().requestReset("nobody@booktimer.com");

        verifyNoInteractions(tokenService);
        verifyNoInteractions(emailDispatcher);
    }

    @Test
    @DisplayName("requestReset: 소셜(비밀번호 없는) 계정이면 발송하지 않는다 — 재설정할 비번이 없음")
    void requestReset_socialAccount_doesNotSend() {
        User user = socialUser();
        when(userRepository.findByEmail("social@booktimer.com")).thenReturn(Optional.of(user));

        service().requestReset("social@booktimer.com");

        verifyNoInteractions(tokenService);
        verifyNoInteractions(emailDispatcher);
    }

    @Test
    @DisplayName("reset: 유효 토큰이면 비밀번호를 새 해시로 교체·저장하고 true")
    void reset_validToken_changesPasswordAndSaves() {
        User user = localUser();
        when(tokenService.consume("good", EmailTokenType.PASSWORD_RESET)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newSecret123")).thenReturn("NEWHASH");

        boolean result = service().reset("good", "newSecret123");

        assertThat(result).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("NEWHASH");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("reset: 무효/만료/이미 사용 토큰이면 false, 아무 것도 저장하지 않는다")
    void reset_invalidToken_returnsFalseNoSave() {
        when(tokenService.consume(eq("bad"), eq(EmailTokenType.PASSWORD_RESET))).thenReturn(Optional.empty());

        boolean result = service().reset("bad", "newSecret123");

        assertThat(result).isFalse();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
