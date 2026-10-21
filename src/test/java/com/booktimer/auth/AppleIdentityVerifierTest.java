package com.booktimer.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Apple identity token 검증(v1 REQ-01) — 서명·발급자·대상·만료·sub 형식이 <b>모두</b> 맞아야 sub가 나온다.
 *
 * <p>Apple JWKS 대신 테스트 RSA 키로 서명하고, 운영과 <b>같은 검증기</b>({@link AppleIdentityVerifier#validator})를
 * 공개키 디코더에 단다 — 검증 규칙이 디코더 설정에 있으므로 그 설정을 그대로 재야 한다.
 */
class AppleIdentityVerifierTest {

    private static final String BUNDLE = "com.goospel.chaeksaenggeul";
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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(AppleIdentityVerifier.validator(BUNDLE));
        return decoder;
    }

    private static String token(KeyPair signer, String iss, String aud, Instant exp, String sub) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(iss).audience(aud).issueTime(new Date()).expirationTime(Date.from(exp));
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
    @DisplayName("번들 ID가 없으면(not-configured) 기동은 되고, 꺼진 상태로 아무 토큰도 통과시키지 않는다 — 네트워크 0")
    void notConfiguredBootsAndRejects() {
        AppleIdentityVerifier off = new AppleIdentityVerifier("not-configured");
        assertThat(off.isConfigured()).isFalse();
        assertThat(off.verify(token(APPLE, ISS, BUNDLE, later(), SUB))).isEmpty();

        assertThat(new AppleIdentityVerifier(BUNDLE).isConfigured()).isTrue(); // 양성 대조 — 값이 있으면 켜진다(JWKS는 첫 검증 때 받는다)
        assertThat(verifier.isConfigured()).isTrue();
    }
}
