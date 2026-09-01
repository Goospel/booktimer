package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * StudyDailyCheck 영속성. User와 N:1.
 *
 * <p>{@link StudySessionRepository}와 같은 규율이다 — 독서 집계(잔디·부채·기록)는 이 인터페이스를
 * 아예 모르고, 공부 달력만 여기를 본다.
 */
public interface StudyDailyCheckRepository extends JpaRepository<StudyDailyCheck, Long> {

    /** 그날의 판정(없으면 무기록). 「하루 한 판정」이라 단건이다. */
    Optional<StudyDailyCheck> findByUserAndCheckDate(User user, LocalDate checkDate);

    /** 달력 한 달치 — 양끝 포함 구간(1일 ~ 말일). */
    List<StudyDailyCheck> findByUserAndCheckDateBetween(User user, LocalDate from, LocalDate to);

    /** 회원 탈퇴 시 정리(FK: study_daily_check.user_id → users). */
    void deleteByUser(User user);
}
