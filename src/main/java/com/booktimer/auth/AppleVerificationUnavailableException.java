package com.booktimer.auth;

/**
 * Apple 토큰을 <b>판정하지 못했다</b>(Apple 키 목록을 못 받음 등) — 「위조·만료라 거부」와 다른 장애다. 앞의 것을 401로 내면
 * 앱이 Apple 창을 다시 띄우는 헛걸음이 되고 장애가 「로그인 실패」로 가려진다. 컨트롤러는 503으로 낸다.
 */
public class AppleVerificationUnavailableException extends RuntimeException {

    public AppleVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
