package com.booktimer.web.api;

import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.auth.AppleTokenRevoker;
import com.booktimer.auth.AppleVerificationUnavailableException;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.AccountService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * 아이폰 앱 탈퇴 {@code POST /api/app/account/delete}(v1 REQ-07) — Bearer + <b>새로 받은 Apple 재인증</b>.
 *
 * <p>순서: Apple 검증(400) → sub ≠ 본인 {@code apple_sub}면 403 → 웹·미니앱에서 만든 계정이면 409(철회도 안 함) →
 * Apple 토큰 철회(실패해도 진행) → purge. 403·409에서는 <b>아무것도 지우지 않고 철회도 하지 않는다</b>.
 * 웹 계정의 409는 연결 코드가 새도 앱에서 데이터를 지울 수 없게 하는 자리다 — 앱은 {@code /settings#delete}를 연다.
 *
 * <p>⚠️ 401을 쓰지 않는다 — 앱의 401은 「토큰 폐기 + 로그인 화면」이라, 재인증 실패가 로그아웃으로 둔갑한다
 * ({@link MiniappAccountApiController}와 같은 이유).
 */
@RestController
public class AppAccountApiController {

    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final AppleIdentityVerifier verifier;
    private final AppleTokenRevoker revoker;

    public AppAccountApiController(CurrentUserService currentUserService,
                                   AccountService accountService,
                                   AppleIdentityVerifier verifier,
                                   AppleTokenRevoker revoker) {
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.verifier = verifier;
        this.revoker = revoker;
    }

    /** @return 204 탈퇴 완료 / 400 Apple 재인증 실패 / 403 다른 Apple ID / 409 웹에서 만든 계정 / 401 토큰 없음·무효(체인) */
    @PostMapping("/api/app/account/delete")
    public ResponseEntity<Void> delete(Principal principal, @RequestBody AppleReauthRequest request) {
        User user = currentUserService.resolve(principal);
        String sub;
        try {
            sub = verifier.verify(request.identityToken()).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Apple 확인에 실패했어요 — 다시 시도해 주세요"));
        } catch (AppleVerificationUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Apple 로그인을 지금 확인할 수 없어요 — 잠시 뒤 다시 해 주세요");
        }
        if (user.getAppleSub() == null || !user.getAppleSub().equals(sub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 확인에 실패해 탈퇴를 진행하지 않았어요");
        }
        if (user.isWebAccount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "웹에서 만든 계정은 웹 설정에서 지울 수 있어요");
        }
        revoker.revoke(request.authorizationCode()); // 실패는 안에서 WARN — 탈퇴는 그대로 간다
        accountService.deleteAppleVerifiedAccount(user, sub); // 서비스가 한 번 더 대조한다
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return AppAuthApiController.plainTextError(e);
    }

    /**
     * @param identityToken     탈퇴 직전에 새로 받은 Apple identity token
     * @param authorizationCode 같은 Apple 창이 준 인가코드 — 철회용(없으면 철회만 건너뛴다)
     */
    public record AppleReauthRequest(String identityToken, String authorizationCode) {
    }
}
