package com.booktimer.user;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 소셜 로그인(OAuth) 사용자 프로비저닝 — find-or-create.
 *
 * <p>OAuth 인증이 성공하면 provider가 보증한 이메일로 우리 도메인 사용자를 찾고, 없으면 새로 만든다.
 * 신규 생성은 비밀번호 없는 사용자({@link UserRegistrationService#registerOAuth})로, 타임존은
 * 기본값({@link #DEFAULT_TIMEZONE} — 추후 설정에서 변경)으로 만든다. "오늘"(누적 시작일)은 주입된
 * {@link Clock} + 기본 타임존으로 계산한다.
 *
 * <p>이 로직을 {@code OidcUserService} 어댑터에서 분리해 둔 이유: 어댑터는 네트워크(토큰 교환)에
 * 묶여 단위 테스트가 어렵지만, find-or-create 규칙은 순수해서 Mockito로 격리 검증할 수 있다(N-009).
 */
@Service
public class OAuthUserProvisioningService {

    /** 소셜 가입 사용자의 기본 타임존. 사용자는 가입 후 설정에서 바꿀 수 있다. */
    static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    private final UserRepository userRepository;
    private final UserRegistrationService registrationService;
    private final Clock clock;

    public OAuthUserProvisioningService(UserRepository userRepository,
                                        UserRegistrationService registrationService,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /**
     * 이메일로 사용자를 찾고, 없으면 GOOGLE 소셜 사용자로 새로 만든다.
     *
     * <p><b>보안 전제 — 검증된 이메일만 신뢰</b>: find-or-create는 이메일을 신원으로 삼아 자동 계정
     * 연결을 한다. 따라서 provider가 그 이메일 소유를 보증({@code email_verified == true})했을 때만
     * 허용한다. 미검증 이메일을 주장하는 소셜 계정으로 동일 이메일의 기존 계정을 탈취하는 벡터를 막는다.
     * 클레임이 없으면(null) 검증 안 된 것으로 간주해 거부한다. (보안 점검 N-026)
     *
     * @param email         provider가 보증한 이메일(식별자)
     * @param displayName   provider가 준 표시 이름(비면 이메일 local part를 닉네임으로)
     * @param emailVerified provider의 {@code email_verified} 클레임(true가 아니면 거부)
     * @return 기존 또는 새로 만든 사용자
     * @throws OAuth2AuthenticationException 이메일이 검증되지 않았을 때(자동 연결/생성 전에 차단)
     */
    @Transactional
    public User provision(String email, String displayName, Boolean emailVerified) {
        if (emailVerified == null || !emailVerified) {
            throw new OAuth2AuthenticationException("email_not_verified");
        }
        return userRepository.findByEmail(email).orElseGet(() -> {
            String nickname = (displayName == null || displayName.isBlank())
                    ? emailLocalPart(email)
                    : displayName.trim();
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(DEFAULT_TIMEZONE));
            return registrationService.registerOAuth(
                    email, nickname, DEFAULT_TIMEZONE, AuthProvider.GOOGLE, today);
        });
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
