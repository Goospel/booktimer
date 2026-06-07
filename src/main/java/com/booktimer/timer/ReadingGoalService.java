package com.booktimer.timer;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 하루 목표 변경 이력 기록 유스케이스.
 *
 * <p>사용자가 하루 목표를 <b>설정·변경</b>할 때(온보딩·설정) 그 시점을 이력으로 남겨, 나중에 목표를 바꿔도
 * 과거 날을 그날 목표로 판정할 수 있게 한다({@link GoalSchedule}로 해석, 부채 계산에서 소급 함정 차단 — N-059).
 *
 * <p>이력의 시점은 서버 UTC가 아니라 <b>유저 타임존</b> 자정 경계로 정해야 하므로 {@link Clock} +
 * {@link User#getTimezone()}으로 "오늘"을 계산한다(N-010). 같은 날 여러 번 바꾸면 그날 행의 목표만
 * 갱신해(upsert) 중복 행을 만들지 않는다. 호출자(온보딩·설정 서비스)의 트랜잭션에 합류한다.
 */
@Service
@Transactional
public class ReadingGoalService {

    private final ReadingGoalChangeRepository repository;
    private final Clock clock;

    public ReadingGoalService(ReadingGoalChangeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 유저의 현재 하루 목표를 "오늘(유저 TZ)"자 이력 행으로 기록한다 — 있으면 갱신, 없으면 생성.
     *
     * @param user        대상 사용자
     * @param goalSeconds 지금 설정된 하루 목표(초)
     */
    public void record(User user, long goalSeconds) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
        repository.findByUserAndEffectiveDate(user, today).ifPresentOrElse(
                existing -> existing.changeGoal(goalSeconds), // 영속 엔티티 — dirty checking으로 반영
                () -> repository.save(ReadingGoalChange.of(user, today, goalSeconds)));
    }
}
