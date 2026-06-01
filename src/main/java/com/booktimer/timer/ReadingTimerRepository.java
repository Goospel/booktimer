package com.booktimer.timer;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ReadingTimer 영속성. User와 1:1이므로 user 기준 단건 조회를 제공한다.
 */
public interface ReadingTimerRepository extends JpaRepository<ReadingTimer, Long> {

    Optional<ReadingTimer> findByUser(User user);
}
