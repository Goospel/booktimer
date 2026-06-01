package com.booktimer.timer;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReadingTimerRepository 슬라이스 테스트 (@DataJpaTest, H2 인메모리).
 *
 * <p>저장/조회, user 기반 조회, user_id 유니크 제약(1:1)을 검증한다.
 * ReadingTimer가 FK(user_id)를 소유하므로 User를 먼저 저장해야 FK가 충족된다.
 */
@DataJpaTest
class ReadingTimerRepositoryTest {

    private static final long HOUR = 3600L;
    private static final LocalDate DAY0 = LocalDate.of(2026, 5, 31);

    @Autowired
    private ReadingTimerRepository readingTimerRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("저장 후 user로 조회된다 (id 부여 확인)")
    void save_thenFindByUser() {
        User user = persistedUser("reader@booktimer.com");
        readingTimerRepository.save(ReadingTimer.startFor(user, HOUR, 5 * HOUR, DAY0));

        Optional<ReadingTimer> found = readingTimerRepository.findByUser(user);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.get().getDailyIncrementSeconds()).isEqualTo(HOUR);
        assertThat(found.get().getCapSeconds()).isEqualTo(5 * HOUR);
    }

    @Test
    @DisplayName("타이머 없는 user 조회는 Optional.empty")
    void findByUser_absent_empty() {
        User user = persistedUser("none@booktimer.com");

        Optional<ReadingTimer> found = readingTimerRepository.findByUser(user);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 user에 타이머 둘이면 user_id 유니크(1:1) 위반")
    void duplicateUser_violatesUnique() {
        User user = persistedUser("dup@booktimer.com");
        readingTimerRepository.saveAndFlush(ReadingTimer.startFor(user, HOUR, 5 * HOUR, DAY0));

        assertThatThrownBy(() ->
                readingTimerRepository.saveAndFlush(ReadingTimer.startFor(user, HOUR, 5 * HOUR, DAY0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
