package com.booktimer.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 아이폰 앱 카카오 신원 프로비저닝(REQ-81) — 조회(login) / 신규(register). {@link AppleUserProvisioningService}와 같은 꼴이고
 * LINK는 없다(비목표). 신원은 회원번호(sub) 하나다 — 카카오 이메일은 「바뀔 수 있어 식별자로 쓰지 말라」(카카오 문서)라
 * 받지도 쓰지도 않고, 이메일은 늘 합성 주소다.
 */
@Service
public class KakaoUserProvisioningService {

    private static final String DEFAULT_NICKNAME = "독자";

    private final UserRepository userRepository;
    private final UserRegistrationService registrationService;
    private final Clock clock;

    public KakaoUserProvisioningService(UserRepository userRepository,
                                        UserRegistrationService registrationService,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /** 카카오 신원으로 기존 사용자를 <b>조회만</b> 한다 — 없으면 빈 값(앱은 {@code registered:false}로 받는다). */
    @Transactional(readOnly = true)
    public Optional<User> login(String kakaoSub) {
        return userRepository.findByKakaoSub(kakaoSub);
    }

    /** 새 계정을 만든다(User + ReadingTimer 한 트랜잭션). 이미 그 sub의 계정이 있으면 그 계정(멱등). */
    @Transactional
    public User register(String kakaoSub) {
        Optional<User> existing = userRepository.findByKakaoSub(kakaoSub);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(TossUserProvisioningService.DEFAULT_TIMEZONE));
        User created = registrationService.registerOAuth(syntheticEmail(kakaoSub), DEFAULT_NICKNAME,
                TossUserProvisioningService.DEFAULT_TIMEZONE, AuthProvider.KAKAO, today, false);
        created.linkKakaoSub(kakaoSub);
        userRepository.save(created);
        return created;
    }

    /** {@code kakao-{sub}@noreply.booktimer.app}. sub는 검증기가 숫자 20자 이하로 거른 값이라 그대로 들어간다. */
    static String syntheticEmail(String kakaoSub) {
        return "kakao-" + kakaoSub + User.SYNTHETIC_EMAIL_DOMAIN;
    }
}
