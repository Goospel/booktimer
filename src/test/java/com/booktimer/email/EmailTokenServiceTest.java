package com.booktimer.email;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmailTokenService 슬라이스 테스트 (@DataJpaTest, H2) — 발급/검증의 경계를 전수로 못 박는다(보안 로직).
 *
 * <p>토큰은 자격증명이다. 평문은 메일 링크에만 실리고 DB엔 SHA-256 해시만 저장된다(평문 미저장). 검증은
 * 만료·일회용·type 일치·존재를 모두 통과해야 성공한다. 만료 경계는 {@link Clock#fixed}로 고정해 결정적으로 확인.
 */
@DataJpaTest
@Import(JpaConfig.class)
class EmailTokenServiceTest {

    @Autowired
    private EmailTokenRepository repository;
    @Autowired
    private UserRepository userRepository;

    /** 발급 기준 시각(고정). */
    private static final Instant T0 = Instant.parse("2026-06-11T00:00:00Z");

    private EmailTokenService serviceAt(Instant now) {
        return new EmailTokenService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private User persistUser() {
        User u = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId("reader01");
        return userRepository.save(u);
    }

    @Test
    @DisplayName("issue: 평문 토큰을 돌려주되 DB엔 평문이 아니라 SHA-256 해시(64 hex)만 저장한다")
    void issue_storesHashNotPlaintext() {
        User user = persistUser();
        EmailTokenService service = serviceAt(T0);

        String raw = service.issue(user, EmailTokenType.VERIFICATION);

        assertThat(raw).isNotBlank();
        EmailToken stored = repository.findAll().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);     // 평문 미저장
        assertThat(stored.getTokenHash()).hasSize(64);           // SHA-256 hex
        assertThat(stored.getType()).isEqualTo(EmailTokenType.VERIFICATION);
        assertThat(stored.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("consume: 유효 토큰이면 user를 돌려주고 일회용 처리 — 재사용은 거부")
    void consume_validToken_thenSingleUse() {
        User user = persistUser();
        EmailTokenService service = serviceAt(T0);
        String raw = service.issue(user, EmailTokenType.VERIFICATION);

        Optional<User> first = service.consume(raw, EmailTokenType.VERIFICATION);
        assertThat(first).isPresent();
        assertThat(first.get().getId()).isEqualTo(user.getId());

        Optional<User> second = service.consume(raw, EmailTokenType.VERIFICATION);
        assertThat(second).isEmpty(); // 일회용 — 두 번째는 거부
    }

    @Test
    @DisplayName("consume: VERIFICATION 만료(24h) 직후면 거부, 직전이면 통과 (Clock 경계)")
    void consume_verificationExpiryBoundary() {
        User user = persistUser();
        String raw = serviceAt(T0).issue(user, EmailTokenType.VERIFICATION);

        // 24h + 1s 경과 → 거부
        assertThat(serviceAt(T0.plusSeconds(24 * 3600 + 1)).consume(raw, EmailTokenType.VERIFICATION)).isEmpty();
    }

    @Test
    @DisplayName("consume: VERIFICATION 만료 직전(24h-1s)이면 통과")
    void consume_verificationJustBeforeExpiry() {
        User user = persistUser();
        String raw = serviceAt(T0).issue(user, EmailTokenType.VERIFICATION);

        assertThat(serviceAt(T0.plusSeconds(24 * 3600 - 1)).consume(raw, EmailTokenType.VERIFICATION)).isPresent();
    }

    @Test
    @DisplayName("consume: PASSWORD_RESET은 짧은 만료(1h) — 1h+1s 경과면 거부")
    void consume_passwordResetShorterExpiry() {
        User user = persistUser();
        String raw = serviceAt(T0).issue(user, EmailTokenType.PASSWORD_RESET);

        assertThat(serviceAt(T0.plusSeconds(3600 + 1)).consume(raw, EmailTokenType.PASSWORD_RESET)).isEmpty();
        // 같은 raw가 1h 안이라도 type 불일치면 또 거부(아래 테스트와 별개로 만료만 검증)
    }

    @Test
    @DisplayName("consume: type 불일치 거부 — VERIFICATION 토큰으로 PASSWORD_RESET 소비 불가")
    void consume_typeMismatch_rejected() {
        User user = persistUser();
        String raw = serviceAt(T0).issue(user, EmailTokenType.VERIFICATION);

        assertThat(serviceAt(T0).consume(raw, EmailTokenType.PASSWORD_RESET)).isEmpty();
        // 올바른 type으론 여전히 통과(거부가 소비로 새지 않음)
        assertThat(serviceAt(T0).consume(raw, EmailTokenType.VERIFICATION)).isPresent();
    }

    @Test
    @DisplayName("consume: 존재하지 않는 토큰은 거부")
    void consume_unknownToken_rejected() {
        EmailTokenService service = serviceAt(T0);

        assertThat(service.consume("nonexistent-raw-token", EmailTokenType.VERIFICATION)).isEmpty();
    }

    @Test
    @DisplayName("issue: 같은 user+type의 기존 미사용 토큰은 무효화 — 직전 토큰은 더 못 쓰고 최신만 유효")
    void issue_invalidatesPreviousUnusedToken() {
        User user = persistUser();
        EmailTokenService service = serviceAt(T0);

        String first = service.issue(user, EmailTokenType.VERIFICATION);
        String second = service.issue(user, EmailTokenType.VERIFICATION);

        assertThat(service.consume(first, EmailTokenType.VERIFICATION)).isEmpty();   // 무효화됨
        assertThat(service.consume(second, EmailTokenType.VERIFICATION)).isPresent(); // 최신만 유효
    }
}
