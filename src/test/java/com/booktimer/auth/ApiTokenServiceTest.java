package com.booktimer.auth;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiTokenService — 미니앱용 불투명 Bearer 토큰의 발급·검증·폐기.
 *
 * <p>평문 토큰은 발급 응답에만 실리고 DB엔 SHA-256 해시만 둔다(EmailTokenService와 같은 정신).
 * 만료 경계는 주입 {@link Clock}으로 고정해 결정적으로 검증한다(N-010).
 */
@SpringBootTest
@Transactional
class ApiTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Autowired ApiTokenRepository tokenRepository;
    @Autowired UserRepository userRepository;

    private ApiTokenService serviceAt(Instant now) {
        return new ApiTokenService(tokenRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private User user(String email) {
        return userRepository.save(User.ofOAuth(email, "토스유저", "Asia/Seoul", Role.USER,
                com.booktimer.user.AuthProvider.GOOGLE));
    }

    @Test
    @DisplayName("발급한 토큰은 검증을 통과해 소유 User를 돌려준다 (평문은 DB에 저장되지 않는다)")
    void issuedToken_authenticates() {
        User u = user("token-ok@booktimer.com");
        ApiTokenService service = serviceAt(NOW);

        String raw = service.issue(u);

        assertThat(service.authenticate(raw)).map(User::getId).contains(u.getId());
        // 평문 미저장 — 저장된 해시는 평문과 다르다.
        assertThat(tokenRepository.findAll()).allSatisfy(t -> assertThat(t.getTokenHash()).isNotEqualTo(raw));
    }

    @Test
    @DisplayName("위조 토큰은 거부한다")
    void forgedToken_rejected() {
        User u = user("token-forged@booktimer.com");
        ApiTokenService service = serviceAt(NOW);
        service.issue(u);

        assertThat(service.authenticate("완전히-지어낸-토큰")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("  ")).isEmpty();
    }

    @Test
    @DisplayName("만료(TTL 90일 경과) 토큰은 거부한다 — 경계: 만료 직전은 통과")
    void expiredToken_rejected() {
        User u = user("token-expired@booktimer.com");
        String raw = serviceAt(NOW).issue(u);

        Instant justBefore = NOW.plus(ApiTokenService.TTL).minusSeconds(1);
        Instant justAfter = NOW.plus(ApiTokenService.TTL).plusSeconds(1);

        assertThat(serviceAt(justBefore).authenticate(raw)).isPresent();
        assertThat(serviceAt(justAfter).authenticate(raw)).isEmpty();
    }

    @Test
    @DisplayName("폐기(logout)한 토큰은 이후 거부한다")
    void revokedToken_rejected() {
        User u = user("token-revoked@booktimer.com");
        ApiTokenService service = serviceAt(NOW);
        String raw = service.issue(u);
        assertThat(service.authenticate(raw)).isPresent();

        service.revoke(raw);

        assertThat(service.authenticate(raw)).isEmpty();
    }

    @Test
    @DisplayName("검증에 성공하면 last_used_at을 갱신한다 (유휴 토큰 식별 근거)")
    void authenticate_touchesLastUsedAt() {
        User u = user("token-touch@booktimer.com");
        String raw = serviceAt(NOW).issue(u);
        Instant later = NOW.plus(Duration.ofDays(3));

        Optional<User> authenticated = serviceAt(later).authenticate(raw);

        assertThat(authenticated).isPresent();
        assertThat(tokenRepository.findAll()).anySatisfy(t -> assertThat(t.getLastUsedAt()).isEqualTo(later));
    }
}
