package com.booktimer.block;

import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.follow.FollowService;
import com.booktimer.profile.ProfileService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 차단(block) 서비스 통합 테스트 (실제 빈 + H2) — SNS 5단계, 대칭 차단.
 *
 * <p>차단은 대칭이다: 관계가 한 방향이라도 있으면 둘은 서로 팔로우 못 하고 서로 프로필을 못 본다.
 * 차단 시 기존 팔로우는 양방향 제거. 자기 차단 금지·중복 멱등·언차단 즉시.
 */
@SpringBootTest
@Transactional
class BlockServiceTest {

    @Autowired
    private BlockService blockService;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private FollowService followService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private UserRepository userRepository;

    private User user(String email, String nick) {
        return userRepository.saveAndFlush(User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("차단하면 양방향 기존 팔로우가 제거되고, 차단 관계는 대칭으로 인식된다")
    void block_removesFollowsBothWays_symmetric() {
        User a = user("a@booktimer.com", "에이");
        User b = user("b@booktimer.com", "비");
        followRepository.saveAndFlush(Follow.of(a, b));
        followRepository.saveAndFlush(Follow.of(b, a));

        blockService.block(a, b);

        assertThat(blockService.isBlockedBetween(a, b)).isTrue();
        assertThat(blockService.isBlockedBetween(b, a)).isTrue(); // 대칭
        assertThat(followService.isFollowing(a, b)).isFalse();
        assertThat(followService.isFollowing(b, a)).isFalse();
    }

    @Test
    @DisplayName("차단 관계가 있으면 어느 쪽도 새로 팔로우할 수 없다(조용히 무시)")
    void blockedPair_cannotFollowEitherDirection() {
        User a = user("a2@booktimer.com", "에이2");
        User b = user("b2@booktimer.com", "비2");
        blockService.block(a, b);

        followService.follow(b, a);
        followService.follow(a, b);

        assertThat(followService.isFollowing(b, a)).isFalse();
        assertThat(followService.isFollowing(a, b)).isFalse();
    }

    @Test
    @DisplayName("차단 관계면 상대 프로필이 보이지 않는다(대칭, profileOf 빈 결과 → 404)")
    void blockedPair_profileHidden() {
        User a = user("a3@booktimer.com", "에이3");
        User b = user("b3@booktimer.com", "비3");
        blockService.block(a, b);

        assertThat(profileService.profileOf(a, "비3")).isEmpty();   // 차단한 쪽도 못 봄
        assertThat(profileService.profileOf(b, "에이3")).isEmpty(); // 차단당한 쪽도 못 봄
    }

    @Test
    @DisplayName("중복 차단은 멱등(1행), 자기 차단은 거부")
    void block_idempotent_andSelfRejected() {
        User a = user("a4@booktimer.com", "에이4");
        User b = user("b4@booktimer.com", "비4");

        blockService.block(a, b);
        blockService.block(a, b); // 멱등
        assertThat(blockRepository.findByBlockerOrderByCreatedAtDesc(a)).hasSize(1);

        assertThatThrownBy(() -> blockService.block(a, a)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("언차단하면 차단 관계가 사라진다")
    void unblock_removesBlock() {
        User a = user("a5@booktimer.com", "에이5");
        User b = user("b5@booktimer.com", "비5");
        blockService.block(a, b);

        blockService.unblock(a, b);

        assertThat(blockService.isBlockedBetween(a, b)).isFalse();
    }
}
