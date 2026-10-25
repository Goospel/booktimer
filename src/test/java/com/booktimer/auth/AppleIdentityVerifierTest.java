package com.booktimer.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Apple identity token 검증(v1 REQ-01) — 서명·발급자·대상·만료·sub 형식이 <b>모두</b> 맞아야 sub가 나온다.
 *
 * <p>테스트 RSA 키로 서명하고, Apple JWKS 응답만 그 공개키로 바꿔 끼운 <b>운영 디코더 조립</b>을 그대로 잰다 — 검증 규칙이
 * 디코더 설정(검증기 부착)에 있으므로 조립 한 줄이 빠져도 여기서 깨져야 한다.
 */
class AppleIdentityVerifierTest {

    private static final String BUNDLE = "com.goospel.chaeksaenggeul";
    private static final String DEV_BUNDLE = "com.goospel.chaeksaenggeul.dev";
    private static final String ISS = "https://appleid.apple.com";

    private static final KeyPair APPLE = rsa();
    private static final KeyPair OTHER = rsa();

    private final AppleIdentityVerifier verifier = new AppleIdentityVerifier(decoderFor((RSAPublicKey) APPLE.getPublic()));

    private static KeyPair rsa() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtDecoder decoderFor(RSAPublicKey key) {
        return decoderFor(key, null);
    }

