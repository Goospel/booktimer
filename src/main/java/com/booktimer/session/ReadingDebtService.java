package com.booktimer.session;

import com.booktimer.timer.GoalSchedule;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ReadingGoalWaiverRepository waiverRepository;
    private final Clock clock;

    public ReadingDebtService(ReadingHistoryService historyService,
                              ReadingTimerRepository timerRepository,
                              ReadingGoalChangeRepository goalChangeRepository,
                              ReadingGoalWaiverRepository waiverRepository,
                              Clock clock) {
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
        this.waiverRepository = waiverRepository;
        this.clock = clock;
    }

    /**
     * 유저의 7일 윈도우 부채(오늘 부채 + 빠뜨린 날)를 계산해 반환한다.
     *
     * @param user 조회 주체
     */
    public WeeklyDebt weeklyDebt(User user) {
        return weeklyDebtTrace(user, today(user)).toWeeklyDebt();
    }

    /**
     * 오늘 유효한 하루 목표(초) — 진행바 분모로 쓴다. 부채 계산과 <b>같은 trace</b>에서 유도해(오늘 날의
     * {@code goalSeconds}) 항상 동일 값이 보장된다 — {@link com.booktimer.timer.ReadingTimer#getDailyIncrementSeconds()}
     * 직접 호출 시의 N-059 소급 함정(목표 변경 이력이 있으면 그날 유효 목표와 현재 평면값이 달라짐)을 피한다.
     */
    public long todayGoalSeconds(User user) {
        return weeklyDebt(user).todayGoalSeconds();
    }

    /**
     * 임의 기준일(asOf) 윈도우의 날짜별 부채 추적 결과를 반환한다 (관찰성·진단용).
     *
     * <p>재계산은 <b>현재</b> 세션·목표 이력으로 과거를 재현한다. 사후 수동 입력이나 목표 변경이 있었으면
     * 당시 표시값과 다를 수 있으나 — 이는 현재 진실이라 진단엔 오히려 맞다(계획 §0 재계산 충실성 주의).
     *
     * @param user  조회 주체
     * @param asOf  기준일(유저 TZ). null이면 유저 TZ 오늘
     */
    public WeeklyDebtTrace weeklyDebtTrace(User user, LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : today(user);
        GoalSchedule schedule = buildGoalSchedule(user);

        Optional<LocalDate> baseline = schedule.earliestEffectiveDate();
        Map<LocalDate, Long> goalByDate = new LinkedHashMap<>();
        for (int offset = 0; offset < WeeklyDebtCalculator.WINDOW_DAYS; offset++) {
            LocalDate date = effectiveAsOf.minusDays(offset);
            if (baseline.isPresent() && date.isBefore(baseline.get())) {
                continue; // 가입(첫 목표) 이전 날 — 판정에서 제외(goal=0으로 내려가 deficit 없음)
            }
            goalByDate.put(date, schedule.goalFor(date));
        }

        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : historyService.dailyHistory(user)) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        return WeeklyDebtCalculator.computeTrace(secondsByDate, goalByDate, waivedDates(user, effectiveAsOf), effectiveAsOf);
    }

    /**
     * 윈도우 내 용서된 날짜 — 리워드 광고 보상({@link ReadingGoalWaiver})의 유일한 배선점이다.
     *
     * <p>여기 한 곳에 넣으면 {@code DashboardModel.computeLive}를 경유하는 모든 소비처(웹 SSR·미니앱
     * {@code /api/dashboard}·start/stop 응답)가 자동으로 같은 값을 본다 — 채널별 동기화 코드가 필요 없다.
     * 윈도우 밖(7일 초과) 용서는 부채 자체가 자동 소멸해 의미가 없으므로 쿼리에서 잘라낸다.
     */
    private Set<LocalDate> waivedDates(User user, LocalDate asOf) {
        return waiverRepository
                .findByUserAndWaivedDateGreaterThanEqual(user, asOf.minusDays(WeeklyDebtCalculator.WINDOW_DAYS - 1))
                .stream()
                .map(ReadingGoalWaiver::getWaivedDate)
                .collect(Collectors.toSet());
    }

    /** 유저 타임존 기준 오늘. */
    public LocalDate today(User user) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
    }

    private GoalSchedule buildGoalSchedule(User user) {
        long currentGoalSeconds = timerRepository.findByUser(user)
                .map(ReadingTimer::getDailyIncrementSeconds)
                .orElse(DEFAULT_GOAL_SECONDS);
        Map<LocalDate, Long> changesByDate = new LinkedHashMap<>();
        for (ReadingGoalChange change : goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)) {
            changesByDate.put(change.getEffectiveDate(), change.getGoalSeconds());
        }
        return GoalSchedule.of(changesByDate, currentGoalSeconds);
    }
}
