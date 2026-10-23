package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleVerificationUnavailableException;
import com.booktimer.auth.IdTokenVerificationUnavailableException;
import com.booktimer.auth.IdTokenVerifier;
import com.booktimer.auth.IdTokenVerifier.GoogleIdentity;
import com.booktimer.email.AppLinkNotificationService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AppleLinkConflictException;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.KakaoUserProvisioningService;
import com.booktimer.user.OAuthUserProvisioningService;
import com.booktimer.user.OAuthUserProvisioningService.GoogleLookup;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 아이폰 앱 「책생글」 신원 API — {@code POST /api/app/auth/apple} 하나에 의도 셋(LOGIN · REGISTER · LINK, v1 REQ-02),
 * 그리고 {@code /api/app/auth/google} · {@code /api/app/auth/kakao}(LOGIN · REGISTER — LINK는 400, PR-M REQ-79 · 81).
 *
 * <p>{@link TossAuthApiController}와 같은 「먼저 묻고 만든다」다. 서버에 pending 상태가 없다 — 매 요청이 Apple identity
 * token(24시간)으로 신원을 다시 증명하고, 앱은 LOGIN이 {@code registered:false}면 같은 토큰으로 REGISTER·LINK를 다시 부른다.
 *
 * <p>순서(v1 REQ-04): 키 확인(503) → IP 상한(APP_AUTH) → Apple 검증(401) → 의도 분기. LINK는 sub 상한(APP_LINK)을
 * 코드 소비 <b>전에</b> 센다 — 뒤에 세면 실패한 추측이 카운트되지 않는다.
 *
 * <p>에러 본문은 <b>평문 문구</b>다 — 앱이 그대로 띄운다(v1 REQ-30). 전역 핸들러는 상태 예외를 HTML error 뷰로 바꾸므로
 * 이 컨트롤러 안의 핸들러가 먼저 받는다.
 */
@RestController
public class AppAuthApiController {

    private static final Logger log = LoggerFactory.getLogger(AppAuthApiController.class);

    static final String CODE_INVALID = "코드가 맞지 않거나 시간이 지났어요 — 웹 설정에서 새 코드를 받아 주세요";

    private final AppleIdentityVerifier verifier;
    private final AppleUserProvisioningService provisioningService;
    private final TossLinkCodeService linkCodeService;
    private final ApiTokenService apiTokenService;
    private final RateLimitService rateLimitService;
    private final AppLinkNotificationService linkNotifier;
    private final IdTokenVerifier idTokens;
    private final OAuthUserProvisioningService googleUsers;
    private final KakaoUserProvisioningService kakaoUsers;

    public AppAuthApiController(AppleIdentityVerifier verifier,
                                AppleUserProvisioningService provisioningService,
                                TossLinkCodeService linkCodeService,
                                ApiTokenService apiTokenService,
                                RateLimitService rateLimitService,
                                AppLinkNotificationService linkNotifier,
                                IdTokenVerifier idTokens,
                                OAuthUserProvisioningService googleUsers,
                                KakaoUserProvisioningService kakaoUsers) {
        this.verifier = verifier;
        this.provisioningService = provisioningService;
        this.linkCodeService = linkCodeService;
        this.apiTokenService = apiTokenService;
        this.rateLimitService = rateLimitService;
        this.linkNotifier = linkNotifier;
        this.idTokens = idTokens;
        this.googleUsers = googleUsers;
        this.kakaoUsers = kakaoUsers;
    }

    static final String GOOGLE_MISMATCH = "이 이메일의 계정은 처음 로그인한 구글 계정으로만 열려요 — 그 구글 계정으로 다시 해 주세요";
    private static final String NO_LINK = "이 로그인 방식은 웹 계정 연결을 지원하지 않아요";

