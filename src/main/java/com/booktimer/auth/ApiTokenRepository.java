package com.booktimer.auth;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@link ApiToken} 영속성 저장소 — 조회 키는 언제나 평문이 아니라 해시다. */
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    /** 계정 삭제 시 FK 자식 정리용(users를 참조하므로 유저보다 먼저 지워야 한다 — T-029). */
    void deleteByUser(User user);
}
