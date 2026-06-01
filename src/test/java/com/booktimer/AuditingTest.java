package com.booktimer;

import com.booktimer.config.JpaConfig;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA Auditing 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>@DataJpaTest는 auditing 설정을 자동 로드하지 않으므로 {@code @Import(JpaConfig.class)}로
 * @EnableJpaAuditing을 끌어온다. BaseTimeEntity의 createdAt/updatedAt이 저장/수정 시
 * 자동으로 채워지는지 검증한다.
 */
@DataJpaTest
@Import(JpaConfig.class)
class AuditingTest {

    private static final LocalDate DAY0 = LocalDate.of(2026, 5, 31);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingTimerRepository readingTimerRepository;

    private User sampleUser(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("User 저장 시 createdAt/updatedAt이 자동으로 채워진다")
    void user_auditingPopulatedOnInsert() {
        User saved = userRepository.saveAndFlush(sampleUser("a@booktimer.com"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("ReadingTimer 저장 시에도 auditing이 채워진다 (베이스 상속 동작)")
    void timer_auditingPopulatedOnInsert() {
        User user = userRepository.save(sampleUser("b@booktimer.com"));

        ReadingTimer saved = readingTimerRepository.saveAndFlush(
                ReadingTimer.startFor(user, 3600L, 18000L, DAY0));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("수정 후 updatedAt은 createdAt 이상으로 유지된다")
    void timer_updatedAtNotBeforeCreatedOnUpdate() {
        User user = userRepository.save(sampleUser("c@booktimer.com"));
        ReadingTimer saved = readingTimerRepository.saveAndFlush(
                ReadingTimer.startFor(user, 3600L, 18000L, DAY0));
        Instant created = saved.getCreatedAt();

        saved.accrueUntil(DAY0.plusDays(3)); // remainingSeconds 변경 → dirty update
        ReadingTimer updated = readingTimerRepository.saveAndFlush(saved);

        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
