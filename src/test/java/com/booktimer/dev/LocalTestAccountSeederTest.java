package com.booktimer.dev;

import com.booktimer.config.JpaConfig;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LocalTestAccountSeeder 슬라이스 테스트 (@DataJpaTest, H2).
 * UserRegistrationService·LocalTestAccountSeeder를 수동 배선해 호출 — @DataJpaTest ambient 트랜잭션 안에서
 * 실행되고 끝나면 롤백.
 */
@DataJpaTest
@Import(JpaConfig.class)
class LocalTestAccountSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingTimerRepository timerRepository;

    private LocalTestAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        var registrationService = new UserRegistrationService(
                userRepository, timerRepository, new BCryptPasswordEncoder(), Clock.systemUTC());
        seeder = new LocalTestAccountSeeder(registrationService, userRepository);
    }

    @Test
    @DisplayName("run: testid 계정 생성 — login_id·BCrypt·타이머·onboarded·emailVerified·USER")
    void run_createsTestAccount() throws Exception {
        seeder.run(null);

        var user = userRepository.findByLoginId(LocalTestAccountSeeder.LOGIN_ID).orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(LocalTestAccountSeeder.PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(timerRepository.findByUser(user)).isPresent();
        assertThat(user.isOnboarded()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("run: 멱등 — 두 번 호출해도 testid 유저가 정확히 1개")
    void run_idempotent() throws Exception {
        seeder.run(null);
        seeder.run(null);

        long count = userRepository.findAll()
                .stream()
                .filter(u -> LocalTestAccountSeeder.LOGIN_ID.equals(u.getLoginId()))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
