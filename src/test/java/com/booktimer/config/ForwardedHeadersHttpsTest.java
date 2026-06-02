package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ALB(프록시) 뒤에서의 HTTPS 인식 — forward-headers 종단 테스트 (실제 서버 기동).
 *
 * <p>운영에서 ALB가 TLS를 종료(termination)하고 평문 HTTP로 앱에 전달한다. 앱이 그대로면 자기 주소를
 * http로 인식해, 구글로 보내는 OAuth {@code redirect_uri}를 {@code http://...}로 만든다 → 구글은 https만
 * 허용하므로 {@code redirect_uri_mismatch}가 난다. {@code server.forward-headers-strategy=framework}가
 * {@link org.springframework.web.filter.ForwardedHeaderFilter}를 등록해 X-Forwarded-Proto/Host를 신뢰하면,
 * 앱이 https로 인식해 redirect_uri도 https로 만든다.
 *
 * <p>이 필터는 <b>실제 서블릿 컨테이너</b>가 떠야 등록·동작하므로(MockMvc는 적용하지 않음), 여기서는
 * {@code RANDOM_PORT}로 진짜 서버를 띄우고 실제 HTTP로 X-Forwarded-* 헤더를 보내 동작을 검증한다(N-021).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedHeadersHttpsTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("X-Forwarded-Proto=https면 구글로 보내는 OAuth redirect_uri가 https로 만들어진다 (ALB TLS termination 대응)")
    void forwardedProtoHttps_buildsHttpsRedirectUri() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // 302의 Location을 직접 보려면 따라가면 안 됨
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/authorization/google"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "booktimer.click")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElse("");
        assertThat(location).contains("accounts.google.com");
        // 핵심: ALB 뒤에서도 redirect_uri가 http(localhost)가 아니라 https://booktimer.click로 만들어진다.
        assertThat(location).contains("redirect_uri=https://booktimer.click/login/oauth2/code/google");
    }
}
