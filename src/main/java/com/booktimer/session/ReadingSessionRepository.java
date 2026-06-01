package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * ReadingSession 영속성. User와 N:1.
 */
public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    List<ReadingSession> findByUser(User user);

    /** 진행 중(endedAt == null) 세션. 서비스의 중복 start 방지에 쓰인다. */
    Optional<ReadingSession> findByUserAndEndedAtIsNull(User user);
}
