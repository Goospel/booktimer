package com.booktimer.email;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * SES 반송(bounce)·불만(complaint) 통지 처리 — SNS 봉투 안쪽 SES JSON을 파싱해 억제 목록에 반영한다.
 *
 * <ul>
 *   <li><b>영구 반송(Permanent)</b> → 각 수신자를 {@link SuppressionReason#BOUNCE}로 억제.
 *       일시 반송(Transient)은 재시도로 성공할 수 있어 억제하지 않는다.</li>
 *   <li><b>불만(Complaint)</b> → 각 수신자를 {@link SuppressionReason#COMPLAINT}로 억제하고,
 *       해당 사용자의 마케팅 수신 동의도 철회한다(스팸 신고했으므로 재발송·마케팅 모두 중단).</li>
 * </ul>
 *
 * <p>이 처리는 {@link SnsSignatureVerifier} 검증을 통과한 메시지에 대해서만 호출된다.
 */
@Component
public class SesBounceComplaintHandler {

    private static final Logger log = LoggerFactory.getLogger(SesBounceComplaintHandler.class);

    private final EmailSuppressionService suppressionService;
    private final UserRepository userRepository;
    // 앱에 Jackson ObjectMapper 빈이 자동 등록되지 않아 자체 인스턴스를 둔다(CoupangDeeplinkClient와 동일 관례).
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public SesBounceComplaintHandler(EmailSuppressionService suppressionService,
                                     UserRepository userRepository) {
        this.suppressionService = suppressionService;
        this.userRepository = userRepository;
    }

    /**
     * SNS 봉투의 {@code Message}(SES 통지 JSON 문자열)를 파싱해 억제를 반영한다.
     *
     * @param sesMessageJson SES가 보낸 notificationType(Bounce/Complaint) JSON
     */
    @Transactional
    public void handle(String sesMessageJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sesMessageJson);
        } catch (tools.jackson.core.JacksonException e) {
            log.warn("SES 통지 JSON 파싱 실패 — 무시");
            return;
        }
        String type = root.path("notificationType").asText("");
        switch (type) {
            case "Bounce" -> handleBounce(root.path("bounce"));
            case "Complaint" -> handleComplaint(root.path("complaint"));
            default -> log.info("SES 통지 무시(대상 아님) — notificationType={}", type);
        }
    }

    private void handleBounce(JsonNode bounce) {
        String bounceType = bounce.path("bounceType").asText("");
        if (!"Permanent".equals(bounceType)) {
            // 일시 반송은 억제하지 않는다(재시도 여지).
            return;
        }
        String detail = bounceType + "/" + bounce.path("bounceSubType").asText("");
        for (JsonNode recipient : bounce.path("bouncedRecipients")) {
            String email = recipient.path("emailAddress").asText("");
            suppressionService.suppress(email, SuppressionReason.BOUNCE, detail);
        }
    }

    private void handleComplaint(JsonNode complaint) {
        String detail = complaint.path("complaintFeedbackType").asText("");
        for (JsonNode recipient : complaint.path("complainedRecipients")) {
            String email = recipient.path("emailAddress").asText("");
            suppressionService.suppress(email, SuppressionReason.COMPLAINT, detail);
            withdrawMarketingConsent(email);
        }
    }

    /** 불만 신고자의 마케팅 동의를 철회한다(있으면). 억제가 이미 발송을 막지만, 동의 상태도 정합화한다. */
    private void withdrawMarketingConsent(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            user.withdrawMarketingConsent();
            userRepository.save(user);
            log.info("불만 신고로 마케팅 동의 철회 — userId={}", user.getId());
        });
    }
}
