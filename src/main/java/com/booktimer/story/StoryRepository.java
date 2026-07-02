package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /**
     * 피드: viewer가 팔로우한 작성자의 활성(미만료) 스토리 (sns-design §13.2).
     *
     * <p>Follow와는 매핑된 연관이 없어 theta 조인({@code f.followee = u})으로 묶는다
     * ({@code BookRepository.followScopePopularity} 미러). 차단 필터는 쿼리에 불필요 —
     * "팔로우 존재 → 차단 없음" write-시점 불변식(차단 시 팔로우 양방향 해제)이 보장하고,
     * 행동 회귀 테스트가 못 박는다. ADMIN·공개핸들(login_id) 미설정 작성자는 노출 불변식대로
     * 제외(N-055 — {@code FollowRepository.findFriendsOfFriends} 전례). 만료는 표시 필터라
     * 정확히 24h 지난 것부터 제외({@code createdAt > cutoff}, §13.8 경계).
     * 작성자·첨부 책은 fetch로 즉시 초기화 — 카드 조립이 책 라벨 재검사({@code isPublic})까지
     * 읽으므로 홈 진입 핫패스의 N+1을 막는다.
     */
    @Query("""
            select s from Story s join fetch s.user u left join fetch s.book, com.booktimer.follow.Follow f
            where f.followee = u and f.follower = :viewer
              and s.createdAt > :cutoff
              and u.role <> com.booktimer.user.Role.ADMIN
              and u.loginId is not null
            order by u.id asc, s.createdAt asc
            """)
    List<Story> feedOf(@Param("viewer") User viewer, @Param("cutoff") Instant cutoff);

    /** 내 활성 스토리 — 홈 스트립 mine·책방 링용, 작성순(뷰어 재생 순서). 책 라벨용 book 즉시 로딩(N+1 회피). */
    @EntityGraph(attributePaths = "book")
    List<Story> findByUserAndCreatedAtAfterOrderByCreatedAtAsc(User user, Instant cutoff);

    /** 활성 스토리 수 — 작성 시 활성 상한(20장) 게이트용. */
    long countByUserAndCreatedAtAfter(User user, Instant cutoff);

    /**
     * 책 삭제 시, 그 책을 첨부한 스토리의 첨부만 푼다(book_id = null — 문장은 보존, §13.3).
     * {@code story.book_id} FK 때문에 이 정리 없이 책을 지우면 제약 위반으로 실패한다
     * ({@code ReadingSessionRepository.unlinkBook} 패턴 미러).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Story s set s.book = null where s.book = :book")
    void unlinkBook(@Param("book") Book book);

    /** 회원 탈퇴 정리용 — story_view 자식을 먼저 지운 뒤 호출해야 한다(AccountService.purge). */
    void deleteByUser(User user);
}
