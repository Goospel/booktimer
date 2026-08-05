package com.booktimer.auth;

import com.booktimer.config.TossProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 토스 로그인 서버 연동 — 인가코드로 신원({@link TossUserInfo})만 확인하고 끝낸다 (설계 §2.6).
 *
 * <p>흐름: {@code POST .../oauth2/generate-token}(인가코드 → accessToken) → {@code GET .../oauth2/login-me}
 * (accessToken → userKey·email). <b>토스의 accessToken/refreshToken은 저장하지 않는다</b> — 우리 세계의
 * 인증은 자체 {@link ApiTokenService} 토큰이 담당하므로, 토스 토큰 갱신 관리 부담을 지지 않는다(설계 §2.1).
 *
 * <p><b>mTLS</b>: 토스 파트너 API는 클라이언트 인증서를 요구한다. 인증서는 SSL 번들
 * ({@code spring.ssl.bundle.pem.<name>})로 주입하고, 번들이 없으면 <b>호출 시점에만</b> 실패한다 —
 * 인증서가 아직 없는 개발·테스트 환경에서도 앱은 정상 기동해야 하기 때문이다(지연 초기화).
 *
 * <p>응답 파싱은 필드명을 재귀 탐색({@link JsonNode#findValue})한다. 토스가 응답을 래핑하는지
 * ({@code {"success":{...}}} 등) 여부는 PR-0 샌드박스에서 확정되는데, 래핑 형태가 무엇이든 필드명만 맞으면
 * 읽히게 해 두면 그 불확실성이 이 클래스 밖으로 새지 않는다.
 */
@Component
public class TossLoginClient {

    private static final String TOKEN_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    private static final String ME_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/login-me";

    private final TossProperties properties;
    private final SslBundles sslBundles;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /** 지연 생성한 mTLS RestClient(인증서 없이도 기동해야 하므로 첫 호출 때 만든다). */
    private volatile RestClient restClient;

    @Autowired
    public TossLoginClient(TossProperties properties, SslBundles sslBundles) {
        this.properties = properties;
        this.sslBundles = sslBundles;
    }

    /** 테스트 전용 — {@link org.springframework.test.web.client.MockRestServiceServer}로 바인딩한 RestClient 주입. */
    TossLoginClient(TossProperties properties, RestClient restClient) {
        this.properties = properties;
        this.sslBundles = null;
        this.restClient = restClient;
    }

    /**
     * 인가코드로 토스 사용자 신원을 확인한다.
     *
     * @param authorizationCode 미니앱 {@code appLogin()}이 준 인가코드(10분·일회성)
     * @param referrer          {@code appLogin()}이 함께 준 referrer
     * @return 확인된 신원(userKey는 항상 존재, email은 없을 수 있음)
     * @throws TossLoginException 인가코드가 유효하지 않거나 토스 API 호출·파싱에 실패한 경우(→ 401)
     */
    public TossUserInfo fetchUserInfo(String authorizationCode, String referrer) {
        JsonNode tokenResponse = post(TOKEN_PATH, Map.of(
                "authorizationCode", nullToEmpty(authorizationCode),
                "referrer", nullToEmpty(referrer)));
        String accessToken = text(tokenResponse, "accessToken");
        if (accessToken == null) {
            throw new TossLoginException("toss generate-token 응답에 accessToken이 없다");
        }

        JsonNode me = get(ME_PATH, accessToken);
        String userKey = text(me, "userKey");
        if (userKey == null) {
            // 신원 없는 로그인 성공은 있을 수 없다 — 여기서 막지 않으면 userKey=null 계정이 만들어진다.
            throw new TossLoginException("toss login-me 응답에 userKey가 없다");
        }
        return new TossUserInfo(userKey, text(me, "email"));
    }

    private JsonNode post(String path, Map<String, String> body) {
        try {
            return read(restClient().post()
                    .uri(properties.getApiBaseUrl() + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class));
        } catch (TossLoginException e) {
            throw e;
        } catch (Exception e) {
            throw new TossLoginException("toss API 호출 실패: " + path, e);
        }
    }

    private JsonNode get(String path, String accessToken) {
        try {
            return read(restClient().get()
                    .uri(properties.getApiBaseUrl() + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class));
        } catch (TossLoginException e) {
            throw e;
        } catch (Exception e) {
            throw new TossLoginException("toss API 호출 실패: " + path, e);
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            throw new TossLoginException("toss API 응답이 비어 있다");
        }
        return objectMapper.readTree(json);
    }

    /** 래핑 형태와 무관하게 필드명으로 값을 찾는다(없으면 null). */
    private static String text(JsonNode root, String field) {
        JsonNode value = root.findValue(field);
        return (value == null || value.isNull() || value.asText().isBlank()) ? null : value.asText();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * mTLS RestClient를 지연 생성한다 — SSL 번들에서 클라이언트 인증서를 읽어 {@code SSLContext}를 만들고
     * JDK HttpClient에 실어 준다(새 의존성 0). 번들이 없으면 여기서 {@link TossLoginException}이 나며,
     * 컨트롤러가 401로 매핑한다(기동은 막지 않는다).
     */
    private RestClient restClient() {
        RestClient local = this.restClient;
        if (local == null) {
            synchronized (this) {
                local = this.restClient;
                if (local == null) {
                    local = buildMutualTlsClient();
                    this.restClient = local;
                }
            }
        }
        return local;
    }

    private RestClient buildMutualTlsClient() {
        String bundleName = properties.getSslBundle();
        try {
            SslBundle bundle = sslBundles.getBundle(bundleName);
            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(bundle.createSslContext())
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(10));
            return RestClient.builder().requestFactory(factory).build();
        } catch (Exception e) {
            throw new TossLoginException("토스 mTLS SSL 번들을 준비하지 못했다: " + bundleName, e);
        }
    }
}
