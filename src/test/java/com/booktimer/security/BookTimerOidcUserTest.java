package com.booktimer.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BookTimerOidcUser 단위 테스트 — principal name이 claim이 아니라 명시한 값(login_id)을 돌려주는지.
 *
 * <p>login_id는 구글 id_token claim이 아니므로 {@code getName()}을 명시 값으로 override한다(PR-4 인증 컷오버).
 */
class BookTimerOidcUserTest {

    private OidcIdToken idToken() {
        return new OidcIdToken("token-value", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
                Map.of("sub", "google-123", "email", "g@booktimer.com"));
    }

    @Test
    @DisplayName("getName()은 claim(email)이 아니라 명시한 name(login_id)을 돌려준다")
    void getName_returnsExplicitLoginId() {
        BookTimerOidcUser user = new BookTimerOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken(), null, "email", "goospel");

        assertThat(user.getName()).isEqualTo("goospel");          // login_id (principal)
        assertThat(user.getEmail()).isEqualTo("g@booktimer.com"); // claim은 그대로 위임
    }

    @Test
    @DisplayName("온보딩 전 첫 세션: login_id가 없으면 email을 name으로 받을 수 있다(브리지)")
    void getName_allowsEmailBridge() {
        BookTimerOidcUser user = new BookTimerOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken(), null, "email", "g@booktimer.com");

        assertThat(user.getName()).isEqualTo("g@booktimer.com");
    }
}
