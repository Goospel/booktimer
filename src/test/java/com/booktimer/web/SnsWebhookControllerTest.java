package com.booktimer.web;

import com.booktimer.email.SesBounceComplaintHandler;
import com.booktimer.email.SnsSignatureVerifier;
import com.booktimer.email.SnsSubscriptionConfirmer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SNS 웹훅 라우팅·거부 — 서명 통과 시에만 처리, TopicArn 불일치/서명 실패는 403, 파싱 실패는 400.
 * 서명 검증 자체는 {@link SnsSignatureVerifier}(별도 테스트)라 여기선 목으로 라우팅만 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
class SnsWebhookControllerTest {

    @Mock
    SnsSignatureVerifier signatureVerifier;

    @Mock
    SnsSubscriptionConfirmer subscriptionConfirmer;

    @Mock
    SesBounceComplaintHandler bounceComplaintHandler;

    private SnsWebhookController controller(String expectedTopicArn) {
        return new SnsWebhookController(signatureVerifier, subscriptionConfirmer,
                bounceComplaintHandler, expectedTopicArn);
    }

    private String notificationJson(String topicArn, String message) {
        return """
                {"Type":"Notification","MessageId":"m1","TopicArn":"%s","Message":"%s",
                "Timestamp":"2026-07-08T00:00:00.000Z","SignatureVersion":"2","Signature":"c2ln",
                "SigningCertURL":"https://sns.ap-northeast-2.amazonaws.com/c.pem"}
                """.formatted(topicArn, message);
    }

    private String subscriptionJson(String subscribeUrl) {
        return """
                {"Type":"SubscriptionConfirmation","MessageId":"m2","TopicArn":"arn:aws:sns:x",
                "Message":"confirm","Token":"tok","SubscribeURL":"%s",
                "Timestamp":"2026-07-08T00:00:00.000Z","SignatureVersion":"2","Signature":"c2ln",
                "SigningCertURL":"https://sns.ap-northeast-2.amazonaws.com/c.pem"}
                """.formatted(subscribeUrl);
    }

    @Test
    void validNotification_verified_handlesInnerSesMessage() {
        when(signatureVerifier.verify(any())).thenReturn(true);

        ResponseEntity<Void> response = controller("arn:aws:sns:x:booktimer-ses")
                .receive(notificationJson("arn:aws:sns:x:booktimer-ses", "SES-INNER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bounceComplaintHandler).handle("SES-INNER");
    }

    @Test
    void subscriptionConfirmation_verified_confirmsAndDoesNotHandle() {
        when(signatureVerifier.verify(any())).thenReturn(true);

        ResponseEntity<Void> response = controller("arn:aws:sns:x")
                .receive(subscriptionJson("https://sns.ap-northeast-2.amazonaws.com/confirm"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(subscriptionConfirmer).confirm("https://sns.ap-northeast-2.amazonaws.com/confirm");
        verify(bounceComplaintHandler, never()).handle(any());
    }

    @Test
    void invalidSignature_rejected403_noProcessing() {
        when(signatureVerifier.verify(any())).thenReturn(false);

        ResponseEntity<Void> response = controller("arn:aws:sns:x")
                .receive(notificationJson("arn:aws:sns:x", "SES-INNER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(bounceComplaintHandler, never()).handle(any());
        verify(subscriptionConfirmer, never()).confirm(any());
    }

    /**
     * TopicArn 미설정(빈 값)이면 <b>서명이 유효해도</b> 무조건 403 — fail-closed.
     *
     * <p>전에는 빈 값이 "체크 건너뜀"이라, 운영에서 {@code BOOKTIMER_SES_SNS_TOPIC_ARN} 주입이 빠지면
     * 이 공개 엔드포인트의 1차 방어가 조용히 사라졌다. 위 {@code validNotification_*}이 양성 대조군이다
     * (일치 ARN + 유효 서명이면 그대로 처리된다 — 즉 이 403은 "항상 거부"가 아니다).
     */
    @Test
    void blankTopicArn_rejected403_failClosed() {
        lenient().when(signatureVerifier.verify(any())).thenReturn(true);

        ResponseEntity<Void> response =
                controller("").receive(notificationJson("arn:aws:sns:x:booktimer-ses", "SES-INNER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(bounceComplaintHandler, never()).handle(any());
        verify(subscriptionConfirmer, never()).confirm(any());
    }

    @Test
    void topicArnMismatch_rejected403_beforeSignatureCheck() {
        ResponseEntity<Void> response = controller("arn:aws:sns:expected")
                .receive(notificationJson("arn:aws:sns:attacker", "SES-INNER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(signatureVerifier, never()).verify(any());
        verify(bounceComplaintHandler, never()).handle(any());
    }

    @Test
    void malformedBody_badRequest() {
        ResponseEntity<Void> response = controller("").receive("not json {{{");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
