package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 캐시된 책BTI 결과 영속성(Phase 4). 사용자당 0~1행.
 */
public interface ReadingPersonalityCacheRepository extends JpaRepository<ReadingPersonalityCache, Long> {

    /** 사용자의 캐시된 성향 결과(없으면 비어 있음). */
    Optional<ReadingPersonalityCache> findByUser(User user);

    /** 회원 탈퇴 시 해당 유저의 캐시를 제거한다(user_id FK 정리). */
    void deleteByUser(User user);
}
