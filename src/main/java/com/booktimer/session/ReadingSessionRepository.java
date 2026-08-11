package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ReadingSession 영속성. User와 N:1.
 */
public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    List<ReadingSession> findByUser(User user);

    /**
     * 세션 전체를 책과 함께 즉시 로딩 — 트랜잭션 밖 매핑/렌더에서 lazy 예외 방지 + N+1 제거.
     * LEFT join: book=null 레거시 세션도 보존(일자·시간 집계에 기여 — N-055 정신).
     */
    @Query("select s from ReadingSession s left join fetch s.book where s.user = :user")
    List<ReadingSession> findByUserWithBook(@Param("user") User user);

    /**
     * 책별 누적 시간(초) — 완료·책지정 세션만 DB에서 GROUP BY 집계(세션 엔티티 미로딩).
     * 진행 중(endedAt=null)·book=null 세션은 제외. PUBLIC·PRIVATE 모두 포함.
     */
    @Query("""
            select new com.booktimer.session.BookSecondsRow(s.book.id, coalesce(sum(s.durationSeconds), 0))
            from ReadingSession s
            where s.user = :user and s.endedAt is not null and s.book is not null
            group by s.book.id
            """)
    List<BookSecondsRow> sumSecondsByBook(@Param("user") User user);

    /**
     * 공개 집계(SNS) — PUBLIC 책 세션만(비공개 독서 시간 누출 방지, sns-design §3.5).
     */
    @Query("""
            select new com.booktimer.session.BookSecondsRow(s.book.id, coalesce(sum(s.durationSeconds), 0))
            from ReadingSession s
            where s.user = :user and s.endedAt is not null and s.book is not null
              and s.book.visibility = com.booktimer.book.BookVisibility.PUBLIC
            group by s.book.id
            """)
    List<BookSecondsRow> sumSecondsByPublicBook(@Param("user") User user);

    /** 한 책에 연결된(그 책으로 측정한) 세션들 — 책별 상세(잔디·기록) 집계에 쓰인다. */
    List<ReadingSession> findByUserAndBook(User user, Book book);

    /** 진행 중(endedAt == null) 세션. 서비스의 중복 start 방지에 쓰인다. */
    Optional<ReadingSession> findByUserAndEndedAtIsNull(User user);

    /**
     * id + 소유자(user)로 세션을 조회 — 종료 후 태깅의 IDOR-안전 경계.
     * 남의 세션 id를 넘겨도 소유자가 다르면 빈 결과라(→ 404 마스킹) 남의 측정에 책을 붙일 수 없다.
     */
    Optional<ReadingSession> findByIdAndUser(Long id, User user);

    /**
     * <b>모든 사용자</b>의 진행 중 세션(목표 달성 푸시 감지 배치). 지금 읽고 있는 사람만 후보라
     * 이 목록이 곧 분당 스캔 대상이다 — 활성 세션은 항상 소수이고 {@code idx_reading_session_ended_at}가 받친다.
     */
    List<ReadingSession> findByEndedAtIsNull();

    /**
     * 한 사용자가 <b>주어진 구간에 시작해 이미 끝낸</b> 세션들의 측정 길이 합(초). 기록이 없으면 0(coalesce).
     *
     * <p>구간은 {@code [from, to)} — 유저 타임존 하루의 시작/끝 Instant를 호출부가 넘긴다. 판정 기준이
     * {@code endedAt}이 아니라 <b>{@code startedAt}</b>인 것이 핵심이다: 세션은 시작일에 귀속되므로
     * ({@link ReadingHistoryService}와 동일 규칙) 자정을 넘겨 끝난 독서도 시작한 날에 계상된다.
     */
    @Query("""
            select coalesce(sum(s.durationSeconds), 0) from ReadingSession s
            where s.user = :user and s.endedAt is not null
              and s.startedAt >= :from and s.startedAt < :to
            """)
    long sumCompletedSeconds(@Param("user") User user,
                             @Param("from") java.time.Instant from,
                             @Param("to") java.time.Instant to);

    /** 진행 중 세션을 책과 함께 즉시 로딩 — 트랜잭션 밖(뷰)에서 책 제목 접근 시 lazy 예외 방지. */
    @Query("select s from ReadingSession s left join fetch s.book where s.user = :user and s.endedAt is null")
    Optional<ReadingSession> findActiveWithBook(@Param("user") User user);

    /**
     * 한 책의 누적 독서 시간(초) — 그 책에 연결된 세션들의 측정 길이 합.
     *
     * <p>진행 중(미종료) 세션은 {@code durationSeconds=0}이라 합계에 영향을 주지 않는다(종료 시 채워짐).
     * 측정 기록이 없으면 0을 돌려준다(coalesce).
     */
    @Query("select coalesce(sum(s.durationSeconds), 0) from ReadingSession s where s.user = :user and s.book = :book")
    long sumDurationByUserAndBook(@Param("user") User user, @Param("book") Book book);

    /**
     * 가장 최근에 측정(읽기 시작)한 책들의 id — startedAt 내림차순. 첫 원소가 "최근 읽은 책"이다.
     *
     * <p>대시보드 측정 드롭다운에서 이 책을 미리 선택해 "이어 읽기"를 자연스럽게 한다.
     * 책 미지정(book is null) 레거시 세션은 제외한다. 호출부에서 {@code PageRequest.of(0, 1)}로 1건만 받는다.
     */
    @Query("select s.book.id from ReadingSession s where s.user = :user and s.book is not null order by s.startedAt desc")
    List<Long> findRecentlyReadBookIds(@Param("user") User user, Pageable pageable);

    /** 회원 탈퇴 시 해당 유저의 모든 측정 기록을 제거한다. */
    void deleteByUser(User user);

    /**
     * 한 사용자의 최근 측정 세션(startedAt 내림차순) — 책을 즉시 로딩(left join fetch)해 트랜잭션 밖
     * 매핑/렌더에서 lazy 예외를 막는다. 호출부가 {@code Pageable}로 최대 N개만 받는다(관리자 드릴다운).
     */
    @Query("select s from ReadingSession s left join fetch s.book where s.user = :user order by s.startedAt desc")
    List<ReadingSession> findRecentWithBookByUser(@Param("user") User user, Pageable pageable);

    /**
     * 최근 활성 사용자 수 — {@code since} 이후 측정(startedAt)을 시작한 <b>distinct 사용자</b>를 센다(운영 통계).
     * 한 사용자가 여러 번 읽어도 1로 집계한다. "활성"의 창(최근 N일)은 호출부(서비스)가 시계로 정한다.
     */
    @Query("select count(distinct s.user.id) from ReadingSession s where s.startedAt >= :since")
    long countActiveUsersSince(@Param("since") java.time.Instant since);

    /**
     * 전체 누적 독서 시간(초) — 모든 세션의 측정 길이 합(운영 통계). 진행 중(미종료) 세션은 0이라 영향 없음.
     * 기록이 없으면 0(coalesce).
     */
    @Query("select coalesce(sum(s.durationSeconds), 0) from ReadingSession s")
    long sumAllDurationSeconds();

    /**
     * 재참여 넛지 대상 사용자(이메일 인프라 2단계 PR-2). §3-1의 5조건을 모두 만족하는 사용자만 반환한다:
     * <ol>
     *   <li>한 번이라도 읽음 — {@code max(startedAt)}이 존재(없으면 서브쿼리 null이라 {@code <= cutoff} 불성립 →
     *       온보딩 이탈자 자동 제외, null-state 누출 가드 N-055).</li>
     *   <li>비활동 — 마지막 활동 {@code max(startedAt) <= cutoff}(호출부가 {@code now - 7일}로 cutoff 지정, 경계 포함).</li>
     *   <li>동의 — {@code marketingEmailConsent = true}(정보통신망법 opt-in).</li>
     *   <li>검증 — {@code emailVerified = true}(미검증 발송 반송→평판 보호 N-053; 마케팅은 트랜잭션과 달리 검증 필수).</li>
     *   <li>이 구간 미발송 — {@code lastNudgeSentAt is null OR lastNudgeSentAt < 마지막 활동}(비활동 구간당 1회 멱등).
     *       재활동 후 재이탈 시 {@code lastNudge < 새 활동}이 되어 다시 대상이 된다('1회'는 영구가 아닌 구간당).</li>
     * </ol>
     * 루트는 User지만 활동 출처가 ReadingSession이라(이미 {@code countActiveUsersSince}가 여기 있음) 이 리포지토리에 둔다.
     */
    @Query("""
            select u from User u
            where u.marketingEmailConsent = true and u.emailVerified = true
              and (select max(s.startedAt) from ReadingSession s where s.user = u) <= :cutoff
              and (u.lastNudgeSentAt is null
                   or u.lastNudgeSentAt < (select max(s.startedAt) from ReadingSession s where s.user = u))
            """)
    List<com.booktimer.user.User> findNudgeTargets(@Param("cutoff") java.time.Instant cutoff);

    /**
     * 책 삭제 시, 그 책을 가리키던 측정 세션을 "책 미지정"으로 푼다(book_id = null).
     *
     * <p>세션 자체는 지우지 않는다 — 책을 책장에서 빼도 그날 읽은 기록(잔디·누적 시간)은 보존돼야 한다.
     * {@code reading_session.book_id} FK 때문에 이 정리 없이 책을 지우면 제약 위반으로 실패한다.
     *
     * <p>벌크 갱신이라 영속성 컨텍스트를 우회하므로, 호출 전후 일관성을 위해 flush/clear를 자동 수행한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReadingSession s set s.book = null where s.book = :book")
    void unlinkBook(@Param("book") Book book);

    /**
     * 친구 추천 활동량 폴백(E 생성기) — {@code since} 이후 distinct 활동일(DATE(startedAt)) 수가 많은 사용자 순.
     * 노출 불변식: ADMIN·본인·login_id null·차단(양방향)·이미 팔로우 제외.
     */
    @Query("""
            select s.user.id as userId,
                   count(distinct function('date', s.startedAt)) as activeDayCount
            from ReadingSession s
            where s.startedAt >= :since
              and s.user.id <> :viewerId
              and s.user.role <> com.booktimer.user.Role.ADMIN
              and s.user.loginId is not null
              and not exists (select 1 from com.booktimer.follow.Follow f
                              where f.follower.id = :viewerId and f.followee.id = s.user.id)
              and not exists (select 1 from com.booktimer.block.Block b
                              where (b.blocker.id = :viewerId and b.blocked.id = s.user.id)
                                 or (b.blocker.id = s.user.id and b.blocked.id = :viewerId))
            group by s.user.id
            order by count(distinct function('date', s.startedAt)) desc, s.user.id asc
            """)
    List<ActiveDayCount> findActiveReaderCandidates(
            @Param("viewerId") Long viewerId,
            @Param("since") java.time.Instant since,
            Pageable pageable);
}
