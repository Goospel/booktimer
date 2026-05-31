package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserRepository 슬라이스 테스트 (@DataJpaTest, H2 인메모리).
 *
 * <p>저장/조회, email 기반 조회, email 유니크 제약(uk_users_email)을 검증한다.
 * 운영은 MySQL이지만 슬라이스 테스트는 H2로 도커 없이 독립 실행.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User sampleUser(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("저장 후 email로 조회된다 (id 부여 확인)")
    void save_thenFindByEmail() {
        userRepository.save(sampleUser("reader@booktimer.com"));

        Optional<User> found = userRepository.findByEmail("reader@booktimer.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getNickname()).isEqualTo("책벌레");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("없는 email 조회는 Optional.empty")
    void findByEmail_absent_empty() {
        Optional<User> found = userRepository.findByEmail("none@booktimer.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 email 중복 저장은 유니크 제약 위반")
    void duplicateEmail_violatesUnique() {
        userRepository.saveAndFlush(sampleUser("dup@booktimer.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(sampleUser("dup@booktimer.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
