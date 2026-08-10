package com.booktimer.auth;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiTokenService#authenticate}가 돌려주는 User를 <b>트랜잭션 밖에서</b> 읽을 수 있는지 검증한다.
 *
 * <p>왜 별도 클래스인가: 이 검증은 <b>테스트 메서드에 {@code @Transactional}이 없어야만</b> 성립한다.
 * {@code @Transactional} 통합 테스트는 테스트 전체에 영속성 컨텍스트가 살아 있어 lazy 프록시가 우연히
 * 초기화되므로, 실제 결함(서블릿 필터 = 트랜잭션 밖에서의 프록시 접근)을 가려 버린다. 형제
 * {@code ApiTokenServiceTest}가 클래스 레벨 {@code @Transactional}이라 여기로 분리했다.
 *
 * <p>재현 대상: 운영에서 미니앱의 모든 Bearer 인증 API가 500. {@code BearerTokenFilter}는
 * {@code DispatcherServlet}보다 앞이라 OSIV로도 세션이 없고, {@code user.getLoginId()}에서
 * {@code LazyInitializationException}이 터졌다.
 */
@SpringBootTest
class ApiTokenServiceDetachedUserTest {

    @Autowired ApiTokenService apiTokenService;
    @Autowired ApiTokenRepository tokenRepository;
    @Autowired UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("authenticate()가 돌려준 User는 트랜잭션 밖(서블릿 필터)에서도 속성을 읽을 수 있다")
    void authenticatedUser_readableOutsideTransaction() {
        User saved = userRepository.save(User.ofOAuth(
                "detached-user@booktimer.com", "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS));
        String rawToken = apiTokenService.issue(saved);

        User authenticated = apiTokenService.authenticate(rawToken).orElseThrow();

        // BearerTokenFilter.authenticate()가 하는 그대로 — 여기가 트랜잭션 밖이다.
        String principalName = authenticated.getLoginId() != null
                ? authenticated.getLoginId() : authenticated.getEmail();
        assertThat(principalName).isEqualTo("detached-user@booktimer.com");
    }
}
