package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 밀린 하루 용서권 영속성.
 *
 * <p>조회는 한 갈래뿐이다 — 부채 계산에 넘길 창 내 용서 날짜 목록. 창 밖 용서는 계산에 쓰이지 않으므로
 * 애초에 읽지 않는다. 일일 상한 판정({@code existsByUserAndGrantedOn})은 횟수 제한 폐지와 함께 사라졌다
 * (2026-08-14 — 부채가 계속 누적되니 갚을 수단도 계속 열려 있어야 한다).
 */
public interface ReadingGoalWaiverRepository extends JpaRepository<ReadingGoalWaiver, Long> {

    /** 부채 창 시작일 이후의 용서 행 — 계산기에 넘길 waived 날짜 집합의 원천. */
    List<ReadingGoalWaiver> findByUserAndWaivedDateGreaterThanEqual(User user, LocalDate from);

    /** 회원 탈퇴 시 해당 유저의 용서 기록을 제거한다(FK: reading_goal_waiver.user_id → users). */
    void deleteByUser(User user);
}
