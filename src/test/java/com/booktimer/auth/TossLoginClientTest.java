package com.booktimer.auth;

import com.booktimer.config.TossProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TossLoginClient — 인가코드 → (generate-token → login-me) 2콜을 한 번에 감싸 신원만 뽑아낸다.
 *
 * <p>mTLS 자체는 단위테스트 대상이 아니다(인증서·핸드셰이크 = PR-0 샌드박스 실측 게이트). 여기서는
 * <b>요청 배선</b>(엔드포인트·바디·accessToken 헤더 전달)과 <b>실패의 401 매핑</b>을 본다 —
 * 토스 4xx를 그대로 흘리면 위조·만료 인가코드가 500으로 새어 나간다.
 */
class TossLoginClientTest {

    private static final String BASE = "https://apps-in-toss-api.example.test";
    private static final String TOKEN_URL = BASE + "/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    private static final String ME_URL = BASE + "/api-partner/v1/apps-in-toss/user/oauth2/login-me";

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

    private TossLoginClient client() {
        return new TossLoginClient(properties, builder.build());
    }

    @Test
    @DisplayName("인가코드 유효: generate-token → login-me 순서로 호출하고 userKey·email을 돌려준다")
    void fetchUserInfo_success() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.authorizationCode").value("code-1"))
                .andExpect(jsonPath("$.referrer").value("ref-1"))
                .andRespond(withSuccess("{\"accessToken\":\"at-1\",\"refreshToken\":\"rt-1\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ME_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer at-1")) // 방금 받은 토큰이 실려야 한다
                .andRespond(withSuccess("{\"userKey\":\"uk-1\",\"email\":\"me@toss.im\"}",
                        MediaType.APPLICATION_JSON));

        TossUserInfo info = client().fetchUserInfo("code-1", "ref-1");

        assertThat(info.userKey()).isEqualTo("uk-1");
        assertThat(info.email()).isEqualTo("me@toss.im");
        server.verify();
    }

    @Test
    @DisplayName("토스 응답이 래핑돼 있어도(success/result 중첩) userKey·email을 찾아낸다")
    void fetchUserInfo_wrappedResponse() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"accessToken\":\"at-2\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ME_URL))
                .andRespond(withSuccess("{\"resultType\":\"SUCCESS\",\"success\":{\"userKey\":\"uk-2\"}}",
                        MediaType.APPLICATION_JSON));

        TossUserInfo info = client().fetchUserInfo("code-2", "ref-2");

        assertThat(info.userKey()).isEqualTo("uk-2");
        assertThat(info.email()).isNull(); // 이메일 동의 안 했으면 없다 — 합성 주소 폴백의 입력
    }

    @Test
    @DisplayName("토스가 4xx(만료·위조 인가코드)면 TossLoginException — 500으로 새지 않는다")
    void fetchUserInfo_tossRejects_throws() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client().fetchUserInfo("bad-code", "ref"))
                .isInstanceOf(TossLoginException.class);
    }

    @Test
    @DisplayName("login-me가 userKey를 안 주면 TossLoginException (신원 없는 로그인 성공 금지)")
    void fetchUserInfo_missingUserKey_throws() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"accessToken\":\"at-3\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ME_URL))
                .andRespond(withSuccess("{\"email\":\"me@toss.im\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().fetchUserInfo("code-3", "ref"))
                .isInstanceOf(TossLoginException.class);
    }
}
