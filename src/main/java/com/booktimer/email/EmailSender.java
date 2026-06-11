package com.booktimer.email;

/**
 * 이메일 발송기(포트) — 수신자·제목·HTML 본문을 받아 한 통을 보낸다(이메일 인프라 1단계).
 *
 * <p>{@link com.booktimer.personality.ReadingPersonalityNarrator}와 같은 정신의 포트 추상화다. 이 포트 덕분에
 * 서비스/테스트는 실제 SMTP·자격증명 없이 가짜/로깅 구현으로 검증되고, 발송 수단(SES SMTP→다른 공급자)을
 * 갈아도 호출부가 안 흔들린다.
 *
 * <p>구현체는 {@code booktimer.email.enabled} 토글로 갈린다 — 켜짐이면 {@link SmtpEmailSender}(실발송),
 * 꺼짐/미설정이면 {@link LoggingEmailSender}(로그만, 메일 0). 인프라(SES·DNS)가 준비되기 전엔 로깅 구현으로
 * 코드를 먼저 개발·출하하고, 준비되면 토글만 켠다.
 */
public interface EmailSender {

    /**
     * 이메일 한 통을 보낸다.
     *
     * <p>발송 실패가 호출 흐름(가입·재설정 요청 등)을 깨선 안 되는 곳에서는 호출자가 예외를 격리(catch+로그)한다 —
     * 단, 인증/재설정처럼 사용자가 메일을 꼭 받아야 하는 흐름은 호출자가 실패를 사용자에게 안내한다.
     *
     * @param toEmail  수신자 이메일
     * @param subject  제목
     * @param htmlBody HTML 본문
     */
    void send(String toEmail, String subject, String htmlBody);
}
