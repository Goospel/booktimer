package com.booktimer.email;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 이메일 토큰 발급·검증 — 가입 인증·비밀번호 재설정 공용.
 *
 * <p><b>발급</b>: {@link SecureRandom} 32바이트 → URL-safe Base64 평문 토큰을 만들고, DB엔 그 SHA-256 해시만
 * 저장한다(평문 미저장). <b>직전 토큰을 죽이지 않는다</b> — 같은 user+type의 유효 토큰이 TTL 안에서 여럿
 * 공존할 수 있다. 죽였을 땐 남이 재설정을 반복 요청하는 것만으로 피해자가 손에 든 링크를 계속 무력화할 수
 * 있었다. 공존해도 각 토큰은 256비트 난수·해시 저장·일회용·TTL(재설정 1h / 인증 24h / 수신거부 30d)이라
 * 추측 가능성이 의미 있게 늘지 않는다.
 *
 * <p><b>검증</b>: 평문을 해싱해 조회 → type 일치·미경과·미사용을 모두 통과하면 {@code usedAt}을 찍어(일회용)
 * user를 돌려준다. 하나라도 불만족이면 빈 {@link Optional}(존재/만료/재사용/type불일치를 호출자가 구분하지 못하게).
 * <b>성공 소비는 같은 user+type의 형제 미사용 토큰도 함께 죽인다</b> — 「한 번 성공하면 나머지는 끝」이라는
 * 위생을 발급 시점이 아니라 소비 시점에 둔 것이다(실패한 소비는 형제를 건드리지 않는다).
 *
 * <p>"지금"은 주입된 {@link Clock}으로 — 만료 경계를 테스트에서 고정·검증한다(N-010).
 */
@Service
@Transactional
public class EmailTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final EmailTokenRepository repository;
    private final Clock clock;

    public EmailTokenService(EmailTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 토큰을 발급한다 — 평문을 돌려주고(메일 링크에만 실림) DB엔 해시·만료를 저장한다. 기존 미사용 토큰은
     * <b>건드리지 않는다</b> — 무효화는 성공 소비 시점에 한다({@link #consume}).
     *
     * @return 평문 토큰(URL-safe Base64) — 호출자는 이걸 메일 링크에만 싣고 저장하지 않는다
     */
    public String issue(User user, EmailTokenType type) {
        Instant now = clock.instant();
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = now.plus(type.ttl());
        repository.save(EmailToken.issue(user, type, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 평문 토큰을 검증·소비한다. 통과하면 소유 user를 돌려주고 토큰을 일회용 처리하며, 같은 user+type의
     * 나머지 미사용 토큰도 함께 사용 처리한다(성공했을 때만 — 실패한 소비가 형제를 죽이면 쓰레기 토큰으로
     * 남의 링크를 끊을 수 있다).
     *
     * @return 유효하면 user, 아니면 빈 Optional(존재하지 않음·type불일치·만료·이미 사용 모두 동일하게 빈 값)
     */
    public Optional<User> consume(String rawToken, EmailTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<EmailToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EmailToken token = found.get();
        Instant now = clock.instant();
        if (token.getType() != type || !token.isConsumableAt(now)) {
            return Optional.empty(); // type 불일치 / 만료 / 이미 사용 — 소비하지 않음
        }
        token.markUsed(now);
        repository.save(token);

        // 성공했으니 형제 링크는 끝 — 같은 user+type의 남은 미사용 토큰을 함께 사용 처리한다.
        // (방금 소비한 토큰은 이미 usedAt이 찍혀 조회에서 빠지거나, 걸려도 같은 값이라 멱등하다.)
        List<EmailToken> siblings = repository.findByUserAndTypeAndUsedAtIsNull(token.getUser(), type);
        for (EmailToken sibling : siblings) {
            sibling.markUsed(now);
        }
        repository.saveAll(siblings);

        return Optional.of(token.getUser());
    }

    /** 평문 토큰의 SHA-256 해시(hex 소문자 64자). */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // 표준 JDK에 항상 존재
        }
    }
}
