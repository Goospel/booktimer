package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;

/**
 * SMTP 기반 이메일 발송 어댑터(prod) — {@link JavaMailSender}에 위임해 실제 메일을 보낸다.
 *
 * <p>{@code booktimer.email.enabled=true}일 때만 빈으로 등록된다({@link LoggingEmailSender}와 상호배타).
 * 운영에서 발송 수단은 SES SMTP를 쓰며, {@code spring.mail.*}(host/port/username/password)는 환경변수/SSM으로
 * 주입한다(repo 미커밋 — OAuth 키와 동일 정책). 발신 도메인의 SPF/DKIM/DMARC가 갖춰져야 반송·평판 하락을 피한다.
 *
 * <p>발송 실패는 {@link EmailSendException}으로 감싸 던진다 — 호출자가 흐름에 맞게 격리(가입 통지)하거나
 * 사용자에게 안내(인증·재설정)한다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.email.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${booktimer.email.from:noreply@booktimer.app}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // HTML 본문
            mailSender.send(message);
        } catch (MessagingException | RuntimeException e) {
            // 본문·토큰 링크가 새지 않게 메시지 상세는 남기지 않고 수신자/제목만 — 호출자가 격리/안내한다.
            log.warn("[email:smtp] 발송 실패 — to={}, subject={}", toEmail, subject);
            throw new EmailSendException("이메일 발송에 실패했습니다.", e);
        }
    }
}
