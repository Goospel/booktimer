package com.booktimer.writing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 월 원가 계량기 — {@code StudyAiDailyTotalRepository}와 같은 규율(가산은 UPDATE 한 문장, 결과는 갱신된 행 수). */
public interface AiSpendMonthRepository extends JpaRepository<AiSpendMonth, Long> {

    /** @return 1이면 더했다, 0이면 그 달 행이 아직 없다 */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiSpendMonth m set m.microUsd = m.microUsd + :d where m.spendMonth = :month")
    int add(@Param("month") String month, @Param("d") long microUsd);

    /** 자기 트랜잭션에서 커밋 — 경합에 진 쪽이 즉시 DataAccessException으로 안다(UNIQUE가 심판). */
    @Transactional
    <S extends AiSpendMonth> S save(S entity);

    Optional<AiSpendMonth> findBySpendMonth(String month);
}
