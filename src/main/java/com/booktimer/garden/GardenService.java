package com.booktimer.garden;

import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingHistoryService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 독서 정원(트랙 A) 조회 유스케이스 — 보유 식물을 저장하지 않고 독서 실적에서 유도한다(부채 모델 N-058).
 *
 * <p>잔디({@code ReadingContributionService})와 같은 입력(일자별 집계·목표 변경 이력)을 쓴다 — "그날 목표를
 * 채운 날"의 누적 수로 식물을 해금한다. 목표를 올려도 옛 목표를 채운 과거 날이 소급 박탈되지 않게 날짜별
 * 목표로 판정하고(N-059), baseline 이전(가입 전) 날은 제외한다. "오늘"은 유저 타임존 자정 경계(N-010).
 *
 * <p>순수 계산은 {@link PlantUnlockCalculator}에 위임해 단위테스트로 전수 검증된다. 여기선 소스 배선
 * (일자 집계·목표 이력·카탈로그 조회)과 해금일·NEW·진척 조립만 한다.
 */
@Service
@Transactional(readOnly = true)
public class GardenService {

    /** 타이머가 없을 때의 기본 하루 목표(초) — {@code ReadingContributionService}와 동일(1시간). */
    static final long DEFAULT_GOAL_SECONDS = 3600L;

    /** 해금 후 며칠까지 "NEW"로 볼지. */
    static final int NEW_WINDOW_DAYS = 7;

    private final PlantRepository plantRepository;
    private final ReadingHistoryService historyService;
    private final ReadingTimerRepository timerRepository;
    private final ReadingGoalChangeRepository goalChangeRepository;
    private final Clock clock;

    public GardenService(PlantRepository plantRepository,
                         ReadingHistoryService historyService,
                         ReadingTimerRepository timerRepository,
                         ReadingGoalChangeRepository goalChangeRepository,
                         Clock clock) {
        this.plantRepository = plantRepository;
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
        this.clock = clock;
    }

    public GardenView view(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        // 1) 일자별 총 독서초 (잔디와 동일 소스)
        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : historyService.dailyHistory(user)) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        // 2) 그날 유효했던 목표 — 변경 이력 → GoalSchedule (ReadingContributionService와 동일 조립)
        long currentGoalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);
        Map<LocalDate, Long> changesByDate = new LinkedHashMap<>();
        for (ReadingGoalChange change : goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)) {
            changesByDate.put(change.getEffectiveDate(), change.getGoalSeconds());
        }
        GoalSchedule schedule = GoalSchedule.of(changesByDate, currentGoalSeconds);

        // 3) 그날 목표를 채운 날들(오름차순) → 누적 달성일
        List<LocalDate> achieved = PlantUnlockCalculator.achievedGoalDates(
                secondsByDate, schedule::goalFor, schedule.earliestEffectiveDate().orElse(null));
        int achievedDays = achieved.size();

        // 4) 카탈로그(임계 오름차순) → 각 식물 상태 조립
        List<Plant> catalog = plantRepository.findAllByOrderByUnlockThresholdDaysAscDisplayOrderAsc();
        List<PlantState> states = new ArrayList<>(catalog.size());
        int ownedCount = 0;
        Integer daysToNextUnlock = null;
        String nextPlantName = null;
        for (Plant plant : catalog) {
            long threshold = plant.getUnlockThresholdDays();
            boolean owned = threshold >= 1 && threshold <= achievedDays;
            LocalDate unlockedOn = owned ? achieved.get((int) threshold - 1) : null;
            boolean isNew = owned && !unlockedOn.isBefore(today.minusDays(NEW_WINDOW_DAYS));
            states.add(new PlantState(plant, owned, unlockedOn, isNew));
            if (owned) {
                ownedCount++;
            } else if (daysToNextUnlock == null) {
                // 카탈로그가 임계 오름차순이라 첫 미보유 = 다음 해금 대상
                daysToNextUnlock = (int) (threshold - achievedDays);
                nextPlantName = plant.getName();
            }
        }

        return new GardenView(states, ownedCount, catalog.size(), achievedDays, daysToNextUnlock, nextPlantName);
    }
}
