package com.booktimer.email;

import java.time.Duration;

/**
 * 이메일 토큰 용도 — 가입 이메일 인증 / 비밀번호 재설정. 같은 토큰 모델을 공용으로 쓰되 용도를 분리해
 * 한 용도의 토큰을 다른 용도로 소비하지 못하게 한다(type 불일치 거부).
 *
 * <p>만료 시간은 용도별로 다르다 — 인증은 24h(여유), 비밀번호 재설정은 1h(짧게 — 탈취 노출 창 최소화).
 */
public enum EmailTokenType {

    VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration ttl;

    EmailTokenType(Duration ttl) {
        this.ttl = ttl;
    }

    /** 이 용도 토큰의 유효 기간. */
    public Duration ttl() {
        return ttl;
    }
}
