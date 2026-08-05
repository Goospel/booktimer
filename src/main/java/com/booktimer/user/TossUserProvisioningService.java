package com.booktimer.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * 토스(앱인토스) 사용자 프로비저닝 — 조회(login) / 신규(register) / 연결(link) (설계 §2.2).
 *
 * <p>{@link OAuthUserProvisioningService}와 결정적으로 다른 점 둘:
 * <ol>
 *   <li><b>신원 키가 이메일이 아니라 {@code userKey}</b>다. 토스 email은 없을 수 있고 소유 보증도 없어,
 *       이메일로 자동 연결하면 계정 탈취 벡터가 된다(N-026/N-053과 같은 위험). 그래서 기존 계정과의 연결은
 *       본인이 웹에서 발급한 코드로만 이뤄진다({@link #link}).</li>
 *   <li><b>조회({@link #login})가 계정을 만들지 않는다.</b> find-or-create로 만들어 버리면 미니앱이
 *       "새로 시작 / 기존 계정 연결"을 물어볼 기회 자체가 사라진다 — 첫 진입에서 이미 새 계정이 생겨 있으니까.</li>
 * </ol>
 */
@Service
public class TossUserProvisioningService {

    /** 토스 가입 사용자의 기본 타임존(설정에서 변경 가능) — OAuth 프로비저닝과 동일. */
    static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    /** 토스가 이메일을 주지 않거나 기존 계정과 충돌할 때 쓰는 합성 주소의 도메인(발송 대상 아님). */
    private static final String SYNTHETIC_EMAIL_DOMAIN = "@noreply.booktimer.app";

    private static final String DEFAULT_NICKNAME = "토스유저";

    private final UserRepository userRepository;
    private final UserRegistrationService registrationService;
    private final Clock clock;

    public TossUserProvisioningService(UserRepository userRepository,
                                       UserRegistrationService registrationService,
                                       Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /**
     * 토스 신원으로 기존 사용자를 <b>조회만</b> 한다 — 없으면 만들지 않고 빈 값을 돌려준다.
     * 미니앱은 빈 값을 {@code registered:false}로 받아 "새로 시작 / 기존 계정 연결"을 묻는다.
     */
    @Transactional(readOnly = true)
    public Optional<User> login(String userKey) {
        return userRepository.findByTossUserKey(userKey);
    }

    /**
     * 토스에서 시작한 새 계정을 만든다(User + ReadingTimer 한 트랜잭션 — 기존 불변식 그대로 재사용).
     * 이미 그 userKey로 등록된 계정이 있으면 <b>새로 만들지 않고 그 계정을 돌려준다</b>(재시도·중복 호출 안전).
     *
     * <p>계정은 {@code login_id} 없이·{@code onboarded=false}로 시작한다(§2.4) — CHECK 불변식
     * ({@code onboarded ⟹ login_id IS NOT NULL})을 그대로 만족하고, 핸들 작명 마찰도 없다.
     *
     * @param email 토스가 준 이메일(없으면 null). 없거나 기존 계정과 충돌하면 합성 주소로 폴백한다 —
     *              <b>기존 계정에 자동으로 붙이지 않는다</b>(자동 연결 금지 불변식)
     */
    @Transactional
    public User register(String userKey, String email) {
        Optional<User> existing = userRepository.findByTossUserKey(userKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(DEFAULT_TIMEZONE));
        // verifyEmail=false: 토스는 이메일 소유를 보증하지 않는다(Google과의 결정적 차이 — 설계 §2.2).
        User created = registrationService.registerOAuth(
                resolveEmail(userKey, email), DEFAULT_NICKNAME, DEFAULT_TIMEZONE,
                AuthProvider.TOSS, today, false);
        created.linkTossUserKey(userKey);
        userRepository.save(created); // 더티체킹에 기대지 않고 명시 저장(기존 서비스들과 같은 관례)
        return created;
    }

    /**
     * 기존 계정(코드 발급자)에 토스 신원을 붙인다 — 웹·미니앱이 같은 User를 쓰게 되는 지점(채널 병행의 핵심).
     *
     * <p>{@code authProvider}는 <b>바꾸지 않는다</b>(LOCAL/GOOGLE 유지) — 비밀번호 변경 가능 여부 등 기존
     * 규칙이 그 값에 걸려 있어 바꾸면 회귀한다. "토스로 로그인 가능한가"는 {@code toss_user_key}가 답한다.
     *
     * @throws TossLinkConflictException 그 userKey가 이미 다른 계정에 붙어 있거나(UNIQUE 위반 선확인),
     *                                  대상 계정에 이미 다른 토스 신원이 붙어 있는 경우(once-set 위반)
     */
    @Transactional
    public User link(User user, String userKey) {
        // DB UNIQUE(uk_users_toss_user_key) 위반이 500으로 새기 전에 서비스가 먼저 확인한다.
        Optional<User> owner = userRepository.findByTossUserKey(userKey);
        if (owner.isPresent()) {
            throw new TossLinkConflictException("이미 다른 계정에 연결된 토스 사용자입니다");
        }
        try {
            user.linkTossUserKey(userKey);
        } catch (IllegalStateException e) {
            throw new TossLinkConflictException("이미 토스에 연결된 계정입니다", e);
        }
        userRepository.save(user);
        return user;
    }

    /**
     * 저장할 이메일을 정한다 — 토스 이메일을 쓸 수 있으면 그대로, <b>없거나 기존 계정과 충돌하면 합성 주소</b>.
     *
     * <p>충돌 시 기존 계정에 붙이지 않는 것이 핵심이다: 붙이면 "토스 계정만 만들면 남의 booktimer 계정을
     * 가져간다"가 되고, 폴백하지 않으면 {@code uk_users_email} 위반이 500으로 샌다.
     */
    private String resolveEmail(String userKey, String email) {
        if (email != null && !email.isBlank() && !userRepository.existsByEmail(email)) {
            return email;
        }
        return syntheticEmail(userKey);
    }

    /**
     * {@code toss-{userKey}@noreply.booktimer.app} — 발송하지 않는 자리표시 주소({@code users.email}은 NOT NULL).
     * userKey에서 이메일 형식을 깨는 문자를 빼고 소문자로 정규화하되 <b>자르지 않는다</b> — 앞부분만 쓰면
     * 서로 다른 userKey가 같은 주소로 접히며 {@code uk_users_email} 위반이 날 수 있다.
     */
    private static String syntheticEmail(String userKey) {
        String sanitized = userKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (sanitized.isEmpty()) {
            sanitized = Integer.toHexString(userKey.hashCode());
        }
        return "toss-" + sanitized + SYNTHETIC_EMAIL_DOMAIN;
    }
}
