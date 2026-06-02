package com.booktimer.security;

import com.booktimer.user.OAuthUserProvisioningService;
import com.booktimer.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Google(OIDC) 로그인 어댑터.
 *
 * <p>표준 {@link OidcUserService}로 구글에서 사용자 정보를 받은 뒤, 우리 도메인 사용자를
 * find-or-create({@link OAuthUserProvisioningService})하고, 권한을 우리 {@code Role}로 맞춘다.
 *
 * <p><b>핵심</b>: 반환 principal의 {@code getName()}이 <b>이메일</b>이 되도록
 * {@link DefaultOidcUser}의 name 속성 키를 {@code "email"}로 지정한다. 폼 로그인의 principal name도
 * 이메일이라, 이렇게 맞춰야 모든 컨트롤러({@code principal.getName()} → {@code findByEmail})가
 * 인증 출처와 무관하게 동일하게 동작한다(폼/소셜 통합).
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
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("OIDC provider did not return an email");
        }

        User user = provisioningService.provision(email, oidcUser.getFullName());

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // name 속성 키를 "email"로 → principal.getName()이 이메일을 돌려준다(폼 로그인과 동일 규약).
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
    }
}
