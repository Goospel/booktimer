package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * SNS 메시지 서명 검증 — AWS 공식 알고리즘을 JDK 표준 크립토로 구현(zero-dependency).
 *
 * <p>AWS가 규정한 순서대로 필드를 이어 붙인 canonical 문자열을 만들고, {@code SigningCertURL}의 공개키
 * ({@link SnsCertificateResolver})로 {@code Signature}(Base64)를 검증한다. {@code SignatureVersion}이 "1"이면
 * SHA1withRSA, 그 외("2")면 SHA256withRSA. 검증 실패·예외는 모두 false로 수렴시켜(fail-closed) 위조·손상
 * 메시지를 거부한다.
 *
 * <p>이 검증이 통과해야만 {@link SesBounceComplaintHandler}가 억제를 반영한다 — 공개 엔드포인트라 위조
 * 방지가 핵심(누구나 가짜 bounce로 임의 주소를 억제하는 것을 막는다).
 */
@Component
public class SnsSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SnsSignatureVerifier.class);

    private final SnsCertificateResolver certificateResolver;

    public SnsSignatureVerifier(SnsCertificateResolver certificateResolver) {
        this.certificateResolver = certificateResolver;
    }

    /** 메시지 서명이 유효하면 true. 어떤 실패·예외든 false(fail-closed). */
    public boolean verify(SnsMessage message) {
        try {
            PublicKey publicKey = certificateResolver.resolve(message.signingCertUrl());
            Signature verifier = Signature.getInstance(algorithm(message.signatureVersion()));
            verifier.initVerify(publicKey);
            verifier.update(canonicalString(message).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(message.signature()));
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            log.warn("SNS 서명 검증 실패 — type={}, messageId={}", message.type(), message.messageId());
            return false;
        }
    }

    private static String algorithm(String signatureVersion) {
        return "1".equals(signatureVersion) ? "SHA1withRSA" : "SHA256withRSA";
    }

    /**
     * AWS가 규정한 서명 대상 문자열 — 필드를 정해진 순서로 {@code key\nvalue\n} 형식으로 잇는다.
     * Notification과 (Un)SubscriptionConfirmation은 포함 필드·순서가 다르다.
     */
    static String canonicalString(SnsMessage m) {
        StringBuilder sb = new StringBuilder();
        if (m.isNotification()) {
            append(sb, "Message", m.message());
            append(sb, "MessageId", m.messageId());
            if (m.subject() != null) {
                append(sb, "Subject", m.subject());
            }
            append(sb, "Timestamp", m.timestamp());
            append(sb, "TopicArn", m.topicArn());
            append(sb, "Type", m.type());
        } else {
            // SubscriptionConfirmation / UnsubscribeConfirmation
            append(sb, "Message", m.message());
            append(sb, "MessageId", m.messageId());
            append(sb, "SubscribeURL", m.subscribeUrl());
            append(sb, "Timestamp", m.timestamp());
            append(sb, "Token", m.token());
            append(sb, "TopicArn", m.topicArn());
            append(sb, "Type", m.type());
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append(key).append('\n').append(value).append('\n');
    }
}
