package com.booktimer.dev;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 로컬(dev) 전용 — 기동 시 1회 멱등하게 테스트 계정(testid / 1234qwer!!, USER)을 시드한다.
 * {@code @Profile("local")}이라 운영(prod 프로파일)·테스트(프로파일 없음)에선 빈이 생성되지 않는다(fail-closed).
 * bootRun이 spring.profiles.active=local을 켜므로 ./gradlew bootRun 시 자동 적용.
 * 로그인은 소문자 'testid'로 한다(loadUserByUsername이 입력을 소문자화하지 않음).
 */
@Component
@Profile("local")
public class LocalTestAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalTestAccountSeeder.class);

    static final String LOGIN_ID = "testid";
    static final String PASSWORD = "1234qwer!!";
    static final String EMAIL = "testid@local.test";
    static final String NICKNAME = "테스트";
    static final String TIMEZONE = "Asia/Seoul";

    private final UserRegistrationService registrationService;
    private final UserRepository userRepository;

    public LocalTestAccountSeeder(UserRegistrationService registrationService, UserRepository userRepository) {
        this.registrationService = registrationService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByLoginId(LOGIN_ID)) {
            log.info("로컬 테스트 계정 '{}' 이미 존재 — 시드 생략", LOGIN_ID);
            return;
        }
        User user = registrationService.register(
                EMAIL, PASSWORD, LOGIN_ID, NICKNAME, TIMEZONE, Role.USER, LocalDate.now());
        user.completeOnboarding();
        user.verifyEmail();
        userRepository.save(user);
        log.info("로컬 테스트 계정 시드 완료 — login_id='{}' (USER)", LOGIN_ID);
    }
}
