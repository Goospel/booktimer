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
                // TOSS 계정도 폐기 대상이 아니다 — 이메일이 미검증이어도 흡수한다(결정: 사용자 2026-09-12).
                //
                // 가정(수용된 위험): 「그 이메일은 같은 사람의 것」. ⚠️ 이 가정은 레포의 확정 진술과 상충한다 —
                // TossUserProvisioningService#register의 "토스는 이메일 소유를 보증하지 않는다"와
                // UserRegistrationService#registerOAuth(…, verifyEmail)의 "Google 경로는 보증하지만 토스 경로는
                // 그런 보증이 없다"(그래서 TOSS 가입은 emailVerified=false다). 즉 흡수가 「같은 사람의 두 채널
                // 합류」인지는 확정이 아니라 가정이고, 사용자가 그 잔여 위험을 수용한 결정이다.
                //
                // 잔여 경로(LOCAL 선점과 같은 pre-hijacking 형태): 공격자가 토스 프로필에 남의 이메일을 적고
                // 미니앱으로 가입 → TossUserProvisioningService#resolveEmail이 그 주소를 그대로 저장한다(그 주소를
                // 쓰는 계정이 아직 없을 때. 선점이 먼저여야 성립한다) → 실소유자가 구글로 로그인하면 여기서
                // 그 계정으로 들어간다. 폐기하면 반대로 그 토스 사용자의 독서 기록이 사라지는데, 사용자는
                // 「기록 보존」쪽을 택했다. 토스가 남의 이메일을 넘기는 사례가 확인되면 이 결정을 재검토한다.
                //
                // ⚠️ LOCAL과의 비대칭: LOCAL 선점은 purge로 선점자의 접근 수단(비밀번호)까지 사라지지만, TOSS는
                // 흡수 뒤에도 toss_user_key가 그 계정에 남아 선점자가 TossUserProvisioningService#login(userKey)로
                // 계속 들어온다. 즉 여기서 흡수를 고른 것은 「두 채널이 한 계정을 공유」를 받아들이는 것이다.
                //
                // 흡수 후 상태: authProvider=TOSS·toss_user_key 유지·emailVerified=false 유지 — 구글로 들어와도
                // 검증 표시가 켜지지 않아 재참여 넛지에서 빠지고 인증 배너가 계속 보인다(바꾸는 코드가 없다).
                //
                // 이 동작은 OAuthUserProvisioningServiceTest#provision_existingTossAccount_isAbsorbedNotPurged가 고정한다.
                .map(existing -> {
                    if (existing.isLocalAccount() && !existing.isEmailVerified()) {
                        accountService.purgeUnverifiedLocalAccount(existing);
                        return createOAuthUser(email, displayName);
                    }
                    return existing;
                })
                .orElseGet(() -> createOAuthUser(email, displayName));
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
