package com.booktimer.session;

import com.booktimer.timer.GoalSchedule;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
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
 * 일자별 읽은 양({@link ReadingHistoryService})과 <b>그날 유효했던 하루 목표</b>를 받아
 * 순수 계산기({@link WeeklyDebtCalculator})로 오늘 부채 + 이번 주 빠뜨린 날을 만든다.
 *
 * <p>그날 목표는 목표 변경 이력({@link ReadingGoalChange})을 {@link GoalSchedule}로 풀어 날짜별로 정한다 —
 * 사용자가 목표를 올려도 옛 목표를 채운 과거 날이 빠뜨린 날로 둔갑하지 않게(소급 함정 차단, N-059). 이력이
 * 비면(레거시·미온보딩) 현재 타이머 목표로 폴백하므로 옛 동작과 동일하다.
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
    private final ReadingGoalChangeRepository goalChangeRepository;
    private final Clock clock;

    public ReadingDebtService(ReadingHistoryService historyService,
                              ReadingTimerRepository timerRepository,
                              ReadingGoalChangeRepository goalChangeRepository,
                              Clock clock) {
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
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

        // 현재(폴백) 목표 — 이력에 그 날짜 이전 변경이 없을 때 쓴다.
        long currentGoalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);

        // 목표 변경 이력 → GoalSchedule → 윈도우 7일 각 날짜의 "그날 목표"를 풀어 둔다.
        Map<LocalDate, Long> changesByDate = new LinkedHashMap<>();
        for (ReadingGoalChange change : goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)) {
            changesByDate.put(change.getEffectiveDate(), change.getGoalSeconds());
        }
        GoalSchedule schedule = GoalSchedule.of(changesByDate, currentGoalSeconds);
        Map<LocalDate, Long> goalByDate = new LinkedHashMap<>();
        for (int offset = 0; offset < WeeklyDebtCalculator.WINDOW_DAYS; offset++) {
            LocalDate date = today.minusDays(offset);
            goalByDate.put(date, schedule.goalFor(date));
        }

        // 일자별 읽은 양(완료 세션 합, 유저 TZ 일자). 계산기는 윈도우(7일)만 들여다보므로 전체를 넘겨도 무방.
        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : historyService.dailyHistory(user)) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        return WeeklyDebtCalculator.compute(secondsByDate, goalByDate, today);
    }
}
