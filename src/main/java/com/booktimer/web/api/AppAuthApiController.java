package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleVerificationUnavailableException;
import com.booktimer.email.AppLinkNotificationService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AppleLinkConflictException;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 아이폰 앱 「책생글」 신원 API — {@code POST /api/app/auth/apple} 하나에 의도 셋(LOGIN · REGISTER · LINK, v1 REQ-02).
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

    public AppAuthApiController(AppleIdentityVerifier verifier,
                                AppleUserProvisioningService provisioningService,
                                TossLinkCodeService linkCodeService,
                                ApiTokenService apiTokenService,
                                RateLimitService rateLimitService,
                                AppLinkNotificationService linkNotifier) {
        this.verifier = verifier;
        this.provisioningService = provisioningService;
        this.linkCodeService = linkCodeService;
        this.apiTokenService = apiTokenService;
        this.rateLimitService = rateLimitService;
        this.linkNotifier = linkNotifier;
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
        return new AppAuthResponse(true, apiTokenService.issue(user), created, user.isWebAccount(), user.getId());
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
     * @param registered 이 Apple 신원에 계정이 있는가(false면 token·userId가 없다)
     * @param created    이번 호출이 계정을 만들었는가(REGISTER에서만 true 가능)
     * @param webAccount 원 가입이 아이폰 앱이 아닌가 — 앱의 삭제 화면 분기(true면 웹 설정으로 보낸다)
     * @param userId     기기 초안 칸을 나누는 키(v1 REQ-38)
     */
    public record AppAuthResponse(boolean registered, String token, boolean created, boolean webAccount, Long userId) {

        static AppAuthResponse unregistered() {
            return new AppAuthResponse(false, null, false, false, null);
        }
    }
}
