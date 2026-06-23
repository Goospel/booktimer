package com.booktimer.block;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Block 영속성. 단방향 저장이지만 게이트는 양방향({@link #existsBetween})으로 검사한다(대칭 효과).
 */
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    void deleteByBlockerAndBlocked(User blocker, User blocked);

    /** 두 사용자 사이에 (방향 무관) 차단이 존재하는가 — 팔로우·프로필 게이트가 쓰는 대칭 검사. */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker = :a and b.blocked = :b) or (b.blocker = :b and b.blocked = :a)
            """)
    boolean existsBetween(@Param("a") User a, @Param("b") User b);

    /**
     * 내가 차단한 사람들(차단 목록 페이지), 최근 차단 순.
     * {@code @EntityGraph}로 blocked(상대 User)를 즉시 로딩 — 행 조립의 lazy User ×N 제거.
     */
    @EntityGraph(attributePaths = "blocked")
    List<Block> findByBlockerOrderByCreatedAtDesc(User blocker);

    /** 회원 탈퇴 정리 — 내가 건 차단과 나를 향한 차단을 모두 제거한다. */
    void deleteByBlocker(User blocker);

    void deleteByBlocked(User blocked);
}
