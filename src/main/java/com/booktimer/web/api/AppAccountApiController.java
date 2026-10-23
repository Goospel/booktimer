package com.booktimer.web.api;

import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleTokenRevoker;
import com.booktimer.auth.AppleVerificationUnavailableException;
import com.booktimer.auth.IdTokenVerificationUnavailableException;
import com.booktimer.auth.IdTokenVerifier;
import com.booktimer.auth.IdTokenVerifier.GoogleIdentity;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.AccountService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.GoogleAccountResolver;
import com.booktimer.user.GoogleAccountResolver.GoogleMatch;
import com.booktimer.user.User;
import com.booktimer.user.VerifiedIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 아이폰 앱 탈퇴 {@code POST /api/app/account/delete}(v1 REQ-07 · REQ-83) — Bearer + <b>가입한 방법으로 새로 받은 재확인</b>.
 *
 * <p>순서: {@code provider}(없으면 APPLE — PR-A 본문 그대로)로 새 토큰 검증(400 · 판정 불가 503) → 신원 대조 403
 * (APPLE {@code apple_sub} · KAKAO {@code kakao_sub} · GOOGLE = 구글 판정이 {@code OPEN}이고 그 계정의 id가 지금 사용자의 id) →
 * APPLE · KAKAO로 확인했는데 원 가입 경로가 다르면 409(GOOGLE 갈래엔 없다) → 서버 쪽 외부 정리(Apple 철회) → 서비스가 한 번 더
 * 대조한 뒤 purge. 400 · 403 · 409에서는 <b>아무것도 지우지 않고 외부 호출도 하지 않는다</b>.
 * 카카오 연결 끊기는 서버가 하지 않는다 — 204를 받은 앱이 SDK {@code unlink()}로 한다(설계자 판단 · 어드민 키 없음).
 *
 * <p>⚠️ 401을 쓰지 않는다 — 앱의 401은 「토큰 폐기 + 로그인 화면」이라, 재인증 실패가 로그아웃으로 둔갑한다
 * ({@link MiniappAccountApiController}와 같은 이유).
 */
@RestController
public class AppAccountApiController {

    private static final Logger log = LoggerFactory.getLogger(AppAccountApiController.class);

    private static final String FORBIDDEN = "본인 확인에 실패해 탈퇴를 진행하지 않았어요";

    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final AppleIdentityVerifier verifier;
    private final AppleTokenRevoker revoker;
    private final IdTokenVerifier idTokens;
    private final GoogleAccountResolver googleAccountResolver;

    public AppAccountApiController(CurrentUserService currentUserService,
                                   AccountService accountService,
                                   AppleIdentityVerifier verifier,
                                   AppleTokenRevoker revoker,
                                   IdTokenVerifier idTokens,
                                   GoogleAccountResolver googleAccountResolver) {
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.verifier = verifier;
        this.revoker = revoker;
        this.idTokens = idTokens;
        this.googleAccountResolver = googleAccountResolver;
    }

    /** @return 204 탈퇴 완료 / 400 재확인 실패 · 모르는 provider / 403 신원 불일치 / 409 웹에서 만든 계정 / 401 토큰 없음·무효(체인) */
    @PostMapping("/api/app/account/delete")
    public ResponseEntity<Void> delete(Principal principal, @RequestBody ReauthRequest request) {
        User user = currentUserService.resolve(principal);
        switch (provider(request.provider())) {
            case APPLE -> deleteByApple(user, request);
            case KAKAO -> deleteByKakao(user, request);
            case GOOGLE -> deleteByGoogle(user, request);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모르는 확인 방식이에요");
        }
        return ResponseEntity.noContent().build();
    }

    /** PR-A 그대로 — 웹 계정에 붙인 Apple로 확인하면 409(연결 코드가 새도 지울 수 없다). */
    private void deleteByApple(User user, ReauthRequest request) {
        String sub = verified(() -> verifier.verify(request.identityToken()), "Apple");
        if (user.getAppleSub() == null || !user.getAppleSub().equals(sub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
        }
        if (user.isWebAccount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "웹에서 만든 계정은 웹 설정에서 지울 수 있어요");
        }
        revoker.revoke(request.authorizationCode()); // 실패는 안에서 WARN — 탈퇴는 그대로 간다
        accountService.deleteAppleVerifiedAccount(user, sub); // 서비스가 한 번 더 대조한다
    }

