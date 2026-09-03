package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 상한 카운터 영속성 — 가감이 <b>조건부 UPDATE 한 문장</b>인 것이 이 인터페이스의 전부다.
 *
 * <p>「읽고 판단한 뒤 쓴다」로 만들면 동시 두 요청이 같은 값을 읽어 둘 다 통과한다(TOCTOU). 여기서는
 * 조건을 SQL의 WHERE에 실어 DB가 행을 잠근 채 판단하게 하고, 통과 여부를 <b>갱신된 행 수</b>로 받는다.
 */
public interface StudyAiUsageRepository extends JpaRepository<StudyAiUsage, Long> {

    /**
     * 한 몫을 선점한다.
     *
     * <p>{@code @Transactional}이 붙은 이유는 호출부가 <b>트랜잭션 밖</b>이기 때문이다 — AI 호출을
     * 트랜잭션 밖에 두는 규율(커넥션 점유 회피) 때문에 상한 갱신도 자기 트랜잭션을 열어 즉시 커밋해야
     * 다른 요청에 보인다.
     *
     * @return 1이면 선점 성공, 0이면 행이 없거나 이미 max를 채웠다
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StudyAiUsage u set u.used = u.used + 1
            where u.user = :user and u.usageDate = :day and u.kind = :kind and u.used < :max
            """)
    int consume(@Param("user") User user, @Param("day") LocalDate day,
                @Param("kind") Kind kind, @Param("max") int max);

    /**
     * 선점한 몫을 되돌린다 — 외부 호출이 실패했을 때만.
     *
     * <p>{@code used > 0} 조건이 가드다: 안 쓴 몫을 환불해도 내일 몫이 늘어나면 안 된다(음수 = 공짜 호출).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StudyAiUsage u set u.used = u.used - 1
            where u.user = :user and u.usageDate = :day and u.kind = :kind and u.used > 0
            """)
    int refund(@Param("user") User user, @Param("day") LocalDate day, @Param("kind") Kind kind);

    /**
     * 카운터 행을 새로 만든다 — 자기 트랜잭션에서 커밋해, 경합에 진 쪽이 즉시
     * {@link org.springframework.dao.DataAccessException}으로 알 수 있게 한다(UNIQUE가 심판).
     */
    @Transactional
    <S extends StudyAiUsage> S save(S entity);

    Optional<StudyAiUsage> findByUserAndUsageDateAndKind(User user, LocalDate day, Kind kind);

    boolean existsByUserAndUsageDateAndKind(User user, LocalDate day, Kind kind);

    /** 테스트·관리 조회용(오늘 몫이 실제로 안 깎였는지 재는 자리). */
    List<StudyAiUsage> findByUser(User user);

    /** 회원 탈퇴 시 정리(FK: study_ai_usage.user_id → users). */
    void deleteByUser(User user);
}
