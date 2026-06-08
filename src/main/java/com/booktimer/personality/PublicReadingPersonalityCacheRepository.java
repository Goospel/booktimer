package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 책방 노출용 공개 책BTI 캐시 영속성(Phase 6). 사용자당 0~1행. 본인용({@link ReadingPersonalityCacheRepository})과
 * 별도 테이블(reading_personality_public)이라 서로 독립적이다.
 */
public interface PublicReadingPersonalityCacheRepository
        extends JpaRepository<PublicReadingPersonalityCache, Long> {

    /** 사용자의 캐시된 공개 성향 결과(없으면 비어 있음). */
    Optional<PublicReadingPersonalityCache> findByUser(User user);

    /** 회원 탈퇴 시 해당 유저의 공개 캐시를 제거한다(user_id FK 정리). */
    void deleteByUser(User user);
}
