package com.booktimer.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 아이폰 앱이 보낸 Apple identity token을 검증해 {@code sub}를 꺼낸다(v1 REQ-01).
 *
 * <p>통과 조건은 <b>전부</b>다: Apple JWKS 서명(RS256) · {@code iss=https://appleid.apple.com} · {@code aud=번들 ID} · 만료 전 ·
 * sub가 1~64자 영숫자·점. sub 형식은 합성 이메일 로컬 부분에 그대로 들어가므로 여기서 거른다.
 *
 * <p><b>nonce는 쓰지 않는다</b>(v1 §4.3) — 서버 상태 없는 nonce는 토큰과 원문을 함께 가로챈 쪽을 못 막는다. 앱 ↔ 서버는 TLS
 * 직결이다. ponytail: 재사용 방지가 필요해지면 서버 발급 nonce(로그인 시작 API + 일회 저장)로 올린다.
 *
 * <p>번들 ID가 없으면(not-configured) 꺼진 채로 뜬다 — 디코더를 만들지 않고 네트워크도 부르지 않는다. 컨트롤러는
 * {@link #isConfigured()}로 503을 낸다(키가 없으면 꺼지는 Claude 어댑터 규율과 같다).
 */
@Component
public class AppleIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleIdentityVerifier.class);

    static final String ISSUER = "https://appleid.apple.com";
    static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String NOT_CONFIGURED = "not-configured";
    private static final Pattern SUB_FORMAT = Pattern.compile("[0-9A-Za-z.]{1,64}");

    /** null이면 꺼짐. */
    private final JwtDecoder decoder;

    @Autowired
    public AppleIdentityVerifier(@Value("${booktimer.apple.bundle-id:not-configured}") String bundleId) {
        this(configured(bundleId) ? appleDecoder(bundleId.strip()) : null);
    }

    /** 테스트 — 로컬 키로 만든 디코더(검증기는 {@link #validator}를 달아 운영과 같은 규칙으로). */
    AppleIdentityVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public boolean isConfigured() {
        return decoder != null;
    }

    /**
     * @return 모든 조건을 통과하면 sub, 위조·만료·형식 위반이면 빈 값
     * @throws AppleVerificationUnavailableException 판정 자체를 못 했을 때(Apple 키 목록을 못 받음 등) — 거부와 구분한다
     */
    public Optional<String> verify(String identityToken) {
        if (decoder == null || identityToken == null || identityToken.isBlank()) {
            return Optional.empty();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(identityToken);
        } catch (BadJwtException e) {
            log.info("apple identity token rejected: {}", e.getMessage()); // 원인만 — 토큰·sub 원문은 남기지 않는다
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("apple identity token could not be verified: {}", e.getMessage());
            throw new AppleVerificationUnavailableException("apple token verification unavailable", e);
        }
        String sub = jwt.getSubject();
        if (sub == null || !SUB_FORMAT.matcher(sub).matches()) {
            log.info("apple identity token rejected: sub format");
            return Optional.empty();
        }
        return Optional.of(sub);
    }

    /** 운영 디코더에 다는 검증기 — 기본(만료·nbf, 60초 허용) + 발급자 + 대상(번들 ID). */
    static OAuth2TokenValidator<Jwt> validator(String bundleId) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(bundleId));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), audience);
    }

    /** JWKS 조회는 연결 3초·읽기 5초 — permitAll 경로가 요청 스레드를 오래 붙잡지 않게. 키 목록은 디코더가 캐시한다. */
    private static JwtDecoder appleDecoder(String bundleId) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).restOperations(new RestTemplate(factory)).build();
        decoder.setJwtValidator(validator(bundleId));
        return decoder;
    }

    static boolean configured(String value) {
        return value != null && !value.isBlank() && !NOT_CONFIGURED.equals(value.strip());
    }
}
