package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로깅 기반 이메일 발송 어댑터(dev/test·기본) — 실제로 보내지 않고 로그만 남긴다.
 *
 * <p>{@code booktimer.email.enabled}가 꺼짐/미설정일 때 선택되는 기본 구현이다(Gemini {@code isEnabled} 게이트와
 * 동형 — 키/인프라가 없으면 외부 호출 없이 폴백). 발송 인프라(SES·DNS)가 준비되기 전에도 호출부 코드를 완성하고
 * 테스트할 수 있게 한다. 본문 전체는 로그에 남기지 않는다(토큰 링크 등 민감정보 노출 방지) — 수신자·제목만 남긴다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.email.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        // 실발송 0 — 인프라 미준비 단계의 폴백. 본문은 민감정보(토큰 링크)를 담을 수 있어 로그에 남기지 않는다.
        log.info("[email:logging] 발송 생략(enabled=false) — to={}, subject={}", toEmail, subject);
    }
}