    /** 운영 조립({@link AppleIdentityVerifier#appleDecoder(String, String, RestOperations)}) 그대로 — JWKS 응답만 테스트 키로 바꿔 끼운다. */
    private static JwtDecoder decoderFor(RSAPublicKey key, String devBundle) {
        RestTemplate rest = new RestTemplate();
        String jwks = new JWKSet(new RSAKey.Builder(key).keyID("k1").build()).toString();
        MockRestServiceServer.bindTo(rest).build()
                .expect(ExpectedCount.manyTimes(), requestTo("https://appleid.apple.com/auth/keys")) // 상수 참조면 주소가 바뀌어도 초록이다
                .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));
        return AppleIdentityVerifier.appleDecoder(BUNDLE, devBundle, rest);
    }

    private static String token(KeyPair signer, String iss, String aud, Instant exp, String sub) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    // iat는 exp보다 앞에 — iat ≥ exp면 Jwt 생성 단언이 먼저 거부해 만료 사례가 타임스탬프 검증기를 안 잰다
                    .issuer(iss).audience(aud).issueTime(Date.from(exp.minusSeconds(600))).expirationTime(Date.from(exp));
            if (sub != null) {
                claims.subject(sub);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant later() {
        return Instant.now().plusSeconds(600);
    }

    private static final String SUB = "001234.0a1b2c3d4e5f60718293a4b5c6d7e8f9.0123";

    @Test
    @DisplayName("REQ-01 · 서명·발급자·대상·만료가 맞는 Apple 토큰만 sub를 돌려준다")
    void onlyFullyValidTokenYieldsSub() {
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), SUB))).contains(SUB);

        assertThat(verifier.verify(token(APPLE, ISS, "com.goospel.symposium", later(), SUB))).as("aud 다름").isEmpty();
        assertThat(verifier.verify(token(APPLE, "https://evil.example", BUNDLE, later(), SUB))).as("iss 다름").isEmpty();
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, Instant.now().minusSeconds(3600), SUB))).as("만료").isEmpty();
        assertThat(verifier.verify("not-a-jwt")).as("JWT 아님").isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify("  ")).isEmpty();
    }

    /** 발급 시각만 바꾼 토큰 — 만료는 넉넉히 뒤라 발급 시각 검사만 잰다. */
    private static String issuedAt(Instant iat) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).audience(BUNDLE).subject(SUB)
                    .issueTime(Date.from(iat)).expirationTime(Date.from(Instant.now().plusSeconds(86_400))).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) APPLE.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("REQ-110 · 11분 전에 발급된 Apple 토큰은 거부한다")
    void staleAppleTokenRejected() {
        assertThat(verifier.verify(issuedAt(Instant.now().minusSeconds(660)))).isEmpty();
    }

    @Test
    @DisplayName("REQ-110 · 방금 발급된 Apple 토큰은 그대로 통과한다")
    void freshAppleTokenPasses() {
        assertThat(verifier.verify(issuedAt(Instant.now()))).contains(SUB);
        assertThat(verifier.verify(issuedAt(Instant.now().minusSeconds(540)))).as("9분 전").contains(SUB);
    }

    @Test
    @DisplayName("REQ-01 · 다른 키로 서명한 토큰은 거부한다")
    void otherKeySignatureRejected() {
        assertThat(verifier.verify(token(OTHER, ISS, BUNDLE, later(), SUB))).isEmpty();
    }

    @Test
    @DisplayName("REQ-01 · sub가 비었거나 64자를 넘거나 영숫자·점 밖의 글자면 거부한다")
    void subFormatEnforced() {
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), null))).as("sub 없음").isEmpty();
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), "a".repeat(65)))).as("65자").isEmpty();
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), "abc-def"))).as("하이픈").isEmpty();
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), "abc@x"))).as("@").isEmpty();
        assertThat(verifier.verify(token(APPLE, ISS, BUNDLE, later(), "a".repeat(64)))).as("64자는 통과").contains("a".repeat(64));
    }

    @Test
    @DisplayName("Apple 키 목록을 못 받으면(위조가 아니라 장애) 「거부」가 아니라 장애로 던진다")
    void upstreamFailureIsNotARejection() {
        AppleIdentityVerifier broken = new AppleIdentityVerifier(t -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        });
        assertThatThrownBy(() -> broken.verify(token(APPLE, ISS, BUNDLE, later(), SUB)))
                .isInstanceOf(AppleVerificationUnavailableException.class);
    }

    @Test
    @DisplayName("운영 JWKS 조회는 연결 3초·읽기 5초 — permitAll 경로가 요청 스레드를 오래 붙잡지 않게")
    void appleRestHasTimeouts() {
        Object factory = AppleIdentityVerifier.appleRest().getRequestFactory();
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5000);
    }

    @Test
    @DisplayName("번들 ID가 없으면(not-configured) 기동은 되고, 꺼진 상태로 아무 토큰도 통과시키지 않는다 — 네트워크 0")
    void notConfiguredBootsAndRejects() {
        AppleIdentityVerifier off = new AppleIdentityVerifier("not-configured", "");
        assertThat(off.isConfigured()).isFalse();
        assertThat(off.verify(token(APPLE, ISS, BUNDLE, later(), SUB))).isEmpty();

        assertThat(new AppleIdentityVerifier(BUNDLE, "").isConfigured()).isTrue(); // 양성 대조 — 값이 있으면 켜진다(JWKS는 첫 검증 때 받는다)
        assertThat(verifier.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("REQ-163 · 개발 앱 번들 ID를 설정하면 그 aud의 토큰도 sub를 돌려준다 — 운영 번들은 그대로, 다른 앱 · 접두만 같은 번들은 거부")
    void devBundleAudienceAccepted() {
        AppleIdentityVerifier withDev = new AppleIdentityVerifier(decoderFor((RSAPublicKey) APPLE.getPublic(), DEV_BUNDLE));
        assertThat(withDev.verify(token(APPLE, ISS, DEV_BUNDLE, later(), SUB))).contains(SUB);
        assertThat(withDev.verify(token(APPLE, ISS, BUNDLE, later(), SUB))).contains(SUB);
        assertThat(withDev.verify(token(APPLE, ISS, "com.goospel.symposium", later(), SUB))).isEmpty();
        assertThat(withDev.verify(token(APPLE, ISS, DEV_BUNDLE + ".x", later(), SUB))).as("접두만 같은 번들").isEmpty();
    }

    @Test
    @DisplayName("REQ-163 · 개발 앱 번들 ID가 비면 개발 aud 토큰은 거부된다")
    void devBundleAudienceRejectedWhenUnset() {
        assertThat(verifier.verify(token(APPLE, ISS, DEV_BUNDLE, later(), SUB))).isEmpty();
    }

    @Test
    @DisplayName("REQ-163 · 운영 번들 ID가 없으면 개발 값만으로는 켜지지 않는다")
    void devBundleAloneDoesNotEnable() {
        assertThat(new AppleIdentityVerifier("not-configured", DEV_BUNDLE).isConfigured()).isFalse();
        assertThat(new AppleIdentityVerifier(BUNDLE, "").isConfigured()).isTrue(); // 양성 대조
    }

    @Test
    @DisplayName("REQ-163 · 운영 설정 파일의 booktimer.apple.dev-bundle-id가 생성자 @Value로 그대로 들어간다 — 테스트 리소스가 메인 파일을 가려 컨텍스트로는 못 잰다")
    void devBundlePropertyWiredFromMainProperties() throws IOException, NoSuchMethodException {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            props.load(r);
        }
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new PropertiesPropertySource("main", props));
        Parameter[] params = AppleIdentityVerifier.class.getConstructor(String.class, String.class).getParameters();
        String devExpr = params[1].getAnnotation(Value.class).value();
        assertThat(env.resolvePlaceholders(devExpr)).isEqualTo(DEV_BUNDLE);
    }
}
