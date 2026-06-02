package com.booktimer.session;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadingSessionRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>user별 세션 목록 조회와 "진행 중(미종료) 세션" 조회를 검증한다.
 * 후자는 서비스의 중복 start 방지에 쓰인다. ReadingSession이 FK(user_id)를
 * 소유하므로 User를 먼저 저장한다.
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화 — 없으면 INSERT 시 NOT NULL 위반
class ReadingSessionRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    @Autowired
    private ReadingSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("user로 세션들이 조회되고, 다른 user의 세션은 제외된다")
    void findByUser_returnsOnlyOwnSessions() {
        User u1 = persistedUser("u1@booktimer.com");
        User u2 = persistedUser("u2@booktimer.com");
        sessionRepository.save(ReadingSession.start(u1, T0));
        ReadingSession ended = ReadingSession.start(u1, T0.plusSeconds(100));
        ended.end(T0.plusSeconds(200));
        sessionRepository.save(ended);
        sessionRepository.save(ReadingSession.start(u2, T0)); // 다른 유저

        List<ReadingSession> u1Sessions = sessionRepository.findByUser(u1);

        assertThat(u1Sessions).hasSize(2);
        assertThat(u1Sessions).allMatch(s -> s.getUser().getId().equals(u1.getId()));
    }

    @Test
    @DisplayName("진행 중(미종료) 세션만 조회된다")
    void findActive_returnsUnendedOnly() {
        User user = persistedUser("active@booktimer.com");
        ReadingSession ended = ReadingSession.start(user, T0);
        ended.end(T0.plusSeconds(60));
        sessionRepository.save(ended);
        ReadingSession active = sessionRepository.save(ReadingSession.start(user, T0.plusSeconds(100)));

        Optional<ReadingSession> found = sessionRepository.findByUserAndEndedAtIsNull(user);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("진행 중 세션이 없으면 Optional.empty")
    void findActive_noneActive_empty() {
        User user = persistedUser("done@booktimer.com");
        ReadingSession ended = ReadingSession.start(user, T0);
        ended.end(T0.plusSeconds(60));
        sessionRepository.save(ended);

        Optional<ReadingSession> found = sessionRepository.findByUserAndEndedAtIsNull(user);

        assertThat(found).isEmpty();
    }
}
