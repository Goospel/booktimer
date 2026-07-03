package com.booktimer.book;

import com.booktimer.config.CoupangDeeplinkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 쿠팡 파트너스 딥링크 API 클라이언트 — raw 쿠팡 URL을 정식 추적링크({@code shortenUrl})로 변환한다.
 *
 * <p>키 미설정(dark-launch, {@link CoupangDeeplinkProperties#isEnabled()} false)이거나 API 실패·429/5xx면
 * {@link Optional#empty()}를 돌려주고, 호출부({@code BookService})가 raw URL로 폴백한다 — 최악에도
 * "추적 안 됨" 이상으로 나빠지지 않는다(graceful degrade). 같은 URL은 인메모리 캐시로 재변환하지 않는다
 * (rate limit 방어, 옵션 A — 재기동 시 초기화되지만 단일/소수 인스턴스라 무해).
 */
@Component
public class CoupangDeeplinkClient {

    private static final Logger log = LoggerFactory.getLogger(CoupangDeeplinkClient.class);
    private static final String DEEPLINK_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/deeplink";

    private final CoupangDeeplinkProperties properties;
    private final CoupangDeeplinkSigner signer;
    private final Clock clock;
    private final RestClient restClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    // AladinBookSearchClient와 동일 이유 — 앱에 Jackson ObjectMapper 빈이 자동 등록되지 않아 자체 인스턴스를 둔다.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    public CoupangDeeplinkClient(CoupangDeeplinkProperties properties, CoupangDeeplinkSigner signer, Clock clock) {
        this(properties, signer, clock, defaultRestClient());
    }

    /**
     * 연결·응답 타임아웃을 명시적으로 둔다 — 이 클라이언트는 {@code BookService}의 @Transactional
     * 메서드(DB 커넥션 보유) 안에서 동기 호출되므로, 무제한 대기는 외부 API 장애 시 스레드풀·커넥션풀을
     * 함께 고갈시킬 수 있다. 타임아웃을 두면 최악에도 아래 catch로 빠르게 격리된다.
     */
    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 테스트 전용 — {@link org.springframework.test.web.client.MockRestServiceServer}로 바인딩한 RestClient 주입. */
    CoupangDeeplinkClient(CoupangDeeplinkProperties properties, CoupangDeeplinkSigner signer, Clock clock,
                          RestClient restClient) {
        this.properties = properties;
        this.signer = signer;
        this.clock = clock;
        this.restClient = restClient;
    }

    /**
     * raw 쿠팡 URL을 파트너스 추적링크로 변환한다. 비활성(키 미설정)·빈 URL이면 API를 호출하지 않고 즉시
     * {@link Optional#empty()}. 캐시 hit면 네트워크 없이 즉시 반환.
     *
     * <p>{@code computeIfAbsent}로 캐시 조회+API 호출+저장을 한 키 기준 원자적으로 묶는다 — 단순
     * get-then-put이면 같은 URL에 대한 동시 요청(예: 인기 공개 책 동시 클릭)이 캐시 미스를 동시에 봐
     * 각자 API를 호출해, 캐시가 막으려는 바로 그 버스트 상황에서 rate limit 방어가 무력화된다.
     * 실패(빈 값)는 캐시에 남기지 않는다 — 다음 호출이 재시도할 수 있어야 한다.
     */
    public Optional<String> toTrackingLink(String coupangUrl) {
        if (!properties.isEnabled() || coupangUrl == null || coupangUrl.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.computeIfAbsent(coupangUrl, this::fetchTrackingLink));
    }

    private String fetchTrackingLink(String coupangUrl) {
        try {
            String datetime = signer.datetime(clock.instant());
            String authHeader = signer.authorizationHeader(
                    properties.getAccessKey(), properties.getSecretKey(), "POST", DEEPLINK_PATH, "", datetime);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("coupangUrls", List.of(coupangUrl));
            requestBody.put("subId", properties.getSubId());
            String requestJson = objectMapper.writeValueAsString(requestBody);

            String responseJson = restClient.post()
                    .uri(properties.getApiBaseUrl() + DEEPLINK_PATH)
                    .header("Authorization", authHeader)
                    .header("X-Requested-By", properties.getRequestedBy())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            return parseShortenUrl(responseJson).orElse(null);
        } catch (Exception e) {
            // 외부 API 장애·429/5xx·인증 실패가 buy 리다이렉트 전체를 깨지 않도록 격리(호출부가 raw URL로
            // 폴백). 예외 클래스명을 명시적으로 남겨 "설정 오류(401 등)"와 "일시적 장애(429/5xx)"를
            // 로그만으로도 구분할 수 있게 한다.
            log.warn("쿠팡 딥링크 변환 실패 — url='{}' [{}]: {}", coupangUrl, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private Optional<String> parseShortenUrl(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(json);
        if (!"0".equals(root.path("rCode").asText())) {
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return Optional.empty();
        }
        String shortenUrl = data.get(0).path("shortenUrl").asText(null);
        return (shortenUrl == null || shortenUrl.isBlank()) ? Optional.empty() : Optional.of(shortenUrl);
    }
}
