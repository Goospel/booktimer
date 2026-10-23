package com.booktimer.auth;

import com.booktimer.auth.IdTokenVerifier.GoogleIdentity;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Google · 카카오 ID 토큰 검증(REQ-78 · 80) — 서명 · 발급자 · 대상(원소 하나) · azp · 만료 · 발급 10분 · 클레임이 <b>모두</b>
 * 맞아야 신원이 나온다. {@link AppleIdentityVerifierTest}처럼 테스트 RSA 키로 서명하고 JWKS 응답만 바꿔 끼운
 * <b>운영 디코더 조립</b>을 그대로 잰다 — 검증기 한 줄이 빠져도 여기서 깨져야 한다.
 */
class IdTokenVerifierTest {

    private static final String CLIENT = "123-ios.apps.googleusercontent.com";
    private static final String APP_KEY = "kakao-native-app-key-1";
    private static final String GOOGLE_ISS = "https://accounts.google.com";
    private static final String KAKAO_ISS = "https://kauth.kakao.com";
    private static final String GOOGLE_SUB = "109876543210987654321";
    private static final String KAKAO_SUB = "4012345678";

    private static final KeyPair KEY = rsa();
    private static final KeyPair OTHER = rsa();

    private final IdTokenVerifier verifier = new IdTokenVerifier(
            IdTokenVerifier.googleDecoder(CLIENT, jwks("https://www.googleapis.com/oauth2/v3/certs")),
            IdTokenVerifier.kakaoDecoder(APP_KEY, jwks("https://kauth.kakao.com/.well-known/jwks.json")));

    private static KeyPair rsa() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWKS 주소는 리터럴로 — 상수를 참조하면 주소가 바뀌어도 초록이다. */
    private static RestTemplate jwks(String uri) {
        RestTemplate rest = new RestTemplate();
        String body = new JWKSet(new RSAKey.Builder((RSAPublicKey) KEY.getPublic()).keyID("k1").build()).toString();
        MockRestServiceServer.bindTo(rest).build()
                .expect(ExpectedCount.manyTimes(), requestTo(uri))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return rest;
    }

