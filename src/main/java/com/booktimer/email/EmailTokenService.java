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
 * 저장한다(평문 미저장). 같은 user+type의 기존 미사용 토큰은 무효화해 항상 하나만 유효하게 둔다.
 *
 * <p><b>검증</b>: 평문을 해싱해 조회 → type 일치·미경과·미사용을 모두 통과하면 {@code usedAt}을 찍어(일회용)
 * user를 돌려준다. 하나라도 불만족이면 빈 {@link Optional}(존재/만료/재사용/type불일치를 호출자가 구분하지 못하게).
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
     * 토큰을 발급한다 — 평문을 돌려주고(메일 링크에만 실림) DB엔 해시·만료를 저장한다. 같은 user+type의
     * 기존 미사용 토큰은 무효화한다(하나만 유효).
     *
     * @return 평문 토큰(URL-safe Base64) — 호출자는 이걸 메일 링크에만 싣고 저장하지 않는다
     */
    public String issue(User user, EmailTokenType type) {
        // 기존 미사용 토큰 무효화 — 즉시 사용 처리(used)해 직전 링크를 무력화한다.
        Instant now = clock.instant();
        List<EmailToken> previous = repository.findByUserAndTypeAndUsedAtIsNull(user, type);
        for (EmailToken token : previous) {
            token.markUsed(now);
        }
        repository.saveAll(previous);

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = now.plus(type.ttl());
        repository.save(EmailToken.issue(user, type, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 평문 토큰을 검증·소비한다. 통과하면 소유 user를 돌려주고 토큰을 일회용 처리한다.
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
