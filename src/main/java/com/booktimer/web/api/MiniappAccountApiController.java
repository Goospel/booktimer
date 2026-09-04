package com.booktimer.web.api;

import com.booktimer.auth.TossLoginClient;
import com.booktimer.auth.TossLoginException;
import com.booktimer.auth.TossUserInfo;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AccountDeletionConfirmationException;
import com.booktimer.user.AccountService;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.TossLinkConflictException;
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
 * 미니앱(앱인토스) 회원 탈퇴 API — {@code POST /api/miniapp/delete-account}.
 *
 * <p>토스로 시작한 계정에는 <b>탈퇴 경로가 하나도 없었다</b>. 비밀번호가 없어 웹 로그인 자체가 불가라
 * {@code /settings/delete}에 닿지 못하고, {@code login_id}가 null일 수 있어(핸들 미작성) 소셜 탈퇴의
 * @핸들 재입력도 성립하지 않는다 — "웹에서 하세요"는 이 계정들에게 죽은 안내다.
 *
 * <p><b>확인 수단은 토스 재인증</b>이다: 미니앱이 {@code tossLogin()}으로 받아 온 fresh 인가코드를 서버가
 * 토스에 물어 userKey로 바꾸고, 그 값을 본인 {@code toss_user_key}와 대조한다({@link AccountService
 * #deleteTossVerifiedAccount}). 토큰을 훔친 쪽은 토스 앱의 본인 인증을 통과할 수 없으므로, 웹의 @핸들
 * 재입력보다 오히려 강한 확인이다. 인가코드는 10분·일회성이라 매번 새로 받는 것이 기존 인증 3종과 같다.
 *
 * <p><b>에러 계약이 인증 3종과 의도적으로 다르다 — 401을 쓰지 않는다.</b> `api.ts`의 {@code request()}는
 * 401을 받으면 토큰을 지우고 로그인 화면으로 튕긴다. 그 계약을 여기 그대로 쓰면 <b>인가코드 만료가
 * 로그아웃으로 둔갑</b>해, 탈퇴하려던 사용자가 대신 로그아웃당한다. 그래서 토스 인증 실패 = <b>400</b>,
 * userKey 불일치 = <b>403</b>이다(401은 "재로그인이 정답"인 자리에만 쓴다).
 *
 * <p>인증은 Bearer 토큰이다 — {@code /api/**} + {@code Authorization: Bearer}는 미니앱 전용 체인이 맡는다
 * (SecurityConfig 무변경). 탈퇴가 성공하면 {@code purge}가 {@code api_token}까지 지우므로 그 토큰은
 * 즉시 죽는다(다른 기기의 토큰도 함께).
 */
@RestController
public class MiniappAccountApiController {

    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final TossLoginClient tossLoginClient;
    private final RateLimitService rateLimitService;
    private final TossLinkCodeService linkCodeService;

    public MiniappAccountApiController(CurrentUserService currentUserService,
                                       AccountService accountService,
                                       TossLoginClient tossLoginClient,
                                       RateLimitService rateLimitService,
                                       TossLinkCodeService linkCodeService) {
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.tossLoginClient = tossLoginClient;
        this.rateLimitService = rateLimitService;
        this.linkCodeService = linkCodeService;
    }

    /** @return 204 탈퇴 완료 / 400 토스 인증 실패 / 403 신원 불일치 / 401 토큰 없음·무효(체인) / 429 */
    @PostMapping("/api/miniapp/delete-account")
    public ResponseEntity<Void> deleteAccount(Principal principal, @RequestBody DeleteAccountRequest request) {
        User user = currentUserService.resolve(principal);
        TossUserInfo info = verify(request.authorizationCode(), request.referrer());
        // 토스 API 두들김 방지 — 인증 3종과 같은 액션을 쓴다(같은 외부 API를 같은 신원으로 부른다).
        limit(info.userKey());
        accountService.deleteTossVerifiedAccount(user, info.userKey());
        return ResponseEntity.noContent().build();
    }

    /**
     * 인가코드로 토스 신원을 확인한다. {@link TossLoginException}을 <b>그대로 던져</b> 아래 핸들러가 400으로
     * 받는다 — {@code TossAuthApiController.verify}처럼 401로 바꾸면 이 경로에서는 로그아웃이 돼 버린다.
     */
    private TossUserInfo verify(String authorizationCode, String referrer) {
        return tossLoginClient.fetchUserInfo(authorizationCode, referrer);
    }

    private void limit(String userKey) {
        if (!rateLimitService.allow(RateLimitAction.TOSS_AUTH, userKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다");
        }
    }

    /**
     * PC 웹 로그인용 일회용 코드를 발급한다 — 토스로 시작한 계정의 <b>유일한 웹 진입로</b>.
     *
     * <p>비밀번호가 없어 폼 로그인이 원리상 불가하고 {@code login_id}도 null일 수 있어, 웹에 들어갈 방법이
     * 아예 없었다. 사용자가 이 코드를 PC {@code booktimer.app/login}에 옮겨 적으면 세션이 열린다
     * ({@code TossCodeLoginController}). 「웹에서 발급 → 미니앱에 입력」 연결 코드의 거울상이다.
     *
     * <p><b>발급 자체엔 레이트리밋을 걸지 않는다</b> — Bearer 인증이 필요하고 재발급이 직전 코드를
     * 무효화해(항상 하나만 유효) 남용해서 얻을 것이 없다. 브루트포스 방어는 <b>소비 지점</b>의 IP 상한이
     * 담당한다({@code RateLimitAction.TOSS_CODE_LOGIN}). 웹 {@code /settings/toss-link-code}도 같은 잣대다.
     *
     * @return 200 코드+TTL / 409 토스 미연결 / 401 토큰 없음·무효(체인)
     */
    @PostMapping("/api/miniapp/web-login-code")
    public WebLoginCodeResponse issueWebLoginCode(Principal principal) {
        User user = currentUserService.resolve(principal);
        return new WebLoginCodeResponse(linkCodeService.issueWebLogin(user), TossLinkCodeService.TTL.toSeconds());
    }

    // 미니앱은 에러 본문 평문을 그대로 사용자에게 띄운다(`api.ts errorMessage()`) — 문구가 곧 안내다.

    /**
     * 토스에 연결되지 않은 계정의 발급 시도 — 409. 실 운용에선 Bearer 토큰이 토스 인증 경로에서만 나와
     * 닿지 않는 분기지만, 방어선을 화면이 아니라 코드에 둔다.
     */
    @ExceptionHandler(TossLinkConflictException.class)
    public ResponseEntity<String> handleNotLinked(TossLinkConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    /** ⚠️ 401 금지 — 401은 `api.ts`에서 토큰 폐기 + 로그인 화면 복귀를 뜻한다(위 클래스 주석). */
    @ExceptionHandler(TossLoginException.class)
    public ResponseEntity<String> handleTossAuthFailed(TossLoginException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("토스 인증에 실패했어요. 다시 시도해 주세요.");
    }

    /** 신원 불일치 — 아무것도 삭제되지 않았다는 사실을 문구로 못 박는다. */
    @ExceptionHandler(AccountDeletionConfirmationException.class)
    public ResponseEntity<String> handleConfirmationFailed(AccountDeletionConfirmationException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("본인 확인에 실패해 탈퇴를 진행하지 않았어요.");
    }

    /** 미니앱 {@code tossLogin()}이 준 fresh 인가코드·referrer — 인증 3종의 {@code TossAuthRequest}와 같은 모양. */
    public record DeleteAccountRequest(String authorizationCode, String referrer) {
    }

    /**
     * @param code             화면에 크게 띄울 평문 코드(8자). 서버는 해시만 갖고 있어 다시 조회할 수 없다
     * @param expiresInSeconds 남은 수명 — 미니앱이 "5분 안에"를 하드코딩하지 않게 서버가 알려 준다
     */
    public record WebLoginCodeResponse(String code, long expiresInSeconds) {
    }
}
