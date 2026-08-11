package com.booktimer.toss;

import com.booktimer.config.TossProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
