package com.booktimer.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SNS URL 신뢰 검증 — SSRF·위조 인증서 차단의 핵심. lookalike 호스트를 반드시 거부해야 한다.
 */
class SnsUrlsTest {

    @Test
    void trusted_httpsSnsRegionalHost() {
        assertThat(SnsUrls.isTrustedSnsHost(
                "https://sns.ap-northeast-2.amazonaws.com/SimpleNotificationService-abc.pem")).isTrue();
    }

    @Test
    void rejects_nonHttps() {
        assertThat(SnsUrls.isTrustedSnsHost(
                "http://sns.ap-northeast-2.amazonaws.com/cert.pem")).isFalse();
    }

    @Test
    void rejects_lookalikeSuffixHost() {
        // 공격자 도메인이 amazonaws.com을 접미사처럼 붙인 경우 — 반드시 거부.
        assertThat(SnsUrls.isTrustedSnsHost(
                "https://sns.ap-northeast-2.amazonaws.com.evil.com/cert.pem")).isFalse();
    }

    @Test
    void rejects_arbitraryHost() {
        assertThat(SnsUrls.isTrustedSnsHost("https://evil.example.com/cert.pem")).isFalse();
    }

    @Test
    void rejects_nullAndBlank() {
        assertThat(SnsUrls.isTrustedSnsHost(null)).isFalse();
        assertThat(SnsUrls.isTrustedSnsHost("   ")).isFalse();
    }
}
