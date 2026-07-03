package com.booktimer.book;

import com.booktimer.config.CoupangDeeplinkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link CoupangDeeplinkClient} 배선 테스트 — 실제 네트워크 없이 {@link MockRestServiceServer}로 요청·응답을 검증한다.
 *
 * <p>{@link CoupangDeeplinkSigner}는 이미 독립 벡터로 검증됐으므로(N-146/T-129 계획 md 6-1), 여기서는
 * 그 서명기가 만든 값이 실제 요청 헤더에 그대로 실리는지(배선), 응답 rCode·shortenUrl 파싱, 실패 시 폴백 유도,
 * 캐시로 재호출을 막는지를 본다(계획 md 6-2).
 */
class CoupangDeeplinkClientTest {

    private static final String API_BASE_URL = "https://api-gateway.coupang.com";
    private static final String DEEPLINK_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/deeplink";
    private static final String DEEPLINK_URL = API_BASE_URL + DEEPLINK_PATH;
    private static final String RAW_URL = "https://www.coupang.com/np/search?q=9788966260959";

    private CoupangDeeplinkProperties properties;
    private CoupangDeeplinkSigner signer;
    private Clock clock;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new CoupangDeeplinkProperties();
        properties.setAccessKey("AK123");
        properties.setSecretKey("SK123");
        properties.setSubId("booktimer-channel");
        properties.setApiBaseUrl(API_BASE_URL);
        properties.setRequestedBy("booktimer");

        signer = new CoupangDeeplinkSigner();
        clock = Clock.fixed(Instant.parse("2025-07-03T09:15:30Z"), ZoneOffset.UTC);

        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private CoupangDeeplinkClient client() {
        return new CoupangDeeplinkClient(properties, signer, clock, restClientBuilder.build());
    }

    private String expectedAuthorizationHeader() {
        String datetime = signer.datetime(clock.instant());
        return signer.authorizationHeader("AK123", "SK123", "POST", DEEPLINK_PATH, "", datetime);
    }

    private String expectedRequestBody() {
        return "{\"coupangUrls\":[\"" + RAW_URL + "\"],\"subId\":\"booktimer-channel\"}";
    }

    @Test
    @DisplayName("rCode=0 + data[0].shortenUrl → 추적링크를 돌려주고, 요청 헤더·바디가 정확히 실린다")
    void toTrackingLink_success_returnsShortenUrlAndSendsCorrectRequest() {
        server.expect(requestTo(DEEPLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuthorizationHeader()))
                .andExpect(header("X-Requested-By", "booktimer"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expectedRequestBody()))
                .andRespond(withSuccess(
                        "{\"rCode\":\"0\",\"data\":[{\"shortenUrl\":\"https://link.coupang.com/a/abc123\"}]}",
                        MediaType.APPLICATION_JSON));

        Optional<String> result = client().toTrackingLink(RAW_URL);

        assertThat(result).contains("https://link.coupang.com/a/abc123");
        server.verify();
    }

    @Test
    @DisplayName("rCode≠0이면 빈 값 — 호출부가 raw URL로 폴백한다")
    void toTrackingLink_nonZeroRCode_returnsEmpty() {
        server.expect(requestTo(DEEPLINK_URL))
                .andRespond(withSuccess("{\"rCode\":\"1\",\"rMessage\":\"invalid\"}", MediaType.APPLICATION_JSON));

        assertThat(client().toTrackingLink(RAW_URL)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("429 Too Many Requests → 빈 값(예외를 삼켜 폴백 유도)")
    void toTrackingLink_rateLimited_returnsEmpty() {
        server.expect(requestTo(DEEPLINK_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client().toTrackingLink(RAW_URL)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("5xx 서버 오류 → 빈 값")
    void toTrackingLink_serverError_returnsEmpty() {
        server.expect(requestTo(DEEPLINK_URL))
                .andRespond(withServerError());

        assertThat(client().toTrackingLink(RAW_URL)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("같은 URL을 2번 요청해도 서버엔 1번만 도달한다(인메모리 캐시 — rate limit 방어)")
    void toTrackingLink_cachesRepeatedUrl() {
        server.expect(ExpectedCount.once(), requestTo(DEEPLINK_URL))
                .andRespond(withSuccess(
                        "{\"rCode\":\"0\",\"data\":[{\"shortenUrl\":\"https://link.coupang.com/a/cached\"}]}",
                        MediaType.APPLICATION_JSON));

        CoupangDeeplinkClient client = client();
        Optional<String> first = client.toTrackingLink(RAW_URL);
        Optional<String> second = client.toTrackingLink(RAW_URL);

        assertThat(first).contains("https://link.coupang.com/a/cached");
        assertThat(second).contains("https://link.coupang.com/a/cached");
        server.verify(); // ExpectedCount.once() — 2번째 호출이 실제 요청을 냈다면 여기서 실패한다
    }

    @Test
    @DisplayName("키 미설정(dark-launch)이면 API 호출 자체를 시도하지 않는다")
    void toTrackingLink_disabled_noApiCallAttempted() {
        properties.setAccessKey("not-configured");
        properties.setSecretKey("not-configured");
        // 서버에 기대(expect)를 등록하지 않음 — 실제 요청이 나가면 server.verify()가 실패한다.

        assertThat(client().toTrackingLink(RAW_URL)).isEmpty();
        server.verify();
    }
}
