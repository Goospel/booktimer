package com.booktimer.block;

import com.booktimer.follow.FollowRepository;
import com.booktimer.search.UserRowAssembler;
import com.booktimer.search.UserSearchResult;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 차단 유스케이스 (SNS 5단계, sns-design §7.5). 대칭 차단.
 *
 * <p>차단하면 둘 사이 기존 팔로우를 <b>양방향 제거</b>하고 차단 행을 남긴다(중복은 멱등). 차단 관계가 있으면
 * 팔로우({@code FollowService})·프로필({@code ProfileService}) 게이트가 양방향으로 막는다.
 * 순환 의존을 피하려고 서비스가 아닌 리포지토리({@link FollowRepository})로 팔로우를 정리한다.
 */
@Service
@Transactional
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRowAssembler rowAssembler;

    public BlockService(BlockRepository blockRepository,
                        FollowRepository followRepository,
                        UserRowAssembler rowAssembler) {
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.rowAssembler = rowAssembler;
    }

    /**
     * blocker가 blocked를 차단한다. 자기 차단은 거부, 이미 차단 중이면 멱등(아무것도 안 함).
     * 차단 시 둘 사이 기존 팔로우를 양방향으로 끊는다.
     *
     * @throws IllegalArgumentException 같은 사용자거나 null인 경우
     */
    public void block(User blocker, User blocked) {
        if (blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return; // 멱등
        }
        Block block = Block.of(blocker, blocked); // null/자기 차단 검증
        followRepository.deleteByFollowerAndFollowee(blocker, blocked);
        followRepository.deleteByFollowerAndFollowee(blocked, blocker);
        blockRepository.save(block);
    }

    /** blocker가 blocked 차단을 해제한다. 관계가 없어도 무방(멱등). */
    public void unblock(User blocker, User blocked) {
        blockRepository.deleteByBlockerAndBlocked(blocker, blocked);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedBetween(User a, User b) {
        return blockRepository.existsBetween(a, b);
    }

    /** 내가 차단한 사용자 목록(차단 목록 페이지용). */
    @Transactional(readOnly = true)
    public List<UserSearchResult> blockedUsers(User blocker) {
        return blockRepository.findByBlockerOrderByCreatedAtDesc(blocker).stream()
                .map(b -> rowAssembler.toRow(blocker, b.getBlocked()))
                .toList();
    }
}
