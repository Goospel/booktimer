package com.booktimer.session;

import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 독서 부채(7일 윈도우 per-day) 조회 유스케이스.
 *
 * <p>부채는 더 이상 저장하지 않고 완료 세션에서 <b>유도</b>한다(N-001의 단일 누적 카운터를 대체).
 * 일자별 읽은 양({@link ReadingHistoryService})과 하루 목표(타이머의 평면 증가값)를 받아
 * 순수 계산기({@link WeeklyDebtCalculator})로 오늘 부채 + 이번 주 빠뜨린 날을 만든다.
 *
 * <p>"오늘"·윈도우 경계는 서버 UTC가 아니라 <b>유저 타임존</b> 자정 경계로 정해야 하므로
 * {@link Clock} + {@link User#getTimezone()}으로 계산한다(N-010). 타이머가 없으면(불변식상 거의
 * 없음) {@link ReadingContributionService}와 동일한 기본 목표로 폴백한다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingDebtService {

    /** 타이머가 없을 때의 기본 하루 목표(초) — 가입 시 기본 증가값과 동일(1시간). */
    public static final long DEFAULT_GOAL_SECONDS = 3600L;

    private final ReadingHistoryService historyService;
    private final ReadingTimerRepository timerRepository;
    private final Clock clock;

    public ReadingDebtService(ReadingHistoryService historyService,
                              ReadingTimerRepository timerRepository,
                              Clock clock) {
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.clock = clock;
    }

    /**
     * 유저의 7일 윈도우 부채(오늘 부채 + 빠뜨린 날)를 계산해 반환한다.
     *
     * @param user 조회 주체
     */
    public WeeklyDebt weeklyDebt(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        long goalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);

        // 일자별 읽은 양(완료 세션 합, 유저 TZ 일자). 계산기는 윈도우(7일)만 들여다보므로 전체를 넘겨도 무방.
        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : historyService.dailyHistory(user)) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        return WeeklyDebtCalculator.compute(secondsByDate, goalSeconds, today);
    }
}
