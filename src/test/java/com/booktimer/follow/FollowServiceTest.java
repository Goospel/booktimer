package com.booktimer.follow;

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
 * FollowService 통합 테스트 (실제 빈 + H2) — 팔로우 관계의 도메인 규칙 (sns-design §7.3·§4).
 *
 * <p>핵심: ① 자기 자신 팔로우 금지, ② 중복 팔로우 멱등(두 번 눌러도 1행), ③ 언팔로우 즉시,
 * ④ 팔로워/팔로잉 카운트 정확.
 */
@SpringBootTest
@Transactional
class FollowServiceTest {

    @Autowired
    private FollowService followService;
    @Autowired
    private UserRepository userRepository;

    private User newUser(String email, String nickname) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("팔로우하면 관계가 생기고 isFollowing이 true가 된다")
    void follow_createsRelation() {
        User a = newUser("a@booktimer.com", "에이");
        User b = newUser("b@booktimer.com", "비");

        followService.follow(a, b);

        assertThat(followService.isFollowing(a, b)).isTrue();
        assertThat(followService.isFollowing(b, a)).isFalse(); // 단방향
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    void follow_self_rejected() {
        User a = newUser("a@booktimer.com", "에이");

        assertThatThrownBy(() -> followService.follow(a, a))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 사람을 두 번 팔로우해도 관계는 하나다(멱등)")
    void follow_duplicate_idempotent() {
        User a = newUser("a@booktimer.com", "에이");
        User b = newUser("b@booktimer.com", "비");

        followService.follow(a, b);
        followService.follow(a, b); // 두 번째

        assertThat(followService.isFollowing(a, b)).isTrue();
        assertThat(followService.followerCount(b)).isEqualTo(1L);
    }

    @Test
    @DisplayName("언팔로우하면 관계가 사라지고 팔로워 수가 준다")
    void unfollow_removesRelation() {
        User a = newUser("a@booktimer.com", "에이");
        User b = newUser("b@booktimer.com", "비");
        followService.follow(a, b);

        followService.unfollow(a, b);

        assertThat(followService.isFollowing(a, b)).isFalse();
        assertThat(followService.followerCount(b)).isZero();
    }

    @Test
    @DisplayName("팔로워/팔로잉 카운트가 정확하다")
    void counts_areAccurate() {
        User a = newUser("a@booktimer.com", "에이");
        User b = newUser("b@booktimer.com", "비");
        User c = newUser("c@booktimer.com", "씨");

        followService.follow(a, c); // a→c
        followService.follow(b, c); // b→c

        assertThat(followService.followerCount(c)).isEqualTo(2L);  // c를 팔로우하는 사람 둘
        assertThat(followService.followingCount(a)).isEqualTo(1L); // a가 팔로우하는 사람 하나
        assertThat(followService.followingCount(c)).isZero();
    }
}
