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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SignupNotificationService 단위 테스트 (Mockito) — 중복 가입 시 기존 계정주에게 보내는 "이미 계정 있음" 통지.
 *
 * <p>통지는 가입을 <b>시도한</b> 사람이 아니라 그 이메일의 <b>실소유자(기존 계정주)</b>에게만 간다 — 그래서
 * 가입 응답은 동일하게 흡수해도(열거완화) 정직하게 잊고 재가입한 주인은 안내를 받는다(N-052 흡수의 정석).
 * 계정 종류에 따라 안내가 갈린다: LOCAL은 로그인/비밀번호 재설정, 소셜은 해당 provider로 로그인.
 */
@ExtendWith(MockitoExtension.class)
class SignupNotificationServiceTest {

    private static final String BASE_URL = "https://booktimer.app";

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailDispatcher emailDispatcher;

    private SignupNotificationService service() {
        return new SignupNotificationService(userRepository, emailDispatcher, BASE_URL);
    }

    @Test
    @DisplayName("notifyExistingAccount: LOCAL 계정이 있으면 로그인·비밀번호 재설정 안내 통지를 그 이메일로 보낸다")
    void notify_localAccount_sendsLoginAndResetHint() {
        User user = User.of("owner@booktimer.com", "hash", "주인", "Asia/Seoul", Role.USER);
        user.assignLoginId("owner01");
        when(userRepository.findByEmail("owner@booktimer.com")).thenReturn(Optional.of(user));

        service().notifyExistingAccount("owner@booktimer.com");

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailDispatcher).dispatch(to.capture(), any(), body.capture());
        assertThat(to.getValue()).isEqualTo("owner@booktimer.com");
        assertThat(body.getValue()).contains(BASE_URL + "/login");
        assertThat(body.getValue()).contains(BASE_URL + "/password/forgot"); // 비번 재설정 경로 안내
    }

    @Test
    @DisplayName("notifyExistingAccount: 소셜 계정이 있으면 해당 provider 로그인 안내(비번 재설정 링크는 없음)")
    void notify_socialAccount_sendsProviderLoginHint() {
        User user = User.ofOAuth("social@booktimer.com", "소셜러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        user.assignLoginId("social01");
        when(userRepository.findByEmail("social@booktimer.com")).thenReturn(Optional.of(user));

        service().notifyExistingAccount("social@booktimer.com");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailDispatcher).dispatch(any(), any(), body.capture());
        assertThat(body.getValue()).contains("Google");
        assertThat(body.getValue()).doesNotContain("/password/forgot"); // 소셜은 재설정할 비번이 없음
    }

    @Test
    @DisplayName("notifyExistingAccount: 그 이메일로 계정이 없으면 아무 것도 보내지 않는다(login_id 레이스 등)")
    void notify_absentEmail_doesNotSend() {
        when(userRepository.findByEmail("nobody@booktimer.com")).thenReturn(Optional.empty());

        service().notifyExistingAccount("nobody@booktimer.com");

        verifyNoInteractions(emailDispatcher);
    }
}
