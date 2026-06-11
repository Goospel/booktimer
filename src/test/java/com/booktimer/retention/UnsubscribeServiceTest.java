package com.booktimer.retention;

import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 메일 구독해지(원클릭) 오케스트레이션 테스트.
 *
 * <p>토큰이 신원 증명이므로 로그인 없이 해지한다 — 유효 토큰만 동의를 끄고(일회용 소비), 무효/만료/사용/위조는
 * 거부하며 상태를 바꾸지 않는다. 토큰은 발급된 본인에게만 바인딩되므로 남의 동의는 못 건드린다(IDOR 방어 — consume가
 * 바인딩된 user만 돌려줌).
 */
@ExtendWith(MockitoExtension.class)
class UnsubscribeServiceTest {

    @Mock private EmailTokenService tokenService;
    @Mock private UserRepository userRepository;

    private UnsubscribeService service() {
        return new UnsubscribeService(tokenService, userRepository);
    }

    private static User consentedUser() {
        User u = User.of("sub@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.consentToMarketing(Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        return u;
    }

    @Test
    @DisplayName("유효 토큰: 동의를 끄고(철회) 저장하며 true를 반환한다")
    void unsubscribe_validToken_withdrawsConsent() {
        User u = consentedUser();
        when(tokenService.consume("good", EmailTokenType.UNSUBSCRIBE)).thenReturn(Optional.of(u));

        boolean ok = service().unsubscribe("good");

        assertThat(ok).isTrue();
        assertThat(u.isMarketingEmailConsent()).isFalse();
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("무효/만료/사용/위조 토큰: 거부하고 동의 상태를 바꾸지 않는다")
    void unsubscribe_invalidToken_noChange() {
        when(tokenService.consume(eq("bad"), eq(EmailTokenType.UNSUBSCRIBE))).thenReturn(Optional.empty());

        boolean ok = service().unsubscribe("bad");

        assertThat(ok).isFalse();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
