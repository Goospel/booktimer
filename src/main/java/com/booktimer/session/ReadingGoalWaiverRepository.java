package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 밀린 하루 용서권 영속성.
 *
 * <p>조회는 두 갈래뿐이다 — 일일 상한 판정({@code existsByUserAndGrantedOn})과 부채 계산에 넘길
 * 윈도우 내 용서 날짜 목록. 윈도우 밖(7일 초과) 용서는 부채 자체가 자동 소멸해 의미가 없으므로
 * 애초에 읽지 않는다.
 */
public interface ReadingGoalWaiverRepository extends JpaRepository<ReadingGoalWaiver, Long> {

    /** 오늘(유저 TZ) 이미 지급받았는지 — 일일 1회 상한의 애플리케이션 검사. DB 제약이 최종 방어. */
    boolean existsByUserAndGrantedOn(User user, LocalDate grantedOn);

    /** 부채 윈도우 시작일 이후의 용서 행 — 계산기에 넘길 waived 날짜 집합의 원천. */
    List<ReadingGoalWaiver> findByUserAndWaivedDateGreaterThanEqual(User user, LocalDate from);

    /** 회원 탈퇴 시 해당 유저의 용서 기록을 제거한다(FK: reading_goal_waiver.user_id → users). */
    void deleteByUser(User user);
}
