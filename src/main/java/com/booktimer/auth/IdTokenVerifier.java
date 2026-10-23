package com.booktimer.auth;

import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 아이폰 앱이 보낸 Google · 카카오 ID 토큰을 검증한다(REQ-78 · 80). {@link AppleIdentityVerifier}의 사본 패턴이다 —
 * Apple 클래스는 구조째 고치지 않는다(설계자 판단, ponytail: 합치는 것은 필요해질 때).
 *
 * <p>Apple과 다른 점: 대상 검사는 {@code aud}가 <b>원소 하나</b>이고 그 값이 기대값일 때만 통과한다(Apple의 {@code contains}는
 * 대상이 여럿인 토큰도 통과시킨다). 구글은 {@code azp}가 있으면 iOS 클라이언트 ID와 같아야 하고, 발급자 두 꼴을 받는다.
 * 세 제공자 모두 발급 10분 이내만 받는다({@link IssuedRecentlyValidator}).
 *
 * <p>제공자마다 설정값(구글 iOS 클라이언트 ID · 카카오 네이티브 앱 키)이 비면 그 제공자만 꺼진 채로 뜬다 — 디코더를 만들지 않고
 * 네트워크도 부르지 않는다. 컨트롤러는 {@link #isGoogleConfigured()} · {@link #isKakaoConfigured()}로 503을 낸다.
 */
@Component
public class IdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(IdTokenVerifier.class);

    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    static final String KAKAO_JWKS_URI = "https://kauth.kakao.com/.well-known/jwks.json";
    static final String KAKAO_ISSUER = "https://kauth.kakao.com";

    /** 구글 sub — 「대소문자를 가리는 ASCII 최대 255자」(OIDC 문서). 보이는 ASCII만. */
    private static final Pattern GOOGLE_SUB = Pattern.compile("[\\x21-\\x7E]{1,255}");
    /** 카카오 sub = 회원번호(숫자). */
    private static final Pattern KAKAO_SUB = Pattern.compile("[0-9]{1,20}");

    /** 검증을 통과한 구글 신원. {@code name} · {@code hd}는 없으면 null. */
    public record GoogleIdentity(String sub, String email, String name, String hd) {
    }

    /** null이면 그 제공자는 꺼짐. */
    private final JwtDecoder google;
    private final JwtDecoder kakao;

    @Autowired
    public IdTokenVerifier(@Value("${booktimer.google.ios-client-id:}") String googleIosClientId,
                           @Value("${booktimer.kakao.native-app-key:}") String kakaoNativeAppKey) {
        this(AppleIdentityVerifier.configured(googleIosClientId)
                        ? googleDecoder(googleIosClientId.strip(), AppleIdentityVerifier.appleRest()) : null,
                AppleIdentityVerifier.configured(kakaoNativeAppKey)
                        ? kakaoDecoder(kakaoNativeAppKey.strip(), AppleIdentityVerifier.appleRest()) : null);
    }

    /** 테스트 — 디코더를 바꿔 끼운다(운영 조립은 {@link #googleDecoder} · {@link #kakaoDecoder}로 만든다). */
    IdTokenVerifier(JwtDecoder google, JwtDecoder kakao) {
        this.google = google;
        this.kakao = kakao;
    }

    public boolean isGoogleConfigured() {
        return google != null;
    }

    public boolean isKakaoConfigured() {
        return kakao != null;
    }

    /**
     * @return 모든 조건을 통과하면 (sub, 이메일, 이름, hd), 위조·만료·형식·클레임 위반이면 빈 값
     * @throws IdTokenVerificationUnavailableException 판정 자체를 못 했을 때(공개키 목록을 못 받음 등)
     */
    public Optional<GoogleIdentity> verifyGoogle(String idToken) {
        Optional<Jwt> decoded = decode(google, idToken, "google");
        if (decoded.isEmpty()) {
            return Optional.empty();
        }
        Jwt jwt = decoded.get();
        String sub = jwt.getSubject();
        if (sub == null || !GOOGLE_SUB.matcher(sub).matches()) {
            return rejected("google", "sub format");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return rejected("google", "email missing");
        }
        Object verified = jwt.getClaims().get("email_verified");
        if (!(Boolean.TRUE.equals(verified) || "true".equals(verified))) {
            return rejected("google", "email not verified");
        }
        if (User.isSyntheticEmailAddress(email)) {
            return rejected("google", "synthetic email"); // 합성 계정(Apple · 카카오 · 토스)이 구글로 열리지 않게 — REQ-116과 같은 규칙
        }
        return Optional.of(new GoogleIdentity(sub, email, jwt.getClaimAsString("name"), jwt.getClaimAsString("hd")));
    }

    /**
     * @return 모든 조건을 통과하면 sub(회원번호), 아니면 빈 값. 카카오 이메일·닉네임은 받지도 쓰지도 않는다(REQ-81).
     * @throws IdTokenVerificationUnavailableException 판정 자체를 못 했을 때
     */
    public Optional<String> verifyKakao(String idToken) {
        Optional<Jwt> decoded = decode(kakao, idToken, "kakao");
        if (decoded.isEmpty()) {
            return Optional.empty();
        }
        String sub = decoded.get().getSubject();
        if (sub == null || !KAKAO_SUB.matcher(sub).matches()) {
            return rejected("kakao", "sub format");
        }
        return Optional.of(sub);
    }

    private static Optional<Jwt> decode(JwtDecoder decoder, String idToken, String provider) {
        if (decoder == null || idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(decoder.decode(idToken));
        } catch (BadJwtException e) {
            log.info("{} id token rejected: {}", provider, e.getMessage()); // 원인만 — 토큰·sub·이메일 원문은 남기지 않는다
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("{} id token could not be verified: {}", provider, e.getMessage());
            throw new IdTokenVerificationUnavailableException(provider + " token verification unavailable", e);
        }
    }

    private static <T> Optional<T> rejected(String provider, String reason) {
        log.info("{} id token rejected: {}", provider, reason);
        return Optional.empty();
    }

    /** 대상이 원소 하나이고 그 값이 기대값일 때만. */
    private static OAuth2TokenValidator<Jwt> singleAudience(String expected) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.size() == 1 && expected.equals(aud.getFirst()));
    }

    /** 발급자 집합 — 변환 결과가 문자열이든 URL이든 문자열로 비교한다. */
    private static OAuth2TokenValidator<Jwt> issuerIn(Set<String> issuers) {
        return new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && issuers.contains(iss.toString()));
    }

    /** 운영 디코더 조립 — 구글 JWKS 서명 + 만료 + 발급자 두 꼴 + 대상 하나 + azp(없거나 같음) + 발급 10분. */
    static JwtDecoder googleDecoder(String iosClientId, RestOperations rest) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).restOperations(rest).build();
        // JwtClaimValidator는 클레임이 없으면 실패시킨다 — azp는 「없어도 된다」라 직접 판정한다.
        OAuth2TokenValidator<Jwt> azp = token -> {
            Object v = token.getClaims().get("azp");
            return v == null || iosClientId.equals(v.toString()) ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "azp mismatch", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
                issuerIn(GOOGLE_ISSUERS), singleAudience(iosClientId), azp, new IssuedRecentlyValidator()));
        return decoder;
    }

    /** 운영 디코더 조립 — 카카오 JWKS 서명 + 만료 + 발급자 + 대상 하나(네이티브 앱 키) + 발급 10분. */
    static JwtDecoder kakaoDecoder(String nativeAppKey, RestOperations rest) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(KAKAO_JWKS_URI).restOperations(rest).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
                issuerIn(Set.of(KAKAO_ISSUER)), singleAudience(nativeAppKey), new IssuedRecentlyValidator()));
        return decoder;
    }
}
