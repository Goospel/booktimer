package com.booktimer.session;

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

    private ReadingDebtService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        service = new ReadingDebtService(historyService, timerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("타이머 목표 + 유저 TZ 오늘 + 일자별 집계를 엮어 오늘 부채와 빠뜨린 날을 계산한다")
    void weeklyDebt_wiresGoalTodayAndHistory() {
        ReadingTimer timer = ReadingTimer.of(GOAL);
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
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
        when(historyService.dailyHistory(user)).thenReturn(List.of());

        WeeklyDebt debt = service.weeklyDebt(user);

        assertThat(debt.todayDebtSeconds()).isEqualTo(ReadingDebtService.DEFAULT_GOAL_SECONDS);
    }
}
