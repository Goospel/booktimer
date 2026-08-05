package com.booktimer.auth;

/**
 * 토스 로그인 신원 확인 실패 — 만료·위조 인가코드, 토스 API 오류, mTLS 미설정 등.
 *
 * <p>컨트롤러는 이걸 <b>401</b>로 매핑한다. 원인을 응답으로 구분해 주지 않는 이유는, 인가코드가 틀렸는지
 * 우리 인증서가 문제인지를 클라이언트가 알아봐야 할 이유가 없고 열거 단서만 되기 때문이다(원인은 로그로 남긴다).
 */
public class TossLoginException extends RuntimeException {

    public TossLoginException(String message) {
        super(message);
    }

    public TossLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
