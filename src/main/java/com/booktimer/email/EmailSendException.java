package com.booktimer.email;

/**
 * 이메일 발송 실패를 나타내는 런타임 예외 — {@link SmtpEmailSender}가 하부 SMTP/MIME 예외를 감싸 던진다.
 *
 * <p>호출자는 흐름에 맞게 처리한다: 가입 통지·열거 통지처럼 실패해도 응답이 같아야 하는 곳은 catch+로그로 격리하고,
 * 인증·비밀번호 재설정처럼 사용자가 메일을 꼭 받아야 하는 곳은 화면에 "잠시 후 재시도"를 안내한다.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
