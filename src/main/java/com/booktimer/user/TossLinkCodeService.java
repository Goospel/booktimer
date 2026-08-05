package com.booktimer.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 계정 연결 코드의 발급·소비 (설계 §2.2 ⓐ).
 *
 * <p>웹 설정에서 로그인 상태로 발급한 코드를 미니앱에 입력하면 그 계정에 토스 신원이 붙는다. 발급 화면·
 * 컨트롤러는 PR-2지만, PR-1의 {@code POST /api/toss/link}가 소비에 의존하므로 서비스는 여기서 만든다.
 *
 * <p>코드는 <b>사람이 눈으로 옮겨 적는</b> 값이라 혼동 문자(0/O, 1/I/L)를 뺀 알파벳으로 8자를 만든다.
 * 짧은 만큼 엔트로피가 낮으므로(≈32^8) 방어는 <b>TTL 5분 + 일회용 + 레이트리밋</b> 세 겹이다 —
 * 어느 하나도 단독으로는 충분하지 않다.
 */
@Service
@Transactional
public class TossLinkCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 혼동 문자를 뺀 코드 알파벳(0·O·1·I·L 제외) — 전화로 불러주거나 손으로 옮겨 적어도 틀리지 않게. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;

    /** 코드 수명 — 발급 화면을 보며 바로 입력하는 값이라 짧게 잡는다. */
    public static final Duration TTL = Duration.ofMinutes(5);

    private final TossLinkCodeRepository repository;
    private final Clock clock;

    public TossLinkCodeService(TossLinkCodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 연결 코드를 발급한다 — 평문을 돌려주고(화면에만 노출) DB엔 해시·만료를 저장한다.
     * 같은 사용자의 기존 미사용 코드는 무효화해 항상 하나만 유효하게 둔다(EmailTokenService와 동일).
     *
     * <p>이미 토스에 연결된 계정은 거부한다 — {@code toss_user_key}는 once-set 불변이라 이 코드로 할 수 있는
     * 일이 없다. 설정 화면이 버튼을 숨기지만 그건 표시일 뿐이라, 실제 방어는 여기서 한다.
     *
     * @return 사용자에게 보여줄 평문 코드(대문자 8자)
     * @throws TossLinkConflictException 이미 토스 신원이 붙어 있는 계정인 경우
     */
    public String issue(User user) {
        if (user.getTossUserKey() != null) {
            throw new TossLinkConflictException("이미 토스에 연결된 계정입니다");
        }
        Instant now = clock.instant();
        List<TossLinkCode> previous = repository.findByUserAndUsedAtIsNull(user);
        for (TossLinkCode code : previous) {
            code.markUsed(now);
        }
        repository.saveAll(previous);

        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String rawCode = code.toString();
        repository.save(TossLinkCode.issue(user, hash(rawCode), now.plus(TTL)));
        return rawCode;
    }

    /**
     * 코드를 검증·소비한다. 통과하면 발급자 user를 돌려주고 일회용 처리한다.
     *
     * @return 유효하면 발급자, 아니면 빈 Optional(없음·만료·이미 사용을 호출자가 구분하지 못하게 동일 처리)
     */
    public Optional<User> consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findByCodeHash(hash(normalize(rawCode)))
                .filter(code -> code.isConsumableAt(now))
                .map(code -> {
                    code.markUsed(now);
                    repository.save(code);
                    return code.getUser();
                });
    }

    /** 사용자가 옮겨 적은 코드를 관대하게 받아들인다 — 앞뒤 공백·소문자 입력은 같은 코드로 본다. */
    private static String normalize(String rawCode) {
        return rawCode.strip().toUpperCase(Locale.ROOT);
    }

    /** 평문 코드의 SHA-256 해시(hex 소문자 64자). */
    private static String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // 표준 JDK에 항상 존재
        }
    }
}
