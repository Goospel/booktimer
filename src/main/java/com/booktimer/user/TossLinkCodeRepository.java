package com.booktimer.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** {@link TossLinkCode} 영속성 저장소 — 조회 키는 언제나 평문이 아니라 해시다. */
public interface TossLinkCodeRepository extends JpaRepository<TossLinkCode, Long> {

    Optional<TossLinkCode> findByCodeHash(String codeHash);

    /** 재발급 시 직전 코드를 무효화하기 위한 조회(항상 하나만 유효). */
    List<TossLinkCode> findByUserAndUsedAtIsNull(User user);

    /** 계정 삭제 시 FK 자식 정리용(users를 참조하므로 유저보다 먼저 지워야 한다 — T-029). */
    void deleteByUser(User user);
}
