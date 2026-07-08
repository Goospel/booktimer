package com.booktimer.email;

import tools.jackson.databind.JsonNode;

/**
 * SNS HTTP(S) 알림 봉투(envelope) 한 건 — 서명 검증·라우팅에 필요한 필드만 담는다.
 *
 * <p>SES 반송·불만 통지는 이 봉투의 {@code Message}(문자열)에 다시 JSON으로 담겨 온다
 * ({@link SesBounceComplaintHandler}가 그 안쪽을 파싱). 봉투 자체의 진위는 {@link SnsSignatureVerifier}가
 * {@code Signature}/{@code SigningCertURL}로 검증한다.
 */
public record SnsMessage(
        String type,
        String messageId,
        String topicArn,
        String message,
        String subject,
        String timestamp,
        String token,
        String subscribeUrl,
        String signatureVersion,
        String signature,
        String signingCertUrl) {

    public static SnsMessage fromJson(JsonNode n) {
        return new SnsMessage(
                text(n, "Type"),
                text(n, "MessageId"),
                text(n, "TopicArn"),
                text(n, "Message"),
                text(n, "Subject"),
                text(n, "Timestamp"),
                text(n, "Token"),
                text(n, "SubscribeURL"),
                text(n, "SignatureVersion"),
                text(n, "Signature"),
                text(n, "SigningCertURL"));
    }

    private static String text(JsonNode n, String field) {
        return n.path(field).asText(null); // 없는 필드는 MissingNode → null
    }

    public boolean isNotification() {
        return "Notification".equals(type);
    }

    public boolean isSubscriptionConfirmation() {
        return "SubscriptionConfirmation".equals(type);
    }
}
