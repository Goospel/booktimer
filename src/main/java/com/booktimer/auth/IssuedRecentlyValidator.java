package com.booktimer.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

/**
 * 발급 10분 이내만 통과시킨다(REQ-78 · 80 · 110): {@code iat}이 있고 {@code now − 10분 ≤ iat ≤ now + 60초}.
 *
 * <p>Apple 토큰 수명은 24시간이라 가로챈 토큰을 하루 동안 로그인 · 탈퇴에 다시 쓸 수 있다 — 앱은 토큰을 보내기 직전에 받으므로
 * 재사용 창을 10분으로 줄인다. 미래 60초는 기기·서버 시계 차이 몫(만료 검사의 기본 허용과 같다).
 * ponytail: 재사용 방지가 더 필요하면 서버 발급 일회 nonce(로그인 시작 API + 일회 저장)로 올린다.
 */
final class IssuedRecentlyValidator implements OAuth2TokenValidator<Jwt> {

    static final Duration MAX_AGE = Duration.ofMinutes(10);
    static final Duration FUTURE_SKEW = Duration.ofSeconds(60);

    private static final OAuth2Error STALE = new OAuth2Error("invalid_token", "iat missing or not within 10 minutes", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant iat = token.getIssuedAt();
        Instant now = Instant.now();
        if (iat == null || iat.isBefore(now.minus(MAX_AGE)) || iat.isAfter(now.plus(FUTURE_SKEW))) {
            return OAuth2TokenValidatorResult.failure(STALE);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
