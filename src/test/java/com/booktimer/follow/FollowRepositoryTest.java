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
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    @Autowired
    private BlockRepository blockRepository;

    private static final Pageable TOP10 = PageRequest.of(0, 10);

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    /** 공개 핸들(login_id)을 가진 일반 사용자 — 추천 후보 적격(login_id null 가드 통과). */
    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private User admin(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "운영자", "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void follow(User follower, User followee) {
        followRepository.save(Follow.of(follower, followee));
    }

    private void block(User blocker, User blocked) {
        blockRepository.save(Block.of(blocker, blocked));
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

    // --- 친구 추천: FoF (findFriendsOfFriends) ---

    @Test
    @DisplayName("FoF: 내 팔로이가 팔로우하는 사람을 공통 친구 수와 함께 후보로 (공통 친구 desc)")
    void fof_returnsCandidatesByCommonFollowCountDesc() {
        User viewer = user("v@booktimer.com", "viewer");
        User a = user("a@booktimer.com", "afriend");
        User b = user("b@booktimer.com", "bfriend");
        User c = user("c@booktimer.com", "cand2"); // a·b 둘 다 팔로우 → 공통 2
        User d = user("d@booktimer.com", "cand1"); // a만 팔로우 → 공통 1
        follow(viewer, a);
        follow(viewer, b);
        follow(a, c);
        follow(b, c);
        follow(a, d);

        List<FriendOfFriendCount> rows = followRepository.findFriendsOfFriends(viewer.getId(), TOP10);

        assertThat(rows).extracting(FriendOfFriendCount::getUserId, FriendOfFriendCount::getCommonFollowCount)
                .containsExactly(tuple(c.getId(), 2L), tuple(d.getId(), 1L));
    }

    @Test
    @DisplayName("FoF: 내가 이미 팔로우한 사람·본인은 후보에서 제외 (새 사람만 추천)")
    void fof_excludesAlreadyFollowedAndSelf() {
        User viewer = user("v@booktimer.com", "viewer");
        User a = user("a@booktimer.com", "afriend");
        User c = user("c@booktimer.com", "cand");
        follow(viewer, a);
        follow(a, c);
        follow(viewer, c); // 이미 팔로우 → c 제외
        follow(a, viewer); // a가 viewer를 팔로우 → viewer가 후보로 보일 수 있으나 self 제외

        List<FriendOfFriendCount> rows = followRepository.findFriendsOfFriends(viewer.getId(), TOP10);

        assertThat(rows).extracting(FriendOfFriendCount::getUserId)
                .doesNotContain(c.getId(), viewer.getId());
    }

    @Test
    @DisplayName("FoF: ADMIN·login_id null·차단(양방향) 후보는 제외 (노출 불변식 보존)")
    void fof_excludesInvariants() {
        User viewer = user("v@booktimer.com", "viewer");
        User a = user("a@booktimer.com", "afriend");
        follow(viewer, a);

        User adminC = admin("admin@booktimer.com", "admincand");
        User nullC = persistedUser("null@booktimer.com"); // login_id 미설정(N-055)
        User blockedC = user("bk@booktimer.com", "blockedcand"); // viewer가 차단
        User blockerC = user("br@booktimer.com", "blockercand"); // viewer를 차단(역방향)
        User ok = user("ok@booktimer.com", "okcand");
        follow(a, adminC);
        follow(a, nullC);
        follow(a, blockedC);
        follow(a, blockerC);
        follow(a, ok);
        block(viewer, blockedC);
        block(blockerC, viewer);

        List<FriendOfFriendCount> rows = followRepository.findFriendsOfFriends(viewer.getId(), TOP10);

        assertThat(rows).extracting(FriendOfFriendCount::getUserId)
                .containsExactly(ok.getId())
                .doesNotContain(adminC.getId(), nullC.getId(), blockedC.getId(), blockerC.getId());
    }

    // --- 친구 추천: 맞팔 후보 (findFollowBackCandidateIds) ---

    @Test
    @DisplayName("맞팔 후보: 나를 팔로우했는데 내가 안 한 사람만 (이미 맞팔하면 제외)")
    void followBack_returnsOnlyNonReciprocated() {
        User viewer = user("v@booktimer.com", "viewer");
        User x = user("x@booktimer.com", "xfollower"); // x→viewer, viewer 안 함 → 후보
        User y = user("y@booktimer.com", "yfollower"); // 맞팔 → 제외
        follow(x, viewer);
        follow(y, viewer);
        follow(viewer, y);

        List<Long> ids = followRepository.findFollowBackCandidateIds(viewer.getId(), TOP10);

        assertThat(ids).containsExactly(x.getId()).doesNotContain(y.getId());
    }

    @Test
    @DisplayName("맞팔 후보: ADMIN·login_id null·차단(양방향) 제외 (노출 불변식 보존)")
    void followBack_excludesInvariants() {
        User viewer = user("v@booktimer.com", "viewer");
        User adminX = admin("admin@booktimer.com", "adminx");
        User nullX = persistedUser("null@booktimer.com"); // login_id 미설정(N-055)
        User blockedX = user("bk@booktimer.com", "blockedx"); // viewer가 차단
        User blockerX = user("br@booktimer.com", "blockerx"); // viewer를 차단(역방향)
        User okX = user("ok@booktimer.com", "okx");
        follow(adminX, viewer);
        follow(nullX, viewer);
        follow(blockedX, viewer);
        follow(blockerX, viewer);
        follow(okX, viewer);
        block(viewer, blockedX);
        block(blockerX, viewer);

        List<Long> ids = followRepository.findFollowBackCandidateIds(viewer.getId(), TOP10);

        assertThat(ids).containsExactly(okX.getId())
                .doesNotContain(adminX.getId(), nullX.getId(), blockedX.getId(), blockerX.getId());
    }
}
