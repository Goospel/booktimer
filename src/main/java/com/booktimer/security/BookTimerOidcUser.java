package com.booktimer.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * principal name을 명시적으로 정하는 OIDC 사용자.
 *
 * <p>{@link DefaultOidcUser}는 {@code getName()}을 claim(name 속성 키)에서 끌어오는데, 우리의 영구
 * 식별자 <b>login_id는 구글 id_token의 claim이 아니다</b>. 그래서 name을 별도로 받아 {@code getName()}을
 * override한다 — 인증 컷오버(PR-4)로 OAuth 사용자도 principal=login_id가 되도록(온보딩 전 첫 세션은 아직
 * login_id가 없어 email을 쓴다, {@link BookTimerOidcUserService} 참고). 나머지(claims·authorities)는
 * 그대로 위임한다.
 */
public class BookTimerOidcUser extends DefaultOidcUser {

    private final String name;

    public BookTimerOidcUser(Collection<? extends GrantedAuthority> authorities,
                             OidcIdToken idToken, OidcUserInfo userInfo,
                             String nameAttributeKey, String name) {
        super(authorities, idToken, userInfo, nameAttributeKey);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
