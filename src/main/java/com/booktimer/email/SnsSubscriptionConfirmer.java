package com.booktimer.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

/**
 * SNS 구독 확인 — {@code SubscriptionConfirmation} 수신 시 {@code SubscribeURL}을 GET해 구독을 확정한다.
 *
 * <p>이 GET이 성공해야 SNS 토픽↔엔드포인트 구독이 활성화돼 이후 알림이 온다. {@code SubscribeURL}은 서명
 * 검증을 통과한 메시지의 것이라 신뢰할 수 있지만, 방어적으로 호스트도 재검증한다({@link SnsUrls}).
 */
@Component
public class SnsSubscriptionConfirmer {

    private static final Logger log = LoggerFactory.getLogger(SnsSubscriptionConfirmer.class);

    private final RestClient restClient;

    public SnsSubscriptionConfirmer() {
        this(defaultRestClient());
    }

    /** 테스트 전용 — MockRestServiceServer로 바인딩한 RestClient 주입. */
    SnsSubscriptionConfirmer(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** {@code SubscribeURL}을 GET해 구독을 확정한다. 실패는 격리(로그) — SNS가 확인 메시지를 재전송한다. */
    public void confirm(String subscribeUrl) {
        if (!SnsUrls.isTrustedSnsHost(subscribeUrl)) {
            log.warn("SNS 구독 확인 거부 — 신뢰할 수 없는 SubscribeURL 호스트");
            return;
        }
        try {
            restClient.get().uri(URI.create(subscribeUrl)).retrieve().toBodilessEntity();
            log.info("SNS 구독 확인 완료");
        } catch (RuntimeException e) {
            log.warn("SNS 구독 확인 실패({}) — SNS가 재전송함", e.getClass().getSimpleName());
        }
    }
}
