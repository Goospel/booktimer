package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
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

    /** 사용자의 총 책 수 — 관리자 드릴다운 책장 요약. */
    long countByUser(User user);

    /** 사용자의 상태(읽고싶음/읽는중/완독)별 책 수 — 관리자 드릴다운 책장 요약. */
    long countByUserAndStatus(User user, BookStatus status);

    /** 소유권 확인용 — 내 책일 때만 조회된다(IDOR 방지). */
    Optional<Book> findByIdAndUser(Long id, User user);

    /**
     * 같은 사용자가 같은 isbn13으로 이미 가진 책 — 검색 등록 시 중복 행 방지(직접 POST 방어).
     * isbn13은 적재 시 정규화돼 저장되므로({@link Isbn#normalize}) 조회 키도 정규화한 값을 넘긴다.
     */
    Optional<Book> findFirstByUserAndIsbn13(User user, String isbn13);

    /** 회원 탈퇴 시 해당 유저의 모든 책을 제거한다. */
    void deleteByUser(User user);

    /**
     * 카탈로그 백필 대상 — 장르(category)가 아직 비었고 ISBN-13이 있는 책(책BTI Phase 1b).
     * isbn이 있어야 알라딘 ItemLookUp으로 채울 수 있다(없으면 조회 키가 없어 제외). {@code Pageable}로
     * 한 번에 처리할 권수를 제한한다(외부 호출량·요청시간 통제).
     */
    List<Book> findByCategoryIsNullAndIsbn13IsNotNull(Pageable pageable);

    /** 백필이 남은 책 수(장르 비었고 isbn 있는) — 백필 결과의 "남은 권수" 표시용. */
    long countByCategoryIsNullAndIsbn13IsNotNull();

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
