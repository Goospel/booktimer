package com.booktimer.user;

/**
 * 토스 신원 연결 충돌 — 대상 계정에 이미 토스 신원이 있거나(once-set 위반), 그 userKey가 이미 다른 계정에
 * 붙어 있는 경우. 컨트롤러는 <b>409</b>로 매핑한다(코드가 틀린 400과 구분 — 재시도해도 소용없는 상태라
 * 사용자에게 다른 안내가 필요하다).
 */
public class TossLinkConflictException extends RuntimeException {

    public TossLinkConflictException(String message) {
        super(message);
    }

    public TossLinkConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
