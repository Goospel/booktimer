package com.booktimer.email;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * SNS가 준 URL(SigningCertURL·SubscribeURL)이 진짜 AWS SNS 호스트인지 검증 — SSRF·위조 인증서 차단.
 *
 * <p>서명 검증 전/후 어떤 경우에도, 앱이 GET하거나 인증서를 내려받는 URL은 반드시 {@code https}이고 호스트가
 * {@code sns.<region>.amazonaws.com} 형태여야 한다. amazonaws.com은 AWS가 소유하므로 이 패턴이면 공격자
 * 임의 서버로의 요청을 막을 수 있다.
 */
public final class SnsUrls {

    private static final Pattern SNS_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com$");

    private SnsUrls() {
    }

    /** {@code https} + 호스트가 {@code sns.<region>.amazonaws.com}이면 true. */
    public static boolean isTrustedSnsHost(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && SNS_HOST.matcher(uri.getHost()).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
