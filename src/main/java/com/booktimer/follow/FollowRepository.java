package com.booktimer.follow;

import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
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

    /**
     * 홈 「함께 읽는 사람」 탭의 모집단 — 내가 팔로우한 사람들, 최근 맺은 순.
     *
     * <p>{@link #findByFollowerOrderByCreatedAtDesc}와 달리 <b>노출 불변식을 쿼리에 건다</b>
     * (ADMIN 제외 · 공개핸들 {@code login_id} 미설정 제외 — N-055). 그쪽은 본인 팔로잉 목록 화면용이라
     * 내가 맺은 관계를 빠짐없이 보여주는 게 맞지만, 이건 <b>남을 노출하는 목록</b>이라 소식 피드
     * ({@code BookRepository.feedStarted})와 같은 게이트를 통과해야 한다.
     *
     * <p>차단 필터는 불필요 — "팔로우 존재 → 차단 없음" write-시점 불변식(차단 시 팔로우 양방향 해제)이
     * 보장한다. {@code Pageable}로 상한.
     */
    @Query("""
            select f.followee from Follow f
            where f.follower = :viewer
              and f.followee.role <> com.booktimer.user.Role.ADMIN
              and f.followee.loginId is not null
            order by f.createdAt desc, f.followee.id asc
            """)
    List<User> findVisibleFollowees(@Param("viewer") User viewer, Pageable pageable);

    /**
     * 주어진 후보들 중 <b>나를</b> 팔로우하는 사람의 id — {@link #findFollowedIdsAmong}의 거울(방향만 반대).
     *
     * <p>둘을 교차하면 맞팔이 나온다: 호출부가 이미 "내가 팔로우한 사람들"을 손에 쥐고 있으므로
     * 이 결과에 속하면 곧 맞팔이다. 행당 {@code existsByFollowerAndFollowee} N+1을 단일 쿼리로 대체한다.
     */
    @Query("select f.follower.id from Follow f where f.followee = :viewer and f.follower.id in :targetIds")
    List<Long> findFollowerIdsAmong(@Param("viewer") User viewer, @Param("targetIds") Collection<Long> targetIds);

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

    /**
     * 친구의 친구(FoF) 추천 후보 — 친구 추천 하이브리드 1단계(G2).
     *
     * <p>내가 팔로우한 사람(f1.followee)이 팔로우하는 사람(f2.followee)을 후보로, 공통 친구 수(distinct f1)
     * 내림차순으로 돌려준다. 노출 불변식을 쿼리에 모두 보존한다: 본인 제외·ADMIN 제외·공개핸들(login_id)
     * 미설정 제외(N-055)·차단(양방향) 제외 + <b>내가 이미 팔로우한 사람 제외</b>(추천은 새 사람을 찾는 것).
     * 동률은 id 오름차순으로 결정적 정렬. {@code Pageable}로 상한.
     */
    @Query("""
            select f2.followee.id as userId,
                   count(distinct f1.followee.id) as commonFollowCount
            from Follow f1, Follow f2
            where f1.follower.id = :viewerId
              and f2.follower.id = f1.followee.id
              and f2.followee.id <> :viewerId
              and f2.followee.role <> com.booktimer.user.Role.ADMIN
              and f2.followee.loginId is not null
              and not exists (select 1 from Follow f3
                              where f3.follower.id = :viewerId and f3.followee.id = f2.followee.id)
              and not exists (select 1 from com.booktimer.block.Block b
                              where (b.blocker.id = :viewerId and b.blocked.id = f2.followee.id)
                                 or (b.blocker.id = f2.followee.id and b.blocked.id = :viewerId))
            group by f2.followee.id
            order by count(distinct f1.followee.id) desc, f2.followee.id asc
            """)
    List<FriendOfFriendCount> findFriendsOfFriends(@Param("viewerId") Long viewerId, Pageable pageable);

    /**
     * 맞팔 후보 — 나를 팔로우했는데 내가 아직 안 한 사람의 id, 최근 맺은 순 — 친구 추천 하이브리드 1단계(G1).
     *
     * <p>역방향 에지(f.follower→viewer)가 곧 후보다. FoF와 같은 노출 불변식을 보존한다(ADMIN·login_id null·
     * 차단 양방향 제외 + 내가 이미 팔로우한 사람 제외 = "맞팔 안 한"의 정의). {@code Pageable}로 상한.
     */
    @Query("""
            select f.follower.id
            from Follow f
            where f.followee.id = :viewerId
              and f.follower.role <> com.booktimer.user.Role.ADMIN
              and f.follower.loginId is not null
              and not exists (select 1 from Follow f2
                              where f2.follower.id = :viewerId and f2.followee.id = f.follower.id)
              and not exists (select 1 from com.booktimer.block.Block b
                              where (b.blocker.id = :viewerId and b.blocked.id = f.follower.id)
                                 or (b.blocker.id = f.follower.id and b.blocked.id = :viewerId))
            order by f.createdAt desc, f.follower.id asc
            """)
    List<Long> findFollowBackCandidateIds(@Param("viewerId") Long viewerId, Pageable pageable);
}
