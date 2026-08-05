package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TossLinkCodeService — 웹 설정에서 발급하는 일회용 계정 연결 코드(TTL 5분).
 *
 * <p>미니앱이 이 코드를 제시하면 그 코드를 발급한 기존 User에 토스 신원이 붙는다(설계 §2.2 ⓐ).
 * 코드가 짧으므로 <b>일회용·짧은 TTL</b>이 방어의 핵심이고(브루트포스 상한은 레이트리밋이 담당),
 * 평문은 화면에만 노출하고 DB엔 해시만 둔다(EmailToken과 같은 성질).
 */
@SpringBootTest
@Transactional
class TossLinkCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Autowired TossLinkCodeRepository codeRepository;
    @Autowired UserRepository userRepository;

    private TossLinkCodeService serviceAt(Instant now) {
        return new TossLinkCodeService(codeRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private User user(String email) {
        return userRepository.save(User.of(email, "hash", "책벌레", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("발급한 코드를 소비하면 발급자 User를 돌려준다 (평문은 저장되지 않는다)")
    void issuedCode_consumes() {
        User u = user("link-ok@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);

        String code = service.issue(u);

        assertThat(code).isNotBlank();
        assertThat(codeRepository.findAll()).allSatisfy(c -> assertThat(c.getCodeHash()).isNotEqualTo(code));
        assertThat(service.consume(code)).map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("이미 소비된 코드는 두 번째부터 거부한다 (일회용)")
    void consumedCode_rejectedSecondTime() {
        User u = user("link-once@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);
        String code = service.issue(u);
        assertThat(service.consume(code)).isPresent();

        assertThat(service.consume(code)).isEmpty();
    }

    @Test
    @DisplayName("TTL(5분) 경과한 코드는 거부한다 — 경계: 만료 직전은 통과")
    void expiredCode_rejected() {
        User u = user("link-expired@booktimer.com");
        String code = serviceAt(NOW).issue(u);

        assertThat(serviceAt(NOW.plus(TossLinkCodeService.TTL).minusSeconds(1)).consume(code)).isPresent();

        String code2 = serviceAt(NOW).issue(u);
        assertThat(serviceAt(NOW.plus(TossLinkCodeService.TTL).plusSeconds(1)).consume(code2)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 코드는 거부한다 (null/공백 포함)")
    void unknownCode_rejected() {
        TossLinkCodeService service = serviceAt(NOW);

        assertThat(service.consume("ZZZZZZZZ")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
        assertThat(service.consume("  ")).isEmpty();
    }

    @Test
    @DisplayName("재발급하면 직전 코드는 무효화된다 (항상 하나만 유효 — EmailTokenService와 동일)")
    void reissue_invalidatesPrevious() {
        User u = user("link-reissue@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);
        String first = service.issue(u);

        String second = service.issue(u);

        assertThat(service.consume(first)).isEmpty();
        assertThat(service.consume(second)).isPresent();
    }
}
