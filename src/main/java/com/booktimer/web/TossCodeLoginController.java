package com.booktimer.web;

import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

/**
 * 토스 → 웹 코드 로그인 — {@code POST /login/toss-code}.
 *
 * <p>토스 미니앱에서 시작한 계정은 <b>비밀번호가 없다</b>({@code password_hash=null}) — 폼 로그인이
 * 원리상 불가하고, {@code login_id}도 null일 수 있어 아이디 입력조차 성립하지 않는다. 그래서 미니앱이
 * 발급한 일회용 코드({@link TossLinkCode.Purpose#WEB_LOGIN})를 PC 웹에서 소비해 세션을 연다 —
 * 「웹에서 발급 → 미니앱에 입력」 연결 코드의 <b>거울상</b>이다.
 *
 * <p><b>왜 커스텀 필터가 아니라 컨트롤러인가</b>: Security 표준 경로(AuthenticationProvider + 토큰 +
 * 필터 등록)로 가면 파일 3개와 체인 편집이 붙는데, 여기서 필요한 것은 폼 로그인 필터가 하는 일 중
 * 「세션 고정 방어 · 컨텍스트 저장 · 성공 이벤트」 셋뿐이다. Spring Security 레퍼런스의 수동 인증 저장
 * 예제를 그대로 따라 컨트롤러 하나로 끝낸다(설정 변경은 permitAll 한 줄).
 *
 * <p><b>CSRF는 유지한다</b> — 이 폼을 CSRF 예외로 두면 다른 사이트가 사용자의 브라우저로 코드를 대신
 * 제출할 수 있다(공격자 계정으로 로그인시키는 CSRF 로그인 벡터).
 */
@Controller
public class TossCodeLoginController {

    private final TossLinkCodeService linkCodeService;
    private final RateLimitService rateLimitService;
    private final AuthenticationEventPublisher eventPublisher;

    /**
     * 체인의 <b>기본 저장소와 같은 구성</b>(요청 속성 + 세션)이다 — Spring Security가 폼 로그인 성공 시
     * 쓰는 그 조합을 그대로 만든다. 세션 쪽은 기본 속성 키({@code SPRING_SECURITY_CONTEXT})를 쓰므로
     * SecurityConfig를 건드리지 않아도 다음 요청이 이 컨텍스트를 읽고, Spring Session JDBC의
     * {@code PRINCIPAL_NAME} 인덱스도 이 속성에서 뽑혀 {@code SessionInvalidator}가 찾는다.
     *
     * <p>요청 속성 쪽을 뺄 수 없는 이유: 그것이 <b>같은 요청 안에서</b> "지금 인증됐다"를 보이게 하는
     * 자리다. 세션에만 쓰면 다음 요청부터는 맞는데 이번 요청은 여전히 익명으로 보여, 같은 요청 안의
     * 재디스패치·필터가 인증을 못 본다.
     */
    private final SecurityContextRepository contextRepository = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

    public TossCodeLoginController(TossLinkCodeService linkCodeService,
                                   RateLimitService rateLimitService,
                                   AuthenticationEventPublisher eventPublisher) {
        this.linkCodeService = linkCodeService;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 코드를 소비해 세션을 연다.
     *
     * @return 성공 {@code /}(온보딩·admin 분기는 DashboardController) / 실패 {@code /login?codeError} /
     *         한도 초과 {@code /login?codeLimited}
     */
    @PostMapping("/login/toss-code")
    public String login(@RequestParam("code") String code, Principal principal,
                        HttpServletRequest request, HttpServletResponse response) {
        if (principal != null) {
            // 이미 로그인된 브라우저에서의 '계정 전환'은 만들지 않는다 — 이전 계정의 세션 속성(플래시·
            // justOnboarded)이 새 계정으로 새고, "누구로 로그인됐나"가 화면에서 모호해진다. 코드는
            // 소비하지 않으므로 로그아웃 후 그대로 다시 쓸 수 있다(TTL 5분).
            return "redirect:/";
        }
        // 코드 검증 '전에' 센다 — 검증 후에 세면 틀린 추측이 카운트되지 않아 상한이 무력해진다.
        // 미인증 단계라 셀 수 있는 키는 IP뿐이다(ForwardedHeaderFilter가 X-Forwarded-For를 반영).
        if (!rateLimitService.allow(RateLimitAction.TOSS_CODE_LOGIN, request.getRemoteAddr())) {
            return "redirect:/login?codeLimited";
        }
        return linkCodeService.consume(code, TossLinkCode.Purpose.WEB_LOGIN)
                .map(user -> {
                    establishSession(user, request, response);
                    return "redirect:/";
                })
                .orElse("redirect:/login?codeError");
    }

    /**
     * 폼 로그인 필터가 해 주던 세 가지를 손으로 한다 — 세션 고정 방어 · 컨텍스트 저장 · 성공 이벤트.
     *
     * <p>principal 이름은 {@code login_id != null ? login_id : email}로, 온보딩 전 OAuth 첫 세션 규칙과
     * 같다({@code BookTimerOidcUserService}). login_id가 없는 토스 계정은 email이 principal이 되고
     * {@code CurrentUserService}가 {@code findByEmail}로 브리지한다 — 새로 만드는 상태가 아니다.
     */
    private void establishSession(User user, HttpServletRequest request, HttpServletResponse response) {
        String principalName = user.getLoginId() != null ? user.getLoginId() : user.getEmail();
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                principalName, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));

        // 세션 고정 방어 — 저장 '앞에' 돌린다(폼 로그인의 ChangeSessionIdAuthenticationStrategy 순서).
        // GET /login의 CSRF 선확정 + 토큰 검증을 지나왔으므로 세션은 항상 있지만 가드를 둔다.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = strategy.createEmptyContext();
        context.setAuthentication(auth);
        strategy.setContext(context);
        contextRepository.saveContext(context, request, response);

        // AuthenticationManager를 안 지나므로 이벤트가 저절로 나지 않는다 — 직접 쏴야 세션이 30일로
        // 연장된다(SessionLifetimeListener). 안 쏘면 익명 기본값 24시간에 머문다.
        eventPublisher.publishAuthenticationSuccess(auth);
    }
}
