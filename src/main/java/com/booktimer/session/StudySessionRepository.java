package com.booktimer.session;

import com.booktimer.book.StudyBook;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StudySession 영속성. User와 N:1.
 *
 * <p>독서 집계(잔디·부채·기록·홈피드·책 통계)는 이 인터페이스를 아예 모르고, 공부 화면은 여기만 본다.
 * 새 공부 쿼리를 여기가 아니라 {@code ReadingSessionRepository}에 다는 것이 이 설계에서 유일하게 남은
 * 누수 경로다 — 책별 집계({@link #sumSecondsByBook})가 독서 쿼리와 <b>같은 이름·같은 조건</b>인 것도
 * 그래서다(조건이 갈리면 두 화면이 다른 초를 말한다).
 */
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /**
     * 진행 중(미종료) 공부 세션 — 사용자당 최대 하나라는 규칙은 서비스가 지킨다.
     *
     * <p><b>책을 함께 즉시 로딩</b>한다: 화면 상태({@code StudyState.activeBook})가 트랜잭션 밖에서
     * 제목을 읽으므로 lazy 프록시로 두면 그 자리가 예외가 된다. LEFT join이라 책 없는 세션도 그대로 온다.
     */
    @Query("select s from StudySession s left join fetch s.book where s.user = :user and s.endedAt is null")
    Optional<StudySession> findByUserAndEndedAtIsNull(@Param("user") User user);

    /** 소유권 확인용 — 내 세션일 때만 조회된다(종료 후 태깅의 IDOR 경계). */
    Optional<StudySession> findByIdAndUser(Long id, User user);

    /**
     * 책별 누적 공부 시간(초) — 완료·책지정 세션만 DB에서 GROUP BY 집계.
     * 조건은 독서 {@code ReadingSessionRepository.sumSecondsByBook}과 글자 그대로 같다.
     */
    @Query("""
            select new com.booktimer.session.BookSecondsRow(s.book.id, coalesce(sum(s.durationSeconds), 0))
            from StudySession s
            where s.user = :user and s.endedAt is not null and s.book is not null
            group by s.book.id
            """)
    List<BookSecondsRow> sumSecondsByBook(@Param("user") User user);

    /** 가장 최근에 <b>책을 걸고</b> 잰 세션 — 홈 캐러셀의 기본 선택({@code recentBookId})의 출처. */
    Optional<StudySession> findFirstByUserAndBookIsNotNullOrderByStartedAtDesc(User user);

    /**
     * 공부 책 삭제 시, 그 책을 가리키던 세션을 "책 미지정"으로 푼다(book_id = null).
     *
     * <p>세션 자체는 지우지 않는다 — 책을 서재에서 빼도 그날 공부한 시간(당일 합·달력)은 보존돼야 한다.
     * 벌크 갱신이라 영속성 컨텍스트를 우회하므로 flush/clear를 자동 수행한다(독서 {@code unlinkBook}과 같다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudySession s set s.book = null where s.book = :book")
    void unlinkBook(@Param("book") StudyBook book);

    /** 방치 스윕 대상 — 임계 시각 이전에 시작해 아직 안 닫힌 세션들. */
    List<StudySession> findByEndedAtIsNullAndStartedAtBefore(Instant threshold);

    /**
     * 완료 세션의 구간 합(초). 귀속 기준은 <b>{@code startedAt}</b>이다(독서 집계가 {@code started_at}
     * 인덱스를 쓰는 선례와 정렬).
     *
     * <p><b>신규 세션은 저장 시 자정으로 분할</b>되므로 한 행이 유저 TZ 하루 안에 있고, 시작일 귀속이
     * 곧 정확한 귀속이다({@code StudySessionService.endSplitAndSave}). 분할 도입 전에 저장된
     * <b>레거시 행은 여전히 자정을 걸칠 수 있다</b> — 소급 재분할을 하지 않았기 때문이다.
     */
    @Query("""
            select coalesce(sum(s.durationSeconds), 0)
            from StudySession s
            where s.user = :user and s.endedAt is not null
              and s.startedAt >= :from and s.startedAt < :to
            """)
    long sumCompletedSeconds(@Param("user") User user,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    /**
     * 구간 안의 완료 세션들 — 달력이 <b>일자별로</b> 갈라 세려고 원본 행을 받는다
     * ({@link #sumCompletedSeconds}는 한 덩어리 합이라 일자별로 못 쪼갠다).
     *
     * <p>귀속 기준은 여기서도 {@code startedAt}이다 — 합계와 달력이 같은 규칙을 써야 한 세션이 두
     * 화면에서 다른 날에 서지 않는다. 자정을 걸친 공부는 저장 시 이미 조각난 행들이라 각 조각이 제
     * 날짜에 선다. 범위는 한 사람의 한 달이라 행 수가 수십을 넘지 않는다.
     */
    List<StudySession> findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            User user, Instant from, Instant to);

    /**
     * 기록 화면 몫 — <b>전 기간</b> 완료 세션. 잔디(53주)와 월별 목록(전 기간)을 한 번에 묶으려면
     * 구간을 자를 수가 없다({@link StudyHistoryService}가 두 범위를 자바에서 가른다).
     *
     * <p>귀속 기준은 다른 둘과 같은 {@code startedAt}이다 — 자정을 걸친 공부는 저장 시 조각난 행들이라
     * 각 조각이 제 날짜에 들어간다(레거시 행은 예외).
     */
    List<StudySession> findByUserAndEndedAtIsNotNull(User user);

    /** 회원 탈퇴 시 해당 유저의 모든 공부 기록을 제거한다(FK: study_session.user_id → users). */
    void deleteByUser(User user);
}
