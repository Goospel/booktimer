package com.booktimer.web;

import com.booktimer.email.SesBounceComplaintHandler;
import com.booktimer.email.SnsMessage;
import com.booktimer.email.SnsSignatureVerifier;
import com.booktimer.email.SnsSubscriptionConfirmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * SES 반송·불만 피드백을 받는 SNS 웹훅 — {@code POST /internal/ses/notifications}.
 *
 * <p>SES 아이덴티티 알림 → SNS 토픽 → 이 엔드포인트 구독으로, 반송·불만이 실시간으로 들어와
 * {@link SesBounceComplaintHandler}가 억제 목록에 반영한다(발신 평판 보호, AWS 승인문 요구사항).
 *
 * <p><b>보안</b>(공개 엔드포인트라 위조 방지가 핵심):
 * <ol>
 *   <li>TopicArn 허용목록 — {@code booktimer.ses.sns.topic-arn}이 설정돼 있으면 그 토픽 메시지만 처리.</li>
 *   <li>서명 검증 — {@link SnsSignatureVerifier}가 AWS 서명을 검증(실패 시 403).</li>
 * </ol>
 * 둘 다 통과해야만 구독 확인/알림 처리로 넘어간다. CSRF는 제외(보안설정) — 외부 SNS는 토큰을 못 싣는다.
 */
@RestController
public class SnsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SnsWebhookController.class);

    private final SnsSignatureVerifier signatureVerifier;
    private final SnsSubscriptionConfirmer subscriptionConfirmer;
    private final SesBounceComplaintHandler bounceComplaintHandler;
    private final String expectedTopicArn;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public SnsWebhookController(SnsSignatureVerifier signatureVerifier,
                                SnsSubscriptionConfirmer subscriptionConfirmer,
                                SesBounceComplaintHandler bounceComplaintHandler,
                                @Value("${booktimer.ses.sns.topic-arn:}") String expectedTopicArn) {
        this.signatureVerifier = signatureVerifier;
        this.subscriptionConfirmer = subscriptionConfirmer;
        this.bounceComplaintHandler = bounceComplaintHandler;
        this.expectedTopicArn = expectedTopicArn;
    }

    @PostMapping("/internal/ses/notifications")
    public ResponseEntity<Void> receive(@RequestBody String rawBody) {
        SnsMessage message;
        try {
            message = SnsMessage.fromJson(objectMapper.readTree(rawBody));
        } catch (RuntimeException e) {
            log.warn("SNS 메시지 파싱 실패 — 무시");
            return ResponseEntity.badRequest().build();
        }

        if (!expectedTopicArn.isBlank() && !expectedTopicArn.equals(message.topicArn())) {
            log.warn("SNS TopicArn 불일치 — 거부");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!signatureVerifier.verify(message)) {
            log.warn("SNS 서명 검증 실패 — 거부, type={}", message.type());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (message.isSubscriptionConfirmation()) {
            subscriptionConfirmer.confirm(message.subscribeUrl());
        } else if (message.isNotification()) {
            bounceComplaintHandler.handle(message.message());
        } else {
            log.info("SNS 메시지 무시(대상 아님) — type={}", message.type());
        }
        return ResponseEntity.ok().build();
    }
}
