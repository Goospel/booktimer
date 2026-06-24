package com.booktimer.session;

import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ReadingDebtService 배선 단위 테스트 (Mockito — DB 무관).
 *
 * <p>부채 계산 자체(경계값)는 {@link WeeklyDebtCalculatorTest}가 본다. 여기선 서비스가
 * ① 유저 타임존으로 "오늘"을 정하고(N-010) ② 타이머에서 하루 목표를 ③ 일자별 집계에서
 * 그날 읽은 양을 가져와 계산기에 올바로 엮는지(배선)만 격리 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingDebtServiceTest {

    private static final long GOAL = 3600L;
    // UTC로는 06-06 저녁이지만 KST로는 06-07 새벽 → "오늘"이 유저 TZ(KST)로 정해지는지 구분되는 시각.
    private static final Instant NOW = Instant.parse("2026-06-06T20:00:00Z");
    private static final LocalDate TODAY_KST = LocalDate.of(2026, 6, 7);

    @Mock
    private ReadingHistoryService historyService;
    @Mock
    private ReadingTimerRepository timerRepository;
    @Mock
    private ReadingGoalChangeRepository goalChangeRepository;

    private ReadingDebtService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        service = new ReadingDebtService(historyService, timerRepository, goalChangeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("타이머 목표 + 유저 TZ 오늘 + 일자별 집계를 엮어 오늘 부채와 빠뜨린 날을 계산한다")
    void weeklyDebt_wiresGoalTodayAndHistory() {
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of()); // 이력 없음 → 타이머 목표 폴백
        when(historyService.dailyHistory(user)).thenReturn(List.of(
                new DailyReadingRecord(TODAY_KST, 1200L, List.of()),          // 오늘 20분 → 부채 2400
                new DailyReadingRecord(TODAY_KST.minusDays(1), GOAL, List.of()), // 어제 달성 → 제외
                new DailyReadingRecord(TODAY_KST.minusDays(3), 600L, List.of())  // 3일 전 10분 → 부채 3000
        ));

        WeeklyDebt debt = service.weeklyDebt(user);

        assertThat(debt.todayDebtSeconds()).isEqualTo(GOAL - 1200L);
        assertThat(debt.missedDays()).extracting(DayDebt::date)
                .doesNotContain(TODAY_KST)                 // 오늘은 헤드라인
                .doesNotContain(TODAY_KST.minusDays(1));   // 달성한 날 제외
        assertThat(debt.missedDays()).contains(new DayDebt(TODAY_KST.minusDays(3), 3000L));
    }

    @Test
    @DisplayName("타이머가 없으면 기본 목표(1시간)로 계산한다 (불변식상 거의 없지만 안전 폴백)")
    void weeklyDebt_noTimer_usesDefaultGoal() {
        when(timerRepository.findByUser(user)).thenReturn(Optional.empty());
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of());
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebt debt = service.weeklyDebt(user);

        assertThat(debt.todayDebtSeconds()).isEqualTo(ReadingDebtService.DEFAULT_GOAL_SECONDS);
    }

    @Test
    @DisplayName("목표 변경 이력으로 과거 날을 그날 목표로 판정한다 — 목표 인상 전 옛 목표를 채운 날은 빠뜨린 날 아님")
    void weeklyDebt_usesGoalHistory_pastDayJudgedByItsOwnGoal() {
        ReadingTimer timer = ReadingTimer.of(3660L); // 현재(폴백) 목표 61분
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        // 가입 무렵 60분 → 어제부터 61분으로 인상
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of(
                ReadingGoalChange.of(user, TODAY_KST.minusDays(5), 3600L),
                ReadingGoalChange.of(user, TODAY_KST.minusDays(1), 3660L)));
        // 이틀 전(인상 전, 목표 60분)에 정확히 60분 읽어 그날 목표를 채움
        when(historyService.dailyHistory(user)).thenReturn(List.of(
                new DailyReadingRecord(TODAY_KST.minusDays(2), 3600L, List.of())));

        WeeklyDebt debt = service.weeklyDebt(user);

        // 현재 목표(3660)로 일괄 판정했다면 60초 부족으로 떴겠지만, 그날 목표(3600)로 보면 부채 0 → 제외
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(TODAY_KST.minusDays(2));
    }

    @Test
    @DisplayName("baseline(첫 목표 기록) 이전 날은 빠뜨린 날에서 제외한다 — 가입(시작) 전 날을 '못 지킴'으로 보지 않음")
    void weeklyDebt_daysBeforeBaseline_excludedFromMissed() {
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        // 목표 이력은 이틀 전(today-2)부터 시작 = 그 전엔 사용자가 목표를 가진 적 없음(가입 전).
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of(
                ReadingGoalChange.of(user, TODAY_KST.minusDays(2), GOAL)));
        // 아무 날도 안 읽음 → 윈도우 전체가 0 읽음.
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebt debt = service.weeklyDebt(user);

        // baseline 이후(today-1, today-2)는 진짜 빠뜨린 날.
        assertThat(debt.missedDays()).extracting(DayDebt::date)
                .contains(TODAY_KST.minusDays(1), TODAY_KST.minusDays(2));
        // baseline 이전(today-3 ~ today-6)은 시작 전이라 제외 — 폴백 목표로 잡히면 안 됨.
        assertThat(debt.missedDays()).extracting(DayDebt::date)
                .doesNotContain(TODAY_KST.minusDays(3), TODAY_KST.minusDays(4),
                        TODAY_KST.minusDays(5), TODAY_KST.minusDays(6));
    }

    // --- weeklyDebtTrace(user, asOf) ---

    @Test
    @DisplayName("weeklyDebtTrace: asOf 윈도우(asOf-6..asOf)로 trace를 구성한다")
    void weeklyDebtTrace_buildsAsOfWindow() {
        LocalDate asOf = TODAY_KST.minusDays(2); // 이틀 전 기준
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of());
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebtTrace trace = service.weeklyDebtTrace(user, asOf);

        assertThat(trace.days()).hasSize(WeeklyDebtCalculator.WINDOW_DAYS);
        assertThat(trace.days().get(WeeklyDebtCalculator.WINDOW_DAYS - 1).date()).isEqualTo(asOf);
        assertThat(trace.days().get(0).date()).isEqualTo(asOf.minusDays(WeeklyDebtCalculator.WINDOW_DAYS - 1));
        assertThat(trace.days().get(WeeklyDebtCalculator.WINDOW_DAYS - 1).isToday()).isTrue(); // asOf = trace의 "today"
    }

    @Test
    @DisplayName("weeklyDebtTrace: baseline 이전 날은 goal=0으로 구성(빠뜨린 날 제외)")
    void weeklyDebtTrace_daysBeforeBaseline_goalZero() {
        LocalDate asOf = TODAY_KST;
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of(
                ReadingGoalChange.of(user, TODAY_KST.minusDays(2), GOAL)));
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebtTrace trace = service.weeklyDebtTrace(user, asOf);

        // baseline 이전(today-3..today-6)은 goal=0이라 deficit 없음
        for (int i = 3; i <= 6; i++) {
            int idx = WeeklyDebtCalculator.WINDOW_DAYS - 1 - i;
            assertThat(trace.days().get(idx).goalSeconds()).isZero();
            assertThat(trace.days().get(idx).rawDeficitSeconds()).isZero();
        }
        // baseline 이후(today-2, today-1, today)는 goal=GOAL
        assertThat(trace.days().get(WeeklyDebtCalculator.WINDOW_DAYS - 3).goalSeconds()).isEqualTo(GOAL);
    }

    @Test
    @DisplayName("weeklyDebtTrace: weeklyDebt(User)와 결과 동치 — asOf=today이면 같은 계산")
    void weeklyDebtTrace_nullAsOf_sameAsWeeklyDebt() {
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of());
        when(historyService.dailyHistory(user)).thenReturn(List.of(
                new DailyReadingRecord(TODAY_KST, 1200L, List.of())));

        WeeklyDebt byWeeklyDebt = service.weeklyDebt(user);
        WeeklyDebt byTrace = service.weeklyDebtTrace(user, TODAY_KST).toWeeklyDebt();

        assertThat(byTrace.todayDebtSeconds()).isEqualTo(byWeeklyDebt.todayDebtSeconds());
        assertThat(byTrace.totalDebtSeconds()).isEqualTo(byWeeklyDebt.totalDebtSeconds());
    }

    // --- todayGoalSeconds (진행바 분모 — 부채 계산과 단일 출처) ---

    @Test
    @DisplayName("weeklyDebt: todayGoalSeconds = 오늘 유효 목표 (trace 마지막 날 goal에서 유도)")
    void weeklyDebt_exposesTodayGoalSeconds() {
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of());
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebt debt = service.weeklyDebt(user);

        assertThat(debt.todayGoalSeconds()).isEqualTo(GOAL);
    }

    @Test
    @DisplayName("weeklyDebt.todayGoalSeconds: 목표 변경 이력 있으면 오늘 유효 목표(최신) 반영")
    void weeklyDebt_todayGoalSeconds_reflectsGoalHistory() {
        ReadingTimer timer = ReadingTimer.of(3660L);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of(
                ReadingGoalChange.of(user, TODAY_KST.minusDays(5), 3600L),
                ReadingGoalChange.of(user, TODAY_KST.minusDays(1), 3660L)));
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        assertThat(service.weeklyDebt(user).todayGoalSeconds()).isEqualTo(3660L);
    }

    @Test
    @DisplayName("todayGoalSeconds(user) == weeklyDebt(user).todayGoalSeconds() — 단일 출처(buildGoalSchedule 중복 호출 제거)")
    void todayGoalSeconds_derivesFromWeeklyDebt() {
        ReadingTimer timer = ReadingTimer.of(7200L);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)).thenReturn(List.of());
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        assertThat(service.todayGoalSeconds(user)).isEqualTo(7200L);
        assertThat(service.todayGoalSeconds(user)).isEqualTo(service.weeklyDebt(user).todayGoalSeconds());
    }
}
