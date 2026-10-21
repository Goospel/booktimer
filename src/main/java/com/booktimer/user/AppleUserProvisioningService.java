package com.booktimer.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * 아이폰 앱 Apple 신원 프로비저닝 — 조회(login) / 신규(register) / 연결(link). {@link TossUserProvisioningService}와 같은 3분할이다.
 *
 * <p>{@code login}은 계정을 만들지 않는다 — 만들어 버리면 앱이 「처음이에요 / 웹에서 쓰던 계정이 있어요」를 물을 기회가 사라진다.
 * 이메일은 <b>늘 합성 주소</b>다: Apple 이메일·이름은 저장하지 않고(데이터 최소화), 기존 계정과 이메일로 엮일 일이 0이다.
 */
@Service
public class AppleUserProvisioningService {

    private static final String DEFAULT_NICKNAME = "독자";

    private final UserRepository userRepository;
    private final UserRegistrationService registrationService;
    private final Clock clock;

    public AppleUserProvisioningService(UserRepository userRepository,
                                        UserRegistrationService registrationService,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /** Apple 신원으로 기존 사용자를 <b>조회만</b> 한다 — 없으면 빈 값(앱은 {@code registered:false}로 받아 다음을 묻는다). */
    @Transactional(readOnly = true)
    public Optional<User> login(String appleSub) {
        return userRepository.findByAppleSub(appleSub);
    }

    /**
     * 아이폰 앱에서 시작한 새 계정을 만든다(User + ReadingTimer 한 트랜잭션). 이미 그 sub의 계정이 있으면 그 계정(멱등).
     * 핸들 없이·이메일 미검증으로 시작한다 — {@code hasSyntheticEmail()}이 참이라 발송·인증 유도가 자동으로 빠진다.
     */
    @Transactional
    public User register(String appleSub) {
        Optional<User> existing = userRepository.findByAppleSub(appleSub);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(TossUserProvisioningService.DEFAULT_TIMEZONE));
        User created = registrationService.registerOAuth(syntheticEmail(appleSub), DEFAULT_NICKNAME,
                TossUserProvisioningService.DEFAULT_TIMEZONE, AuthProvider.APPLE, today, false);
        created.linkAppleSub(appleSub);
        userRepository.save(created);
        return created;
    }

    /**
     * 웹이 발급한 코드의 주인 계정에 Apple 신원을 붙인다. {@code authProvider}는 바꾸지 않는다(토스 연결과 같은 규칙).
     *
     * @throws AppleLinkConflictException 그 sub가 이미 다른 계정에 있거나, 대상 계정에 이미 Apple이 붙어 있는 경우
     */
    @Transactional
    public User link(User owner, String appleSub) {
        // DB UNIQUE(uk_users_apple_sub) 위반이 500으로 새기 전에 먼저 확인한다.
        if (userRepository.findByAppleSub(appleSub).isPresent()) {
            throw new AppleLinkConflictException(AppleLinkConflictException.SUB_TAKEN);
        }
        try {
            owner.linkAppleSub(appleSub);
        } catch (IllegalStateException e) {
            throw new AppleLinkConflictException(AppleLinkConflictException.ACCOUNT_LINKED);
        }
        userRepository.save(owner);
        return owner;
    }

    /**
     * {@code apple-{sub 소문자}@noreply.booktimer.app}. 토스 합성 주소의 {@code [^a-z0-9]} 제거는 옮기지 않는다 —
     * 서로 다른 키를 같은 주소로 접을 수 있어서다(v1 §3.3-17). sub는 검증기가 영숫자·점 64자 이하로 거른 값이라
     * 로컬 부분에 그대로 들어가고, 그래도 겹치면 {@code uk_users_email} 위반으로 드러난다(조용히 접히지 않는다).
     */
    static String syntheticEmail(String appleSub) {
        return "apple-" + appleSub.toLowerCase(Locale.ROOT) + User.SYNTHETIC_EMAIL_DOMAIN;
    }
}
