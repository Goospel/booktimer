package com.booktimer.follow;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 유스케이스 (sns-design §7.3·§4).
 *
 * <p>단방향·즉시 성립(승인 없음). 자기 자신 팔로우 금지, 중복 팔로우는 멱등(두 번 눌러도 1행).
 * 관계만 저장하고 독서 데이터는 건드리지 않는다(N-037).
 */
@Service
@Transactional
public class FollowService {

    private final FollowRepository followRepository;

    public FollowService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    /**
     * follower가 followee를 팔로우한다. 자기 자신은 거부, 이미 팔로우 중이면 아무것도 안 한다(멱등).
     *
     * @throws IllegalArgumentException 같은 사용자거나 null인 경우
     */
    public void follow(User follower, User followee) {
        if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
            return; // 멱등 — 중복 저장 시 유니크 제약 위반을 피한다
        }
        followRepository.save(Follow.of(follower, followee)); // of()가 자기 팔로우/null 검증
    }

    /** follower가 followee를 언팔로우한다. 관계가 없어도 무방(멱등). */
    public void unfollow(User follower, User followee) {
        followRepository.deleteByFollowerAndFollowee(follower, followee);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(User follower, User followee) {
        return followRepository.existsByFollowerAndFollowee(follower, followee);
    }

    /** user를 팔로우하는 사람 수(팔로워 수). */
    @Transactional(readOnly = true)
    public long followerCount(User user) {
        return followRepository.countByFollowee(user);
    }

    /** user가 팔로우하는 사람 수(팔로잉 수). */
    @Transactional(readOnly = true)
    public long followingCount(User user) {
        return followRepository.countByFollower(user);
    }
}
