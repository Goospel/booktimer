package com.booktimer.follow;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Follow 영속성. 관계 존재 확인·삭제·카운트·본인 목록 조회를 다룬다.
 */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerAndFollowee(User follower, User followee);

    /**
     * viewer가 {@code targetIds} 중 실제로 팔로우 중인 followee id만 — 사용자 행 목록 조립의 행당
     * {@code existsByFollowerAndFollowee} N+1을 단일 쿼리로 대체한다. 방향은 {@code viewer→target}만
     * (역방향은 미포함). 결과는 부분집합이므로 호출부에서 {@code Set} 멤버십으로 following을 판정한다.
     */
    @Query("select f.followee.id from Follow f where f.follower = :viewer and f.followee.id in :targetIds")
    List<Long> findFollowedIdsAmong(@Param("viewer") User viewer, @Param("targetIds") Collection<Long> targetIds);

    void deleteByFollowerAndFollowee(User follower, User followee);

    /** followee를 팔로우하는 사람 수(= 팔로워 수). */
    long countByFollowee(User followee);

    /** follower가 팔로우하는 사람 수(= 팔로잉 수). */
    long countByFollower(User follower);

    /**
     * 나를 팔로우한 관계(= 내 팔로워), 최근 맺은 순. 본인 목록 페이지에서만 쓴다.
     * {@code @EntityGraph}로 follower(상대 User)를 즉시 로딩 — 행 조립의 lazy User ×N 제거.
     */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeOrderByCreatedAtDesc(User followee);

    /**
     * 내가 건 팔로우 관계(= 내 팔로잉), 최근 맺은 순. 본인 목록 페이지에서만 쓴다.
     * {@code @EntityGraph}로 followee(상대 User)를 즉시 로딩 — 행 조립의 lazy User ×N 제거.
     */
    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerOrderByCreatedAtDesc(User follower);

    /** 회원 탈퇴 정리 — 내가 건 관계와 나를 향한 관계를 모두 제거한다. */
    void deleteByFollower(User follower);

    void deleteByFollowee(User followee);
}
