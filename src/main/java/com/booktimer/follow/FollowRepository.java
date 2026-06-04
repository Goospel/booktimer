package com.booktimer.follow;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Follow 영속성. 관계 존재 확인·삭제·카운트·본인 목록 조회를 다룬다.
 */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerAndFollowee(User follower, User followee);

    void deleteByFollowerAndFollowee(User follower, User followee);

    /** followee를 팔로우하는 사람 수(= 팔로워 수). */
    long countByFollowee(User followee);

    /** follower가 팔로우하는 사람 수(= 팔로잉 수). */
    long countByFollower(User follower);

    /** 나를 팔로우한 관계(= 내 팔로워), 최근 맺은 순. 본인 목록 페이지에서만 쓴다. */
    List<Follow> findByFolloweeOrderByCreatedAtDesc(User followee);

    /** 내가 건 팔로우 관계(= 내 팔로잉), 최근 맺은 순. 본인 목록 페이지에서만 쓴다. */
    List<Follow> findByFollowerOrderByCreatedAtDesc(User follower);

    /** 회원 탈퇴 정리 — 내가 건 관계와 나를 향한 관계를 모두 제거한다. */
    void deleteByFollower(User follower);

    void deleteByFollowee(User followee);
}
