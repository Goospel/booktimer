package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Book 영속성. User와 N:1.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** 내 책장(최신 등록 먼저). */
    List<Book> findByUserOrderByCreatedAtDesc(User user);

    /** 공개 범위로 거른 책장 — 프로필(책방)에서 PUBLIC 책만 노출. 기본 이름순(제목, 동명이면 id로 안정 정렬). */
    List<Book> findByUserAndVisibilityOrderByTitleAscIdAsc(User user, BookVisibility visibility);

    /** 공개 범위별 책 수 — 검색 결과의 "공개 책 N권" 표시에 쓰인다. */
    long countByUserAndVisibility(User user, BookVisibility visibility);

    /**
     * 여러 사용자의 PUBLIC 책 수를 한 번에 집계 — 사용자 행 목록 조립의 행당 {@code countByUserAndVisibility}
     * N+1을 단일 group by로 대체한다. <b>공개 책이 0권인 사용자는 결과에 행이 없으니</b> 호출부에서
     * {@code getOrDefault(id, 0L)}로 0 디폴트한다(하우스 스타일 {@link FollowScopeCount}와 동일 매핑).
     */
    @Query("""
            select b.user.id as userId, count(b) as publicCount
            from Book b
            where b.user.id in :userIds
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
            group by b.user.id
            """)
    List<UserPublicBookCount> countPublicByUsers(@Param("userIds") Collection<Long> userIds);

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
     * 제휴링크 백필 대상 — purchaseLink는 있으나 TTBKey 마커({@code marker}, 예: {@code "ttbkey="})가 없는 알라딘
     * 링크 + ISBN-13이 있는 책. {@code includeKey=1} 픽스 전에 저장된 무추적 링크를 {@code lookupByIsbn} 재조회로
     * 갱신하기 위한 후보다. isbn이 있어야 재조회할 수 있고(없으면 조회 키가 없어 제외), purchaseLink가 없는 수동
     * 등록분도 갱신할 링크가 없어 제외한다. {@code Pageable}로 한 번에 처리할 권수를 제한한다(외부 호출량 통제).
     */
    List<Book> findByIsbn13IsNotNullAndPurchaseLinkIsNotNullAndPurchaseLinkNotContaining(String marker, Pageable pageable);

    /** 제휴링크 백필이 남은 책 수(purchaseLink에 ttbkey 마커 없는, isbn 있는) — 백필 결과의 "남은 권수" 표시용. */
    long countByIsbn13IsNotNullAndPurchaseLinkIsNotNullAndPurchaseLinkNotContaining(String marker);

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

    /**
     * 내가 읽은(읽는중∪완독) 책의 isbn13 — 친구 추천 "같은 책" 신호의 입력(viewer 본인 책이라 PRIVATE 포함).
     * null isbn(수동 등록 등)은 동일성 키가 없어 제외. distinct로 중복 제거.
     */
    @Query("""
            select distinct b.isbn13
            from Book b
            where b.user.id = :userId
              and b.isbn13 is not null
              and b.status in (com.booktimer.book.BookStatus.READING,
                               com.booktimer.book.BookStatus.FINISHED)
            """)
    List<String> findReadIsbnsByUser(@Param("userId") Long userId);

    /**
     * "같은 책" 추천 후보 — 친구 추천 하이브리드 1단계(C). 내 isbn 목록과 겹치는 책을 <b>PUBLIC</b>으로
     * 읽은/완독한 사용자를, 겹친 권수(distinct isbn13) 내림차순으로.
     *
     * <p>노출 불변식을 모두 보존: 본인 제외·ADMIN 제외·login_id null 제외(N-055)·차단(양방향) 제외 +
     * 내가 이미 팔로우한 사람 제외. <b>visibility=PUBLIC</b>으로 비공개 독서가 새지 않게 막고
     * (후보 쪽만 PUBLIC 강제 — 내 isbn 입력은 PRIVATE 포함 내 데이터), 상태는 읽는중∪완독("읽음")만.
     * 동률은 id 오름차순. {@code Pageable}로 상한. {@code myIsbns}가 비면 호출부가 이 쿼리를 스킵한다.
     */
    @Query("""
            select b.user.id as userId,
                   count(distinct b.isbn13) as sharedBookCount
            from Book b
            where b.isbn13 in :myIsbns
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and b.status in (com.booktimer.book.BookStatus.READING,
                               com.booktimer.book.BookStatus.FINISHED)
              and b.user.id <> :viewerId
              and b.user.role <> com.booktimer.user.Role.ADMIN
              and b.user.loginId is not null
              and not exists (select 1 from com.booktimer.follow.Follow f
                              where f.follower.id = :viewerId and f.followee.id = b.user.id)
              and not exists (select 1 from com.booktimer.block.Block bl
                              where (bl.blocker.id = :viewerId and bl.blocked.id = b.user.id)
                                 or (bl.blocker.id = b.user.id and bl.blocked.id = :viewerId))
            group by b.user.id
            order by count(distinct b.isbn13) desc, b.user.id asc
            """)
    List<CoReadCount> findCoReadCandidates(@Param("viewerId") Long viewerId,
                                           @Param("myIsbns") Collection<String> myIsbns,
                                           Pageable pageable);

    /**
     * 여러 사람의 PUBLIC 책 전부 — 둘러보기 카드가 세울 책의 원천. 사람별 정렬·상위 4권 자르기는 호출부가 한다.
     *
     * <p>DB에서 사람별 top-N을 뽑으려면 윈도우 함수(=native 쿼리)라 H2·MySQL 방언을 둘 다 떠안는데,
     * 후보는 수십 명이고 사람당 책은 수십 권이라 전부 받아 자바에서 자르는 편이 싸다(2026-08-20 판단).
     */
    @Query("""
            select b from Book b
            where b.user.id in :userIds
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
            """)
    List<Book> findPublicBooksOfUsers(@Param("userIds") Collection<Long> userIds);

    /**
     * 홈 소식 피드 — <b>완독</b> 이벤트: {@code viewer}가 팔로우한 사람의 PUBLIC 완독 책(최근 창).
     *
     * <p>{@code StoryRepository.feedOf} 미러. Follow와는 매핑된 연관이 없어 theta 조인
     * ({@code f.followee = u})으로 묶고, ADMIN·공개핸들(login_id) 미설정 소유자는 노출 불변식대로
     * 제외한다(N-055). <b>차단 필터는 쿼리에 불필요</b> — "팔로우 존재 → 차단 없음" write-시점 불변식
     * (차단 시 팔로우 양방향 해제)이 보장하고 {@code BookFeedBlockInvariantTest}가 행동으로 못 박는다.
     * 비공개 전환·삭제된 책은 조회 시점 상태로 자연 소멸한다(이벤트를 따로 적재하지 않는 방식의 이점).
     * 소유자는 fetch로 즉시 초기화 — 이벤트 줄이 닉네임·핸들을 읽으므로 N+1을 막는다.
     *
     * <p><b>제거 장치는 {@code status = FINISHED}</b>이고(완독을 취소하면 즉시 사라진다),
     * <b>시각·정렬은 첫 완독({@code firstFinishedAt})</b>이다 — 완독↔읽는중 토글로 재스탬프되는
     * {@code finishedAt}을 쓰면 옛 책이 "방금"처럼 피드 맨 위로 재부상한다(2026-08-18 수정).
     */
    @Query("""
            select b from Book b join fetch b.user u, com.booktimer.follow.Follow f
            where f.followee = u and f.follower = :viewer
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and b.status = com.booktimer.book.BookStatus.FINISHED
              and b.firstFinishedAt > :cutoff
              and u.role <> com.booktimer.user.Role.ADMIN
              and u.loginId is not null
            order by b.firstFinishedAt desc
            """)
    List<Book> feedFinished(@Param("viewer") User viewer, @Param("cutoff") Instant cutoff);

    /**
     * 홈 소식 피드 — <b>읽기 시작</b> 이벤트: 같은 게이트({@link #feedFinished})에 시작 시각 기준.
     *
     * <p>{@code status = READING} 조건은 걸지 않는다 — 시작 후 곧 완독했어도 "읽기 시작했어요"는
     * 사실이었고, 두 줄이 시간순으로 나란히 서는 게 자연스럽다. 시작 시각이 없는 기존 책(V64 백필 안 함)은
     * {@code >} 비교에서 자연 제외된다.
     */
    @Query("""
            select b from Book b join fetch b.user u, com.booktimer.follow.Follow f
            where f.followee = u and f.follower = :viewer
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and b.startedReadingAt > :cutoff
              and u.role <> com.booktimer.user.Role.ADMIN
              and u.loginId is not null
            order by b.startedReadingAt desc
            """)
    List<Book> feedStarted(@Param("viewer") User viewer, @Param("cutoff") Instant cutoff);

    /**
     * 뉴스 수집 대상 — <b>완독</b>했고 isbn13·저자가 모두 있는 책을 <b>isbn당 한 행</b>으로 모은다.
     *
     * <p>사용자별이 아니라 isbn별인 이유: 같은 책을 여러 사람이 완독해도 기사는 하나면 되므로 외부 호출을
     * 1회로 줄인다(쿼터·중복 절약). 제목·저자 표기가 사용자마다 미세하게 달라도 {@code group by isbn13} +
     * 대표값({@code min})으로 한 행이 되게 해, delete-then-insert가 같은 isbn을 두 번 갈아엎지 않는다.
     * 저자 null은 매칭 AND를 못 만들어(오탐 무방비), isbn null은 캐시 키가 없어 제외한다.
     * PRIVATE 완독 책도 포함 — 뉴스는 책 자체에 대한 공개 정보고 노출은 완독한 본인에게만 된다.
     */
    @Query("""
            select b.isbn13 as isbn13, min(b.title) as title, min(b.author) as author
            from Book b
            where b.status = com.booktimer.book.BookStatus.FINISHED
              and b.isbn13 is not null
              and b.author is not null
            group by b.isbn13
            """)
    List<BookNewsTarget> findNewsCollectionTargets();

    /**
     * 내가 완독한 책 중 isbn13이 있는 것 — 홈 「책 뉴스」의 조인 키이자 "내 어느 책의 기사인가" 라벨 출처.
     * isbn이 없는 책은 뉴스 캐시와 이을 키가 없어 제외된다.
     */
    @Query("""
            select b from Book b
            where b.user = :user
              and b.status = com.booktimer.book.BookStatus.FINISHED
              and b.isbn13 is not null
            """)
    List<Book> findFinishedWithIsbn(@Param("user") User user);
}
