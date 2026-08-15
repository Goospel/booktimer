package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /**
     * 피드: viewer가 팔로우한 작성자의 여백 (sns-design §13.2).
     *
     * <p>Follow와는 매핑된 연관이 없어 theta 조인({@code f.followee = u})으로 묶는다
     * ({@code BookRepository.followScopePopularity} 미러). 차단 필터는 쿼리에 불필요 —
     * "팔로우 존재 → 차단 없음" write-시점 불변식(차단 시 팔로우 양방향 해제)이 보장하고,
     * 행동 회귀 테스트가 못 박는다. ADMIN·공개핸들(login_id) 미설정 작성자는 노출 불변식대로
     * 제외(N-055 — {@code FollowRepository.findFriendsOfFriends} 전례).
     *
     * <p><b>시간 만료는 없다</b>(2026-08-16) — 여백은 남는 글이라 나이로 거르지 않는다. 대신
     * {@code pageable}이 <b>최신부터</b> 잘라 무한 성장을 막는다. 그래서 정렬이 작성자별이 아니라
     * 전체 최신순이어야 한다(작성자 묶기·재생순 복원은 {@link StoryService}가 한다) — 작성자순으로
     * 정렬하면 상한이 "id가 작은 작성자만" 남기는 엉뚱한 절단이 된다. 동시각 tie는 id로 갈라
     * 상한 경계가 호출마다 흔들리지 않게 한다.
     *
     * <p>작성자·첨부 책은 fetch로 즉시 초기화 — 카드 조립이 책 라벨 재검사({@code isPublic})까지
     * 읽으므로 홈 진입 핫패스의 N+1을 막는다. ToOne fetch라 페이징과 함께 써도 안전하다
     * (컬렉션 fetch였다면 메모리 페이징으로 떨어진다).
     */
    @Query("""
            select s from Story s join fetch s.user u left join fetch s.book, com.booktimer.follow.Follow f
            where f.followee = u and f.follower = :viewer
              and u.role <> com.booktimer.user.Role.ADMIN
              and u.loginId is not null
            order by s.createdAt desc, s.id desc
            """)
    List<Story> feedOf(@Param("viewer") User viewer, Pageable pageable);

    /**
     * 한 사람의 여백 — 홈 스트립 mine·책방 링용. <b>최신순</b>이라 {@code pageable} 상한이 최근 것을
     * 남긴다(뷰어 재생 순서인 작성순 복원은 {@link StoryService}). 책 라벨용 book 즉시 로딩(N+1 회피).
     */
    @EntityGraph(attributePaths = "book")
    List<Story> findByUserOrderByCreatedAtDescIdDesc(User user, Pageable pageable);

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
