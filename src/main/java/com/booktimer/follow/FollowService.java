package com.booktimer.follow;

import com.booktimer.block.BlockRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 팔로우 유스케이스 (sns-design §7.3·§4).
 *
 * <p>단방향·즉시 성립(승인 없음). 자기 자신 팔로우 금지, 중복 팔로우는 멱등(두 번 눌러도 1행).
 * 차단 관계(어느 방향이든)가 있으면 팔로우 불가(§7.5). 관계만 저장하고 독서 데이터는 건드리지 않는다(N-037).
 */
@Service
@Transactional
public class FollowService {

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;

    public FollowService(FollowRepository followRepository, BlockRepository blockRepository) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
    }

    /**
     * follower가 followee를 팔로우한다. 자기 자신은 거부, 이미 팔로우 중이면 아무것도 안 한다(멱등).
     * 둘 사이 차단 관계가 있으면 조용히 무시(팔로우 안 됨).
     *
     * @throws IllegalArgumentException 같은 사용자거나 null인 경우
     */
    public void follow(User follower, User followee) {
        if (blockRepository.existsBetween(follower, followee)) {
            return; // 차단 관계면 팔로우 불가(§7.5)
        }
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

    /**
     * viewer가 주어진 후보들({@code targetIds}) 중 팔로우 중인 id 집합 — 사용자 행 목록 조립용 배치
     * (행당 {@link #isFollowing} N+1 회피). 빈 입력이면 빈 집합(쿼리 안 탐). 서비스 경계 유지:
     * assembler는 리포지토리가 아니라 이 서비스를 통해 팔로우 관계를 본다.
     */
    @Transactional(readOnly = true)
    public Set<Long> followingAmong(User viewer, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(followRepository.findFollowedIdsAmong(viewer, targetIds));
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
