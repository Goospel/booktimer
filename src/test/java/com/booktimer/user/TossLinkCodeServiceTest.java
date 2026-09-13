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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(service.consume(code, TossLinkCode.Purpose.LINK_TOSS)).map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("이미 소비된 코드는 두 번째부터 거부한다 (일회용)")
    void consumedCode_rejectedSecondTime() {
        User u = user("link-once@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);
        String code = service.issue(u);
        assertThat(service.consume(code, TossLinkCode.Purpose.LINK_TOSS)).isPresent();

        assertThat(service.consume(code, TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
    }

    @Test
    @DisplayName("TTL(5분) 경과한 코드는 거부한다 — 경계: 만료 직전은 통과")
    void expiredCode_rejected() {
        User u = user("link-expired@booktimer.com");
        String code = serviceAt(NOW).issue(u);

        assertThat(serviceAt(NOW.plus(TossLinkCodeService.TTL).minusSeconds(1)).consume(code, TossLinkCode.Purpose.LINK_TOSS)).isPresent();

        String code2 = serviceAt(NOW).issue(u);
        assertThat(serviceAt(NOW.plus(TossLinkCodeService.TTL).plusSeconds(1)).consume(code2, TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 코드는 거부한다 (null/공백 포함)")
    void unknownCode_rejected() {
        TossLinkCodeService service = serviceAt(NOW);

        assertThat(service.consume("ZZZZZZZZ", TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
        assertThat(service.consume(null, TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
        assertThat(service.consume("  ", TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
    }

    @Test
    @DisplayName("이미 토스에 연결된 계정은 코드 발급을 거부한다 — 화면이 버튼을 숨겨도 서비스가 다시 막는다")
    void alreadyLinkedUser_issueRejected() {
        User u = user("link-already@booktimer.com");
        u.linkTossUserKey("uk-already");
        userRepository.save(u);
        TossLinkCodeService service = serviceAt(NOW);

        assertThatThrownBy(() -> service.issue(u)).isInstanceOf(TossLinkConflictException.class);
        assertThat(codeRepository.findByUserAndUsedAtIsNull(u)).isEmpty();
    }

    @Test
    @DisplayName("재발급하면 직전 코드는 무효화된다 (항상 하나만 유효 — EmailTokenService와 동일)")
    void reissue_invalidatesPrevious() {
        User u = user("link-reissue@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);
        String first = service.issue(u);

        String second = service.issue(u);

        assertThat(service.consume(first, TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
        assertThat(service.consume(second, TossLinkCode.Purpose.LINK_TOSS)).isPresent();
    }

    /** 토스에서 시작한 계정 — 웹 로그인 코드를 받을 수 있는 유일한 조건은 토스 신원이 붙어 있다는 것. */
    private User linkedUser(String email, String userKey) {
        User u = user(email);
        u.linkTossUserKey(userKey);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("웹 로그인 코드는 WEB_LOGIN 소비 지점에서만 먹는다 — LINK_TOSS로 들이밀면 거절되고, 그 거절이 코드를 소모하지도 않는다")
    void webLoginCode_consumesOnlyForWebLogin() {
        User u = linkedUser("weblogin-purpose@booktimer.com", "uk-purpose-1");
        TossLinkCodeService service = serviceAt(NOW);

        String code = service.issueWebLogin(u);

        // 다른 목적의 소비 지점에선 "없는 코드"와 동일 취급(존재를 누설하지 않는다).
        assertThat(service.consume(code, TossLinkCode.Purpose.LINK_TOSS)).isEmpty();
        // 그리고 그 거절이 일회용을 소진시키면 안 된다 — 남의 소비 지점이 내 코드를 태우는 셈이 된다.
        assertThat(service.consume(code, TossLinkCode.Purpose.WEB_LOGIN)).map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("웹→토스 연결 코드는 웹 로그인에 먹지 않는다 — 어깨너머로 본 연결 코드가 로그인 토큰으로 승격되지 않게")
    void linkCode_rejectedForWebLogin() {
        User u = user("linkcode-purpose@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);

        String code = service.issue(u);

        assertThat(service.consume(code, TossLinkCode.Purpose.WEB_LOGIN)).isEmpty();
        assertThat(service.consume(code, TossLinkCode.Purpose.LINK_TOSS)).map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("토스 신원이 없는 계정은 웹 로그인 코드를 발급받지 못한다 — issue()의 거울(상호 배타)")
    void issueWebLogin_requiresTossIdentity() {
        User u = user("weblogin-unlinked@booktimer.com");
        TossLinkCodeService service = serviceAt(NOW);

        assertThatThrownBy(() -> service.issueWebLogin(u)).isInstanceOf(TossLinkConflictException.class);
        assertThat(codeRepository.findByUserAndUsedAtIsNull(u)).isEmpty();
    }

    @Test
    @DisplayName("웹 로그인 코드도 재발급하면 직전 코드가 무효화된다 (항상 하나만 유효)")
    void issueWebLogin_invalidatesPrevious() {
        User u = linkedUser("weblogin-reissue@booktimer.com", "uk-purpose-2");
        TossLinkCodeService service = serviceAt(NOW);
        String first = service.issueWebLogin(u);

        String second = service.issueWebLogin(u);

        assertThat(service.consume(first, TossLinkCode.Purpose.WEB_LOGIN)).isEmpty();
        assertThat(service.consume(second, TossLinkCode.Purpose.WEB_LOGIN)).isPresent();
    }

    @Test
    @DisplayName("코드 안쪽 공백·소문자를 관대하게 받는다 — 미니앱이 4자씩 띄워 보여줘도 그대로 옮겨 적으면 통과")
    void consume_ignoresInnerWhitespaceAndCase() {
        User u = linkedUser("weblogin-spacing@booktimer.com", "uk-purpose-3");
        TossLinkCodeService service = serviceAt(NOW);
        String code = service.issueWebLogin(u);

        String spaced = " " + code.substring(0, 4).toLowerCase(java.util.Locale.ROOT) + " " + code.substring(4) + " ";

        assertThat(service.consume(spaced, TossLinkCode.Purpose.WEB_LOGIN)).map(User::getId).contains(u.getId());
    }
}
