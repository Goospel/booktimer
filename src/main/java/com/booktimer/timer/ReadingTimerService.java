package com.booktimer.timer;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 누적 타이머 적용 흐름.
 *
 * <p>누적은 배치가 아니라 접속 시 Lazy로 따라잡는다(N-001). 이 서비스는 "지금 유저의 오늘"을
 * <b>유저 타임존</b>으로 계산해 {@link ReadingTimer#accrueUntil(LocalDate)} 에 위임한다.
 * "오늘"은 서버 시각이 아니라 유저가 사는 곳의 자정 경계로 정해져야 하므로, 시간 의존은
 * 주입된 {@link Clock} 으로 격리해 테스트 가능하게 둔다.
 */
@Service
@Transactional
public class ReadingTimerService {

    private final ReadingTimerRepository timerRepository;
    private final Clock clock;

    public ReadingTimerService(ReadingTimerRepository timerRepository, Clock clock) {
        this.timerRepository = timerRepository;
        this.clock = clock;
    }

    /**
     * 유저의 타이머를 "유저 타임존 기준 오늘"까지 누적시키고 저장한다.
     *
     * @throws IllegalStateException 유저 타이머가 없는 경우
     */
    public ReadingTimer accrueToToday(User user) {
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user"));

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
        timer.accrueUntil(today);

        return timerRepository.save(timer);
    }
}
