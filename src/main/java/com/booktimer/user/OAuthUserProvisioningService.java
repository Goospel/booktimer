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
    private final AccountService accountService;
    private final Clock clock;

    public OAuthUserProvisioningService(UserRepository userRepository,
                                        UserRegistrationService registrationService,
                                        AccountService accountService,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.accountService = accountService;
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
        return userRepository.findByEmail(email)
                // pre-hijacking 차단(정책 ①, N-053): 같은 이메일을 *미검증 LOCAL*로 선점한 계정은 자동 연결하지 않고
                // 폐기한 뒤 OAuth 신규로 만든다 — 미검증 = 이메일 소유 미증명이라, Google이 소유를 보증한 OAuth가
                // 진짜 주인이다. 검증된 LOCAL·기존 OAuth 계정은 정당한 소유자이므로 그대로 연결한다(폐기 안 함).
                //
                // 정책 ②(결정: 사용자 2026-09-12) — *미검증 TOSS* 계정은 흡수하지도, 폐기하지도 않고 **이메일만
                // 재배정**한다. 토스는 이메일 소유를 보증하지 않으므로(TossUserProvisioningService#register,
                // UserRegistrationService#registerOAuth(…, verifyEmail) — 그래서 TOSS 가입은 emailVerified=false)
                // 그 주소가 남의 것일 수 있다. 흡수하면 LOCAL 선점과 같은 pre-hijacking이 된다: 공격자가 토스
                // 프로필에 남의 이메일을 적고 미니앱으로 가입 → resolveEmail이 그 주소를 저장 → 실소유자의 구글
                // 로그인이 그 계정으로 들어간다. 반대로 폐기하면 그 토스 사용자의 기록이 사라진다.
                //
                // 그래서 계정은 남기고 이메일만 합성 주소(toss-{userKey}@…)로 비켜 준다 — 기록·toss_user_key·
                // API 토큰이 모두 보존되고 두 계정이 섞이지 않는다. LOCAL 비대칭(purge는 선점자의 접근을 없애지만
                // toss_user_key는 남는다)도 이 갈래로 닫힌다: 선점자는 자기 계정에 그대로 남고, 실소유자의 이메일은
                // 새 구글 계정이 가진다. 재배정된 토스 사용자는 미니앱을 그대로 쓰고(신원은 userKey다), 웹 이메일
                // 경로만 합성 주소가 된다 — 원래 미검증이라 메일이 가지 않던 주소다.
                //
                // TOSS인데 emailVerified=true면 그 사용자가 웹에서 소유를 증명한 것이라 **흡수를 유지**한다(같은 사람).
                // 이 동작은 OAuthUserProvisioningServiceTest#provision_existingUnverifiedTossAccount_reassignedNotAbsorbed
                // ·provision_existingVerifiedTossAccount_isAbsorbed + OAuthPreHijackingIntegrationTest가 고정한다.
                .map(existing -> {
                    if (existing.isLocalAccount() && !existing.isEmailVerified()) {
                        accountService.purgeUnverifiedLocalAccount(existing);
                        return createOAuthUser(email, displayName);
                    }
                    if (existing.getAuthProvider() == AuthProvider.TOSS && !existing.isEmailVerified()) {
                        return reassignTossEmailAndCreateOAuthUser(existing, email, displayName);
                    }
                    return existing;
                })
                .orElseGet(() -> createOAuthUser(email, displayName));
    }

    /**
     * 미검증 TOSS 계정의 이메일을 합성 주소로 옮긴 뒤, 그 이메일로 GOOGLE 계정을 새로 만든다(정책 ②).
     *
     * <p><b>flush 순서가 본질</b>이다 — 재배정을 먼저 {@code saveAndFlush}로 내려 {@code uk_users_email}을 비운
     * 뒤에야 INSERT가 안전하다. 더티체킹에 맡기면 Hibernate가 INSERT를 UPDATE보다 먼저 실행해 유니크 제약을
     * 위반한다(폐기 경로의 같은 함정 — OAuthPreHijackingIntegrationTest가 실 스키마로 잡는다).
     *
     * @throws IllegalStateException TOSS 계정인데 toss_user_key가 없는 경우(이론상 불가) — 합성 주소를 만들 키가
     *                               없으면 조용히 흡수로 빠지지 않고 드러낸다
     */
    private User reassignTossEmailAndCreateOAuthUser(User existingToss, String email, String displayName) {
        String userKey = existingToss.getTossUserKey();
        if (userKey == null) {
            throw new IllegalStateException(
                    "TOSS account without toss_user_key cannot be reassigned: id=" + existingToss.getId());
        }
        existingToss.reassignEmailToSynthetic(TossUserProvisioningService.syntheticEmail(userKey));
        userRepository.saveAndFlush(existingToss);
        return createOAuthUser(email, displayName);
    }

    /** GOOGLE 소셜 사용자를 기본 타임존으로 새로 만든다(닉네임은 표시 이름, 비면 이메일 local part). */
    private User createOAuthUser(String email, String displayName) {
        // 닉네임은 단순 표시 이름(중복 허용) — provider가 준 이름을 그대로 쓴다. 식별/검색 핸들은
        // 온보딩에서 정하는 불변 login_id가 담당한다.
        String nickname = (displayName == null || displayName.isBlank())
                ? emailLocalPart(email)
                : displayName.trim();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(DEFAULT_TIMEZONE));
        return registrationService.registerOAuth(
                email, nickname, DEFAULT_TIMEZONE, AuthProvider.GOOGLE, today);
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
