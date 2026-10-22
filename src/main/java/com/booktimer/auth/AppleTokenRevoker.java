package com.booktimer.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 아이폰 앱 탈퇴 때 Apple 쪽 로그인 연결을 철회한다 — 인가코드 → refresh_token 교환 → 즉시 철회(Apple 권고, v1 REQ-07).
 *
 * <p>탈퇴의 <b>부수 작업</b>이라 어떤 실패도 예외로 내보내지 않는다 — false를 돌려주고 WARN을 남기며, 탈퇴는 그대로 진행한다.
 * 실패를 「성공」 모양으로 감추지 않도록 응답은 받은 바이트로 판정한다: 상태·content-type·본문 앞 120자를 로그에 싣는다.
 *
 * <p>키(팀 ID · 키 ID · .p8)가 하나라도 없으면(not-configured) 네트워크를 부르지 않는다 — 키가 없어도 서버는 뜬다.
 */
@Component
public class AppleTokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenRevoker.class);

    private static final String APPLE = "https://appleid.apple.com";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final String bundleId;
    private final String p8Base64;
    private final RestClient http;
    private final Clock clock;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    public AppleTokenRevoker(@Value("${booktimer.apple.team-id:not-configured}") String teamId,
                             @Value("${booktimer.apple.signin-key-id:not-configured}") String keyId,
                             @Value("${booktimer.apple.bundle-id:not-configured}") String bundleId,
                             @Value("${booktimer.apple.signin-p8-base64:not-configured}") String p8Base64,
                             Clock clock) {
        this(teamId, keyId, bundleId, p8Base64, RestClient.builder().requestFactory(timeouts()).build(), clock);
    }

    /** 테스트 — {@code MockRestServiceServer}에 묶은 RestClient. */
    AppleTokenRevoker(String teamId, String keyId, String bundleId, String p8Base64, RestClient http, Clock clock) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.bundleId = bundleId;
        this.p8Base64 = p8Base64;
        this.http = http;
        this.clock = clock;
    }

    /** @return 교환·철회가 둘 다 성공하면 true. 키 없음·코드 없음·어떤 실패든 false(예외 없음) */
    public boolean revoke(String authorizationCode) {
        if (!AppleIdentityVerifier.configured(teamId) || !AppleIdentityVerifier.configured(keyId)
                || !AppleIdentityVerifier.configured(bundleId) || !AppleIdentityVerifier.configured(p8Base64)) {
            log.warn("apple-revoke skipped: signin key not configured");
            return false;
        }
        if (authorizationCode == null || authorizationCode.isBlank()) {
            log.warn("apple-revoke skipped: no authorization code");
            return false;
        }
        try {
            String secret = clientSecret(teamId, keyId, bundleId, parseKey(p8Base64), clock.instant());
            String refreshToken = exchange(authorizationCode, secret);
            if (refreshToken == null) {
                return false;
            }
            MultiValueMap<String, String> form = form(secret);
            form.add("token", refreshToken);
            form.add("token_type_hint", "refresh_token");
            ResponseEntity<String> revoked = post("/auth/revoke", form);
            if (!revoked.getStatusCode().is2xxSuccessful()) {
                log.warn("apple-revoke failed: revoke {}", describe(revoked));
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("apple-revoke failed: {}", e.toString());
            return false;
        }
    }

    /** 인가코드 → refresh_token. 실패·모양이 다른 응답이면 null(이유는 로그에). */
    private String exchange(String authorizationCode, String secret) {
        MultiValueMap<String, String> form = form(secret);
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        ResponseEntity<String> res = post("/auth/token", form);
        MediaType type = res.getHeaders().getContentType();
        if (!res.getStatusCode().is2xxSuccessful() || type == null || !type.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            log.warn("apple-revoke failed: token {}", describe(res));
            return null;
        }
        JsonNode refresh = objectMapper.readTree(res.getBody()).get("refresh_token");
        if (refresh == null || refresh.asText().isBlank()) {
            log.warn("apple-revoke failed: token response has no refresh_token");
            return null;
        }
        return refresh.asText();
    }

    private ResponseEntity<String> post(String path, MultiValueMap<String, String> form) {
        try {
            return http.post().uri(APPLE + path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().toEntity(String.class);
        } catch (RestClientResponseException e) { // 4xx·5xx도 판정 재료다 — 받은 바이트를 그대로 넘긴다
            return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }

    private MultiValueMap<String, String> form(String secret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", bundleId);
        form.add("client_secret", secret);
        return form;
    }

    private static String describe(ResponseEntity<String> res) {
        String body = res.getBody() == null ? "" : res.getBody();
        return "status=" + res.getStatusCode().value() + " type=" + res.getHeaders().getContentType()
                + " body=" + body.substring(0, Math.min(120, body.length()));
    }

    /**
     * Apple 「client_secret」 — ES256 JWT {@code {iss=팀, iat, exp=+5분, aud=https://appleid.apple.com, sub=번들}}, 헤더 kid=키 ID.
     */
    static String clientSecret(String teamId, String keyId, String bundleId, ECPrivateKey key, Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(SECRET_TTL)))
                .audience(APPLE)
                .subject(bundleId)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
        try {
            jwt.sign(new ECDSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("apple client secret signing failed", e);
        }
        return jwt.serialize();
    }

    /**
     * SSM 값 = .p8 파일(PEM)을 {@code base64 -w0}한 한 줄(T-266 — openssl base64는 CRLF를 섞는다). PEM 머리·꼬리를 벗긴
     * PKCS#8 DER을 JDK {@code KeyFactory("EC")}로 읽는다(BouncyCastle 없이). DER을 바로 base64한 값도 받는다.
     */
    static ECPrivateKey parseKey(String p8Base64) {
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(p8Base64.strip());
            String text = new String(decoded, StandardCharsets.US_ASCII);
            byte[] der = text.contains("BEGIN PRIVATE KEY")
                    ? Base64.getMimeDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").strip())
                    : decoded;
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("apple signin p8 is not a PKCS#8 EC key", e);
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