    /**
     * 구글 로그인(REQ-79) — LOGIN은 판정이 여는 계정만 주고(빈 고정을 채우는 것이 유일한 쓰기), REGISTER는 웹 구글 로그인과
     * 같은 {@code provision}(정책 ①② · 새 계정 고정)이다. 다른 구글 계정에 고정된 이메일이면 409. LINK는 없다(400).
     */
    @PostMapping("/api/app/auth/google")
    public AppAuthResponse google(@RequestBody IdTokenAuthRequest request, HttpServletRequest http) {
        if (!idTokens.isGoogleConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "구글 로그인이 아직 준비되지 않았어요");
        }
        limit(RateLimitAction.APP_AUTH, http.getRemoteAddr());
        GoogleIdentity id = verified(() -> idTokens.verifyGoogle(request.identityToken()), "구글");
        Intent intent = requireIntent(request.intent());
        AppAuthResponse response;
        try {
            response = switch (intent) {
                case LOGIN -> {
                    GoogleLookup found = googleUsers.lookup(id.sub(), id.email(), id.hd());
                    yield found.user() == null
                            ? AppAuthResponse.unregistered(found.unverifiedWebAccount())
                            : issued(found.user(), false);
                }
                case REGISTER -> {
                    requireAge(request.ageConfirmed());
                    // 먼저 열어 보고(멱등 · created=false) 없을 때만 웹과 같은 provision으로 만든다 — Apple REGISTER와 같은 꼴
                    GoogleLookup found = googleUsers.lookup(id.sub(), id.email(), id.hd());
                    yield found.user() != null
                            ? issued(found.user(), false)
                            : issued(googleUsers.provision(id.sub(), id.email(), id.hd(), id.name(), true), true);
                }
                case LINK -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_LINK);
            };
        } catch (OAuth2AuthenticationException e) {
            if (OAuthUserProvisioningService.GOOGLE_ACCOUNT_MISMATCH.equals(e.getError().getErrorCode())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, GOOGLE_MISMATCH);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 로그인 확인에 실패했어요 — 다시 시도해 주세요");
        }
        logOk("google", response, intent);
        return response;
    }

    /** 카카오 로그인(REQ-81) — 신원은 회원번호 하나. LOGIN은 조회만, REGISTER는 합성 이메일 계정(멱등). LINK는 없다(400). */
    @PostMapping("/api/app/auth/kakao")
    public AppAuthResponse kakao(@RequestBody IdTokenAuthRequest request, HttpServletRequest http) {
        if (!idTokens.isKakaoConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인이 아직 준비되지 않았어요");
        }
        limit(RateLimitAction.APP_AUTH, http.getRemoteAddr());
        String sub = verified(() -> idTokens.verifyKakao(request.identityToken()), "카카오");
        Intent intent = requireIntent(request.intent());
        AppAuthResponse response = switch (intent) {
            case LOGIN -> kakaoUsers.login(sub).map(user -> issued(user, false))
                    .orElseGet(() -> AppAuthResponse.unregistered(false));
            case REGISTER -> {
                requireAge(request.ageConfirmed());
                Optional<User> existing = kakaoUsers.login(sub);
                yield issued(existing.orElseGet(() -> kakaoUsers.register(sub)), existing.isEmpty());
            }
            case LINK -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_LINK);
        };
        logOk("kakao", response, intent);
        return response;
    }

    /** 검증 — 거부 401(앱은 로그인 창을 다시 띄운다) · 판정 불가 503. */
    private static <T> T verified(Supplier<Optional<T>> verification, String providerName) {
        try {
            return verification.get().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    providerName + " 로그인 확인에 실패했어요 — 다시 시도해 주세요"));
        } catch (IdTokenVerificationUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    providerName + " 로그인을 지금 확인할 수 없어요 — 잠시 뒤 다시 해 주세요");
        }
    }

    private static Intent requireIntent(Intent intent) {
        if (intent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "로그인 방식이 빠졌어요");
        }
        return intent;
    }

    private static void requireAge(Boolean ageConfirmed) {
        if (!Boolean.TRUE.equals(ageConfirmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "만 14세 이상인지 먼저 확인해 주세요");
        }
    }

    /** 성공 한 줄 — userId만(토큰 · sub · 이메일 원문 없음, REQ-82). U-43 · U-51의 신호다. */
    private static void logOk(String provider, AppAuthResponse response, Intent intent) {
        if (response.registered()) {
            log.info("app auth {} ok userId={} intent={}", provider, response.userId(), intent);
        }
    }

    @PostMapping("/api/app/auth/apple")
    public AppAuthResponse apple(@RequestBody AppleAuthRequest request, HttpServletRequest http) {
        String sub = verify(request.identityToken(), http);
        if (request.intent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "로그인 방식이 빠졌어요");
        }
        return switch (request.intent()) {
            case LOGIN -> provisioningService.login(sub)
                    .map(user -> issued(user, false))
                    .orElseGet(AppAuthResponse::unregistered);
            case REGISTER -> register(sub, request.ageConfirmed());
            case LINK -> link(sub, request.linkCode());
        };
    }

    /** 만 14세 미만 가입 차단(개인정보 보호법 제22조의2 — 웹 SignupForm.ageConfirmed와 같은 규칙). 있으면 그 계정(멱등). */
    private AppAuthResponse register(String sub, Boolean ageConfirmed) {
        if (!Boolean.TRUE.equals(ageConfirmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "만 14세 이상인지 먼저 확인해 주세요");
        }
        Optional<User> existing = provisioningService.login(sub);
        User user = existing.orElseGet(() -> provisioningService.register(sub));
        return issued(user, existing.isEmpty());
    }

    /** 웹이 발급한 LINK_APPLE 코드의 주인 계정에 sub를 붙인다. 코드 없음·만료·사용됨·목적 불일치는 하나의 400(REQ-03). */
    private AppAuthResponse link(String sub, String linkCode) {
        limit(RateLimitAction.APP_LINK, sub);
        User owner = linkCodeService.consume(linkCode, TossLinkCode.Purpose.LINK_APPLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, CODE_INVALID));
        User linked;
        try {
            linked = provisioningService.link(owner, sub);
        } catch (AppleLinkConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        // 연결은 위 트랜잭션에서 이미 확정됐다 — 알림이 무슨 이유로 실패해도 되돌리지 않는다(REQ-42).
        try {
            linkNotifier.notifyLinked(linked);
        } catch (RuntimeException e) {
            log.warn("app-link notify failed: userId={} {}", linked.getId(), e.toString());
        }
        return issued(linked, false);
    }

    private String verify(String identityToken, HttpServletRequest http) {
        if (!verifier.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "앱 로그인이 아직 준비되지 않았어요");
        }
        limit(RateLimitAction.APP_AUTH, http.getRemoteAddr());
        try {
            return verifier.verify(identityToken)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Apple 로그인 확인에 실패했어요 — 다시 시도해 주세요"));
        } catch (AppleVerificationUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Apple 로그인을 지금 확인할 수 없어요 — 잠시 뒤 다시 해 주세요");
        }
    }

    private void limit(RateLimitAction action, String key) {
        if (!rateLimitService.allow(action, key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦아요 — 잠시 뒤 다시 해 주세요");
        }
    }

    private AppAuthResponse issued(User user, boolean created) {
        return new AppAuthResponse(true, apiTokenService.issue(user), created, user.isWebAccount(), user.getId(),
                user.getAuthProvider().name(), user.getAppleSub() != null, user.getGoogleSub() != null, false);
    }

    /** 앱은 에러 본문을 그대로 띄운다 — 상태 예외의 문구를 평문으로(전역 핸들러의 HTML error 뷰보다 먼저 받는다). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return plainTextError(e);
    }

    static ResponseEntity<String> plainTextError(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getReason());
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    public enum Intent { LOGIN, REGISTER, LINK }

    /**
     * @param identityToken Sign in with Apple이 준 identity token(JWT)
     * @param linkCode      LINK일 때 웹 설정에서 받은 8자 코드
     * @param ageConfirmed  REGISTER일 때 만 14세 이상 확인
     */
    public record AppleAuthRequest(String identityToken, Intent intent, String linkCode, Boolean ageConfirmed) {
    }

    /**
     * @param identityToken 구글 · 카카오가 준 ID 토큰(JWT)
     * @param ageConfirmed  REGISTER일 때 만 14세 이상 확인
     */
    public record IdTokenAuthRequest(String identityToken, Intent intent, Boolean ageConfirmed) {
    }

    /**
     * 필드는 <b>맨 뒤에만</b> 덧붙인다(REQ-84) — {@code webAccount}는 그대로 두고 앱은 {@code authProvider}를 읽는다.
     *
     * @param registered           이 신원에 계정이 있는가(false면 token·userId가 없다)
     * @param created              이번 호출이 계정을 만들었는가(REGISTER에서만 true 가능)
     * @param webAccount           원 가입이 Apple 앱이 아닌가(PR-A 그대로)
     * @param userId               기기 초안 칸을 나누는 키(v1 REQ-38)
     * @param authProvider         원 가입 경로 — 앱이 탈퇴 화면을 고른다
     * @param appleLinked          Apple이 붙어 있는가 — 탈퇴 때 Apple 창을 한 번 더 연다(REQ-111)
     * @param googleLinked         구글 신원이 고정돼 있는가 — LOCAL · TOSS의 탈퇴 갈래(REQ-87)
     * @param unverifiedWebAccount 구글 LOGIN에서 같은 이메일의 미검증 웹 계정이 있다 — REGISTER 전에 앱이 확인을 받는다
     */
    public record AppAuthResponse(boolean registered, String token, boolean created, boolean webAccount, Long userId,
                                  String authProvider, boolean appleLinked, boolean googleLinked,
                                  boolean unverifiedWebAccount) {

        static AppAuthResponse unregistered() {
            return unregistered(false);
        }

        static AppAuthResponse unregistered(boolean unverifiedWebAccount) {
            return new AppAuthResponse(false, null, false, false, null, null, false, false, unverifiedWebAccount);
        }
    }
}
