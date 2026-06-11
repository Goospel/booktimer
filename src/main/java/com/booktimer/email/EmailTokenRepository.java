package com.booktimer.email;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link EmailToken} 저장소. 검증은 해시로 조회하고(평문 미저장), 재발급 시 같은 user+type의 기존
 * 미사용 토큰을 무효화하기 위해 미사용분을 조회한다. 탈퇴 정리는 {@code deleteByUser}(FK).
 */
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /** 해시로 토큰을 찾는다(검증 진입점). 평문은 호출자가 해싱해 넘긴다. */
    Optional<EmailToken> findByTokenHash(String tokenHash);

    /** 같은 사용자·용도의 아직 안 쓴 토큰들 — 재발급 시 무효화 대상. */
    List<EmailToken> findByUserAndTypeAndUsedAtIsNull(User user, EmailTokenType type);

    /** 탈퇴 정리용 — 해당 사용자의 모든 토큰 삭제(FK: email_token.user_id → users). */
    void deleteByUser(User user);
}
