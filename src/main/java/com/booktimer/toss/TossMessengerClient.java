package com.booktimer.toss;

import com.booktimer.config.TossProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 앱인토스 메신저(서버 발송 푸시) 클라이언트 — 토스 앱의 네이티브 푸시·알림함으로 한 통 보낸다.
 *
 * <p>{@code POST /api-partner/v1/apps-in-toss/messenger/send-message}, 헤더 {@code x-toss-user-key},
 * 바디 {@code {"templateSetCode":..., "context":{...}}}. 문구는 <b>콘솔에서 검수 승인된 템플릿</b>에만
 * 있고 우리는 변수만 채운다.
 *
 * <p><b>절대 던지지 않는다.</b> 호출자(완독 축하)는 사용자 행동의 곁가지라, 발송 실패가 예외로 새면
 * 축하 때문에 완독 처리가 깨진다. 모든 실패는 로그 + {@code false}로 끝난다.
 *
 * <p>mTLS·지연 초기화·SSL 번들은 {@code TossLoginClient}와 동일 계약이다(인증서가 없어도 앱은 뜨고,
 * 호출 시점에만 실패). 타임아웃은 로그인보다 짧게 잡는다 — 완독 응답을 붙잡고 있을 이유가 없다.
 *
 * <p><b>{@code resultType=SUCCESS}는 수락이지 도달이 아니다</b> — 알림동의문 미동의 수신자에게도 SUCCESS가
 * 온다(2026-09-26 확정, T-257). 그래서 성공 본문의 채널별 발송 수·도달 실패 사유를 로그로 남긴다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.toss.messenger.enabled", havingValue = "true")
public class TossMessengerClient {

    private static final Logger log = LoggerFactory.getLogger(TossMessengerClient.class);

    private static final String SEND_PATH = "/api-partner/v1/apps-in-toss/messenger/send-message";

    private final TossProperties properties;
    private final SslBundles sslBundles;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /** 지연 생성한 mTLS RestClient(인증서 없이도 기동해야 하므로 첫 호출 때 만든다). */
    private volatile RestClient restClient;

    @Autowired
    public TossMessengerClient(TossProperties properties, SslBundles sslBundles) {
        this.properties = properties;
        this.sslBundles = sslBundles;
    }

    /** 테스트 전용 — {@link org.springframework.test.web.client.MockRestServiceServer}로 바인딩한 RestClient 주입. */
    TossMessengerClient(TossProperties properties, RestClient restClient) {
        this.properties = properties;
        this.sslBundles = null;
        this.restClient = restClient;
    }

    /**
     * 토스 사용자 한 명에게 템플릿 메시지를 보낸다.
     *
     * @param tossUserKey     대상 사용자의 토스 userKey
     * @param templateSetCode 콘솔에서 검수 승인된 템플릿 코드
     * @param context         템플릿 변수
     * @return 토스가 {@code resultType=SUCCESS}를 준 경우에만 true. 그 외 모든 경우(네트워크·인증서·4xx·5xx·
     *         미승인 템플릿)는 로그만 남기고 false — <b>예외를 던지지 않는다</b>. true는 수락이지 도달이 아니다 —
     *         알림동의문 미동의 수신자에게도 SUCCESS가 온다(2026-09-26 확정).
     */
    public boolean sendMessage(String tossUserKey, String templateSetCode, Map<String, String> context) {
        try {
            String body = restClient().post()
                    .uri(properties.getApiBaseUrl() + SEND_PATH)
                    .header("x-toss-user-key", tossUserKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("templateSetCode", templateSetCode, "context", context))
                    .retrieve()
                    .body(String.class);
            return isSuccess(body, templateSetCode);
        } catch (Exception e) {
            log.warn("토스 메시지 발송 실패 (template={}): {}", templateSetCode, e.toString());
            return false;
        }
    }

    /** 토스는 2xx로도 {@code resultType=FAIL}(미승인 템플릿 5004 등)을 준다 — 상태코드만 보면 성공으로 오인한다. */
    private boolean isSuccess(String body, String templateSetCode) {
        if (body == null || body.isBlank()) {
            log.warn("토스 메시지 발송 응답이 비어 있다 (template={})", templateSetCode);
            return false;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode resultType = root.findValue("resultType");
        if (resultType != null && "SUCCESS".equals(resultType.asText())) {
            try {
                logDelivery(root.path("success"), templateSetCode);
            } catch (RuntimeException e) {
                // 로그는 곁가지다 — 여기서 던지면 sendMessage의 catch가 이미 수락된 발송을 false로 뒤집고,
                // 목표 달성 폴러(성공만 마킹)가 동의자에게 분마다 다시 보낸다.
                log.warn("토스 메시지 발송 결과 기록 실패(판정은 성공 그대로, template={}): {}", templateSetCode, e.toString());
            }
            return true; // SUCCESS는 수락이지 도달이 아니다(T-257)
        }
        log.warn("토스 메시지 발송 거부 (template={}): {}", templateSetCode, body);
        return false;
    }

    /** 성공 본문의 채널별 발송 수·도달 실패 사유 — 유저키는 헤더로만 보내 본문에 없다. */
    private void logDelivery(JsonNode success, String templateSetCode) {
        int push = success.path("sentPushCount").asInt(-1);   // 필드가 없으면 -1(모양이 바뀐 신호)
        int inbox = success.path("sentInboxCount").asInt(-1);
        List<String> reasons = new ArrayList<>();
        success.path("fail").forEach(channel -> channel.forEach(item -> {
            String reason = item.path("reachedFailReason").asString("");
            if (!reason.isBlank()) reasons.add(reason);
        }));
        if (push == 0 && inbox == 0) {
            log.warn("토스 메시지 발송 결과 — 도달 0 (template={}): push={} inbox={} fail={}", templateSetCode, push, inbox, reasons);
        } else {
            log.info("토스 메시지 발송 결과 (template={}): push={} inbox={} fail={}", templateSetCode, push, inbox, reasons);
        }
    }

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
        SslBundle bundle = sslBundles.getBundle(properties.getSslBundle());
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(bundle.createSslContext())
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory).build();
    }
}
