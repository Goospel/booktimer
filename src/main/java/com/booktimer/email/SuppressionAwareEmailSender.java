package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 억제 목록을 존중하는 {@link EmailSender} 데코레이터 — 모든 발송의 단일 관문.
 *
 * <p>실제 발송기({@code @Qualifier("rawEmailSender")} = {@link SmtpEmailSender} 또는 {@link LoggingEmailSender})를
 * 감싸, 발송 직전 {@link EmailSuppressionService#isSuppressed}로 억제된 주소인지 확인한다. 억제됐으면 발송을
 * 건너뛰어(반송·불만 누적으로 인한 발신 평판 하락 방지), 아니면 실제 발송기에 위임한다.
 *
 * <p>{@code @Primary}라 {@link EmailDispatcher}(트랜잭션 메일)와 {@link com.booktimer.retention.RetentionNudgeService}
 * (마케팅 넛지)가 주입받는 {@code EmailSender}가 이 데코레이터가 된다 → 두 발송 경로 모두 억제 게이트를 탄다.
 * transactional·마케팅을 가리지 않고 억제한다 — 영구 반송/불만 주소엔 어떤 메일도 보내지 않는 게 평판상 옳다.
 */
@Component
@Primary
public class SuppressionAwareEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SuppressionAwareEmailSender.class);

    private final EmailSender delegate;
    private final EmailSuppressionService suppressionService;

    public SuppressionAwareEmailSender(@Qualifier("rawEmailSender") EmailSender delegate,
                                       EmailSuppressionService suppressionService) {
        this.delegate = delegate;
        this.suppressionService = suppressionService;
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        if (suppressionService.isSuppressed(toEmail)) {
            // 본문·토큰이 새지 않게 수신자/제목만 — 억제 목록에 있어 발송을 생략한다.
            log.info("[email:suppressed] 발송 생략(억제 목록) — to={}, subject={}", toEmail, subject);
            return;
        }
        delegate.send(toEmail, subject, htmlBody);
    }
}
