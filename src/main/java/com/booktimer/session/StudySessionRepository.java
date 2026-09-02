package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StudySession 영속성. User와 N:1.
 *
 * <p>쿼리가 넷뿐인 것이 이 원장의 요점이다 — 독서 집계(잔디·부채·기록·홈피드·책 통계)는 이 인터페이스를
 * 아예 모르고, 공부 화면은 이 넷만 본다. 새 공부 쿼리를 여기가 아니라 {@code ReadingSessionRepository}에
 * 다는 것이 이 설계에서 유일하게 남은 누수 경로다.
 */
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /** 진행 중(미종료) 공부 세션 — 사용자당 최대 하나라는 규칙은 서비스가 지킨다. */
    Optional<StudySession> findByUserAndEndedAtIsNull(User user);

    /** 방치 스윕 대상 — 임계 시각 이전에 시작해 아직 안 닫힌 세션들. */
    List<StudySession> findByEndedAtIsNullAndStartedAtBefore(Instant threshold);

    /**
     * 완료 세션의 구간 합(초). 귀속 기준은 <b>{@code startedAt}</b>이라 자정을 걸친 세션은
     * <b>시작한 날에 전부</b> 들어간다(단순 규칙 — 독서 집계가 {@code started_at} 인덱스를 쓰는 선례와 정렬).
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
     * <p>귀속 기준은 여기서도 {@code startedAt}이다 — 합계와 달력이 같은 규칙을 써야 자정을 걸친
     * 세션이 두 화면에서 다른 날에 서지 않는다. 범위는 한 사람의 한 달이라 행 수가 수십을 넘지 않는다.
     */
    List<StudySession> findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            User user, Instant from, Instant to);

    /**
     * 기록 화면 몫 — <b>전 기간</b> 완료 세션. 잔디(53주)와 월별 목록(전 기간)을 한 번에 묶으려면
     * 구간을 자를 수가 없다({@link StudyHistoryService}가 두 범위를 자바에서 가른다).
     *
     * <p>귀속 기준은 다른 둘과 같은 {@code startedAt}이다 — 자정을 걸친 세션은 시작한 날에 전부 들어간다.
     */
    List<StudySession> findByUserAndEndedAtIsNotNull(User user);

    /** 회원 탈퇴 시 해당 유저의 모든 공부 기록을 제거한다(FK: study_session.user_id → users). */
    void deleteByUser(User user);
}
