package com.booktimer.security;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.OAuthUserProvisioningService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 웹 구글 로그인 어댑터(REQ-116) — {@code super.loadUser}(네트워크) 뒤의 몫을 Spring이 이미 검증한 ID 토큰으로 시험한다.
 */
@ExtendWith(MockitoExtension.class)
class BookTimerOidcUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    @Mock
    private OAuthUserProvisioningService provisioningService;

    private BookTimerOidcUserService adapter() {
        return new BookTimerOidcUserService(provisioningService);
    }

    @Test
    @DisplayName("REQ-116 · 어댑터는 ID 토큰의 sub와 이메일과 hd로 provision을 부른다")
    void passesSubEmailAndHd() {
        when(provisioningService.provision(any(), any(), any(), any(), any())).thenReturn(stored("a@corp.example", "corpuser"));

        adapter().toPrincipal(oidcUser("sub-1", "a@corp.example", "corp.example"));
        adapter().toPrincipal(oidcUser("sub-2", "b@gmail.com", null));

        verify(provisioningService).provision("sub-1", "a@corp.example", "corp.example", "구글 이름", true);
        verify(provisioningService).provision("sub-2", "b@gmail.com", null, "구글 이름", true);
    }

    @Test
    @DisplayName("REQ-116 · 온보딩 전 principal은 구글 이메일이 아니라 저장된 이메일이다")
    void principalIsLoginIdOrStoredEmail() {
        // sub로 찾은 계정은 구글 쪽 이메일이 바뀌었을 수 있다 — 세션 해석·축출은 저장된 이메일로 찾는다.
        when(provisioningService.provision(any(), any(), any(), any(), any()))
                .thenReturn(stored("stored@booktimer.com", null))
                .thenReturn(stored("stored@booktimer.com", "handle_1"));

        OidcUser beforeOnboarding = adapter().toPrincipal(oidcUser("sub-1", "changed@gmail.com", null));
        OidcUser onboarded = adapter().toPrincipal(oidcUser("sub-1", "changed@gmail.com", null));

        assertThat(beforeOnboarding.getName()).isEqualTo("stored@booktimer.com");
        assertThat(onboarded.getName()).isEqualTo("handle_1");
    }

    @Test
    @DisplayName("REQ-116 · 불일치는 google_account_mismatch 인증 실패로 나간다")
    void mismatchPropagatesAsAuthenticationFailure() {
        when(provisioningService.provision(any(), any(), any(), any(), any()))
                .thenThrow(new OAuth2AuthenticationException("google_account_mismatch"));

        assertThatThrownBy(() -> adapter().toPrincipal(oidcUser("sub-x", "owner@gmail.com", null)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        e -> assertThat(e.getError().getErrorCode()).isEqualTo("google_account_mismatch"));
    }

    private static OidcUser oidcUser(String sub, String email, String hd) {
        OidcIdToken.Builder token = OidcIdToken.withTokenValue("id-token")
                .subject(sub)
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(3600))
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", "구글 이름");
        if (hd != null) {
            token.claim("hd", hd);
        }
        return new DefaultOidcUser(List.of(), token.build());
    }

    private static User stored(String email, String loginId) {
        User u = User.ofOAuth(email, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        if (loginId != null) {
            u.assignLoginId(loginId);
        }
        return u;
    }
}
