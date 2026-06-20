package com.booktimer.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;

/**
 * VAPID 서명 Web Push 발송 서비스.
 *
 * <p>nl.martijndwars:web-push 라이브러리로 VAPID 서명 + AES-GCM 암호화 후 브라우저 푸시 서비스로 전송.
 * HTTP 404/410 응답은 구독이 만료됐음을 의미하므로 호출자가 해당 구독을 삭제해야 한다.
 *
 * <p>VAPID 키 생성 방법 (1회, 환경변수로 주입):
 * <pre>
 *   npx web-push generate-vapid-keys
 *   # 또는 Java:
 *   KeyPair kp = Utils.generateVAPIDKeyPair();
 *   System.out.println("public:  " + Base64.getUrlEncoder().withoutPadding().encodeToString(((ECPublicKey)kp.getPublic()).getQ().getEncoded(false)));
 *   System.out.println("private: " + Base64.getUrlEncoder().withoutPadding().encodeToString(((ECPrivateKey)kp.getPrivate()).getD().toByteArray()));
 * </pre>
 */
@Service
public class PushSenderService {

    private static final Logger log = LoggerFactory.getLogger(PushSenderService.class);

    static {
        // BouncyCastle을 JCA Security 프로바이더로 등록 — ECDH/VAPID 서명에 필요.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushService pushService; // null if VAPID keys not configured

    public PushSenderService(
            @Value("${booktimer.push.vapid.public-key:not-configured}") String vapidPublicKey,
            @Value("${booktimer.push.vapid.private-key:not-configured}") String vapidPrivateKey,
            @Value("${booktimer.push.vapid.subject:mailto:support@booktimer.app}") String vapidSubject) {
        PushService ps = null;
        if (!"not-configured".equals(vapidPublicKey) && !"not-configured".equals(vapidPrivateKey)) {
            try {
                ps = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            } catch (Exception e) {
                log.warn("VAPID 키 초기화 실패 — 환경변수(BOOKTIMER_PUSH_VAPID_*)를 확인하세요. 발송 비활성.", e);
            }
        } else {
            log.info("VAPID 키 미설정 — 푸시 발송 비활성(BOOKTIMER_PUSH_ENABLED=false로 스케줄러도 OFF)");
        }
        this.pushService = ps;
    }

    /**
     * 단일 구독으로 푸시를 발송한다. 동기 호출 — 배치 루프에서 사용.
     *
     * @param sub     발송 대상 구독
     * @param payload JSON 페이로드 문자열
     * @return 발송 결과({@link SendResult})
     */
    public SendResult send(PushSubscription sub, String payload) {
        if (pushService == null) {
            log.warn("VAPID 키 미설정으로 푸시 발송 불가 — endpoint={}", sub.getEndpoint());
            return SendResult.ERROR;
        }
        try {
            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 201 || status == 200) {
                return SendResult.OK;
            } else if (status == 404 || status == 410) {
                // 구독 만료 — 호출자가 삭제해야 함
                return SendResult.EXPIRED;
            } else {
                log.warn("푸시 발송 비정상 응답 — endpoint={}, status={}", sub.getEndpoint(), status);
                return SendResult.ERROR;
            }
        } catch (Exception e) {
            log.warn("푸시 발송 예외 — endpoint={}", sub.getEndpoint(), e);
            return SendResult.ERROR;
        }
    }

    public enum SendResult {
        OK, EXPIRED, ERROR
    }
}
