package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠팡 딥링크 API HMAC 서명기 단위테스트 — 순수 함수(네트워크·Clock 빈 없이 Instant 직접 주입)라 결정적으로 검증한다.
 *
 * <p>기대 hex는 구현이 자기 채점하지 않도록 독립 도구(openssl dgst -sha256 -hmac)로 산출해 하드코딩했다:
 * {@code echo -n "<message>" | openssl dgst -sha256 -hmac "<secret>" -hex}
 */
class CoupangDeeplinkSignerTest {

    private final CoupangDeeplinkSigner signer = new CoupangDeeplinkSigner();

    private static final String DEEPLINK_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/deeplink";

    @Test
    @DisplayName("datetime()은 GMT 기준 yyMMdd'T'HHmmss'Z' 포맷을 만든다")
    void datetime_formatsAsGmt() {
        // 2025-07-03T09:15:30Z (UTC) — 계획 md 예시와 동일한 datetime을 만드는 Instant
        Instant fixed = Instant.parse("2025-07-03T09:15:30Z");

        assertThat(signer.datetime(fixed)).isEqualTo("250703T091530Z");
    }

    @Test
    @DisplayName("signature(): 고정 secret·message → 독립 계산한 기대 hex와 일치(자가채점 방지)")
    void signature_matchesIndependentlyComputedVector() {
        String datetime = "250703T091530Z";
        String message = datetime + "POST" + DEEPLINK_PATH; // query 없음(딥링크 API)

        String actual = signer.signature("test-secret-key", message);

        assertThat(actual).isEqualTo("363277d7d0dd29db7a756bb385e970799086189f5e3b0d8b87fb9c4ae733833a");
    }

    @Test
    @DisplayName("signature(): query가 있으면 message 뒤에 이어붙어 서명이 달라진다(함수 일반성)")
    void signature_withQuery_matchesIndependentlyComputedVector() {
        String datetime = "250703T091530Z";
        String message = datetime + "GET" + DEEPLINK_PATH + "foo=bar";

        String actual = signer.signature("test-secret-key", message);

        assertThat(actual).isEqualTo("653862af3a766adc39d78974974e23c1fc840a67866bf031a900af12c8bfa5ce");
    }

    @Test
    @DisplayName("signature(): secret이 다르면 같은 message라도 다른 서명(하드코딩된 별도 벡터)")
    void signature_differentSecret_producesDifferentVector() {
        String datetime = "250703T091530Z";
        String message = datetime + "POST" + DEEPLINK_PATH;

        String actual = signer.signature("another-secret", message);

        assertThat(actual).isEqualTo("0297871454bc8029834c8b007c6e302b523e2603d996760fbc76431b945646bf");
        // 다른 secret과 대조 — 자리만 맞는 우연한 일치가 아님을 확인
        assertThat(actual).isNotEqualTo(signer.signature("test-secret-key", message));
    }

    @Test
    @DisplayName("authorizationHeader(): CEA 포맷 — algorithm/access-key/signed-date/signature 필드 순서·구두점 정확")
    void authorizationHeader_formatsCorrectly() {
        String datetime = "250703T091530Z";

        String header = signer.authorizationHeader(
                "AK123", "test-secret-key", "POST", DEEPLINK_PATH, "", datetime);

        assertThat(header).isEqualTo(
                "CEA algorithm=HmacSHA256, access-key=AK123, signed-date=250703T091530Z, "
                        + "signature=363277d7d0dd29db7a756bb385e970799086189f5e3b0d8b87fb9c4ae733833a");
    }
}
