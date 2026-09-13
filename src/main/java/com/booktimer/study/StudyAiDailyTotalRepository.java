package com.booktimer.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 전역 상한 카운터 영속성 — {@link StudyAiUsageRepository}와 <b>같은 규율</b>이다: 가감이 조건부
 * UPDATE 한 문장이고, 통과 여부를 갱신된 행 수로 받는다.
 *
 * <p>「읽고 판단한 뒤 쓴다」로 만들면 동시 두 요청이 같은 값을 읽어 둘 다 통과한다(TOCTOU). 여기서는
 * 조건을 SQL의 WHERE에 실어 DB가 행을 잠근 채 판단하게 한다. 사용자당 카운터보다 <b>경합이 더 심한</b>
 * 자리다 — 모든 사용자의 모든 호출이 이 한 행으로 몰린다.
 */
public interface StudyAiDailyTotalRepository extends JpaRepository<StudyAiDailyTotal, Long> {

    /**
     * 그 날의 전역 몫에서 한 번을 선점한다.
     *
     * <p>{@code max}를 인자로 받는 것이 의도다 — 상한을 프로퍼티로 읽는 것은 서비스의 일이고, 여기는
     * 「주어진 한도 안에서 원자적으로 센다」만 한다. 덕분에 킬 스위치(0)도 특수 분기 없이 그냥 통과한다.
     *
     * @return 1이면 선점 성공, 0이면 행이 없거나 이미 {@code max}를 채웠다
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StudyAiDailyTotal t set t.used = t.used + 1
            where t.usageDate = :day and t.used < :max
            """)
    int consume(@Param("day") LocalDate day, @Param("max") int max);

    /**
     * 선점한 몫을 되돌린다 — 외부 호출이 실패했을 때만.
     *
     * <p>{@code used > 0} 가드가 중요하다: 안 쓴 몫을 환불해 음수가 되면 <b>그 날 상한이 조용히 늘어난다</b>
     * (공짜 호출). 사용자당 카운터보다 파급이 크다 — 여기 음수는 서비스 전체의 예산을 늘린다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StudyAiDailyTotal t set t.used = t.used - 1
            where t.usageDate = :day and t.used > 0
            """)
    int refund(@Param("day") LocalDate day);

    /**
     * 카운터 행을 새로 만든다 — 자기 트랜잭션에서 커밋해, 경합에 진 쪽이 즉시
     * {@link org.springframework.dao.DataAccessException}으로 알 수 있게 한다(UNIQUE가 심판).
     */
    @Transactional
    <S extends StudyAiDailyTotal> S save(S entity);

    Optional<StudyAiDailyTotal> findByUsageDate(LocalDate day);

    boolean existsByUsageDate(LocalDate day);
}
