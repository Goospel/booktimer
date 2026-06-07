package com.booktimer.timer;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 하루 목표 변경 이력 영속성.
 *
 * <p>{@link GoalSchedule} 구성을 위해 유저의 전체 이력을 날짜순으로 읽고, upsert 판단을 위해
 * 특정 날짜 행을 단건 조회한다. {@code (user, effective_date)} 유니크라 단건 조회는 0/1건이다.
 */
public interface ReadingGoalChangeRepository extends JpaRepository<ReadingGoalChange, Long> {

    /** 유저의 목표 변경 이력을 effective_date 오름차순으로 — GoalSchedule 구성용. */
    List<ReadingGoalChange> findByUserOrderByEffectiveDateAsc(User user);

    /** 그날 이미 기록된 변경이 있는지(있으면 목표만 갱신해 중복 행 방지). */
    Optional<ReadingGoalChange> findByUserAndEffectiveDate(User user, LocalDate effectiveDate);

    /** 회원 탈퇴 시 해당 유저의 목표 이력을 제거한다. */
    void deleteByUser(User user);
}
