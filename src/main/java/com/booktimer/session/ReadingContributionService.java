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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독서 잔디(컨트리뷰션 그래프) 조회 유스케이스.
 *
 * <p>일자별 집계({@link ReadingHistoryService})를 GitHub 잔디 형태의 1년치 그리드로 펼친다.
 * "오늘"은 서버 UTC가 아니라 <b>유저 타임존</b> 자정 경계로 정해야 하므로 {@link Clock} +
 * {@link User#getTimezone()}으로 계산한다(N-010). 그리드 구성 자체는 순수 빌더
 * {@link ContributionGraphBuilder}에 위임해 단위테스트로 검증된다.
 *
 * <p>색 농도는 각 칸을 <b>그날 유효했던 하루 목표</b> 대비 달성 비율로 정한다 — 목표를 올려도 옛 목표를 채운
 * 과거 날의 색이 새 목표 기준으로 내려가 소급해 다시 칠해지지 않게(N-059의 잔디 판). 목표 변경 이력
 * ({@link ReadingGoalChange})을 {@link GoalSchedule}로 날짜별로 풀어 빌더에 넘긴다. 이력이 비면(레거시·미온보딩)
 * 현재 타이머 목표로 폴백하므로 옛 동작과 동일하다. 타이머도 없으면(불변식상 거의 없음) 기본 목표로 폴백한다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingContributionService {

    /** 타이머가 없을 때의 기본 하루 목표(초) — 가입 시 기본 증가값과 동일(1시간). */
    static final long DEFAULT_GOAL_SECONDS = 3600L;

    private final ReadingHistoryService historyService;
    private final ReadingTimerRepository timerRepository;
    private final ReadingGoalChangeRepository goalChangeRepository;
    private final Clock clock;

    public ReadingContributionService(ReadingHistoryService historyService,
                                      ReadingTimerRepository timerRepository,
                                      ReadingGoalChangeRepository goalChangeRepository,
                                      Clock clock) {
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
        this.clock = clock;
    }

    /** 본인이 보는 전체 잔디 — 모든 완료 세션을 합산한다. */
    public ContributionGraph contributionGraph(User user) {
        return graphFrom(user, historyService.dailyHistory(user));
    }

    /**
     * 타인 프로필(SNS)에서 보이는 <b>공개 잔디</b> — PUBLIC 책 세션만 합산한다(sns-design §3.5).
     * 색 농도 목표·타임존은 대상 사용자 기준(자기 잔디를 자기 기준으로 본다).
     */
    public ContributionGraph publicContributionGraph(User user) {
        return graphFrom(user, historyService.publicDailyHistory(user));
    }

    /** 일자별 집계를 받아 유저 타임존 "오늘"·하루 목표로 잔디 그리드를 만든다(가시성 필터는 호출자가 끝낸 상태). */
    private ContributionGraph graphFrom(User user, List<DailyReadingRecord> history) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        Set<LocalDate> manualDates = new LinkedHashSet<>(); // 직접 채운(수동 입력 포함) 날 — 잔디 테두리 표시
        for (DailyReadingRecord record : history) {
            secondsByDate.put(record.date(), record.totalSeconds());
            if (record.manuallyFilled()) {
                manualDates.add(record.date());
            }
        }

        // 현재(폴백) 목표 — 이력에 그 날짜 이전 변경이 없을 때 쓴다.
        long currentGoalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);

        // 목표 변경 이력 → GoalSchedule → 각 칸을 그날 목표로 색칠(소급 재채색 차단, N-059).
        Map<LocalDate, Long> changesByDate = new LinkedHashMap<>();
        for (ReadingGoalChange change : goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)) {
            changesByDate.put(change.getEffectiveDate(), change.getGoalSeconds());
        }
        GoalSchedule schedule = GoalSchedule.of(changesByDate, currentGoalSeconds);

        return ContributionGraphBuilder.build(secondsByDate, today, schedule::goalFor, manualDates);
    }
}
