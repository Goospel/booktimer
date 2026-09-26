package com.booktimer.toss;

import com.booktimer.config.TossProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 메신저(푸시) 발송 클라이언트 — 요청 배선과 <b>실패 무해화</b>를 본다.
 *
 * <p>이 테스트가 단독으로 잡는 실패: ① 요청 형태가 틀려(엔드포인트·{@code x-toss-user-key} 헤더·
 * {@code templateSetCode}/{@code context} 바디) 토스가 못 알아듣는 것, ② <b>발송 실패가 예외로 새어
 * 완독 처리를 막는 것</b> — 축하 푸시 때문에 책 상태 변경이 500 나면 본말전도다. 그래서 어떤 실패든
 * 던지지 않고 {@code false}여야 한다.
 *
 * <p>mTLS 핸드셰이크는 대상 밖(인증서 = 운영 게이트) — {@code TossLoginClientTest}와 같은 이유로
 * 패키지-프라이빗 생성자에 MockRestServiceServer가 바인딩된 RestClient를 주입한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class TossMessengerClientTest {

    private static final String BASE = "https://apps-in-toss-api.example.test";
    private static final String SEND_URL = BASE + "/api-partner/v1/apps-in-toss/messenger/send-message";

    private TossProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new TossProperties();
        properties.setApiBaseUrl(BASE);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private TossMessengerClient client() {
        return new TossMessengerClient(properties, builder.build());
    }

    @Test
    @DisplayName("발송 성공: send-message에 userKey 헤더·템플릿코드·context를 실어 보내고 true")
    void sendMessage_success() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "uk-1"))
                .andExpect(jsonPath("$.templateSetCode").value("FINISH_CELEBRATION"))
                .andExpect(jsonPath("$.context.bookTitle").value("클린 코드"))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"sentPushCount\":1}}",
                        MediaType.APPLICATION_JSON));

        boolean sent = client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of("bookTitle", "클린 코드"));

        assertThat(sent).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("토스가 200으로 resultType=FAIL을 주면(미승인 템플릿 등) false — 성공으로 오인하지 않는다")
    void sendMessage_resultTypeFail_returnsFalse() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess(
                        "{\"resultType\":\"FAIL\",\"error\":{\"errorCode\":\"5004\",\"reason\":\"미승인 템플릿\"},"
                                + "\"success\":null}",
                        MediaType.APPLICATION_JSON));

        assertThat(client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isFalse();
    }

    @Test
    @DisplayName("토스가 5xx면 예외를 던지지 않고 false — 발송 실패가 완독 처리를 막지 않는다")
    void sendMessage_serverError_returnsFalseWithoutThrowing() {
        server.expect(requestTo(SEND_URL)).andRespond(withServerError());

        assertThat(client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isFalse();
    }

    @Test
    @DisplayName("토스가 4xx면(잘못된 userKey 등) 예외 없이 false")
    void sendMessage_clientError_returnsFalseWithoutThrowing() {
        server.expect(requestTo(SEND_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isFalse();
    }

    // --- REQ-02: 성공 본문 관측 — SUCCESS는 수락이지 도달이 아니다(T-257) ---

    @Test
    @DisplayName("REQ-02 · 성공 응답이면 푸시·알림함 발송 수와 실패 사유를 한 줄로 남기고 true")
    void success_logsDeliveryCounts(CapturedOutput output) {
        server.expect(requestTo(SEND_URL))
                .andExpect(header("x-toss-user-key", "uk-secret-1"))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"sentPushCount\":1,"
                        + "\"sentInboxCount\":1,\"fail\":{\"sentPush\":[],\"sentInbox\":[]}}}", MediaType.APPLICATION_JSON));

        boolean sent = client().sendMessage("uk-secret-1", "FINISH_CELEBRATION", Map.of());

        assertThat(sent).isTrue();
        assertThat(output.getOut()).contains("토스 메시지 발송 결과 (template=FINISH_CELEBRATION): push=1 inbox=1");
        // 음성 판정 전용 — 지금 로그 시그니처엔 키가 들어오지 않아 못 실패한다. 누가 키를 싣는 회귀를 막는 가드.
        assertThat(output.getOut()).doesNotContain("uk-secret-1");
    }

    @Test
    @DisplayName("REQ-02 · 성공인데 푸시·알림함이 모두 0이면 「도달 0」 경고와 실패 사유를 남긴다")
    void success_zeroDelivery_warnsWithReasons(CapturedOutput output) {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"sentPushCount\":0,"
                        + "\"sentInboxCount\":0,\"fail\":{\"sentPush\":[{\"contentId\":\"x\","
                        + "\"reachedFailReason\":\"NOT_AGREED_TEST\"}]}}}", MediaType.APPLICATION_JSON));

        assertThat(client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isTrue();
        assertThat(output.getOut()).contains("도달 0").contains("NOT_AGREED_TEST");
    }

    @Test
    @DisplayName("REQ-02 · success 필드가 없으면 push=-1로 남기고 판정은 true 그대로다")
    void success_missingSuccessField_logsMinusOne(CapturedOutput output) {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        assertThat(client().sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isTrue();
        assertThat(output.getOut()).contains("push=-1");
    }

    @Test
    @DisplayName("REQ-02 · 성공 본문 모양이 깨져도(success가 배열, fail이 문자열) 판정은 true다")
    void success_malformedBody_stillTrue() {
        // 계약 보존 가드 — 로그 처리가 판정을 뒤집으면 목표 달성 폴러(성공만 마킹)가 동의자에게 분마다 재발송한다.
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":[1,2]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"sentPushCount\":1,\"fail\":\"oops\"}}",
                        MediaType.APPLICATION_JSON));

        TossMessengerClient client = client();
        assertThat(client.sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isTrue();
        assertThat(client.sendMessage("uk-1", "FINISH_CELEBRATION", Map.of())).isTrue();
        server.verify();
    }
}