    private void deleteByKakao(User user, ReauthRequest request) {
        String sub = verified(() -> idTokens.verifyKakao(request.identityToken()), "카카오");
        if (user.getKakaoSub() == null || !user.getKakaoSub().equals(sub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
        }
        if (user.getAuthProvider() != AuthProvider.KAKAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "웹에서 만든 계정은 웹 설정에서 지울 수 있어요");
        }
        revokeAppleIfEnclosed(user, request);
        accountService.deleteProviderVerifiedAccount(user, AuthProvider.KAKAO, new VerifiedIdentity(sub, null, null));
    }

    /**
     * 구글 판정이 {@code OPEN}이고 <b>판정한 계정이 지금 사용자</b>일 때만 지운다 — Bearer엔 채널 열이 없어, 연결 코드를 가로채
     * Apple로 Bearer를 얻은 쪽이 자기 구글 토큰을 내면 판정은 그 사람 자신의 계정을 연다(보안 검토 2). 고정은 하지 않는다.
     */
    private void deleteByGoogle(User user, ReauthRequest request) {
        GoogleIdentity id = verified(() -> idTokens.verifyGoogle(request.identityToken()), "구글");
        GoogleMatch match = googleAccountResolver.resolve(id.sub(), id.email(), id.hd());
        if (match.kind() != GoogleAccountResolver.Kind.OPEN || !match.user().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
        }
        revokeAppleIfEnclosed(user, request);
        accountService.deleteProviderVerifiedAccount(user, AuthProvider.GOOGLE,
                new VerifiedIdentity(id.sub(), id.email(), id.hd()));
    }

    /**
     * Apple이 붙은 계정을 구글 · 카카오로 지울 때(REQ-111) — 동봉한 Apple 토큰의 sub가 그 계정의 것일 때만 그 인가코드로 철회한다.
     * 없거나 · 다르거나 · 판정 불가면 철회만 건너뛰고 WARN(userId만 — sub · 토큰 없음). 탈퇴는 그대로 간다.
     */
    private void revokeAppleIfEnclosed(User user, ReauthRequest request) {
        if (user.getAppleSub() == null) {
            return;
        }
        Optional<String> appleSub;
        try {
            appleSub = request.appleIdentityToken() == null ? Optional.empty() : verifier.verify(request.appleIdentityToken());
        } catch (AppleVerificationUnavailableException e) {
            appleSub = Optional.empty();
        }
        if (appleSub.isPresent() && appleSub.get().equals(user.getAppleSub())) {
            revoker.revoke(request.appleAuthorizationCode()); // 실패는 안에서 WARN
        } else {
            log.warn("app delete: apple revoke skipped userId={}", user.getId());
        }
    }

    /** provider 문자열 → APPLE · GOOGLE · KAKAO. 없으면 APPLE(PR-A 호출 그대로), 그 밖은 400. */
    private static AuthProvider provider(String raw) {
        if (raw == null) {
            return AuthProvider.APPLE;
        }
        return switch (raw) {
            case "APPLE" -> AuthProvider.APPLE;
            case "GOOGLE" -> AuthProvider.GOOGLE;
            case "KAKAO" -> AuthProvider.KAKAO;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모르는 확인 방식이에요");
        };
    }

    /** 검증 — 거부 400 · 판정 불가 503(401은 쓰지 않는다). */
    private static <T> T verified(Supplier<Optional<T>> verification, String providerName) {
        try {
            return verification.get().orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, providerName + " 확인에 실패했어요 — 다시 시도해 주세요"));
        } catch (AppleVerificationUnavailableException | IdTokenVerificationUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    providerName + " 로그인을 지금 확인할 수 없어요 — 잠시 뒤 다시 해 주세요");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return AppAuthApiController.plainTextError(e);
    }

    /**
     * @param provider               확인한 제공자 APPLE · GOOGLE · KAKAO(없으면 APPLE — PR-A 본문)
     * @param identityToken          탈퇴 직전에 새로 받은 그 제공자의 토큰
     * @param authorizationCode      APPLE일 때 같은 Apple 창이 준 인가코드 — 철회용(없으면 철회만 건너뛴다)
     * @param appleIdentityToken     GOOGLE · KAKAO인데 Apple이 붙은 계정일 때 한 번 더 연 Apple 창의 토큰(선택 — REQ-111)
     * @param appleAuthorizationCode 그 Apple 창의 인가코드(선택)
     */
    public record ReauthRequest(String provider, String identityToken, String authorizationCode,
                                String appleIdentityToken, String appleAuthorizationCode) {
    }
}