    private static String sign(KeyPair signer, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 통과하는 구글 토큰에서 한 군데만 바꾼다. */
    private static String google(Consumer<JWTClaimsSet.Builder> change) {
        return google(KEY, change);
    }

    private static String google(KeyPair signer, Consumer<JWTClaimsSet.Builder> change) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(GOOGLE_ISS).audience(CLIENT).claim("azp", CLIENT).subject(GOOGLE_SUB)
                .claim("email", "reader@gmail.com").claim("email_verified", true).claim("name", "읽는사람")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)));
        change.accept(b);
        return sign(signer, b.build());
    }

    private static String kakao(Consumer<JWTClaimsSet.Builder> change) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(KAKAO_ISS).audience(APP_KEY).subject(KAKAO_SUB)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)));
        change.accept(b);
        return sign(KEY, b.build());
    }

    private static Consumer<JWTClaimsSet.Builder> issuedSecondsAgo(long seconds) {
        return b -> b.issueTime(Date.from(Instant.now().minusSeconds(seconds)));
    }

    // ── Google ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-78 · Google 토큰은 서명·발급자·대상·만료·발급 시각·email_verified가 모두 맞을 때만 sub와 이메일과 hd를 준다(hd 없는 토큰은 null)")
    void googleYieldsIdentityOnlyWhenEverythingHolds() {
        assertThat(verifier.verifyGoogle(google(b -> { })))
                .contains(new GoogleIdentity(GOOGLE_SUB, "reader@gmail.com", "읽는사람", null));
        assertThat(verifier.verifyGoogle(google(b -> b.claim("hd", "school.ac.kr"))))
                .map(GoogleIdentity::hd).contains("school.ac.kr");

        // 양성 대조 — 통과해야 하는 꼴
        assertThat(verifier.verifyGoogle(google(b -> b.issuer("accounts.google.com")))).as("scheme 없는 발급자 꼴").isPresent();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("azp", null)))).as("azp 없음").isPresent();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email_verified", "true")))).as("문자열 \"true\"").isPresent();
        assertThat(verifier.verifyGoogle(google(issuedSecondsAgo(540)))).as("9분 전 발급").isPresent();

        assertThat(verifier.verifyGoogle(google(b -> b.audience("other-client")))).as("aud 다름").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.issuer("https://evil.example")))).as("iss 다름").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.issueTime(Date.from(Instant.now().minusSeconds(7200)))
                .expirationTime(Date.from(Instant.now().minusSeconds(3600)))))).as("만료").isEmpty();
        assertThat(verifier.verifyGoogle(google(OTHER, b -> { }))).as("다른 키").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email_verified", false)))).as("email_verified 거짓").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email_verified", "false")))).as("email_verified \"false\"").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email_verified", null)))).as("email_verified 없음").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email", null)))).as("email 없음").isEmpty();
        assertThat(verifier.verifyGoogle(google(issuedSecondsAgo(660)))).as("iat 11분 전").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.issueTime(Date.from(Instant.now().plusSeconds(120))))))
                .as("iat 2분 뒤").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.issueTime(null)))).as("iat 없음").isEmpty();
        assertThat(verifier.verifyGoogle("not-a-jwt")).isEmpty();
        assertThat(verifier.verifyGoogle(null)).isEmpty();
        assertThat(verifier.verifyGoogle(" ")).isEmpty();
    }

    @Test
    @DisplayName("REQ-78 · sub가 없거나 255자를 넘으면 거부한다")
    void googleSubShape() {
        assertThat(verifier.verifyGoogle(google(b -> b.subject(null)))).as("sub 없음").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.subject("1".repeat(256))))).as("256자").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.subject("12 34")))).as("공백").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.subject("1".repeat(255))))).as("255자는 통과")
                .map(GoogleIdentity::sub).contains("1".repeat(255));
    }

    @Test
    @DisplayName("REQ-78 · 대상이 둘이거나 azp가 iOS 클라이언트 ID가 아니면 거부한다")
    void googleAudienceAndAzp() {
        assertThat(verifier.verifyGoogle(google(b -> b.audience(List.of(CLIENT, "other-client"))))).as("aud 2개").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("azp", "web-client")))).as("azp 다름").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.audience(List.of(CLIENT))))).as("aud 하나(배열) — 양성 대조").isPresent();
    }

    @Test
    @DisplayName("REQ-78 · 합성 도메인 이메일이면 대소문자와 무관하게 거부한다")
    void googleSyntheticEmailRejected() {
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email", "apple-001.abc@noreply.booktimer.app")))).isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email", "apple-001.abc@NoReply.BookTimer.App")))).as("대문자 꼴").isEmpty();
        assertThat(verifier.verifyGoogle(google(b -> b.claim("email", "someone@booktimer.app")))).as("양성 대조 — 다른 주소").isPresent();
    }

    @Test
    @DisplayName("REQ-78 · 공개키를 못 받으면 거부가 아니라 판정 불가다")
    void unavailableIsNotARejection() {
        JwtDecoder broken = t -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        };
        IdTokenVerifier down = new IdTokenVerifier(broken, broken);
        assertThatThrownBy(() -> down.verifyGoogle(google(b -> { })))
                .isInstanceOf(IdTokenVerificationUnavailableException.class);
        assertThatThrownBy(() -> down.verifyKakao(kakao(b -> { })))
                .isInstanceOf(IdTokenVerificationUnavailableException.class);
    }

    // ── 카카오 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-80 · 카카오 토큰은 대상이 네이티브 앱 키 하나이고 sub가 숫자이며 10분 안에 발급됐을 때만 통과한다")
    void kakaoRules() {
        assertThat(verifier.verifyKakao(kakao(b -> { }))).contains(KAKAO_SUB);

        assertThat(verifier.verifyKakao(kakao(b -> b.audience("another-app-key")))).as("aud 다름").isEmpty();
        assertThat(verifier.verifyKakao(kakao(b -> b.audience(List.of(APP_KEY, "another-app-key"))))).as("aud 2개").isEmpty();
        assertThat(verifier.verifyKakao(kakao(b -> b.subject("abc123")))).as("sub 숫자 아님").isEmpty();
        assertThat(verifier.verifyKakao(kakao(b -> b.subject("1".repeat(21))))).as("sub 21자").isEmpty();
        assertThat(verifier.verifyKakao(kakao(issuedSecondsAgo(660)))).as("iat 11분 전").isEmpty();
        assertThat(verifier.verifyKakao(kakao(b -> b.issuer(GOOGLE_ISS)))).as("iss 다름").isEmpty();
        // 구글 토큰을 카카오 문에 — 대상·발급자가 다르다
        assertThat(verifier.verifyKakao(google(b -> { }))).as("구글 토큰").isEmpty();
    }

    @Test
    @DisplayName("설정값이 비면 그 제공자만 꺼진다 — 기동은 되고 아무 토큰도 통과시키지 않는다(네트워크 0)")
    void blankConfigTurnsProviderOff() {
        IdTokenVerifier off = new IdTokenVerifier("", " ");
        assertThat(off.isGoogleConfigured()).isFalse();
        assertThat(off.isKakaoConfigured()).isFalse();
        assertThat(off.verifyGoogle(google(b -> { }))).isEmpty();
        assertThat(off.verifyKakao(kakao(b -> { }))).isEmpty();

        IdTokenVerifier googleOnly = new IdTokenVerifier(CLIENT, "");
        assertThat(googleOnly.isGoogleConfigured()).isTrue(); // 양성 대조 — 값이 있으면 그 제공자만 켜진다
        assertThat(googleOnly.isKakaoConfigured()).isFalse();
    }
}
