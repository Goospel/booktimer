package com.booktimer.auth;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 미니앱 Bearer 토큰의 발급·검증·폐기 (설계 §2.1).
 *
 * <p><b>발급</b>: {@link SecureRandom} 32바이트 → URL-safe Base64 평문 토큰을 만들고 DB엔 SHA-256 해시만
 * 저장한다(평문 미저장 — {@code EmailTokenService}와 같은 정신).
 *
 * <p><b>검증</b>: 평문을 해싱해 조회하고 만료만 본다. 만료(TTL {@value #TTL_DAYS}일) 시 재로그인은 토스
 * {@code appLogin()} 한 번이라 마찰이 사실상 0이다 — 그래서 슬라이딩 연장을 v2로 미룬다.
 *
 * <p>"지금"은 주입된 {@link Clock}으로 읽어 만료 경계를 테스트에서 고정한다(N-010).
 */
@Service
@Transactional
public class ApiTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int TTL_DAYS = 90;

    /** 토큰 수명. 만료되면 미니앱이 토스 로그인으로 새 토큰을 받는다. */
    public static final Duration TTL = Duration.ofDays(TTL_DAYS);

    private final ApiTokenRepository repository;
    private final Clock clock;

    public ApiTokenService(ApiTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 새 토큰을 발급한다 — 평문을 돌려주고(응답에만 실림) DB엔 해시·만료를 저장한다.
     *
     * <p>기존 토큰을 무효화하지 <b>않는다</b>: 같은 사람이 여러 기기(미니앱·재설치)에서 동시에 쓸 수 있어야
     * 하고, 새 기기 로그인이 옛 기기를 조용히 로그아웃시키면 오히려 버그로 보인다.
     */
    public String issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repository.save(ApiToken.issue(user, hash(rawToken), clock.instant().plus(TTL)));
        return rawToken;
    }

    /**
     * Bearer 토큰을 검증한다. 통과하면 소유 user를 돌려주고 마지막 사용 시각을 갱신한다.
     *
     * @return 유효하면 user, 아니면 빈 Optional(없음·만료·폐기를 호출자가 구분하지 못하게 동일 처리)
     */
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isValidAt(now))
                .map(token -> {
                    token.touch(now);
                    repository.save(token);
                    return token.getUser();
                });
    }

    /** 토큰을 폐기한다(logout) — 행을 지워 즉시 무효화한다. 없는 토큰이면 조용히 무시(멱등). */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(repository::delete);
    }

    /** 평문 토큰의 SHA-256 해시(hex 소문자 64자). */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // 표준 JDK에 항상 존재
        }
    }
}
