package com.booktimer.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SNS 서명 검증 로직 — 인테스트 RSA 키페어로 canonical 문자열에 서명한 뒤, 검증기가 통과/거부를 옳게 하는지 못박는다.
 * 인증서 리졸버는 목으로 대체해 고정 공개키를 돌려주므로 네트워크·실인증서 없이 결정적으로 검증된다.
 */
class SnsSignatureVerifierTest {

    private KeyPair keyPair;
    private SnsCertificateResolver resolver;
    private SnsSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        resolver = mock(SnsCertificateResolver.class);
        when(resolver.resolve("https://sns.ap-northeast-2.amazonaws.com/cert.pem"))
                .thenReturn(keyPair.getPublic());
        verifier = new SnsSignatureVerifier(resolver);
    }

    private String signSha256(String canonical) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private SnsMessage notification(String signature) {
        return new SnsMessage("Notification", "msg-1", "arn:aws:sns:ap-northeast-2:1:booktimer-ses",
                "{\"notificationType\":\"Bounce\"}", null, "2026-07-08T00:00:00.000Z",
                null, null, "2", signature, "https://sns.ap-northeast-2.amazonaws.com/cert.pem");
    }

    @Test
    void verify_validNotificationSignature_returnsTrue() throws Exception {
        SnsMessage unsigned = notification(null);
        String sig = signSha256(SnsSignatureVerifier.canonicalString(unsigned));

        assertThat(verifier.verify(notification(sig))).isTrue();
    }

    @Test
    void verify_tamperedMessage_returnsFalse() throws Exception {
        SnsMessage original = notification(null);
        String sig = signSha256(SnsSignatureVerifier.canonicalString(original));

        // 서명은 원본 canonical에 대한 것인데, 내용을 바꾼 메시지에 붙이면 검증이 실패해야 한다.
        SnsMessage tampered = new SnsMessage("Notification", "msg-1", "arn:aws:sns:ap-northeast-2:1:booktimer-ses",
                "{\"notificationType\":\"Complaint\"}", null, "2026-07-08T00:00:00.000Z",
                null, null, "2", sig, "https://sns.ap-northeast-2.amazonaws.com/cert.pem");

        assertThat(verifier.verify(tampered)).isFalse();
    }

    @Test
    void verify_subscriptionConfirmationSignature_returnsTrue() throws Exception {
        SnsMessage unsigned = new SnsMessage("SubscriptionConfirmation", "msg-2",
                "arn:aws:sns:ap-northeast-2:1:booktimer-ses", "You have chosen to subscribe",
                null, "2026-07-08T00:00:00.000Z", "token-abc",
                "https://sns.ap-northeast-2.amazonaws.com/confirm", "2", null,
                "https://sns.ap-northeast-2.amazonaws.com/cert.pem");
        String sig = signSha256(SnsSignatureVerifier.canonicalString(unsigned));

        SnsMessage signed = new SnsMessage("SubscriptionConfirmation", "msg-2",
                "arn:aws:sns:ap-northeast-2:1:booktimer-ses", "You have chosen to subscribe",
                null, "2026-07-08T00:00:00.000Z", "token-abc",
                "https://sns.ap-northeast-2.amazonaws.com/confirm", "2", sig,
                "https://sns.ap-northeast-2.amazonaws.com/cert.pem");

        assertThat(verifier.verify(signed)).isTrue();
    }

    @Test
    void verify_garbageSignature_returnsFalseNotThrows() {
        assertThat(verifier.verify(notification("not-base64-!!!"))).isFalse();
    }
}
