package com.booktimer.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Apple 토큰 철회(v1 REQ-07의 「철회를 시도하고(실패해도 진행)」) — 인가코드 → refresh_token → 철회 두 콜과
 * ES256 client_secret. 철회는 탈퇴의 부수 작업이라 <b>어떤 실패도 예외로 내보내지 않고</b> false로 돌려준다.
 */
class AppleTokenRevokerTest {

    private static final String TEAM = "QHP4SAP927";
    private static final String KEY_ID = "ABC123DEFG";
    private static final String BUNDLE = "com.goospel.chaeksaenggeul";
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
    private static final KeyPair EC = ec();

    private static KeyPair ec() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Apple이 주는 .p8 파일(PEM)을 SSM에 넣듯 base64 한 줄로. */
    private static String p8Base64() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(EC.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }

    private record Rig(AppleTokenRevoker revoker, MockRestServiceServer server) {
    }

    private static Rig rig(String p8) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppleTokenRevoker revoker = new AppleTokenRevoker(TEAM, KEY_ID, BUNDLE, p8, builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Rig(revoker, server);
    }

    @Test
    @DisplayName("client_secret은 ES256 · kid · iss=팀 · sub=번들 · aud=Apple · 5분 수명이고 그 키로 검증된다")
    void clientSecretIsVerifiableEs256() throws Exception {
        String secret = AppleTokenRevoker.clientSecret(TEAM, KEY_ID, BUNDLE, (ECPrivateKey) EC.getPrivate(), NOW);

        SignedJWT jwt = SignedJWT.parse(secret);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) EC.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(BUNDLE);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    @DisplayName("인가코드를 refresh_token으로 바꾼 뒤 그 토큰을 철회한다 — 둘 다 성공하면 true")
    void exchangesThenRevokes() {
        Rig rig = rig(p8Base64());
        rig.server().expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=auth-code-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=" + BUNDLE)))
                .andRespond(withSuccess("{\"access_token\":\"a\",\"refresh_token\":\"r-tok\",\"id_token\":\"x\"}",
                        MediaType.APPLICATION_JSON));
        rig.server().expect(requestTo("https://appleid.apple.com/auth/revoke")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("token=r-tok")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("token_type_hint=refresh_token")))
                .andRespond(withSuccess());

        assertThat(rig.revoker().revoke("auth-code-1")).isTrue();
        rig.server().verify();
    }

    @Test
    @DisplayName("교환이 4xx이거나 HTML이 오면(200이라도) 철회를 부르지 않고 false — 예외를 밖으로 내지 않는다")
    void failuresAreFalseNotThrown() {
        Rig bad = rig(p8Base64());
        bad.server().expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));
        assertThat(bad.revoker().revoke("expired")).isFalse();
        bad.server().verify();

        Rig html = rig(p8Base64());
        html.server().expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("<html>challenge</html>", MediaType.TEXT_HTML));
        assertThat(html.revoker().revoke("c")).isFalse();
        html.server().verify(); // 철회 콜은 기대하지 않았다 — 불렸으면 여기서 깨진다
    }

    @Test
    @DisplayName("키가 아직 없으면(not-configured) 네트워크를 부르지 않고 false — 서버 기동도 막지 않는다")
    void notConfiguredSkips() {
        Rig off = rig("not-configured");
        assertThat(off.revoker().revoke("code")).isFalse();
        off.server().verify(); // 기대한 요청 0 — 무엇이든 나갔으면 깨진다

        Rig noCode = rig(p8Base64());
        assertThat(noCode.revoker().revoke(null)).isFalse();
        noCode.server().verify();
    }
}
