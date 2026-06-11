package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 이메일 발송을 <b>요청 스레드 밖(비동기)</b>으로 빼는 디스패처(이메일 인프라 1단계 하드닝).
 *
 * <p>{@link EmailVerificationService} 등은 토큰 발급(DB)만 동기로 하고, SMTP 발송은 이 디스패처에 위임한다.
 * 별도 빈으로 둔 이유: {@link Async}는 같은 빈 내부 호출(self-invocation)엔 프록시가 안 걸려 동작하지 않으므로,
 * 비동기 경계를 독립 빈으로 분리해야 한다.
 *
 * <p>효과: ① 요청 스레드가 SMTP 왕복 동안 블로킹되지 않음 ② 발송을 트랜잭션 밖으로 빼 DB 커넥션을 SMTP 동안
 * 점유하지 않음 ③ 가입 시 발송 유무에 따른 응답시간 차이를 없애 계정 열거 타이밍 사이드채널 제거.
 *
 * <p>발송 실패는 비동기 스레드라 호출자가 받지 못하므로 여기서 격리(catch+로그)한다 — 인증/재설정 흐름과 분리된다.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailSender emailSender;

    public EmailDispatcher(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    /** 이메일을 비동기로 발송한다(전용 풀 {@code emailTaskExecutor}). 실패는 격리·로그. */
    @Async("emailTaskExecutor")
    public void dispatch(String toEmail, String subject, String htmlBody) {
        try {
            emailSender.send(toEmail, subject, htmlBody);
        } catch (RuntimeException e) {
            // 본문·토큰 링크가 새지 않게 수신자/제목만 — 비동기라 호출 흐름과 분리됨.
            log.warn("이메일 비동기 발송 실패 — to={}, subject={}", toEmail, subject);
        }
    }
}
