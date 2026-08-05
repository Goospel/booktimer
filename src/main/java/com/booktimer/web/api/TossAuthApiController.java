package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.TossLoginClient;
import com.booktimer.auth.TossLoginException;
import com.booktimer.auth.TossUserInfo;
import com.booktimer.security.BearerTokenFilter;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.TossLinkConflictException;
import com.booktimer.user.TossUserProvisioningService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 미니앱(앱인토스) 신원 API — 로그인 / 신규가입 / 기존 계정 연결 / 로그아웃 (설계 §2.2).
 *
 * <p><b>서버에 pending 상태를 두지 않는다.</b> 세 엔드포인트가 각자 <b>fresh 인가코드</b>로 신원을 다시
 * 증명하므로(인가코드는 10분·일회성, 재발급은 {@code appLogin()} 한 번이라 마찰 0), "로그인 시도 중"
 * 같은 중간 상태를 저장할 이유가 없다 — 상태가 없으면 만료·정리·경합도 없다.
 *
 * <p>에러 계약: 401 = 토스가 인가코드를 거부(만료·위조) · 400 = 연결 코드가 유효하지 않음(없음·만료·이미 사용,
 * 셋을 구분해 주지 않는다 — 구분은 추측 단서가 된다) · 409 = 연결 충돌(이미 연결된 계정 / 이미 쓰인 userKey) ·
 * 429 = 레이트리밋.
 */
@RestController
public class TossAuthApiController {

    private final TossLoginClient tossLoginClient;
    private final TossUserProvisioningService provisioningService;
    private final TossLinkCodeService linkCodeService;
    private final ApiTokenService apiTokenService;
    private final RateLimitService rateLimitService;

    public TossAuthApiController(TossLoginClient tossLoginClient,
                                 TossUserProvisioningService provisioningService,
                                 TossLinkCodeService linkCodeService,
                                 ApiTokenService apiTokenService,
                                 RateLimitService rateLimitService) {
        this.tossLoginClient = tossLoginClient;
        this.provisioningService = provisioningService;
        this.linkCodeService = linkCodeService;
        this.apiTokenService = apiTokenService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 토스 로그인 — <b>조회만</b> 한다. 미등록 신원이면 계정을 만들지 않고 {@code registered:false}를 돌려주어
     * 미니앱이 "새로 시작 / 기존 계정 연결"을 물을 수 있게 한다(여기서 만들어 버리면 그 선택권이 사라진다).
     */
    @PostMapping("/api/toss/login")
    public TossAuthResponse login(@RequestBody TossAuthRequest request) {
        TossUserInfo info = verify(request.authorizationCode(), request.referrer());
        limit(RateLimitAction.TOSS_AUTH, info.userKey());
        return provisioningService.login(info.userKey())
                .map(this::registeredResponse)
                .orElseGet(TossAuthResponse::unregistered);
    }

    /** 토스에서 시작하는 신규 계정 생성(핸들·비밀번호 없음, 이메일 미검증) + 토큰 발급. */
    @PostMapping("/api/toss/register")
    public TossAuthResponse register(@RequestBody TossAuthRequest request) {
        TossUserInfo info = verify(request.authorizationCode(), request.referrer());
        limit(RateLimitAction.TOSS_AUTH, info.userKey());
        return registeredResponse(provisioningService.register(info.userKey(), info.email()));
    }

    /**
     * 기존 booktimer 계정에 토스 신원을 연결한다 — 웹에서 발급한 일회용 코드가 "이 계정의 주인" 증명이다.
     * 성공 이후 미니앱의 모든 기록이 그 기존 계정으로 쌓인다(채널 병행의 성립 지점).
     */
    @PostMapping("/api/toss/link")
    public TossAuthResponse link(@RequestBody TossLinkRequest request) {
        TossUserInfo info = verify(request.authorizationCode(), request.referrer());
        // 코드 검증 '전에' 센다 — 검증 후에 세면 실패한 추측이 카운트되지 않아 브루트포스 방어가 무력해진다.
        limit(RateLimitAction.TOSS_LINK, info.userKey());
        User owner = linkCodeService.consume(request.linkCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "연결 코드가 유효하지 않습니다"));
        try {
            return registeredResponse(provisioningService.link(owner, info.userKey()));
        } catch (TossLinkConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 로그아웃 — 이 요청에 실린 토큰만 폐기한다(다른 기기의 토큰은 살려 둔다). */
    @PostMapping("/api/toss/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        apiTokenService.revoke(BearerTokenFilter.extractToken(request));
        return ResponseEntity.noContent().build();
    }

    /** 인가코드로 토스 신원을 확인한다 — 실패는 전부 401(원인은 로그에만, 응답으로 구분해 주지 않는다). */
    private TossUserInfo verify(String authorizationCode, String referrer) {
        try {
            return tossLoginClient.fetchUserInfo(authorizationCode, referrer);
        } catch (TossLoginException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토스 로그인 확인에 실패했습니다");
        }
    }

    private void limit(RateLimitAction action, String userKey) {
        if (!rateLimitService.allow(action, userKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다");
        }
    }

    private TossAuthResponse registeredResponse(User user) {
        return new TossAuthResponse(true, apiTokenService.issue(user), user.getLoginId());
    }

    // ── DTO records ──────────────────────────────────────────────────────────

    /** 미니앱 {@code appLogin()}이 준 인가코드·referrer. */
    public record TossAuthRequest(String authorizationCode, String referrer) {
    }

    /** link는 여기에 사용자가 웹에서 발급받아 입력한 연결 코드가 더 붙는다. */
    public record TossLinkRequest(String authorizationCode, String referrer, String linkCode) {
    }

    /**
     * @param registered 이 토스 신원에 연결된 계정이 있는가. false면 {@code token}이 없고, 미니앱이
     *                   "새로 시작 / 기존 계정 연결" 선택 화면을 띄운다
     * @param token      우리 서비스의 Bearer 토큰(미등록이면 null)
     * @param loginId    공개 @핸들(미니앱에서 시작한 계정은 없어서 null — §2.4)
     */
    public record TossAuthResponse(boolean registered, String token, String loginId) {

        static TossAuthResponse unregistered() {
            return new TossAuthResponse(false, null, null);
        }
    }
}
