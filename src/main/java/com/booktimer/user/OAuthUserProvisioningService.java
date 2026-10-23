package com.booktimer.user;

import com.booktimer.user.GoogleAccountResolver.GoogleMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 소셜 로그인(OAuth) 사용자 프로비저닝 — find-or-create.
 *
 * <p>OAuth 인증이 성공하면 구글 신원 판정({@link GoogleAccountResolver} — 고정된 sub가 먼저, 고정 전엔 provider가 보증한
 * 이메일)으로 우리 도메인 사용자를 찾고, 없으면 새로 만든다. 구글로 연 계정에는 그 구글 계정의 sub를 고정한다(REQ-115).
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

    /** 다른 구글 계정에 고정된 계정 · 고정 경합의 거부 코드 — 웹은 /login?error, 앱 컨트롤러는 409로 바꾼다. */
    public static final String GOOGLE_ACCOUNT_MISMATCH = "google_account_mismatch";

    private static final Logger log = LoggerFactory.getLogger(OAuthUserProvisioningService.class);

    private final UserRepository userRepository;
    private final UserRegistrationService registrationService;
    private final AccountService accountService;
    private final GoogleAccountResolver googleAccountResolver;
    private final Clock clock;

    public OAuthUserProvisioningService(UserRepository userRepository,
                                        UserRegistrationService registrationService,
                                        AccountService accountService,
                                        GoogleAccountResolver googleAccountResolver,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.accountService = accountService;
        this.googleAccountResolver = googleAccountResolver;
        this.clock = clock;
    }

    /**
     * 구글 신원 판정으로 사용자를 찾고(고정 → 이메일), 없으면 GOOGLE 소셜 사용자로 새로 만든다. 연 계정의 빈 고정은 채운다.
     *
     * <p><b>보안 전제 — 검증된 이메일만 신뢰</b>: 고정 전의 find-or-create는 이메일을 신원으로 삼아 자동 계정
     * 연결을 한다. 따라서 provider가 그 이메일 소유를 보증({@code email_verified == true})했을 때만
     * 허용한다. 미검증 이메일을 주장하는 소셜 계정으로 동일 이메일의 기존 계정을 탈취하는 벡터를 막는다.
     * 클레임이 없으면(null) 검증 안 된 것으로 간주해 거부한다. (보안 점검 N-026)
     * 합성 도메인 주소도 같은 자리에서 거부한다(REQ-116) — 그 도메인에 메일을 붙이는 날 Apple · 카카오 · 토스 합성 계정이
     * 구글로 열리지 않게(지금 그 주소로 구글 로그인이 안 되는 것은 DNS에 없어서일 뿐이다).
     *
     * <p><b>구글 신원 고정(REQ-115)</b>: 판정이 {@code OPEN}이면 비어 있는 {@code google_sub}를 조건부 UPDATE로 채운다
     * (0행 = 그사이 다른 요청이 고정 · 유니크 위반 = 다른 계정이 그 sub를 가졌다 → 둘 다 불일치로 거부하고 트랜잭션은
     * 되돌아간다). {@code OPEN_UNPINNED}(구글이 그 주소에 권한이 없는 검증된 이메일 가입 계정)는 열기만 하고 쓰지 않는다.
     * 새로 만든 계정은 만든 직후 같은 문으로 고정한다.
     *
     * @param sub           검증된 ID 토큰의 sub
     * @param email         provider가 보증한 이메일
     * @param hd            토큰의 구글 워크스페이스 도메인(없으면 null)
     * @param displayName   provider가 준 표시 이름(비면 이메일 local part를 닉네임으로)
     * @param emailVerified provider의 {@code email_verified} 클레임(true가 아니면 거부)
     * @return 기존 또는 새로 만든 사용자(고정했으면 다시 읽은 것 — {@code getGoogleSub()}가 보인다)
     * @throws OAuth2AuthenticationException 이메일 미검증({@code email_not_verified}) · 합성 도메인({@code synthetic_email}) ·
     *                                       다른 구글 계정에 고정됨/고정 경합({@value #GOOGLE_ACCOUNT_MISMATCH})
     */
    @Transactional
    public User provision(String sub, String email, String hd, String displayName, Boolean emailVerified) {
        if (emailVerified == null || !emailVerified) {
            throw new OAuth2AuthenticationException("email_not_verified");
        }
        if (User.isSyntheticEmailAddress(email)) {
            throw new OAuth2AuthenticationException("synthetic_email");
        }
        // 정책 ②(결정: 사용자 2026-09-12) — *토스를 연결한 미검증* 계정(toss_user_key가 있으면 TOSS 가입이든
        // 웹 LOCAL 가입 후 연결이든)은 흡수하지도, 폐기하지도 않고 **이메일만 재배정**한다. 토스는 이메일
        // 소유를 보증하지 않으므로(TossUserProvisioningService#register, UserRegistrationService#
        // registerOAuth(…, verifyEmail) — 그래서 TOSS 가입은 emailVerified=false) 그 주소가 남의 것일 수
        // 있다. 흡수하면 LOCAL 선점과 같은 pre-hijacking이 된다: 공격자가 토스
        // 프로필에 남의 이메일을 적고 미니앱으로 가입 → resolveEmail이 그 주소를 저장 → 실소유자의 구글
        // 로그인이 그 계정으로 들어간다. 반대로 폐기하면 그 토스 사용자의 기록이 사라진다.
        //
        // 그래서 계정은 남기고 이메일만 합성 주소(toss-{userKey}@…)로 비켜 준다 — 기록·toss_user_key·
        // API 토큰이 모두 보존된다. 섞임이 사라지는 이유는 이렇다: **옛 흡수는 선점자의 toss_user_key가
        // 붙은 계정을 피해자에게 그대로 넘겨** 한 계정을 두 사람이 쓰게 만들었는데(선점자는 이후에도
        // 미니앱 login(userKey)로 들어온다), 재배정은 그 계정을 선점자 쪽에 두고 피해자에게는 새 구글
        // 계정을 준다. 세션도 함께 끊는다 — AccountService#reassignUnverifiedTossEmail이 이메일 변경
        // **전에** 무효화한다(안 끊으면 옛 미니앱 세션의 principal=피해자 이메일이 새 계정으로 해석된다).
        // 재배정된 토스 사용자는 미니앱을 그대로 쓰고(신원은 userKey다), 웹 이메일 경로만 합성 주소가
        // 된다 — 원래 미검증이라 메일이 가지 않던 주소다.
        //
        // 그래서 순서가 정책 ① 앞이다: 미검증 LOCAL이라도 toss_user_key가 있으면 purge가 아니라 재배정으로
        // 보낸다(안 그러면 그 사람의 미니앱 기록이 삭제된다). 웹 LOCAL 사용자는 loginId+비밀번호로 로그인하므로
        // (BookTimerUserDetailsService#loadUserByUsername이 findByLoginId) 이메일이 합성 주소가 돼도 웹
        // 로그인은 그대로다.
        //
        // ⚠️ 수용한 트레이드오프: 재배정된 LOCAL 사용자는 **실제 이메일로 비밀번호 재설정을 못 받는다**.
        // PasswordResetService는 isLocalAccount()만 보고 emailVerified를 안 봐서 미검증 LOCAL도 재설정
        // 메일을 받아 왔는데, 재배정 후에는 findByEmail이 새 구글 계정을 집어 조용히 무발송이 된다.
        // 그래도 계정 접근은 loginId+비밀번호 웹 로그인으로 살아 있고, 기록이 통째로 사라지는 폐기보다 낫다.
        //
        // TOSS인데 emailVerified=true면 그 사용자가 웹에서 소유를 증명한 것이라 **흡수를 유지**한다(같은 사람).
        // 이 동작은 OAuthUserProvisioningServiceTest#provision_existingUnverifiedTossAccount_reassignedNotAbsorbed
        // ·provision_existingVerifiedTossAccount_isAbsorbed + OAuthPreHijackingIntegrationTest가 고정한다.
        GoogleMatch match = googleAccountResolver.resolve(sub, email, hd);
        return switch (match.kind()) {
            case OPEN -> match.user().getGoogleSub() == null ? pin(match.user(), sub, true) : match.user();
            case OPEN_UNPINNED -> match.user();
            case MISMATCH -> throw mismatch(match.user());
            // pre-hijacking 차단(정책 ①, N-053): 같은 이메일을 *미검증 LOCAL*로 선점한 계정은 자동 연결하지 않고
            // 폐기한 뒤 OAuth 신규로 만든다 — 미검증 = 이메일 소유 미증명이라, Google이 소유를 보증한 OAuth가
            // 진짜 주인이다. 검증된 LOCAL·기존 OAuth 계정은 정당한 소유자이므로 그대로 연결한다(폐기 안 함).
            case PURGE_LOCAL -> {
                accountService.purgeUnverifiedLocalAccount(match.user());
                yield createOAuthUser(sub, email, displayName);
            }
            case REASSIGN_TOSS -> reassignTossEmailAndCreateOAuthUser(match.user(), sub, email, displayName);
            case NONE -> createOAuthUser(sub, email, displayName);
        };
    }

    /**
     * 토스를 연결한 미검증 계정(TOSS 가입이든 웹 LOCAL 가입 후 연결이든)의 이메일을 합성 주소로 옮긴 뒤,
     * 그 이메일로 GOOGLE 계정을 새로 만든다(정책 ②).
     *
     * <p>재배정의 본체는 {@link AccountService#reassignUnverifiedTossEmail}이다 — <b>세션 무효화 → 이메일 변경 →
     * flush</b> 순서와 합성 주소 충돌 폴백이 거기 있다(순서를 뒤집으면 옛 미니앱 세션이 새 구글 계정을 잡는다).
     * 여기서는 합성 주소의 재료(userKey)를 확인해 넘기는 일만 한다.
     *
     * @throws IllegalStateException TOSS 계정인데 toss_user_key가 없는 경우(이론상 불가) — 합성 주소를 만들 키가
     *                               없으면 조용히 흡수로 빠지지 않고 드러낸다
     */
    private User reassignTossEmailAndCreateOAuthUser(User existingToss, String sub, String email, String displayName) {
        String userKey = existingToss.getTossUserKey();
        if (userKey == null) {
            throw new IllegalStateException(
                    "TOSS account without toss_user_key cannot be reassigned: id=" + existingToss.getId());
        }
        accountService.reassignUnverifiedTossEmail(
                existingToss, TossUserProvisioningService.syntheticEmail(userKey));
        return createOAuthUser(sub, email, displayName);
    }

    /** GOOGLE 소셜 사용자를 기본 타임존으로 새로 만들고 sub를 고정한다(닉네임은 표시 이름, 비면 이메일 local part). */
    private User createOAuthUser(String sub, String email, String displayName) {
        // 닉네임은 단순 표시 이름(중복 허용) — provider가 준 이름을 그대로 쓴다. 식별/검색 핸들은
        // 온보딩에서 정하는 불변 login_id가 담당한다.
        String nickname = (displayName == null || displayName.isBlank())
                ? emailLocalPart(email)
                : displayName.trim();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(DEFAULT_TIMEZONE));
        User created = registrationService.registerOAuth(
                email, nickname, DEFAULT_TIMEZONE, AuthProvider.GOOGLE, today);
        return pin(created, sub, false); // 새 계정은 INFO 없음 — 그 줄은 기존 계정의 첫 고정 신호다(U-53)
    }

    /**
     * 비어 있는 {@code google_sub}에 sub를 고정하고, 다시 읽은 계정을 돌려준다(UPDATE가 영속성 컨텍스트를 비운다).
     * 0행(그사이 다른 요청이 고정)과 유니크 위반(다른 계정이 그 sub를 가졌다)은 불일치로 거부한다 — 예외가 트랜잭션을
     * 되돌려 정책 ①②의 삭제·비키기도 함께 되돌아간다. 0행 뒤 잠금 읽기로 같은 sub면 여는 길은 두지 않았다 —
     * 헛 거부는 같은 사람이 두 곳에서 동시에 처음 로그인할 때뿐이고 다시 하면 sub로 열린다.
     * ponytail: 헛 거부가 실제로 보이면 0행 뒤 {@code PESSIMISTIC_WRITE} 조회로 지금 값을 읽어 같으면 연다.
     */
    private User pin(User user, String sub, boolean existingAccount) {
        int rows;
        try {
            rows = userRepository.pinGoogleSubIfEmpty(user.getId(), sub);
        } catch (DataIntegrityViolationException e) {
            rows = 0;
        }
        if (rows != 1) {
            throw mismatch(user);
        }
        if (existingAccount) {
            log.info("google sub pinned userId={}", user.getId()); // sub · 이메일은 남기지 않는다(REQ-116)
        }
        return userRepository.findById(user.getId()).orElseThrow();
    }

    /** 불일치 거부 — WARN 한 줄(userId만 — sub · 이메일 없음)과 인증 예외(웹은 /login?error, 500 아님). */
    private OAuth2AuthenticationException mismatch(User user) {
        log.warn("google sub mismatch userId={}", user.getId());
        return new OAuth2AuthenticationException(GOOGLE_ACCOUNT_MISMATCH);
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
