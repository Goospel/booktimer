package com.booktimer.security;

import com.booktimer.user.OAuthUserProvisioningService;
import com.booktimer.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Google(OIDC) 로그인 어댑터.
 *
 * <p>표준 {@link OidcUserService}로 구글에서 사용자 정보를 받은 뒤, 우리 도메인 사용자를
 * find-or-create({@link OAuthUserProvisioningService})하고, 권한을 우리 {@code Role}로 맞춘다.
 *
 * <p><b>핵심</b>(인증 컷오버 PR-4): 반환 principal의 {@code getName()}이 <b>login_id</b>가 되도록
 * {@link BookTimerOidcUser}로 name을 명시한다 — 폼 로그인 principal도 login_id라 출처와 무관하게 통일된다.
 * 단 온보딩 전 <b>첫 세션은 아직 login_id가 없어 저장된 email</b>을 쓴다(그 짧은 창은 {@code CurrentUserService}가
 * email로 브리지; 온보딩에서 login_id 확정 후 재로그인부터 login_id가 principal).
 *
 * <p>이 클래스는 네트워크(super.loadUser의 토큰·userinfo 교환)에 묶인 얇은 어댑터다 — find-or-create
 * 규칙 자체는 {@link OAuthUserProvisioningService}로 분리해 단위 테스트한다(N-009).
 */
@Service
public class BookTimerOidcUserService extends OidcUserService {

    private final OAuthUserProvisioningService provisioningService;

    public BookTimerOidcUserService(OAuthUserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return toPrincipal(super.loadUser(userRequest));
    }

    /** {@code super.loadUser}(네트워크) 뒤의 몫 — 시험할 수 있게 뺐다. */
    OidcUser toPrincipal(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("OIDC provider did not return an email");
        }

        // 검증된 이메일만 신뢰 — provision이 email_verified!=true면 거부(자동 계정 연결 탈취 방어).
        // sub · hd는 Spring이 이미 검증한 ID 토큰에서 — 구글 신원 고정(REQ-115 · 116)의 재료다. 고정된 계정에 다른 구글
        // 계정이 오면 provision이 google_account_mismatch 인증 예외를 던지고, oauth2Login이 /login?error로 보낸다.
        User user = provisioningService.provision(oidcUser.getSubject(), email,
                oidcUser.getIdToken().getClaimAsString("hd"), oidcUser.getFullName(), oidcUser.getEmailVerified());

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // principal.getName() = login_id(있으면) / 온보딩 전 첫 세션은 **저장된** 이메일(아직 login_id 없음).
        // 구글 이메일이 아니다 — sub로 찾은 계정은 구글 쪽 이메일이 바뀌었을 수 있고, CurrentUserService ·
        // SessionInvalidator는 저장된 이메일로 찾는다. login_id는 id_token claim이 아니라 BookTimerOidcUser로 name을 명시한다.
        String principalName = user.getLoginId() != null ? user.getLoginId() : user.getEmail();
        return new BookTimerOidcUser(
                authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email", principalName);
    }
}
