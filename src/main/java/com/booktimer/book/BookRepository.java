package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Book 영속성. User와 N:1.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** 내 책장(최신 등록 먼저). */
    List<Book> findByUserOrderByCreatedAtDesc(User user);

    /** 공개 범위로 거른 책장 — 프로필 페이지에서 PUBLIC 책만 노출(최신 등록 먼저). */
    List<Book> findByUserAndVisibilityOrderByCreatedAtDesc(User user, BookVisibility visibility);

    /** 공개 범위별 책 수 — 검색 결과의 "공개 책 N권" 표시에 쓰인다. */
    long countByUserAndVisibility(User user, BookVisibility visibility);

    /** 소유권 확인용 — 내 책일 때만 조회된다(IDOR 방지). */
    Optional<Book> findByIdAndUser(Long id, User user);

    /** 회원 탈퇴 시 해당 유저의 모든 책을 제거한다. */
    void deleteByUser(User user);

    /**
     * 팔로우 스코프 인기 카운트 (sns-design §7.4) — 주어진 isbn 목록을 <b>한 번의 group by</b>로 집계(N+1 회피).
     *
     * <p>{@code viewer}가 팔로우한 사용자(followee)가 PUBLIC으로 가진 책만 대상으로, isbn13별로
     * 원함(WANT_TO_READ)·읽음(READING∪FINISHED) distinct 사용자 수를 센다. Follow와는 매핑된 연관이
     * 없어 theta 조인({@code f.followee = b.user})으로 묶는다. PRIVATE·비팔로우·본인(자기 팔로우 없음)은 자연 제외.
     */
    @Query("""
            select b.isbn13 as isbn,
                   count(distinct case when b.status = com.booktimer.book.BookStatus.WANT_TO_READ
                                       then b.user.id end) as wantCount,
                   count(distinct case when b.status in (com.booktimer.book.BookStatus.READING,
                                                         com.booktimer.book.BookStatus.FINISHED)
                                       then b.user.id end) as readCount
            from Book b, com.booktimer.follow.Follow f
            where f.followee = b.user
              and f.follower = :viewer
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and b.isbn13 in :isbns
            group by b.isbn13
            """)
    List<FollowScopeCount> followScopePopularity(@Param("viewer") User viewer,
                                                 @Param("isbns") Collection<String> isbns);

    /**
     * 팔로우 스코프 인기 카운트 <b>drill-down</b> — 한 isbn을 {@code viewer}가 팔로우한 사용자(followee)
     * 중 주어진 {@code statuses}로 PUBLIC 보유한 사람을 distinct로 돌려준다(닉네임 정렬은 호출부에서).
     *
     * <p>카운트({@link #followScopePopularity})와 <b>같은 게이트</b>(팔로우·PUBLIC·distinct)라 명단과 숫자가
     * 어긋나지 않는다. 노출되는 책은 어차피 각 팔로우 프로필의 PUBLIC 책장에서 볼 수 있는 것뿐(새 노출 없음).
     * PRIVATE·비팔로우·본인(자기 팔로우 없음)은 자연 제외.
     */
    @Query("""
            select distinct b.user
            from Book b, com.booktimer.follow.Follow f
            where f.followee = b.user
              and f.follower = :viewer
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and b.isbn13 = :isbn
              and b.status in :statuses
            """)
    List<User> followScopeReaders(@Param("viewer") User viewer,
                                  @Param("isbn") String isbn,
                                  @Param("statuses") Collection<BookStatus> statuses);
}
