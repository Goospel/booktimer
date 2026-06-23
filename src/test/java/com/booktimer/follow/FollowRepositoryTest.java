package com.booktimer.follow;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FollowRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>두 가지를 못 박는다:
 * <ul>
 *   <li>{@link FollowRepository#findFollowedIdsAmong} — viewer가 후보 중 실제로 팔로우한 followee id만
 *       (행당 {@code existsByFollowerAndFollowee} N+1을 단일 쿼리로 대체). 부분집합·방향을 단언.</li>
 *   <li>{@code findByFollowee/FollowerOrderByCreatedAtDesc}의 {@code @EntityGraph} — 관계 행 조회 시
 *       상대 User가 즉시 초기화되는지(목록 조립의 lazy User ×N 제거).</li>
 * </ul>
 *
 * <p>fetch 검증은 {@code em.flush()+clear()}로 1차 캐시를 비운 뒤 조회해야 한다 — 캐시가 살아 있으면
 * EntityGraph 없이도 isInitialized가 true가 돼 가짜 GREEN이 된다.
 */
@DataJpaTest
@Import(JpaConfig.class)
class FollowRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    private void follow(User follower, User followee) {
        followRepository.save(Follow.of(follower, followee));
    }

    // --- findFollowedIdsAmong: 부분집합·방향 ---

    @Test
    @DisplayName("findFollowedIdsAmong: viewer가 팔로우한 후보만 부분집합으로 돌려준다")
    void returnsOnlyFollowedSubset() {
        User viewer = persistedUser("v@booktimer.com");
        User a = persistedUser("a@booktimer.com");
        User b = persistedUser("b@booktimer.com");
        User c = persistedUser("c@booktimer.com"); // 팔로우 안 함
        follow(viewer, a);
        follow(viewer, b);

        List<Long> ids = followRepository.findFollowedIdsAmong(
                viewer, List.of(a.getId(), b.getId(), c.getId()));

        assertThat(ids).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    @DisplayName("findFollowedIdsAmong: 방향이 중요 — A가 viewer를 팔로우(역방향)해도 viewer→A가 없으면 미포함")
    void directionMatters() {
        User viewer = persistedUser("v@booktimer.com");
        User a = persistedUser("a@booktimer.com");
        follow(a, viewer); // 역방향만(A→viewer)

        List<Long> ids = followRepository.findFollowedIdsAmong(viewer, List.of(a.getId()));

        assertThat(ids).isEmpty();
    }

    // --- @EntityGraph fetch 초기화 (flush/clear 필수) ---

    @Test
    @DisplayName("findByFolloweeOrderByCreatedAtDesc: follower가 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void followersInitializeFollower() {
        User viewer = persistedUser("v@booktimer.com");
        User a = persistedUser("a@booktimer.com");
        follow(a, viewer); // a가 viewer를 팔로우 → viewer의 팔로워 = a

        em.flush();
        em.clear();

        List<Follow> rows = followRepository.findByFolloweeOrderByCreatedAtDesc(viewer);

        assertThat(rows).hasSize(1);
        assertThat(Hibernate.isInitialized(rows.get(0).getFollower())).isTrue();
    }

    @Test
    @DisplayName("findByFollowerOrderByCreatedAtDesc: followee가 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void followingInitializeFollowee() {
        User viewer = persistedUser("v@booktimer.com");
        User a = persistedUser("a@booktimer.com");
        follow(viewer, a); // viewer가 a를 팔로우 → viewer의 팔로잉 = a

        em.flush();
        em.clear();

        List<Follow> rows = followRepository.findByFollowerOrderByCreatedAtDesc(viewer);

        assertThat(rows).hasSize(1);
        assertThat(Hibernate.isInitialized(rows.get(0).getFollowee())).isTrue();
    }
}
