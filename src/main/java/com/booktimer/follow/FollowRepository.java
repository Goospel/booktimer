package com.booktimer.follow;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Follow 영속성. 관계 존재 확인·삭제·카운트만 다룬다(목록 조회는 후속).
 */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerAndFollowee(User follower, User followee);

    void deleteByFollowerAndFollowee(User follower, User followee);

    /** followee를 팔로우하는 사람 수(= 팔로워 수). */
    long countByFollowee(User followee);

    /** follower가 팔로우하는 사람 수(= 팔로잉 수). */
    long countByFollower(User follower);

    /** 회원 탈퇴 정리 — 내가 건 관계와 나를 향한 관계를 모두 제거한다. */
    void deleteByFollower(User follower);

    void deleteByFollowee(User followee);
}
