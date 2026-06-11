package com.booktimer.email;

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
import static org.mockito.Mockito.when;

/**
 * EmailVerificationService 단위 테스트 (Mockito) — 인증 메일 발송 구성과 토큰 검증→verifyEmail 반영.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String BASE_URL = "https://booktimer.app";

    @Mock
    private EmailTokenService tokenService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private UserRepository userRepository;

    private EmailVerificationService service() {
        return new EmailVerificationService(tokenService, emailSender, userRepository, BASE_URL);
    }

    private User user() {
        User u = User.of("reader@booktimer.com", "hash", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader01");
        return u;
    }

    @Test
    @DisplayName("sendVerification: VERIFICATION 토큰을 발급해 인증 링크가 담긴 메일을 사용자 이메일로 보낸다")
    void sendVerification_issuesTokenAndSendsLink() {
        User user = user();
        when(tokenService.issue(user, EmailTokenType.VERIFICATION)).thenReturn("RAWTOKEN123");

        service().sendVerification(user);

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(to.capture(), any(), body.capture());
        assertThat(to.getValue()).isEqualTo("reader@booktimer.com");
        assertThat(body.getValue()).contains(BASE_URL + "/verify-email?token=RAWTOKEN123");
    }

    @Test
    @DisplayName("verify: 유효 토큰이면 이메일을 검증 완료로 표시·저장하고 true")
    void verify_validToken_marksVerifiedAndSaves() {
        User user = user();
        when(tokenService.consume("good", EmailTokenType.VERIFICATION)).thenReturn(Optional.of(user));

        boolean result = service().verify("good");

        assertThat(result).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("verify: 무효/만료 토큰이면 false, 아무 것도 저장하지 않는다")
    void verify_invalidToken_returnsFalseNoSave() {
        when(tokenService.consume(eq("bad"), eq(EmailTokenType.VERIFICATION))).thenReturn(Optional.empty());

        boolean result = service().verify("bad");

        assertThat(result).isFalse();
        verify(userRepository, never()).save(any());
    }
}
