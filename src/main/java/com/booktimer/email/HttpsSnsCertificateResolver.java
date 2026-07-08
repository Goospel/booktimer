package com.booktimer.email;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SnsCertificateResolver} 실구현 — {@code SigningCertURL}에서 X.509 인증서를 내려받아 공개키를 추출한다.
 *
 * <p>보안: URL은 반드시 {@code sns.<region>.amazonaws.com}(https)이어야 한다({@link SnsUrls}) — 위조된
 * SigningCertURL로 공격자 인증서를 가져오는 것을 막는다. 같은 URL의 인증서는 캐시해 매 알림마다 재다운로드하지
 * 않는다(SNS는 인증서를 주기적으로만 교체).
 */
@Component
public class HttpsSnsCertificateResolver implements SnsCertificateResolver {

    private final RestClient restClient;
    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    public HttpsSnsCertificateResolver() {
        this(defaultRestClient());
    }

    /** 테스트 전용 — MockRestServiceServer로 바인딩한 RestClient 주입. */
    HttpsSnsCertificateResolver(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public PublicKey resolve(String signingCertUrl) {
        if (!SnsUrls.isTrustedSnsHost(signingCertUrl)) {
            throw new IllegalArgumentException("신뢰할 수 없는 SigningCertURL 호스트");
        }
        return cache.computeIfAbsent(signingCertUrl, this::fetch);
    }

    private PublicKey fetch(String url) {
        byte[] pem = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
        if (pem == null || pem.length == 0) {
            throw new IllegalStateException("빈 SNS 인증서 응답");
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate =
                    (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(pem));
            return certificate.getPublicKey();
        } catch (CertificateException e) {
            throw new IllegalStateException("SNS 인증서 파싱 실패", e);
        }
    }
}
