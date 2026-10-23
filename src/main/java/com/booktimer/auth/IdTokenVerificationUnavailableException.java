package com.booktimer.auth;

/**
 * Google · 카카오 ID 토큰을 <b>판정하지 못했다</b>(공개키 목록을 못 받음 등) — 「위조·만료라 거부」와 다른 장애다.
 * {@link AppleVerificationUnavailableException}과 같은 이유로 컨트롤러는 503으로 낸다.
 */
public class IdTokenVerificationUnavailableException extends RuntimeException {

    public IdTokenVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
